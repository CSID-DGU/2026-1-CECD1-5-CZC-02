package com.salesmap.backend.minutes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.ai.client.AiClient;
import com.salesmap.backend.ai.client.MockAiClient;
import com.salesmap.backend.ai.config.AiModuleProperties;
import com.salesmap.backend.minutes.client.FakeIdentityResolutionClient;
import com.salesmap.backend.minutes.client.RevisionClient;
import com.salesmap.backend.minutes.client.TaskDraftClient;
import com.salesmap.backend.minutes.dto.MeetingNoteCreateRequest;
import com.salesmap.backend.minutes.dto.MeetingNoteFromSourceResponse;
import com.salesmap.backend.minutes.dto.MeetingNoteResponse;
import com.salesmap.backend.minutes.dto.MeetingNoteUpdateRequest;
import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.entity.MeetingNoteStatus;
import com.salesmap.backend.minutes.repository.MeetingNoteRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceDirection;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.entity.UserStatus;
import com.salesmap.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.Query;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** 회의록 보관(숨김)·해제 (D-025). MySQL·네트워크 없음. */
class MeetingNoteArchiveTest {

    private static final Long USER_ID = 1L;
    private static final Long NOTE_ID = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MeetingNoteRepository meetingNoteRepository = mock(MeetingNoteRepository.class);
    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TaskDraftClient taskDraftClient = mock(TaskDraftClient.class);
    private final RevisionClient revisionClient = mock(RevisionClient.class);
    private final AiClient aiClient = spy(new MockAiClient(new AiModuleProperties(), objectMapper));
    private final AtomicReference<MeetingNote> savedNote = new AtomicReference<>();
    private User user;
    private MeetingNoteService service;

    @BeforeEach
    void setUp() {
        user = new User("sales@example.com", "영업담당", "pw", "USER", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(sourceRepository.save(any(Source.class))).thenAnswer(invocation -> {
            Source source = invocation.getArgument(0);
            ReflectionTestUtils.setField(source, "id", 101L);
            return source;
        });
        when(meetingNoteRepository.save(any(MeetingNote.class))).thenAnswer(invocation -> {
            MeetingNote note = invocation.getArgument(0);
            ReflectionTestUtils.setField(note, "id", NOTE_ID);
            savedNote.set(note);
            return note;
        });
        when(meetingNoteRepository.findById(NOTE_ID)).thenAnswer(invocation -> Optional.ofNullable(savedNote.get()));
        when(meetingNoteRepository.findFirstByUserIdAndOriginKeyOrderByIdDesc(anyLong(), any())).thenReturn(Optional.empty());
        when(taskDraftClient.draftTasks(any(), anyLong())).thenReturn(List.of());
        service = new MeetingNoteService(
                meetingNoteRepository,
                sourceRepository,
                userRepository,
                aiClient,
                new FakeIdentityResolutionClient(objectMapper),
                taskDraftClient,
                revisionClient,
                mock(ApplicationEventPublisher.class),
                objectMapper
        );
        service.createDraft(new MeetingNoteCreateRequest(
                LocalDateTime.of(2026, 11, 2, 10, 0), "가상전자 본사", List.of("홍 부장"), "메모 원문", null, null), USER_ID);
    }

    @Test
    void 보관하면_숨김_표시만_하고_상태와_할_일은_그대로_둔다() {
        MeetingNoteResponse archived = service.archiveMeetingNote("mn_7", USER_ID);

        assertThat(archived.archivedAt()).isNotNull();
        assertThat(archived.meetingNote().status()).isEqualTo("DRAFT");  // 계약 status는 그대로
        assertThat(savedNote.get().isArchived()).isTrue();
        // 할 일(C)을 몰래 확정·취소하지 않는다: 초안 만들 때 한 번 부른 것 말고는 C를 부르지 않음
        verify(taskDraftClient).draftTasks(any(), anyLong());
        verifyNoMoreInteractions(taskDraftClient);

        MeetingNoteResponse restored = service.unarchiveMeetingNote("mn_7", USER_ID);
        assertThat(restored.archivedAt()).isNull();
        assertThat(savedNote.get().isArchived()).isFalse();
    }

    @Test
    void 보관과_해제는_수정_기록이_아니라_누가_언제를_회의록에_남긴다() {
        service.archiveMeetingNote("mn_7", USER_ID);
        MeetingNote note = savedNote.get();
        assertThat(note.getArchiveDecidedBy()).isEqualTo(USER_ID);
        assertThat(note.getArchiveDecidedAt()).isEqualTo(note.getArchivedAt());

        service.unarchiveMeetingNote("mn_7", USER_ID);
        assertThat(note.getArchivedAt()).isNull();
        assertThat(note.getArchiveDecidedBy()).isEqualTo(USER_ID);
        assertThat(note.getArchiveDecidedAt()).isNotNull();

        // AI 제안이 없는 결정이라 수정 기록(revision, ML-4 학습 정답)에는 넣지 않는다 (D-026)
        verify(revisionClient, never()).record(any(), any());
    }

    @Test
    void 확정한_회의록도_보관할_수_있다() {
        service.confirmMeetingNote("mn_7", USER_ID);

        MeetingNoteResponse archived = service.archiveMeetingNote("mn_7", USER_ID);

        assertThat(archived.meetingNote().status()).isEqualTo("CONFIRMED");
        assertThat(archived.archivedAt()).isNotNull();
    }

    @Test
    void 이미_보관했거나_보관하지_않은_것은_거부한다() {
        assertThatThrownBy(() -> service.unarchiveMeetingNote("mn_7", USER_ID)).isInstanceOf(IllegalArgumentException.class);
        service.archiveMeetingNote("mn_7", USER_ID);
        assertThatThrownBy(() -> service.archiveMeetingNote("mn_7", USER_ID)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 남의_회의록은_보관하거나_해제할_수_없다() {
        assertThatThrownBy(() -> service.archiveMeetingNote("mn_7", 2L)).isInstanceOf(AccessDeniedException.class);
        service.archiveMeetingNote("mn_7", USER_ID);
        assertThatThrownBy(() -> service.unarchiveMeetingNote("mn_7", 2L)).isInstanceOf(AccessDeniedException.class);
        assertThat(savedNote.get().isArchived()).isTrue();
    }

    @Test
    void 보관된_회의록은_해제하기_전까지_고치거나_확정할_수_없다() {
        service.archiveMeetingNote("mn_7", USER_ID);

        assertThatThrownBy(() -> service.updateMeetingNote("mn_7",
                new MeetingNoteUpdateRequest(new MeetingNoteUpdateRequest.FieldsUpdate(null, null, List.of(), null, null), null), USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("보관 해제");
        assertThatThrownBy(() -> service.confirmMeetingNote("mn_7", USER_ID)).isInstanceOf(IllegalArgumentException.class);
        assertThat(savedNote.get().getStatus()).isEqualTo(MeetingNoteStatus.DRAFT);
        verify(revisionClient, never()).record(any(), any());
    }

    @Test
    void 기본_목록은_보관한_회의록을_빼고_보관함은_ARCHIVED로만_본다() throws Exception {
        when(meetingNoteRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(meetingNoteRepository.findArchivedByUserId(USER_ID)).thenReturn(List.of(savedNote.get()));
        service.archiveMeetingNote("mn_7", USER_ID);

        assertThat(service.getMeetingNotes(null, USER_ID)).isEmpty();
        assertThat(service.getMeetingNotes("archived", USER_ID))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.noteId()).isEqualTo("mn_7");
                    assertThat(summary.archivedAt()).isNotNull();
                });
        assertThatThrownBy(() -> service.getMeetingNotes("DELETED", USER_ID)).isInstanceOf(IllegalArgumentException.class);

        // 기본 목록·상태별 목록(확인 대기함·DRAFT 배지) 쿼리는 보관한 것을 뺀다
        assertThat(query("findByUserId", Long.class)).contains("n.archivedAt is null");
        assertThat(query("findByUserIdAndStatus", Long.class, MeetingNoteStatus.class)).contains("n.archivedAt is null");
        assertThat(query("findArchivedByUserId", Long.class)).contains("n.archivedAt is not null");
    }

    @Test
    void 보관한_메일_회의록은_같은_메일로_다시_만들지_않고_보관된_회의록을_알려_준다() {
        Source mail = new Source(user, null, null, SourceType.EMAIL, "msg-1", "견적 문의", "견적서 언제 받을 수 있을까요?",
                SourceStatus.COLLECTED, null, SourceDirection.RECEIVED, "홍길동", "gildong.hong@gasang-elec.example",
                "영업담당", "sales@example.com", LocalDateTime.of(2026, 11, 1, 9, 0));
        ReflectionTestUtils.setField(mail, "id", 301L);
        when(sourceRepository.findById(301L)).thenReturn(Optional.of(mail));
        MeetingNote archivedMailNote = mock(MeetingNote.class);
        when(archivedMailNote.getId()).thenReturn(9L);
        when(archivedMailNote.isArchived()).thenReturn(true);
        when(meetingNoteRepository.findFirstByUserIdAndOriginKeyOrderByIdDesc(USER_ID, "source-301"))
                .thenReturn(Optional.of(archivedMailNote));

        MeetingNoteFromSourceResponse response = service.createDraftFromSource(301L, USER_ID);

        assertThat(response.created()).isFalse();
        assertThat(response.archived()).isTrue();
        assertThat(response.noteId()).isEqualTo("mn_9");
        verify(aiClient).generateMinutes(any());  // setUp의 메모 초안 한 번뿐 — 메일로는 다시 부르지 않음
    }

    private String query(String methodName, Class<?>... parameterTypes) throws Exception {
        return MeetingNoteRepository.class.getMethod(methodName, parameterTypes).getAnnotation(Query.class).value();
    }
}
