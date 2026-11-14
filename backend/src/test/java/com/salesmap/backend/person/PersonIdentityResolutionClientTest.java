package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmap.backend.minutes.ContractSchemaValidator;
import com.salesmap.backend.minutes.client.AttendeeMention;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.Arrays;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PersonIdentityResolutionClientTest {
    final ObjectMapper mapper = new ObjectMapper();
    final PersonService people = mock(PersonService.class);
    final PersonIdentityService identities=mock(PersonIdentityService.class);
    MockRestServiceServer server;
    PersonIdentityResolutionClient client;
    PersonDocument person;

    @BeforeEach void setup() throws Exception {
        when(identities.storeProposals(anyString(),anyList(),anyLong())).thenAnswer(call -> call.getArgument(1));
        person=PersonRequest.parse(mapper.readTree(PersonServiceTest.body())).withId(17L);
        when(people.forResolution(7L)).thenReturn(List.of(person));
        RestClient.Builder builder=RestClient.builder().baseUrl("http://identity.test");
        server=MockRestServiceServer.bindTo(builder).build();
        client=new PersonIdentityResolutionClient(people,identities,mapper,builder.build());
    }

    ObjectNode mention(String name, String email, String channel) {
        return mapper.createObjectNode().put("name",name).put("email",email).put("channel",channel).put("sourceRef","src_101");
    }

    ObjectNode result(ObjectNode mention, String decision) {
        ObjectNode row=mapper.createObjectNode().put("resolutionId","ir_test_1").put("decision",decision)
                .put("decidedBy","RULE").put("modelVersion","identity-rule-0.2-d022");
        row.set("mention",mention); row.putNull("personId");
        ArrayNode candidates=row.putArray("candidates");
        if (!decision.equals("NEW")) {
            ObjectNode candidate=candidates.addObject().put("personId","p_17").putNull("score");
            candidate.putArray("reasons").add("등록된 인물 단서 일치 — 확인 필요");
        }
        if (decision.equals("CONFIRMED")) row.put("personId","p_17");
        return row;
    }

    @Test void postsCompleteOwnedRosterAndOriginalNameEmailChannelSourceWithoutNetwork() throws Exception {
        ObjectNode observed=mention("가상인물_0001","synthetic@people.example","EMAIL");
        ObjectNode expected=mapper.createObjectNode(); expected.putArray("mentions").add(observed);
        expected.set("persons",mapper.valueToTree(List.of(person)));
        ObjectNode answer=result(observed,"CONFIRMED");
        server.expect(requestTo("http://identity.test/identity/resolve")).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(content().json(expected.toString(),JsonCompareMode.STRICT))
                .andRespond(withSuccess(mapper.createArrayNode().add(answer).toString(),MediaType.APPLICATION_JSON));
        List<JsonNode> response=client.resolve("src_101",List.of(new AttendeeMention("가상인물_0001","synthetic@people.example","EMAIL")),7L);
        assertThat(response).containsExactly(answer);
        assertThat(ContractSchemaValidator.load("identity_resolution.schema.json").validate(response.get(0))).isEmpty();
        verify(people).forResolution(7L); server.verify();
    }

    @Test void acceptsModelCandidatesAndNewWithoutTurningThemIntoConfirmed() throws Exception {
        ObjectNode one=mention("가상인물_오타",null,"MEETING");
        ObjectNode two=mention("가상인물_미등록",null,"MEETING");
        ObjectNode a=result(one,"CANDIDATES").put("decidedBy","MODEL").put("modelVersion","identity-logistic-v1");
        ((ObjectNode)a.path("candidates").get(0)).put("score",0.72);
        ObjectNode b=result(two,"NEW").put("resolutionId","ir_test_2").put("decidedBy","MODEL").put("modelVersion","identity-logistic-v1");
        server.expect(requestTo("http://identity.test/identity/resolve"))
                .andRespond(withSuccess(mapper.createArrayNode().add(a).add(b).toString(),MediaType.APPLICATION_JSON));
        var response=client.resolve("src_101",List.of(new AttendeeMention("가상인물_오타",null,"MEETING"),
                new AttendeeMention("가상인물_미등록",null,"MEETING")),7L);
        assertThat(response).containsExactly(a,b);
        for(JsonNode row:response) assertThat(ContractSchemaValidator.load("identity_resolution.schema.json").validate(row)).isEmpty();
        verify(identities).storeProposals("src_101",response,7L);server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings={"confirmed","negative","over_one","null_score","text_score","missing_version","blank_version","human"})
    void rejectsUnsafeModelResponses(String fault) {
        ObjectNode observed=mention("가상인물_0001",null,"MEETING");
        ObjectNode row=result(observed,"CANDIDATES").put("decidedBy","MODEL").put("modelVersion","identity-logistic-v1");
        ObjectNode candidate=(ObjectNode)row.path("candidates").get(0);candidate.put("score",0.7);
        switch(fault) {
            case "confirmed" -> row.put("decision","CONFIRMED").put("personId","p_17");
            case "negative" -> candidate.put("score",-0.1);
            case "over_one" -> candidate.put("score",1.1);
            case "null_score" -> candidate.putNull("score");
            case "text_score" -> candidate.put("score","0.7");
            case "missing_version" -> row.remove("modelVersion");
            case "blank_version" -> row.put("modelVersion"," ");
            case "human" -> row.put("decidedBy","HUMAN");
            default -> throw new IllegalArgumentException();
        }
        server.expect(requestTo("http://identity.test/identity/resolve"))
                .andRespond(withSuccess(mapper.createArrayNode().add(row).toString(),MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.resolve("src_101",List.of(new AttendeeMention("가상인물_0001",null,"MEETING")),7L))
                .isInstanceOf(RestClientException.class);
        verifyNoInteractions(identities);server.verify();
    }

    @Test void batchOrderAndUnknownNameArePreservedRatherThanFillingNameFromEmail() {
        ObjectNode one=mention("가상인물_0001",null,"MEETING");
        ObjectNode two=mention(null,"synthetic@people.example","EMAIL");
        ObjectNode a=result(one,"CANDIDATES"), b=result(two,"CONFIRMED"); b.put("resolutionId","ir_test_2");
        server.expect(requestTo("http://identity.test/identity/resolve"))
                .andExpect(content().json("{\"mentions\":["+one+","+two+"]}",JsonCompareMode.LENIENT))
                .andRespond(withSuccess(mapper.createArrayNode().add(a).add(b).toString(),MediaType.APPLICATION_JSON));
        List<JsonNode> response=client.resolve("src_101",List.of(new AttendeeMention("가상인물_0001",null,"MEETING"),
                new AttendeeMention(null,"synthetic@people.example","EMAIL")),7L);
        assertThat(response.get(1).path("mention").get("name").isNull()).isTrue();
        assertThat(response).extracting(r -> r.path("decision").asText()).containsExactly("CANDIDATES","CONFIRMED");
        server.verify();
    }

    @Test void emptyRosterStillAsksApiForNewWithoutLoadingSamples() {
        when(people.forResolution(8L)).thenReturn(List.of());
        ObjectNode observed=mention(null,null,"MEETING");
        server.expect(requestTo("http://identity.test/identity/resolve"))
                .andExpect(content().json("{\"persons\":[]}",JsonCompareMode.LENIENT))
                .andRespond(withSuccess(mapper.createArrayNode().add(result(observed,"NEW")).toString(),MediaType.APPLICATION_JSON));
        var response=client.resolve("src_101",List.of(new AttendeeMention(null,null,"MEETING")),8L);
        assertThat(response.get(0).path("decision").asText()).isEqualTo("NEW");
        verify(people,never()).forResolution(7L); server.verify();
    }

    @Test void emptyAttendeesDoNotReadRosterOrCallHttp() {
        assertThat(client.resolve("src_101",List.of(),7L)).isEmpty();
        verifyNoInteractions(people); server.verify();
    }

    @Test void invalidOwnerOrSourceOrChannelNeverCallsHttpOrReadsRoster() {
        List<AttendeeMention> valid=List.of(new AttendeeMention("가상인물",null,"MEETING"));
        assertThatThrownBy(() -> client.resolve("src_101",valid,null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> client.resolve("src_0",valid,7L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.resolve("src_101",null,7L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.resolve("src_101",Arrays.asList((AttendeeMention)null),7L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.resolve("src_101",List.of(new AttendeeMention(null,null,null)),7L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.resolve("src_101",List.of(new AttendeeMention(null,null,"UNKNOWN")),7L)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(people); server.verify();
    }

    @Test void httpFailureIsNotConvertedToNewAndUpstreamBodyIsNotCopiedIntoLogs() {
        server.expect(requestTo("http://identity.test/identity/resolve"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("upstream_secret_marker").contentType(MediaType.TEXT_PLAIN));
        assertThatThrownBy(() -> client.resolve("src_101",List.of(new AttendeeMention("가상인물",null,"MEETING")),7L))
                .isInstanceOf(RestClientException.class).hasMessageContaining("502").hasMessageNotContaining("upstream_secret_marker")
                .hasNoCause();
        server.verify();
    }


    @Test void sameDisplayNameWithDifferentEmailsCannotBecomeOneWrongMeetingPerson() {
        ObjectNode one=mention("가상인물_0001","synthetic1@people.example","EMAIL");
        ObjectNode two=mention("가상 인물_0001","synthetic2@people.example","EMAIL");
        ObjectNode a=result(one,"CONFIRMED"), b=result(two,"CANDIDATES"); b.put("resolutionId","ir_test_2");
        server.expect(requestTo("http://identity.test/identity/resolve"))
                .andRespond(withSuccess(mapper.createArrayNode().add(a).add(b).toString(),MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.resolve("src_101",List.of(
                new AttendeeMention("가상인물_0001","synthetic1@people.example","EMAIL"),
                new AttendeeMention("가상 인물_0001","synthetic2@people.example","EMAIL")),7L))
                .isInstanceOf(RestClientException.class).hasMessageContaining("구분할 수 없습니다");
        server.verify();
    }

    @Test void transportFailureIsNotReportedAsNew() {
        server.expect(requestTo("http://identity.test/identity/resolve"))
                .andRespond(withException(new java.net.SocketTimeoutException("test_timeout_marker")));
        assertThatThrownBy(() -> client.resolve("src_101",List.of(new AttendeeMention("가상인물",null,"MEETING")),7L))
                .isInstanceOf(RestClientException.class).hasMessageContaining("실패").hasMessageNotContaining("test_timeout_marker");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings={"foreign_person","wrong_mention","bad_id","missing_field","extra_field","numeric_score",
            "empty_reasons","duplicate_candidate","new_with_candidates","candidates_with_person","confirmed_without_person",
            "human","wrong_count","duplicate_resolution","wrong_type","too_many","null_body","bad_model_version"})
    void rejectsMalformedUncorrelatedOrForeignRosterReferences(String fault) {
        ObjectNode observed=mention("가상인물_0001",null,"MEETING");
        ObjectNode row=result(observed.deepCopy(),"CANDIDATES");
        ArrayNode response=mapper.createArrayNode().add(row);
        switch (fault) {
            case "foreign_person" -> ((ObjectNode)row.path("candidates").get(0)).put("personId","p_999");
            case "wrong_mention" -> ((ObjectNode)row.get("mention")).put("sourceRef","src_999");
            case "bad_id" -> row.put("resolutionId","bad-id");
            case "missing_field" -> row.remove("decision");
            case "extra_field" -> row.put("extra",true);
            case "numeric_score" -> ((ObjectNode)row.path("candidates").get(0)).put("score",0.9);
            case "empty_reasons" -> ((ObjectNode)row.path("candidates").get(0)).putArray("reasons");
            case "duplicate_candidate" -> ((ArrayNode)row.get("candidates")).add(row.path("candidates").get(0).deepCopy());
            case "new_with_candidates" -> row.put("decision","NEW");
            case "candidates_with_person" -> row.put("personId","p_17");
            case "confirmed_without_person" -> row.put("decision","CONFIRMED");
            case "human" -> row.put("decidedBy","HUMAN");
            case "wrong_count" -> response.removeAll();
            case "duplicate_resolution" -> response.add(row.deepCopy());
            case "wrong_type" -> row.put("candidates","bad");
            case "too_many" -> { for(int n=0;n<3;n++) ((ArrayNode)row.get("candidates")).add(row.path("candidates").get(0).deepCopy()); }
            case "bad_model_version" -> row.put("modelVersion",17);
            default -> { }
        }
        server.expect(requestTo("http://identity.test/identity/resolve"))
                .andRespond(withSuccess(fault.equals("null_body") ? "null" : response.toString(),MediaType.APPLICATION_JSON));
        List<AttendeeMention> mentions=fault.equals("duplicate_resolution")
                ? List.of(new AttendeeMention("가상인물_0001",null,"MEETING"),new AttendeeMention("가상인물_0001",null,"MEETING"))
                : List.of(new AttendeeMention("가상인물_0001",null,"MEETING"));
        assertThatThrownBy(() -> client.resolve("src_101",mentions,7L)).isInstanceOf(RestClientException.class);
        server.verify();
    }
}
