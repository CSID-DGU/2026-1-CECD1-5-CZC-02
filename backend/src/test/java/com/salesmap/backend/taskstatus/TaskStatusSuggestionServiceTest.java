package com.salesmap.backend.taskstatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.minutes.ContractSchemaValidator;
import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.repository.MeetingNoteRepository;
import com.salesmap.backend.person.PersonDocument;
import com.salesmap.backend.person.PersonService;
import com.salesmap.backend.revision.dto.RevisionCreateRequest;
import com.salesmap.backend.revision.service.RevisionService;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceDirection;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.task.TaskDocument;
import com.salesmap.backend.task.TaskService;
import com.salesmap.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 메일 근거로 완료를 제안하고, 사람이 눌러야만 상태가 바뀌는지. MySQL·네트워크 없음. */
class TaskStatusSuggestionServiceTest {

    private static final Long OWNER = 1L;
    private static final LocalDateTime MEETING = LocalDateTime.of(2026, 11, 23, 15, 0);
    private static final String CUSTOMER = "lee.gasang@yesi.example";

    private final ObjectMapper mapper = new ObjectMapper();
    private final TaskService tasks = mock(TaskService.class);
    private final PersonService people = mock(PersonService.class);
    private final MeetingNoteRepository notes = mock(MeetingNoteRepository.class);
    private final SourceRepository sources = mock(SourceRepository.class);
    private final TaskStatusDismissalRepository dismissals = mock(TaskStatusDismissalRepository.class);
    private final RevisionService revisions = mock(RevisionService.class);
    private final List<Source> mails = new ArrayList<>();
    private TaskStatusSuggestionService service;

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(OWNER);
        MeetingNote note = mock(MeetingNote.class);
        when(note.getUser()).thenReturn(user);
        when(note.getOccurredAt()).thenReturn(MEETING);
        when(notes.findById(7L)).thenReturn(Optional.of(note));
        when(people.get("p_1", OWNER)).thenReturn(new PersonDocument("p_1", "이가상", List.of(), "예시전자", null, "부장",
                List.of(CUSTOMER), List.of(), List.of("MEETING"), List.of(), null, true));
        when(sources.findByUserId(OWNER)).thenReturn(mails);
        when(dismissals.findByUserId(OWNER)).thenReturn(List.of());
        service = new TaskStatusSuggestionService(tasks, people, notes, sources, dismissals, revisions, mapper);
    }

    private static TaskDocument task(String kind, String type, String status, String person, String content) {
        return new TaskDocument("t_1", "mn_7", content, "2026-11-27", false, "user_1", person, kind, type, 1.0, "RULE",
                "계약서 초안을 보내드리기로 함", null, status);
    }

    private static TaskDocument contractTask() {
        return task("EXPLICIT", "CONTRACT_PREP", "CONFIRMED", "p_1", "계약서 초안 이번 주 금요일까지 송부");
    }

    private Source mail(long id, SourceDirection direction, String from, String to, LocalDateTime at, String title, String body) {
        Source source = mock(Source.class);
        when(source.getId()).thenReturn(id);
        when(source.getSourceType()).thenReturn(SourceType.EMAIL);
        when(source.getStatus()).thenReturn(SourceStatus.COLLECTED);
        when(source.getDirection()).thenReturn(direction);
        when(source.getSenderEmail()).thenReturn(from);
        when(source.getReceiverEmails()).thenReturn(to);
        when(source.getSentAt()).thenReturn(at);
        when(source.getTitle()).thenReturn(title);
        when(source.getContent()).thenReturn(body);
        mails.add(source);
        return source;
    }

    private Source sentContract(long id) {
        return mail(id, SourceDirection.SENT, "me@example.test", "Other <x@example.test>, " + CUSTOMER,
                MEETING.plusDays(1), "계약서 초안 보내드립니다", "요청하신 계약서 초안을 첨부합니다.");
    }

    @Test
    void 우리_약속은_상대에게_보낸_메일로_완료를_제안하고_상태는_바꾸지_않는다() {
        when(tasks.list(OWNER)).thenReturn(List.of(contractTask()));
        sentContract(11);

        List<TaskStatusSuggestion> result = service.list(OWNER);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.suggestedStatus()).isEqualTo("DONE");
            assertThat(item.sourceId()).isEqualTo("src_11");
            assertThat(item.matchedKeywords()).containsExactly("계약서", "초안");
            assertThat(item.reason()).contains("이가상에게 보낸 메일");
        });
        verify(tasks, never()).update(any(), any(), any(), any());
        verify(revisions, never()).create(any(), any());
    }

    @Test
    void 고객_측_약속은_상대에게서_온_메일만_본다() {
        when(tasks.list(OWNER)).thenReturn(List.of(task("IMPLICIT", "NONE", "CONFIRMED", "p_1",
                "투입 일정 내부 검토 후 회신 (고객 측) — 이행 여부 확인")));
        mail(11, SourceDirection.SENT, "me@example.test", CUSTOMER, MEETING.plusDays(1), "투입 일정 문의", "투입 일정 검토 부탁드립니다.");
        assertThat(service.list(OWNER)).isEmpty();

        mail(12, SourceDirection.RECEIVED, CUSTOMER, "me@example.test", MEETING.plusDays(2), "투입 일정 회신", "투입 일정 검토 결과입니다.");
        assertThat(service.list(OWNER)).singleElement().satisfies(item -> assertThat(item.sourceId()).isEqualTo("src_12"));
    }

    @Test
    void 미팅_전_메일_다른_사람_메일_인용문만_맞는_메일은_제안하지_않는다() {
        when(tasks.list(OWNER)).thenReturn(List.of(contractTask()));
        mail(11, SourceDirection.SENT, "me@example.test", CUSTOMER, MEETING.minusHours(1), "계약서 초안", "계약서 초안");
        mail(12, SourceDirection.SENT, "me@example.test", "other@example.test", MEETING.plusDays(1), "계약서 초안", "계약서 초안");
        mail(13, SourceDirection.SENT, "me@example.test", CUSTOMER, MEETING.plusDays(1), "안녕하세요", "일정 확인 부탁드립니다.\n> 계약서 초안 부탁드립니다");

        assertThat(service.list(OWNER)).isEmpty();
    }

    @Test
    void 확정_전_미팅_준비_상대방_없는_할_일은_제안하지_않는다() {
        when(tasks.list(OWNER)).thenReturn(List.of(
                task("EXPLICIT", "CONTRACT_PREP", "DRAFT", "p_1", "계약서 초안 송부"),
                task("EXPLICIT", "MEETING_PREP", "CONFIRMED", "p_1", "계약서 초안 미팅 준비"),
                task("EXPLICIT", "CONTRACT_PREP", "CONFIRMED", null, "계약서 초안 송부")));
        sentContract(11);

        assertThat(service.list(OWNER)).isEmpty();
    }

    @Test
    void 사람이_승인하면_완료로_바꾸고_계약_형식으로_기록한다() throws Exception {
        TaskDocument before = contractTask();
        when(tasks.list(OWNER)).thenReturn(List.of(before));
        when(tasks.get("t_1", OWNER)).thenReturn(before);
        when(tasks.etag(before)).thenReturn("\"v1\"");
        when(tasks.update(eq("t_1"), any(), eq("\"v1\""), eq(OWNER))).thenReturn(
                task("EXPLICIT", "CONTRACT_PREP", "DONE", "p_1", before.content()));
        sentContract(11);

        assertThat(service.accept("t_1", "src_11", OWNER).status()).isEqualTo("DONE");

        ArgumentCaptor<JsonNode> sent = ArgumentCaptor.forClass(JsonNode.class);
        verify(tasks).update(eq("t_1"), sent.capture(), eq("\"v1\""), eq(OWNER));
        assertThat(sent.getValue().path("status").asText()).isEqualTo("DONE");
        RevisionCreateRequest revision = revision();
        assertThat(revision.action()).isEqualTo("ACCEPTED");
        assertThat(revision.finalValue().asText()).isEqualTo("DONE");
        assertRevisionMatchesContract(revision);
    }

    @Test
    void 사람이_거절하면_상태는_그대로이고_같은_메일로_다시_묻지_않는다() throws Exception {
        when(tasks.list(OWNER)).thenReturn(List.of(contractTask()));
        sentContract(11);

        service.dismiss("t_1", "src_11", OWNER);

        verify(dismissals).saveAndFlush(any(TaskStatusDismissal.class));
        verify(tasks, never()).update(any(), any(), any(), any());
        RevisionCreateRequest revision = revision();
        assertThat(revision.action()).isEqualTo("REJECTED");
        assertThat(revision.finalValue().asText()).isEqualTo("CONFIRMED");
        assertRevisionMatchesContract(revision);

        when(dismissals.findByUserId(OWNER)).thenReturn(List.of(new TaskStatusDismissal(OWNER, "t_1", 11L, MEETING)));
        assertThat(service.list(OWNER)).isEmpty();
        sentContractLater(12);
        assertThat(service.list(OWNER)).singleElement().satisfies(item -> assertThat(item.sourceId()).isEqualTo("src_12"));
    }

    @Test
    void 지금_없는_제안은_승인할_수_없다() {
        when(tasks.list(OWNER)).thenReturn(List.of(contractTask()));

        assertThatThrownBy(() -> service.accept("t_1", "src_99", OWNER)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.dismiss("t_1", "bad", OWNER)).isInstanceOf(IllegalArgumentException.class);
        verify(tasks, never()).update(any(), any(), any(), any());
    }

    private void sentContractLater(long id) {
        mail(id, SourceDirection.SENT, "me@example.test", CUSTOMER, MEETING.plusDays(2), "계약서 초안 수정본", "계약서 초안 수정본입니다.");
    }

    private RevisionCreateRequest revision() {
        ArgumentCaptor<RevisionCreateRequest> captured = ArgumentCaptor.forClass(RevisionCreateRequest.class);
        verify(revisions).create(captured.capture(), eq(OWNER));
        return captured.getValue();
    }

    private void assertRevisionMatchesContract(RevisionCreateRequest revision) throws Exception {
        var node = mapper.createObjectNode();
        node.put("revisionId", "rv_1").put("screen", revision.screen()).put("targetType", revision.targetType())
                .put("targetId", revision.targetId()).put("fieldName", revision.fieldName());
        node.set("proposedValue", revision.proposedValue());
        node.set("finalValue", revision.finalValue());
        node.put("action", revision.action()).put("proposedBy", revision.proposedBy())
                .put("modelVersion", revision.modelVersion()).put("actorId", "user_1").put("decidedAt", "2026-11-24T10:00:00");
        assertThat(ContractSchemaValidator.load("revision.schema.json").validate(node)).isEmpty();
    }
}
