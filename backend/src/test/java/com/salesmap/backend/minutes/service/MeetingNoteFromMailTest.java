package com.salesmap.backend.minutes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.ai.client.AiClient;
import com.salesmap.backend.ai.client.MockAiClient;
import com.salesmap.backend.ai.config.AiModuleProperties;
import com.salesmap.backend.ai.dto.AiMinutesRequest;
import com.salesmap.backend.minutes.ContractSchemaValidator;
import com.salesmap.backend.minutes.client.AttendeeMention;
import com.salesmap.backend.minutes.client.FakeIdentityResolutionClient;
import com.salesmap.backend.minutes.client.IdentityResolutionClient;
import com.salesmap.backend.minutes.client.RevisionClient;
import com.salesmap.backend.minutes.client.TaskDraftClient;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;
import com.salesmap.backend.minutes.dto.MeetingNoteFromSourceResponse;
import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.repository.MeetingNoteRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceDirection;
import com.salesmap.backend.source.entity.SourceGroup;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.entity.UserStatus;
import com.salesmap.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 메일(스레드)에서 회의록 만들기 (B-5). MySQL·네트워크 없음. */
class MeetingNoteFromMailTest {

    private static final Long USER_ID = 1L;
    private static final Long GROUP_ID = 50L;
    private static final String OWN_EMAIL = "sales@example.com";
    private static final String QUESTION = "안녕하세요, 가상전자 홍길동입니다.\n견적서 언제 받을 수 있을까요?\n--\n가상전자 영업팀 부장 홍길동";
    private static final String ANSWER = "부장님, 견적서는 금요일까지 보내드리겠습니다.\n\n"
            + "2026년 10월 14일 (화) 오전 9:00, 홍길동 <gildong.hong@gasang-elec.example>님이 작성:\n> 견적서 언제 받을 수 있을까요?";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MeetingNoteRepository meetingNoteRepository = mock(MeetingNoteRepository.class);
    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TaskDraftClient taskDraftClient = mock(TaskDraftClient.class);
    private final IdentityResolutionClient identityClient = spy(new FakeIdentityResolutionClient(objectMapper));
    private final AiClient aiClient = spy(new MockAiClient(new AiModuleProperties(), objectMapper));
    private final AtomicReference<MeetingNote> savedNote = new AtomicReference<>();
    private User user;
    private SourceGroup group;
    private Source question;
    private Source answer;

    @BeforeEach
    void setUp() {
        user = new User(OWN_EMAIL, "영업담당", "pw", "USER", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        group = new SourceGroup(user, null, SourceType.EMAIL, "thread-1", "견적 문의", true);
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        // 고객 메일: 나 + 참조 박가상에게
        question = mail(201L, group, SourceDirection.RECEIVED, "홍길동", "gildong.hong@gasang-elec.example",
                "영업담당, 박가상", OWN_EMAIL + ", gasang.park@gasang-elec.example", QUESTION,
                LocalDateTime.of(2026, 10, 14, 9, 0));
        // 우리 회신
        answer = mail(202L, group, SourceDirection.SENT, "영업담당", OWN_EMAIL,
                null, "gildong.hong@gasang-elec.example", ANSWER, LocalDateTime.of(2026, 10, 14, 10, 0, 30));
        when(sourceRepository.findById(201L)).thenReturn(Optional.of(question));
        when(sourceRepository.findById(202L)).thenReturn(Optional.of(answer));
        when(sourceRepository.findBySourceGroupIdOrderBySentAtAscIdAsc(GROUP_ID)).thenReturn(List.of(question, answer));

        when(meetingNoteRepository.findFirstByUserIdAndOriginKeyOrderByIdDesc(anyLong(), any())).thenReturn(Optional.empty());
        when(meetingNoteRepository.save(any(MeetingNote.class))).thenAnswer(invocation -> {
            MeetingNote note = invocation.getArgument(0);
            ReflectionTestUtils.setField(note, "id", 9L);
            savedNote.set(note);
            return note;
        });
        when(taskDraftClient.draftTasks(any(), anyLong())).thenReturn(List.of());
    }

    @Test
    void 스레드_메일로_만들면_기존_메일을_참조하고_스레드_전체를_엔진에_보낸다() throws Exception {
        MeetingNoteFromSourceResponse response = service().createDraftFromSource(202L, USER_ID);

        assertThat(response.created()).isTrue();
        assertThat(response.noteId()).isEqualTo("mn_9");
        verify(sourceRepository, never()).save(any());  // 새 원본(메모)을 만들지 않는다

        ArgumentCaptor<AiMinutesRequest> request = ArgumentCaptor.forClass(AiMinutesRequest.class);
        verify(aiClient).generateMinutes(request.capture());
        assertThat(request.getValue().sourceRefs()).containsExactly(
                new AiMinutesRequest.SourceRef("src_201", "EMAIL"),
                new AiMinutesRequest.SourceRef("src_202", "EMAIL")
        );
        assertThat(request.getValue().text()).isEqualTo(
                "------ 메일 1/2 · 2026-10-14 09:00 · 홍길동 ------\n" + QUESTION + "\n"
                        + "------ 메일 2/2 · 2026-10-14 10:00 · 영업담당 ------\n" + ANSWER);
        // 마지막 메일의 발송 시각
        assertThat(request.getValue().occurredAt()).isEqualTo("2026-10-14T10:00:30");
        // 내 주소는 빼고 상대만 (발신자·참조)
        assertThat(request.getValue().attendees()).containsExactly("홍길동", "박가상");
        assertThat(request.getValue().mode()).isNull();

        // A에는 이름과 메일 주소를 함께 넘긴다 → 홍길동은 샘플의 이메일 일치로 CONFIRMED(ir_001), 박가상은 이름 일치로 NEW(ir_003)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttendeeMention>> mentions = ArgumentCaptor.forClass(List.class);
        verify(identityClient).resolve(eq("src_202"), mentions.capture(), eq(USER_ID));
        assertThat(mentions.getValue()).containsExactly(
                new AttendeeMention("홍길동", "gildong.hong@gasang-elec.example", "EMAIL"),
                new AttendeeMention("박가상", "gasang.park@gasang-elec.example", "EMAIL")
        );
        assertThat(response.draft().identityResolutions()).extracting(r -> r.path("resolutionId").asText())
                .containsExactly("ir_001", "ir_003");
        assertThat(response.draft().note().meetingNote().participants().get(0))
                .isEqualTo(new MeetingNoteDocument.Participant("홍길동", "ir_001", "p_001"));

        // 저장: 출처(스레드), 원본 목록, 엔진에 보낸 원문(인용·서명 제거 전)
        MeetingNote note = savedNote.get();
        assertThat(note.getOriginKey()).isEqualTo("group-50");
        assertThat(note.getSource()).isSameAs(answer);
        MeetingNoteDocument document = response.draft().note().meetingNote();
        assertThat(document.sourceRefs()).extracting(MeetingNoteDocument.SourceRef::sourceId).containsExactly("src_201", "src_202");
        assertThat(response.draft().note().source().content()).isEqualTo(request.getValue().text());
        assertThat(ContractSchemaValidator.load("meeting_note.schema.json").validate(objectMapper.valueToTree(document))).isEmpty();
    }

    @Test
    void 메일_한_통이면_본문을_그대로_보내고_원문을_따로_저장하지_않는다() {
        Source single = mail(301L, null, SourceDirection.RECEIVED, "홍길동", "gildong.hong@gasang-elec.example",
                "영업담당", OWN_EMAIL, QUESTION, LocalDateTime.of(2026, 10, 15, 11, 0));
        when(sourceRepository.findById(301L)).thenReturn(Optional.of(single));

        MeetingNoteFromSourceResponse response = service().createDraftFromSource(301L, USER_ID);

        ArgumentCaptor<AiMinutesRequest> request = ArgumentCaptor.forClass(AiMinutesRequest.class);
        verify(aiClient).generateMinutes(request.capture());
        assertThat(request.getValue().text()).isEqualTo(QUESTION);
        assertThat(request.getValue().sourceRefs()).containsExactly(new AiMinutesRequest.SourceRef("src_301", "EMAIL"));
        assertThat(savedNote.get().getOriginKey()).isEqualTo("source-301");
        assertThat(savedNote.get().getSourceRefsJson()).isNull();
        assertThat(savedNote.get().getSourceText()).isNull();
        assertThat(response.draft().note().source().content()).isEqualTo(QUESTION);
    }

    @Test
    void 같은_스레드로_이미_만든_회의록이_있으면_새로_만들지_않고_알려_준다() {
        MeetingNote existing = mock(MeetingNote.class);
        when(existing.getId()).thenReturn(4L);
        when(meetingNoteRepository.findFirstByUserIdAndOriginKeyOrderByIdDesc(USER_ID, "group-50")).thenReturn(Optional.of(existing));

        // 스레드 안 다른 메일에서 눌러도 같은 회의록
        MeetingNoteFromSourceResponse response = service().createDraftFromSource(201L, USER_ID);

        assertThat(response.created()).isFalse();
        assertThat(response.noteId()).isEqualTo("mn_4");
        assertThat(response.draft()).isNull();
        verify(aiClient, never()).generateMinutes(any());
        verify(meetingNoteRepository, never()).save(any());
    }

    @Test
    void 거의_동시에_두_번_눌러_저장이_충돌하면_먼저_만든_회의록을_알려_준다() {
        MeetingNote first = mock(MeetingNote.class);
        when(first.getId()).thenReturn(5L);
        when(meetingNoteRepository.findFirstByUserIdAndOriginKeyOrderByIdDesc(USER_ID, "group-50"))
                .thenReturn(Optional.empty(), Optional.of(first));
        when(meetingNoteRepository.save(any(MeetingNote.class))).thenThrow(new DataIntegrityViolationException("uk_meeting_notes_user_origin"));

        MeetingNoteFromSourceResponse response = service().createDraftFromSource(202L, USER_ID);

        assertThat(response.created()).isFalse();
        assertThat(response.noteId()).isEqualTo("mn_5");
    }

    @Test
    void 남의_메일로는_만들_수_없다() {
        User other = new User("other@example.com", "다른 사람", "pw", "USER", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(other, "id", 2L);
        Source othersMail = mail(401L, null, SourceDirection.RECEIVED, "홍길동", "gildong.hong@gasang-elec.example",
                "다른 사람", "other@example.com", QUESTION, LocalDateTime.of(2026, 10, 15, 11, 0));
        ReflectionTestUtils.setField(othersMail, "user", other);
        when(sourceRepository.findById(401L)).thenReturn(Optional.of(othersMail));

        assertThatThrownBy(() -> service().createDraftFromSource(401L, USER_ID)).isInstanceOf(AccessDeniedException.class);
        verify(aiClient, never()).generateMinutes(any());
    }

    @Test
    void 메일이_아닌_원본이나_지운_메일로는_만들_수_없다() {
        Source memo = new Source(user, null, SourceType.MEETING_NOTE, null, "미팅 메모", "메모", SourceStatus.CREATED, null);
        ReflectionTestUtils.setField(memo, "id", 501L);
        when(sourceRepository.findById(501L)).thenReturn(Optional.of(memo));
        question.markDeleted();

        assertThatThrownBy(() -> service().createDraftFromSource(501L, USER_ID)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().createDraftFromSource(201L, USER_ID)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service().createDraftFromSource(999L, USER_ID)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void 스레드_구분_줄은_AI_모듈_전처리가_알아보는_형식이다() {
        // ai-module/app/minutes/preprocess.py THREAD_SEPARATOR와 같은 패턴 — 바꾸면 두 곳을 같이 바꾼다
        Pattern engineSeparator = Pattern.compile("^-{4,} 메일 \\d+/\\d+ .*-{4,}$");

        assertThat(MailThreadText.separator(2, 3, LocalDateTime.of(2026, 10, 14, 15, 0), "홍길동"))
                .isEqualTo("------ 메일 2/3 · 2026-10-14 15:00 · 홍길동 ------")
                .matches(engineSeparator);
        assertThat(MailThreadText.separator(1, 1, null, "gildong.hong@gasang-elec.example")).matches(engineSeparator);
    }

    private MeetingNoteService service() {
        return new MeetingNoteService(
                meetingNoteRepository,
                sourceRepository,
                userRepository,
                aiClient,
                identityClient,
                taskDraftClient,
                mock(RevisionClient.class),
                mock(ApplicationEventPublisher.class),
                objectMapper
        );
    }

    private Source mail(Long id, SourceGroup sourceGroup, SourceDirection direction, String senderName, String senderEmail,
                        String receiverNames, String receiverEmails, String content, LocalDateTime sentAt) {
        Source source = new Source(user, null, sourceGroup, SourceType.EMAIL, "msg-" + id, "견적 문의", content,
                SourceStatus.COLLECTED, sentAt, direction, senderName, senderEmail, receiverNames, receiverEmails, sentAt);
        ReflectionTestUtils.setField(source, "id", id);
        return source;
    }
}
