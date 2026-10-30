package com.salesmap.backend.minutes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmap.backend.ai.client.AiClient;
import com.salesmap.backend.ai.client.MockAiClient;
import com.salesmap.backend.ai.config.AiModuleProperties;
import com.salesmap.backend.ai.exception.AiClientException;
import com.salesmap.backend.minutes.client.FakeIdentityResolutionClient;
import com.salesmap.backend.minutes.client.TaskDraftClient;
import com.salesmap.backend.minutes.client.RevisionRecord;
import com.salesmap.backend.minutes.dto.MeetingNoteCreateRequest;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;
import com.salesmap.backend.minutes.dto.MeetingNoteDraftResponse;
import com.salesmap.backend.minutes.dto.MeetingNoteUpdateRequest;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 백엔드가 내보내는 회의록·수정 기록·가짜 A 결과가 contracts/*.schema.json을 통과하는지. */
class MeetingNoteContractTest {

    private static final Long USER_ID = 1L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MeetingNoteRepository meetingNoteRepository = mock(MeetingNoteRepository.class);
    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final List<RevisionRecord> revisions = new ArrayList<>();
    private final AtomicReference<MeetingNote> savedNote = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        User user = new User("sales@example.com", "영업담당", "pw", "USER", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(sourceRepository.save(any(Source.class))).thenAnswer(invocation -> {
            Source source = invocation.getArgument(0);
            ReflectionTestUtils.setField(source, "id", 101L);
            return source;
        });
        when(meetingNoteRepository.save(any(MeetingNote.class))).thenAnswer(invocation -> {
            MeetingNote note = invocation.getArgument(0);
            ReflectionTestUtils.setField(note, "id", 7L);
            savedNote.set(note);
            return note;
        });
        when(meetingNoteRepository.findById(7L)).thenAnswer(invocation -> Optional.ofNullable(savedNote.get()));
    }

    @Test
    void FULL_초안_수정_확정_회의록과_수정기록이_스키마를_통과한다() throws Exception {
        MeetingNoteService service = service(new MockAiClient(new AiModuleProperties(), objectMapper));
        MeetingNoteDraftResponse draft = service.createDraft(request(List.of("홍 부장", "최가상"), null), USER_ID);
        assertValid("meeting_note.schema.json", draft.note().meetingNote());

        service.updateMeetingNote("mn_7", new MeetingNoteUpdateRequest(
                new MeetingNoteUpdateRequest.FieldsUpdate(null, null,
                        List.of(new MeetingNoteDocument.Item("예산 불확실", "예산은 미정")), null, List.of()),
                null
        ), USER_ID);
        assertValid("meeting_note.schema.json", service.confirmMeetingNote("mn_7", USER_ID).meetingNote());

        assertThat(revisions).hasSize(5);
        for (int index = 0; index < revisions.size(); index++) {
            // revisionId는 D가 붙이므로 검사할 때만 임시로 넣는다
            ObjectNode revision = objectMapper.valueToTree(revisions.get(index));
            revision.put("revisionId", "rv_" + (index + 1));
            assertValid("revision.schema.json", revision);
        }

        for (JsonNode resolution : draft.identityResolutions()) {
            assertValid("identity_resolution.schema.json", resolution);
        }
    }

    @Test
    void LIGHT_초안과_사람별_수정이_스키마를_통과한다() throws Exception {
        MeetingNoteService service = service(new MockAiClient(new AiModuleProperties(), objectMapper));
        MeetingNoteDocument draft = service.createDraft(request(List.of("홍 부장", "박가상"), "LIGHT"), USER_ID)
                .note().meetingNote();
        assertValid("meeting_note.schema.json", draft);

        MeetingNoteDocument edited = service.updateMeetingNote("mn_7", new MeetingNoteUpdateRequest(null, List.of(
                new MeetingNoteDocument.PerPerson("홍 부장", null,
                        new MeetingNoteDocument.Item("신규 과제 검토", "신규 과제 검토 중"), null, null),
                new MeetingNoteDocument.PerPerson("박가상", null, null, null, null)
        )), USER_ID).meetingNote();
        assertValid("meeting_note.schema.json", edited);
    }

    @Test
    void AI_실패로_만든_빈_회의록도_스키마를_통과한다() throws Exception {
        AiClient failingAiClient = mock(AiClient.class);
        when(failingAiClient.generateMinutes(any())).thenThrow(new AiClientException("연결 실패"));

        MeetingNoteDocument full = service(failingAiClient).createDraft(request(List.of("홍 부장"), null), USER_ID)
                .note().meetingNote();
        assertValid("meeting_note.schema.json", full);

        MeetingNoteDocument light = service(failingAiClient).createDraft(request(List.of(), "LIGHT"), USER_ID)
                .note().meetingNote();
        assertValid("meeting_note.schema.json", light);
        assertThat(light.missingFields()).hasSize(5);
    }

    @Test
    void 검사기가_스키마_위반을_잡는다() throws Exception {
        MeetingNoteDocument draft = service(new MockAiClient(new AiModuleProperties(), objectMapper))
                .createDraft(request(List.of("홍 부장"), null), USER_ID).note().meetingNote();
        ObjectNode broken = objectMapper.valueToTree(draft);
        broken.remove("status");
        broken.put("summary", "스키마에 없는 키");
        broken.put("noteId", "MN-7");
        ((ObjectNode) broken.get("fields").get("commitments").get(0)).put("evidence", "");

        assertThat(ContractSchemaValidator.load("meeting_note.schema.json").validate(broken))
                .hasSize(4)
                .anySatisfy(error -> assertThat(error).contains("필수 키 status"))
                .anySatisfy(error -> assertThat(error).contains("스키마에 없는 키 summary"))
                .anySatisfy(error -> assertThat(error).contains("$.noteId: pattern"))
                .anySatisfy(error -> assertThat(error).contains("commitments[0].evidence: minLength"));
    }

    private void assertValid(String schemaFileName, Object value) throws Exception {
        JsonNode json = objectMapper.valueToTree(value);
        assertThat(ContractSchemaValidator.load(schemaFileName).validate(json))
                .as(schemaFileName + " 검사: " + json)
                .isEmpty();
    }

    private MeetingNoteService service(AiClient aiClient) {
        return new MeetingNoteService(
                meetingNoteRepository,
                sourceRepository,
                userRepository,
                aiClient,
                new FakeIdentityResolutionClient(objectMapper),
                // 할 일 형식은 C 패키지 테스트가 검사한다
                mock(TaskDraftClient.class),
                (revision, userId) -> revisions.add(revision),
                mock(ApplicationEventPublisher.class),
                objectMapper
        );
    }

    private MeetingNoteCreateRequest request(List<String> attendees, String mode) {
        return new MeetingNoteCreateRequest(
                LocalDateTime.of(2026, 10, 14, 15, 0, 30, 123_000_000),
                "가상전자 본사",
                attendees,
                "홍 부장님 미팅 메모",
                mode,
                null
        );
    }
}
