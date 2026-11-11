package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.ai.client.AiClient;
import com.salesmap.backend.ai.dto.AiMinutesRequest;
import com.salesmap.backend.ai.exception.AiClientException;
import com.salesmap.backend.minutes.ContractSchemaValidator;
import com.salesmap.backend.minutes.client.RevisionClient;
import com.salesmap.backend.minutes.client.TaskDraftClient;
import com.salesmap.backend.minutes.dto.MeetingNoteCreateRequest;
import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.repository.MeetingNoteRepository;
import com.salesmap.backend.minutes.service.MeetingNoteService;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.entity.UserStatus;
import com.salesmap.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** B의 실제 초안 서비스에 A 실제 클라이언트를 주입한다. B 소스·실제 DB·외부 네트워크는 바꾸지 않는다. */
class PersonMeetingIdentityFlowTest {
    final ObjectMapper mapper = new ObjectMapper();
    final PersonService people = mock(PersonService.class);
    final UserRepository users = mock(UserRepository.class);
    final SourceRepository sources = mock(SourceRepository.class);
    final MeetingNoteRepository notes = mock(MeetingNoteRepository.class);
    final AiClient minutes = mock(AiClient.class);
    final RevisionClient revisions = mock(RevisionClient.class);
    final TaskDraftClient tasks = mock(TaskDraftClient.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final PersonIdentityService identities=mock(PersonIdentityService.class);
    MockRestServiceServer server;
    MeetingNoteService flow;

    @BeforeEach void setup() throws Exception {
        when(identities.storeProposals(anyString(),anyList(),anyLong())).thenAnswer(call -> call.getArgument(1));
        var user = new User("synthetic@people.example","가상사용자_001","unused","USER",UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user,"id",7L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(sources.save(any(Source.class))).thenAnswer(call -> {
            Source source=call.getArgument(0); ReflectionTestUtils.setField(source,"id",101L); return source;
        });
        when(notes.save(any(MeetingNote.class))).thenAnswer(call -> {
            MeetingNote note=call.getArgument(0); ReflectionTestUtils.setField(note,"id",21L); return note;
        });
        when(people.forResolution(7L)).thenReturn(List.of(PersonRequest.parse(mapper.readTree(PersonServiceTest.body())).withId(17L)));
        RestClient.Builder builder=RestClient.builder().baseUrl("http://identity.test");
        server=MockRestServiceServer.bindTo(builder).build();
        var identity=new PersonIdentityResolutionClient(people,identities,mapper,builder.build());
        flow=new MeetingNoteService(notes,sources,users,minutes,identity,tasks,revisions,events,mapper);
        // B의 기존 실패 처리도 실제로 통과시켜 회의록 내용 없이 연결 정보가 보존되는지 검사한다.
        when(minutes.generateMinutes(any())).thenThrow(new AiClientException("테스트용 회의록 엔진 실패"));
        when(tasks.draftTasks(any(),anyLong())).thenReturn(List.of());
    }

    MeetingNoteCreateRequest request() {
        return new MeetingNoteCreateRequest(null,null,List.of("가상인물_0001"),"가상인물_0001과 미팅했다.","FULL",null);
    }

    @Test void draftCallsRealIdentityAdapterAndKeepsCandidateResolutionForHumanReview() throws Exception {
        String response="""
                [{"resolutionId":"ir_actual_a","mention":{"name":"가상인물_0001","email":null,
                "channel":"MEETING","sourceRef":"src_101"},"decision":"CANDIDATES","personId":null,
                "candidates":[{"personId":"p_17","score":null,"reasons":["이름 일치 — 사용자 확인 필요"]}],
                "decidedBy":"RULE","modelVersion":"identity-rule-0.2-d022"}]
                """;
        server.expect(requestTo("http://identity.test/identity/resolve"))
                .andRespond(withSuccess(response,MediaType.APPLICATION_JSON));
        var draft=flow.createDraft(request(),7L);
        assertThat(draft.identityResolutions()).hasSize(1);
        assertThat(draft.note().meetingNote().participants().get(0).resolutionId()).isEqualTo("ir_actual_a");
        assertThat(draft.note().meetingNote().participants().get(0).personId()).isNull();
        assertThat(draft.note().meetingNote().status()).isEqualTo("DRAFT");
        assertThat(ContractSchemaValidator.load("meeting_note.schema.json").validate(mapper.valueToTree(draft.note().meetingNote()))).isEmpty();
        assertThat(ContractSchemaValidator.load("identity_resolution.schema.json").validate(draft.identityResolutions().get(0))).isEmpty();
        ArgumentCaptor<AiMinutesRequest> passed=ArgumentCaptor.forClass(AiMinutesRequest.class);
        verify(minutes).generateMinutes(passed.capture());
        assertThat(passed.getValue().identityResolutions()).isEqualTo(draft.identityResolutions());
        verify(people).forResolution(7L);
        verifyNoInteractions(revisions,events); // 초안 작성이 사람 선택·확정이나 외부 등록을 하지 않는다.
        server.verify();
    }

    @Test void identityApiFailureLeavesUnlinkedDraftAndDoesNotInventNewOrConfirmed() {
        server.expect(requestTo("http://identity.test/identity/resolve"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var draft=flow.createDraft(request(),7L);
        assertThat(draft.identityResolutions()).isEmpty();
        assertThat(draft.note().meetingNote().participants().get(0).personId()).isNull();
        assertThat(draft.note().meetingNote().participants().get(0).resolutionId()).isNull();
        assertThat(draft.note().meetingNote().status()).isEqualTo("DRAFT");
        verify(notes).save(any());
        verifyNoInteractions(revisions,events);
        server.verify();
    }
}
