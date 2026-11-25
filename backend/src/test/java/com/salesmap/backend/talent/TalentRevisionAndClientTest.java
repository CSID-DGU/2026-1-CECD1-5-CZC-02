package com.salesmap.backend.talent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.salesmap.backend.analysis.entity.AnalysisRevision;
import com.salesmap.backend.analysis.repository.AnalysisRevisionRepository;
import com.salesmap.backend.minutes.ContractSchemaValidator;
import com.salesmap.backend.revision.dto.RevisionCreateRequest;
import com.salesmap.backend.revision.service.RevisionService;
import com.salesmap.backend.talent.ResumeExtractionClient.ExtractionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TalentRevisionAndClientTest {
    final ObjectMapper mapper = new ObjectMapper();

    @Test
    void revisionServiceAcceptsResumeValuesFromContract() throws Exception {
        JsonNode schema = mapper.readTree(ContractSchemaValidator.contractsDir().resolve("revision.schema.json").toFile());
        List<String> screens = new ArrayList<>();
        schema.path("properties").path("screen").path("enum").forEach(node -> screens.add(node.asText()));
        List<String> targets = new ArrayList<>();
        schema.path("properties").path("targetType").path("enum").forEach(node -> targets.add(node.asText()));
        assertThat(screens).contains(TalentService.SCREEN);
        assertThat(targets).contains(TalentService.TARGET_TYPE);

        AnalysisRevisionRepository repository = mock(AnalysisRevisionRepository.class);
        when(repository.save(any(AnalysisRevision.class))).thenAnswer(call -> {
            AnalysisRevision revision = call.getArgument(0);
            ReflectionTestUtils.setField(revision, "id", 5L);
            return revision;
        });
        RevisionService revisions = new RevisionService(repository, mapper);
        var saved = revisions.create(new RevisionCreateRequest(TalentService.SCREEN, TalentService.TARGET_TYPE, "rp_1",
                "grade", null, TextNode.valueOf("EXPERT"), "EDITED", "RULE", null), 7L);
        assertThat(saved.targetType()).isEqualTo("RESUME_PROFILE");
    }

    HttpResumeExtractionClient client(MockRestServiceServer[] holder) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        holder[0] = MockRestServiceServer.bindTo(builder).build();
        return new HttpResumeExtractionClient(builder.build(), mapper);
    }

    @Test
    void clientSendsBase64AndReadsProfileAndSourceText() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        HttpResumeExtractionClient client = client(server);
        server[0].expect(requestTo("http://ai.test/resumes/extract")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.profileId").value("rp_3"))
                .andExpect(jsonPath("$.contentBase64").value("AQI="))
                .andExpect(jsonPath("$.sourceRefs[0].sourceId").value("src_resume_3"))
                .andRespond(withSuccess("{\"resumeProfile\":{\"profileId\":\"rp_3\"},\"sourceText\":\"샘플 원문\","
                        + "\"llmProvider\":\"ollama\",\"llmModel\":\"fake\",\"llmFallbackReason\":null}", MediaType.APPLICATION_JSON));
        var result = client.extract("샘플.docx", new byte[]{1, 2}, "rp_3", "src_resume_3");
        assertThat(result.resumeProfile().path("profileId").asText()).isEqualTo("rp_3");
        assertThat(result.sourceText()).isEqualTo("샘플 원문");
        assertThat(result.llmFallbackReason()).isNull();
        server[0].verify();
    }

    @Test
    void clientPassesAiGuidanceFor422ButHidesOtherErrors() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        HttpResumeExtractionClient client = client(server);
        server[0].expect(requestTo("http://ai.test/resumes/extract")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON).body("{\"detail\":\"워드(.docx)만 읽을 수 있습니다.\"}"));
        assertThatThrownBy(() -> client.extract("모의.docx", new byte[]{1}, "rp_1", "src_resume_1"))
                .isInstanceOf(ExtractionException.class).hasMessage("워드(.docx)만 읽을 수 있습니다.");

        server[0].reset();
        server[0].expect(requestTo("http://ai.test/resumes/extract")).andRespond(withServerError().body("원문 조각"));
        assertThatThrownBy(() -> client.extract("모의.docx", new byte[]{1}, "rp_1", "src_resume_1"))
                .isInstanceOf(ExtractionException.class).hasMessage("이력서 분석 서버 오류 (500)");
    }
}
