package com.salesmap.backend.minutes.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.ai.client.AiClient;
import com.salesmap.backend.ai.client.MockAiClient;
import com.salesmap.backend.ai.config.AiModuleProperties;
import com.salesmap.backend.ai.exception.AiClientException;
import com.salesmap.backend.minutes.client.FakeIdentityResolutionClient;
import com.salesmap.backend.minutes.client.RevisionClient;
import com.salesmap.backend.minutes.client.RevisionRecord;
import com.salesmap.backend.minutes.client.TaskDraftClient;
import com.salesmap.backend.minutes.dto.MeetingNoteCreateRequest;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;
import com.salesmap.backend.minutes.dto.MeetingNoteDraftResponse;
import com.salesmap.backend.minutes.dto.MeetingNoteResponse;
import com.salesmap.backend.minutes.dto.MeetingNoteUpdateRequest;
import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.entity.MeetingNoteStatus;
import com.salesmap.backend.minutes.repository.MeetingNoteRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceType;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.entity.UserStatus;
import com.salesmap.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AI_MODULE_MODE=mock + A·C 가짜 구현으로 메모 → 초안 → 수정 → 확정이 도는지 (MySQL·네트워크 없음). */
class MeetingNoteServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SOURCE_ID = 101L;
    private static final Long NOTE_ID = 7L;
    private static final String MEMO = "홍 부장님이 내년 상반기에 개발자 두 명 정도 더 필요하다고 하심\n"
            + "계약서 초안은 이번 주 금요일까지 보내드리기로 함\n"
            + "11월 첫 주에 다시 뵙기로";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MeetingNoteRepository meetingNoteRepository = mock(MeetingNoteRepository.class);
    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RevisionClient revisionClient = mock(RevisionClient.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    // C 할 일은 C 패키지 테스트가 따로 검사한다. 여기서는 B가 부르는 순서·결과 전달만 본다
    private final TaskDraftClient taskDraftClient = mock(TaskDraftClient.class);
    private final AtomicReference<MeetingNote> savedNote = new AtomicReference<>();
    private final AtomicReference<Source> savedSource = new AtomicReference<>();
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("sales@example.com", "영업담당", "pw", "USER", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(sourceRepository.save(any(Source.class))).thenAnswer(invocation -> {
            Source source = invocation.getArgument(0);
            ReflectionTestUtils.setField(source, "id", SOURCE_ID);
            savedSource.set(source);
            return source;
        });
        when(meetingNoteRepository.save(any(MeetingNote.class))).thenAnswer(invocation -> {
            MeetingNote note = invocation.getArgument(0);
            ReflectionTestUtils.setField(note, "id", NOTE_ID);
            savedNote.set(note);
            return note;
        });
        when(meetingNoteRepository.findById(NOTE_ID)).thenAnswer(invocation -> Optional.ofNullable(savedNote.get()));
        JsonNode task = objectMapper.createObjectNode().put("taskId", "t_1").put("noteId", "mn_7").put("status", "DRAFT");
        when(taskDraftClient.draftTasks(any(), anyLong())).thenReturn(List.of(task));
    }

    @Test
    void 메모로_초안을_만들면_원본을_남기고_A_B_C_결과를_묶어_돌려준다() {
        MeetingNoteDraftResponse response = service(mockAiClient())
                .createDraft(fullRequest(List.of("홍 부장", "최가상")), USER_ID);

        Source source = savedSource.get();
        assertThat(source.getSourceType()).isEqualTo(SourceType.MEETING_NOTE);
        assertThat(source.getContent()).isEqualTo(MEMO);
        assertThat(source.getTitle()).isEqualTo("미팅 메모 2026-10-14 15:00 · 가상전자 본사");

        MeetingNoteDocument note = response.note().meetingNote();
        assertThat(note.noteId()).isEqualTo("mn_7");
        assertThat(note.status()).isEqualTo("DRAFT");
        assertThat(note.sourceRefs()).containsExactly(new MeetingNoteDocument.SourceRef("src_101", "MEETING_NOTE"));
        assertThat(note.occurredAt()).isEqualTo("2026-10-14T15:00:00");
        // A 가짜: "홍 부장"은 샘플 ir_002(후보 상태라 personId null), 샘플에 없는 이름은 식별 결과 없이 둔다
        assertThat(note.participants()).containsExactly(
                new MeetingNoteDocument.Participant("홍 부장", "ir_002", null),
                new MeetingNoteDocument.Participant("최가상", null, null)
        );
        assertThat(response.identityResolutions()).hasSize(1);
        assertThat(response.identityResolutions().get(0).path("mention").path("sourceRef").asText()).isEqualTo("src_101");
        assertThat(note.missingFields()).containsExactly("concerns");
        assertThat(response.note().source().content()).isEqualTo(MEMO);
        assertThat(response.note().llmProvider()).isEqualTo("mock");

        // C: 저장된 회의록(mn_7)으로 할 일 초안을 만들고 결과를 그대로 묶어 준다
        ArgumentCaptor<MeetingNoteDocument> noteCaptor = ArgumentCaptor.forClass(MeetingNoteDocument.class);
        verify(taskDraftClient).draftTasks(noteCaptor.capture(), eq(USER_ID));
        assertThat(noteCaptor.getValue().noteId()).isEqualTo("mn_7");
        assertThat(response.tasks()).extracting(task -> task.path("taskId").asText()).containsExactly("t_1");
        verify(revisionClient, never()).record(any(), any());
    }

    @Test
    void C_할_일_초안이_실패해도_회의록은_저장한다() {
        when(taskDraftClient.draftTasks(any(), anyLong())).thenThrow(new IllegalStateException("할 일 API 응답이 없습니다."));

        MeetingNoteDraftResponse response = service(mockAiClient()).createDraft(fullRequest(List.of()), USER_ID);

        assertThat(response.note().meetingNote().noteId()).isEqualTo("mn_7");
        assertThat(response.tasks()).isEmpty();
    }

    @Test
    void AI_모듈_호출이_실패하면_빈_회의록을_만들고_원본은_남긴다() {
        AiClient failingAiClient = mock(AiClient.class);
        when(failingAiClient.generateMinutes(any())).thenThrow(new AiClientException("AI Module 호출에 실패했습니다."));

        MeetingNoteDraftResponse response = service(failingAiClient)
                .createDraft(fullRequest(List.of("홍 부장")), USER_ID);

        MeetingNoteDocument note = response.note().meetingNote();
        assertThat(savedSource.get()).isNotNull();
        assertThat(note.status()).isEqualTo("DRAFT");
        assertThat(note.generatedBy()).isEqualTo("RULE");
        assertThat(note.fields().discussion()).isEmpty();
        assertThat(note.fields().commitments()).isEmpty();
        assertThat(note.missingFields())
                .containsExactly("discussion", "customerRequests", "concerns", "commitments", "nextContact");
        assertThat(note.participants()).containsExactly(new MeetingNoteDocument.Participant("홍 부장", "ir_002", null));
        assertThat(response.note().llmProvider()).isEqualTo("rule");
        assertThat(response.note().llmFallbackReason()).contains("AI Module 호출에 실패했습니다.");
    }

    @Test
    void 문항을_고치면_바뀐_문항만_AI_원본값과_함께_수정기록을_남긴다() {
        MeetingNoteService service = service(mockAiClient());
        service.createDraft(fullRequest(List.of("홍 부장")), USER_ID);
        MeetingNoteDocument.Item concern = new MeetingNoteDocument.Item("예산 확정 시기 불확실", "예산은 아직 확정 전이라고 하심");

        MeetingNoteResponse response = service.updateMeetingNote(
                "mn_7",
                new MeetingNoteUpdateRequest(fieldsUpdate(null, List.of(concern)), null),
                USER_ID
        );

        assertThat(response.meetingNote().fields().concerns()).containsExactly(concern);
        assertThat(response.meetingNote().missingFields()).isEmpty();

        ArgumentCaptor<RevisionRecord> captor = ArgumentCaptor.forClass(RevisionRecord.class);
        verify(revisionClient, times(1)).record(captor.capture(), eq(USER_ID));
        RevisionRecord revision = captor.getValue();
        assertThat(revision.screen()).isEqualTo("MEETING_NOTE_REVIEW");
        assertThat(revision.targetType()).isEqualTo("MEETING_NOTE");
        assertThat(revision.targetId()).isEqualTo("mn_7");
        assertThat(revision.fieldName()).isEqualTo("fields.concerns");
        assertThat(revision.proposedValue().isArray()).isTrue();
        assertThat(revision.proposedValue()).isEmpty();
        assertThat(revision.finalValue().get(0).path("evidence").asText()).isEqualTo("예산은 아직 확정 전이라고 하심");
        assertThat(revision.action()).isEqualTo("EDITED");
        assertThat(revision.proposedBy()).isEqualTo("LLM");
        assertThat(revision.actorId()).isEqualTo("user_1");
        assertThat(revision.decidedAt()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    void 같은_값을_다시_보내면_기록하지_않는다() {
        MeetingNoteService service = service(mockAiClient());
        MeetingNoteDocument draft = service.createDraft(fullRequest(List.of()), USER_ID).note().meetingNote();

        service.updateMeetingNote(
                "mn_7",
                new MeetingNoteUpdateRequest(fieldsUpdate(draft.fields().commitments(), null), null),
                USER_ID
        );

        verify(revisionClient, never()).record(any(), any());
    }

    @Test
    void 근거가_빈_항목은_받지_않는다() {
        MeetingNoteService service = service(mockAiClient());
        service.createDraft(fullRequest(List.of()), USER_ID);
        MeetingNoteDocument.Item noEvidence = new MeetingNoteDocument.Item("근거 없는 약속", " ");

        assertThatThrownBy(() -> service.updateMeetingNote(
                "mn_7",
                new MeetingNoteUpdateRequest(fieldsUpdate(List.of(noEvidence), null), null),
                USER_ID
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 확정하면_고치지_않은_문항은_ACCEPTED로_기록하고_C_D에_넘긴다() {
        MeetingNoteService service = service(mockAiClient());
        service.createDraft(fullRequest(List.of("홍 부장")), USER_ID);
        service.updateMeetingNote(
                "mn_7",
                new MeetingNoteUpdateRequest(fieldsUpdate(null, List.of(new MeetingNoteDocument.Item("예산 불확실", "예산은 미정"))), null),
                USER_ID
        );

        MeetingNoteResponse response = service.confirmMeetingNote("mn_7", USER_ID);

        assertThat(response.meetingNote().status()).isEqualTo("CONFIRMED");
        assertThat(response.confirmedAt()).isNotNull();
        assertThat(response.confirmedWithoutEdit()).isFalse();

        ArgumentCaptor<RevisionRecord> captor = ArgumentCaptor.forClass(RevisionRecord.class);
        verify(revisionClient, times(5)).record(captor.capture(), eq(USER_ID));
        List<RevisionRecord> accepted = captor.getAllValues().stream()
                .filter(revision -> "ACCEPTED".equals(revision.action()))
                .toList();
        assertThat(accepted).extracting(RevisionRecord::fieldName)
                .containsExactly("fields.discussion", "fields.customerRequests", "fields.commitments", "fields.nextContact");
        assertThat(accepted).allSatisfy(revision -> assertThat(revision.finalValue()).isEqualTo(revision.proposedValue()));

        verify(taskDraftClient).confirmTasks(any(), eq(USER_ID));
        ArgumentCaptor<MeetingNoteConfirmedEvent> event = ArgumentCaptor.forClass(MeetingNoteConfirmedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().note().status()).isEqualTo("CONFIRMED");
        assertThat(event.getValue().userId()).isEqualTo(USER_ID);
    }

    @Test
    void 확인_안_한_할_일이_남아_있으면_C가_거부해_회의록을_확정하지_않는다() {
        MeetingNoteService service = service(mockAiClient());
        service.createDraft(fullRequest(List.of()), USER_ID);
        doThrow(new IllegalArgumentException("회의록의 할 일을 먼저 확인하여 확정하거나 취소해 주세요."))
                .when(taskDraftClient).confirmTasks(any(), eq(USER_ID));

        assertThatThrownBy(() -> service.confirmMeetingNote("mn_7", USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("할 일을 먼저 확인");

        // C 확인이 가장 먼저라 기록·상태 변경·일정 전달이 하나도 일어나지 않는다
        assertThat(savedNote.get().getStatus()).isEqualTo(MeetingNoteStatus.DRAFT);
        verify(revisionClient, never()).record(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 수정_없이_확정하면_무수정_확정으로_표시한다() {
        MeetingNoteService service = service(mockAiClient());
        service.createDraft(fullRequest(List.of()), USER_ID);

        MeetingNoteResponse response = service.confirmMeetingNote("mn_7", USER_ID);

        assertThat(response.confirmedWithoutEdit()).isTrue();
        verify(revisionClient, times(5)).record(any(), eq(USER_ID));
    }

    @Test
    void 확정된_회의록은_고치거나_다시_확정할_수_없다() {
        MeetingNoteService service = service(mockAiClient());
        service.createDraft(fullRequest(List.of()), USER_ID);
        service.confirmMeetingNote("mn_7", USER_ID);

        assertThatThrownBy(() -> service.updateMeetingNote(
                "mn_7", new MeetingNoteUpdateRequest(fieldsUpdate(List.of(), null), null), USER_ID
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.confirmMeetingNote("mn_7", USER_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void LIGHT_회의록은_사람별_칸_단위로_고치고_기록한다() {
        MeetingNoteService service = service(mockAiClient());
        MeetingNoteDocument draft = service.createDraft(lightRequest(List.of("홍 부장", "박가상")), USER_ID)
                .note().meetingNote();
        assertThat(draft.mode()).isEqualTo("LIGHT");
        assertThat(draft.perPerson()).hasSize(2);
        assertThat(draft.missingFields()).hasSize(6).contains("perPerson[0].status", "perPerson[1].followUp");

        MeetingNoteDocument.Item status = new MeetingNoteDocument.Item("신규 과제 검토 중", "홍 부장 신규 과제 검토 중");
        MeetingNoteResponse response = service.updateMeetingNote(
                "mn_7",
                new MeetingNoteUpdateRequest(null, List.of(
                        new MeetingNoteDocument.PerPerson("무시되는 이름", "p_999", status, null, null),
                        new MeetingNoteDocument.PerPerson("박가상", null, null, null, null)
                )),
                USER_ID
        );

        MeetingNoteDocument.PerPerson first = response.meetingNote().perPerson().get(0);
        assertThat(first.displayName()).isEqualTo("홍 부장");
        assertThat(first.personId()).isNull();
        assertThat(first.status()).isEqualTo(status);
        assertThat(response.meetingNote().missingFields()).hasSize(5).doesNotContain("perPerson[0].status");

        ArgumentCaptor<RevisionRecord> captor = ArgumentCaptor.forClass(RevisionRecord.class);
        verify(revisionClient).record(captor.capture(), eq(USER_ID));
        assertThat(captor.getValue().fieldName()).isEqualTo("perPerson[0].status");
        assertThat(captor.getValue().proposedValue().isNull()).isTrue();
    }

    @Test
    void 모드에_맞지_않는_문항은_고칠_수_없다() {
        MeetingNoteService service = service(mockAiClient());
        service.createDraft(fullRequest(List.of("홍 부장")), USER_ID);

        assertThatThrownBy(() -> service.updateMeetingNote(
                "mn_7", new MeetingNoteUpdateRequest(null, List.of()), USER_ID
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 다른_사용자의_회의록은_볼_수_없다() {
        MeetingNoteService service = service(mockAiClient());
        service.createDraft(fullRequest(List.of()), USER_ID);

        assertThatThrownBy(() -> service.getMeetingNote("mn_7", 2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 회의록_ID_형식이_틀리면_거부한다() {
        assertThatThrownBy(() -> service(mockAiClient()).getMeetingNote("7", USER_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 잘못된_mode는_원본을_저장하기_전에_거부한다() {
        MeetingNoteCreateRequest request = new MeetingNoteCreateRequest(null, null, List.of(), MEMO, "HALF", null);

        assertThatThrownBy(() -> service(mockAiClient()).createDraft(request, USER_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verify(sourceRepository, never()).save(any());
    }

    private MeetingNoteService service(AiClient aiClient) {
        return new MeetingNoteService(
                meetingNoteRepository,
                sourceRepository,
                userRepository,
                aiClient,
                new FakeIdentityResolutionClient(objectMapper),
                taskDraftClient,
                revisionClient,
                eventPublisher,
                objectMapper
        );
    }

    private AiClient mockAiClient() {
        return new MockAiClient(new AiModuleProperties(), objectMapper);
    }

    private MeetingNoteCreateRequest fullRequest(List<String> attendees) {
        return new MeetingNoteCreateRequest(LocalDateTime.of(2026, 10, 14, 15, 0), "가상전자 본사", attendees, MEMO, null, null);
    }

    private MeetingNoteCreateRequest lightRequest(List<String> attendees) {
        return new MeetingNoteCreateRequest(LocalDateTime.of(2026, 10, 14, 15, 0), null, attendees, MEMO, "light", null);
    }

    private MeetingNoteUpdateRequest.FieldsUpdate fieldsUpdate(
            List<MeetingNoteDocument.Item> commitments,
            List<MeetingNoteDocument.Item> concerns
    ) {
        return new MeetingNoteUpdateRequest.FieldsUpdate(null, null, concerns, commitments, null);
    }
}
