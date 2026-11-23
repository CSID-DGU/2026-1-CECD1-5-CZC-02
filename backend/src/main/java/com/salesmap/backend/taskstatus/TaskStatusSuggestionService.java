package com.salesmap.backend.taskstatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 확정된 할 일이 메일로 끝난 것 같으면 "완료로 옮길까요?"를 제안한다 (필수 ④, 시안 ④).
 * 우리 약속은 상대에게 보낸 메일, 고객 측 약속 확인(IMPLICIT)은 상대에게서 온 메일을 본다.
 * 제안은 조회할 때 계산하고, 상태는 사람이 눌러야 바뀐다 (D-025).
 */
@Service
public class TaskStatusSuggestionService {

    static final String RULE_VERSION = "task-status-rule-1";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String DONE = "DONE";
    private static final Set<String> NOT_FINISHED_BY_MAIL = Set.of("MEETING_PREP", "VISIT_PREP");
    private static final Pattern NOTE_ID = Pattern.compile("^mn_(\\d+)$");
    private static final Pattern SOURCE_ID = Pattern.compile("^src_(\\d+)$");

    private final TaskService tasks;
    private final PersonService people;
    private final MeetingNoteRepository notes;
    private final SourceRepository sources;
    private final TaskStatusDismissalRepository dismissals;
    private final RevisionService revisions;
    private final ObjectMapper mapper;

    public TaskStatusSuggestionService(TaskService tasks, PersonService people, MeetingNoteRepository notes,
                                       SourceRepository sources, TaskStatusDismissalRepository dismissals,
                                       RevisionService revisions, ObjectMapper mapper) {
        this.tasks = tasks;
        this.people = people;
        this.notes = notes;
        this.sources = sources;
        this.dismissals = dismissals;
        this.revisions = revisions;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<TaskStatusSuggestion> list(Long owner) {
        List<TaskDocument> candidates = tasks.list(owner).stream().filter(this::canSuggest).toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Source> mails = sources.findByUserId(owner).stream()
                .filter(mail -> mail.getSourceType() == SourceType.EMAIL && mail.getStatus() != SourceStatus.DELETED
                        && mail.getSentAt() != null)
                .sorted(Comparator.comparing(Source::getSentAt).reversed().thenComparing(Source::getId, Comparator.reverseOrder()))
                .toList();
        if (mails.isEmpty()) {
            return List.of();
        }
        Set<String> dismissed = dismissals.findByUserId(owner).stream()
                .map(row -> key(row.getTaskId(), row.getSourceId()))
                .collect(Collectors.toCollection(HashSet::new));
        Map<String, Optional<PersonDocument>> persons = new HashMap<>();
        List<TaskStatusSuggestion> result = new ArrayList<>();
        for (TaskDocument task : candidates) {
            PersonDocument person = persons.computeIfAbsent(task.counterpartPersonId(), id -> person(id, owner)).orElse(null);
            LocalDateTime since = startedAt(task, owner);
            if (person == null || since == null) {
                continue;
            }
            suggest(task, person, since, mails, dismissed).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    /** 사람이 제안을 받아들임. C의 상태 변경 규칙과 수정 기록을 그대로 거치고, 제안 승인도 기록한다. */
    @Transactional
    public TaskDocument accept(String taskId, String sourceId, Long owner) {
        TaskStatusSuggestion suggestion = current(taskId, sourceId, owner);
        TaskDocument before = tasks.get(taskId, owner);
        ObjectNode next = mapper.valueToTree(before);
        next.put("status", suggestion.suggestedStatus());
        TaskDocument saved = tasks.update(taskId, next, tasks.etag(before), owner);
        record(taskId, saved.status(), "ACCEPTED", owner);
        return saved;
    }

    /** 사람이 제안을 거절함. 상태는 그대로 두고, 같은 메일로 다시 묻지 않는다. */
    @Transactional
    public void dismiss(String taskId, String sourceId, Long owner) {
        TaskStatusSuggestion suggestion = current(taskId, sourceId, owner);
        try {
            dismissals.saveAndFlush(new TaskStatusDismissal(owner, taskId, parseSourceId(sourceId), now()));
        } catch (DataIntegrityViolationException alreadyDismissed) {
            return;
        }
        record(taskId, suggestion.currentStatus(), "REJECTED", owner);
    }

    private TaskStatusSuggestion current(String taskId, String sourceId, Long owner) {
        parseSourceId(sourceId);
        return list(owner).stream()
                .filter(item -> item.taskId().equals(taskId) && item.sourceId().equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("이 제안은 더 이상 유효하지 않습니다. 새로고침해 주세요."));
    }

    private boolean canSuggest(TaskDocument task) {
        return CONFIRMED.equals(task.status()) && task.counterpartPersonId() != null && task.noteId() != null
                && !NOT_FINISHED_BY_MAIL.contains(task.taskType());
    }

    private Optional<TaskStatusSuggestion> suggest(TaskDocument task, PersonDocument person, LocalDateTime since,
                                                   List<Source> mails, Set<String> dismissed) {
        Set<String> emails = person.emails().stream().map(mail -> mail.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> keywords = TaskStatusRules.keywords(task.content());
        if (emails.isEmpty() || keywords.isEmpty()) {
            return Optional.empty();
        }
        boolean customerSide = "IMPLICIT".equals(task.commitmentKind());
        for (Source mail : mails) {
            if (!mail.getSentAt().isAfter(since)) {
                break;
            }
            if (dismissed.contains(key(task.taskId(), mail.getId())) || !involves(mail, emails, customerSide)) {
                continue;
            }
            List<String> hits = TaskStatusRules.matched(keywords, mail.getTitle(), mail.getContent());
            if (!hits.isEmpty()) {
                String direction = customerSide ? "에게서 온" : "에게 보낸";
                String reason = person.name() + direction + " 메일에 '" + String.join(", ", hits) + "' 내용이 있어요.";
                return Optional.of(new TaskStatusSuggestion(task.taskId(), task.content(), task.status(), DONE,
                        "src_" + mail.getId(), mail.getTitle(), mail.getSentAt(), hits, reason));
            }
        }
        return Optional.empty();
    }

    private static boolean involves(Source mail, Set<String> emails, boolean customerSide) {
        if (customerSide) {
            return mail.getDirection() == SourceDirection.RECEIVED
                    && TaskStatusRules.containsEmail(mail.getSenderEmail(), emails);
        }
        return mail.getDirection() == SourceDirection.SENT
                && TaskStatusRules.containsEmail(mail.getReceiverEmails(), emails);
    }

    private Optional<PersonDocument> person(String personId, Long owner) {
        try {
            return Optional.of(people.get(personId, owner));
        } catch (NoSuchElementException | IllegalArgumentException | IllegalStateException unknownPerson) {
            return Optional.empty();
        }
    }

    /** 미팅 시각 이후의 메일만 본다. 시각을 모르면 회의록 확정 시각, 둘 다 없으면 제안하지 않는다. */
    private LocalDateTime startedAt(TaskDocument task, Long owner) {
        Matcher matcher = NOTE_ID.matcher(task.noteId());
        if (!matcher.matches()) {
            return null;
        }
        MeetingNote note = notes.findById(Long.parseLong(matcher.group(1))).orElse(null);
        if (note == null || !owner.equals(note.getUser().getId())) {
            return null;
        }
        return note.getOccurredAt() != null ? note.getOccurredAt() : note.getConfirmedAt();
    }

    private void record(String taskId, String finalStatus, String action, Long owner) {
        JsonNode proposed = TextNode.valueOf(DONE);
        revisions.create(new RevisionCreateRequest("TASK_REVIEW", "TASK", taskId, "status", proposed,
                TextNode.valueOf(finalStatus), action, "RULE", RULE_VERSION), owner);
    }

    private static Long parseSourceId(String sourceId) {
        Matcher matcher = SOURCE_ID.matcher(sourceId == null ? "" : sourceId);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("메일 ID는 src_<숫자> 형식이어야 합니다.");
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("메일 ID는 src_<숫자> 형식이어야 합니다.");
        }
    }

    private static String key(String taskId, Long sourceId) {
        return taskId + ":" + sourceId;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0);
    }
}
