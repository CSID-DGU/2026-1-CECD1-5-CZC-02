package com.salesmap.backend.talent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.repository.MeetingNoteRepository;
import com.salesmap.backend.talent.TalentMatchViews.MatchResult;
import com.salesmap.backend.talent.TalentMatchViews.Requirement;
import com.salesmap.backend.talent.TalentMatchViews.SourceLine;
import com.salesmap.backend.talent.TalentMatchViews.SubmissionBrief;
import com.salesmap.backend.talent.TalentMatchViews.SubmittedCandidate;
import com.salesmap.backend.talent.TalentMatchViews.TaskBrief;
import com.salesmap.backend.task.TaskDocument;
import com.salesmap.backend.task.TaskEntry;
import com.salesmap.backend.task.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 후보 추천 (S-05 인력 리스트 제출 · S-10 인력 소싱): 할 일과 그 회의록의 고객 요청에서 조건을 꺼내고(규칙),
 * 확정된 후보 풀에서 거르고 순서를 매긴다(규칙). 담당자가 고른 후보만 "제출 리스트"로 저장한다.
 * 미팅 준비 브리핑은 같은 할 일 · 같은 회의록 · 같은 고객 담당자의 제출 리스트를 한 칸으로 보여 준다.
 */
@Service
public class TalentMatchService {
    static final Set<String> MATCH_TASK_TYPES = Set.of("PROPOSAL_SUBMISSION", "SOURCING_START");
    private static final Pattern TASK_ID = Pattern.compile("^t_(\\d+)$");
    private static final Pattern NOTE_ID = Pattern.compile("^mn_(\\d+)$");
    private static final int MAX_TEXT = 4000;
    private static final int MAX_SUBMISSION = 30;
    private static final Set<String> BRIEF_SKIP_CATEGORIES = Set.of("OS", "MACHINE");

    private final TaskRepository tasks;
    private final MeetingNoteRepository notes;
    private final TalentProfileRepository profiles;
    private final TalentSubmissionRepository submissions;
    private final SkillDictionary dictionary;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Autowired
    public TalentMatchService(TaskRepository tasks, MeetingNoteRepository notes, TalentProfileRepository profiles,
                              TalentSubmissionRepository submissions, SkillDictionary dictionary, ObjectMapper mapper) {
        this(tasks, notes, profiles, submissions, dictionary, mapper, Clock.systemDefaultZone());
    }

    TalentMatchService(TaskRepository tasks, MeetingNoteRepository notes, TalentProfileRepository profiles,
                       TalentSubmissionRepository submissions, SkillDictionary dictionary, ObjectMapper mapper, Clock clock) {
        this.tasks = tasks;
        this.notes = notes;
        this.profiles = profiles;
        this.submissions = submissions;
        this.dictionary = dictionary;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** 후보 추천이 필요한 할 일 (인력 리스트 제출 · 인력 소싱 착수). 취소된 것은 뺀다. */
    @Transactional(readOnly = true)
    public List<TaskBrief> matchTasks(Long owner) {
        Map<String, Integer> counts = new HashMap<>();
        submissions.findByOwnerIdOrderByTaskIdAscPositionAsc(owner)
                .forEach(row -> counts.merge(row.getTaskId(), 1, Integer::sum));
        List<TaskBrief> result = new ArrayList<>();
        for (StoredTask stored : storedTasks(owner)) {
            TaskDocument task = stored.task();
            if (!MATCH_TASK_TYPES.contains(task.taskType()) || "CANCELLED".equals(task.status())) continue;
            result.add(brief(stored, counts.getOrDefault(stored.taskId(), 0)));
        }
        return result;
    }

    /** 할 일 기준 추천. text가 있으면 담당자가 고친 요청 글로 다시 계산한다 (할 일·회의록은 그대로). */
    @Transactional(readOnly = true)
    public MatchResult matchForTask(String taskId, String text, Long owner) {
        StoredTask stored = task(taskId, owner);
        List<SourceLine> sources = new ArrayList<>();
        LocalDate reference = LocalDate.now(clock);
        Optional<MeetingNote> note = note(stored.task().noteId(), owner);
        if (note.isPresent() && note.get().getOccurredAt() != null) reference = note.get().getOccurredAt().toLocalDate();
        if (text != null && !text.isBlank()) {
            sources.add(new SourceLine("TYPED", "직접 고친 요청", clip(text)));
        } else {
            sources.add(new SourceLine("TASK", "할 일", stored.task().content()));
            note.ifPresent(found -> sources.addAll(customerRequests(found)));
        }
        List<String> selected = submissionIds(owner, stored.taskId());
        int count = selected.size();
        return run(brief(stored, count), sources, reference, owner, selected);
    }

    /** 할 일 없이 요청 글만으로 추천 (시연·빠른 확인용). */
    @Transactional(readOnly = true)
    public MatchResult matchText(String text, Long owner) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("요청 내용을 입력해 주세요.");
        return run(null, List.of(new SourceLine("TYPED", "직접 입력", clip(text))), LocalDate.now(clock), owner, List.of());
    }

    @Transactional(readOnly = true)
    public List<SubmittedCandidate> submission(String taskId, Long owner) {
        StoredTask stored = task(taskId, owner);
        return submitted(owner, submissions.findByOwnerIdAndTaskIdOrderByPositionAsc(owner, stored.taskId()));
    }

    /** 담당자가 고른 후보로 제출 리스트를 바꾼다 (순서 유지). 확정된 후보만 넣을 수 있다. */
    @Transactional
    public List<SubmittedCandidate> saveSubmission(String taskId, List<String> profileIds, Long owner) {
        StoredTask stored = task(taskId, owner);
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(profileIds == null ? List.of() : profileIds));
        if (ids.size() > MAX_SUBMISSION) throw new IllegalArgumentException("제출 리스트는 " + MAX_SUBMISSION + "명까지 담을 수 있습니다.");
        List<Long> resolved = new ArrayList<>();
        for (String profileId : ids) {
            TalentProfile profile = profiles.findByIdAndUserId(TalentService.parseId(profileId), owner)
                    .orElseThrow(() -> new NoSuchElementException("인력 후보를 찾을 수 없습니다: " + profileId));
            if (profile.getStatus() != TalentStatus.CONFIRMED)
                throw new IllegalArgumentException("확정한 후보만 제출 리스트에 넣을 수 있습니다: " + profileId);
            resolved.add(profile.getId());
        }
        submissions.deleteByOwnerIdAndTaskId(owner, stored.taskId());
        submissions.flush();   // 같은 후보를 다시 넣을 때 고유 제약에 걸리지 않게 지우기를 먼저 반영한다
        LocalDateTime now = LocalDateTime.now(clock);
        List<TalentSubmission> rows = new ArrayList<>();
        for (int index = 0; index < resolved.size(); index++)
            rows.add(new TalentSubmission(owner, stored.taskId(), resolved.get(index), index, now));
        submissions.saveAll(rows);
        return submitted(owner, rows);
    }

    /**
     * 브리핑 한 칸: 이 할 일과 이어진 제출 리스트. 이어짐 규칙 — 같은 할 일, 같은 회의록(noteId), 같은 고객 담당자(counterpartPersonId).
     * 제출 리스트가 없으면 빈 목록 (브리핑 화면은 칸을 숨긴다).
     */
    @Transactional(readOnly = true)
    public List<SubmissionBrief> related(String taskId, Long owner) {
        StoredTask target = task(taskId, owner);
        Map<String, List<TalentSubmission>> byTask = new LinkedHashMap<>();
        submissions.findByOwnerIdOrderByTaskIdAscPositionAsc(owner)
                .forEach(row -> byTask.computeIfAbsent(row.getTaskId(), key -> new ArrayList<>()).add(row));
        if (byTask.isEmpty()) return List.of();
        List<SubmissionBrief> result = new ArrayList<>();
        for (StoredTask other : storedTasks(owner)) {
            List<TalentSubmission> rows = byTask.get(other.taskId());
            if (rows == null) continue;
            String relation = relation(target, other);
            if (relation == null) continue;
            List<SubmittedCandidate> candidates = submitted(owner, rows);
            if (candidates.isEmpty()) continue;
            result.add(new SubmissionBrief(other.taskId(), other.task().content(), other.task().dueDate(), relation, candidates));
        }
        return result;
    }

    // ── 내부 ──

    private MatchResult run(TaskBrief task, List<SourceLine> sources, LocalDate reference, Long owner, List<String> selected) {
        Requirement requirement = RequirementRules.extract(sources, reference, dictionary);
        List<TalentProfile> all = profiles.findByUserIdOrderByIdDesc(owner);
        List<TalentProfile> pool = all.stream().filter(profile -> profile.getStatus() == TalentStatus.CONFIRMED).toList();
        int drafts = (int) all.stream().filter(profile -> profile.getStatus() == TalentStatus.DRAFT).count();
        Map<Long, JsonNode> documents = new HashMap<>();
        for (TalentProfile profile : pool) {
            JsonNode document = readDocument(profile.getDocument());
            if (document != null) documents.put(profile.getId(), document);
        }
        CandidateMatcher.Result matched = CandidateMatcher.match(requirement, pool, documents, new HashSet<>(selected));
        return new MatchResult(task, sources, requirement, matched.candidates(), matched.excluded(), pool.size(), drafts, selected);
    }

    private static String relation(StoredTask target, StoredTask other) {
        if (target.taskId().equals(other.taskId())) return "SAME_TASK";
        String note = target.task().noteId();
        if (note != null && note.equals(other.task().noteId())) return "SAME_NOTE";
        String person = target.task().counterpartPersonId();
        if (person != null && person.equals(other.task().counterpartPersonId())) return "SAME_COUNTERPART";
        return null;
    }

    private List<String> submissionIds(Long owner, String taskId) {
        return submissions.findByOwnerIdAndTaskIdOrderByPositionAsc(owner, taskId).stream()
                .map(row -> TalentService.profileId(row.getProfileId())).toList();
    }

    private List<SubmittedCandidate> submitted(Long owner, List<TalentSubmission> rows) {
        List<SubmittedCandidate> result = new ArrayList<>();
        for (TalentSubmission row : rows) {
            Optional<TalentProfile> found = profiles.findByIdAndUserId(row.getProfileId(), owner);
            if (found.isEmpty()) continue;   // 지운 이력서는 건너뛴다
            TalentProfile profile = found.get();
            JsonNode document = readDocument(profile.getDocument());
            String grade = document == null ? null : TalentFields.text(document.path("grade").path("value"));
            List<String> skills = new ArrayList<>();
            // 브리핑 요약에는 언어·프레임워크·DBMS 같은 주요 기술만 (OS·기종은 뺀다)
            if (document != null) document.path("skills").forEach(skill -> {
                if (skills.size() < 5 && !BRIEF_SKIP_CATEGORIES.contains(skill.path("category").asText()))
                    skills.add(skill.path("name").asText());
            });
            result.add(new SubmittedCandidate(TalentService.profileId(profile.getId()), profile.getName(), grade,
                    profile.getCareerMonths(), profile.getAvailableFrom(), profile.getAvailableBasis(), List.copyOf(skills)));
        }
        return result;
    }

    private List<SourceLine> customerRequests(MeetingNote note) {
        List<SourceLine> lines = new ArrayList<>();
        JsonNode fields = readDocument(note.getFieldsJson());
        if (fields == null) return lines;
        for (JsonNode item : fields.path("customerRequests")) {
            String text = TalentFields.text(item.path("text"));
            String evidence = TalentFields.text(item.path("evidence"));
            if (text != null) lines.add(new SourceLine("CUSTOMER_REQUEST", "회의록 고객 요청", text));
            if (evidence != null && !evidence.equals(text)) lines.add(new SourceLine("CUSTOMER_REQUEST", "고객 요청 원문", evidence));
        }
        return lines;
    }

    private Optional<MeetingNote> note(String noteId, Long owner) {
        if (noteId == null) return Optional.empty();
        Matcher matcher = NOTE_ID.matcher(noteId);
        if (!matcher.matches()) return Optional.empty();
        return notes.findById(Long.parseLong(matcher.group(1)))
                .filter(note -> note.getUser() != null && Objects.equals(note.getUser().getId(), owner));
    }

    private StoredTask task(String taskId, Long owner) {
        Matcher matcher = TASK_ID.matcher(taskId == null ? "" : taskId);
        if (!matcher.matches()) throw new IllegalArgumentException("할 일 ID 형식이 아닙니다: " + taskId);
        TaskEntry entry = tasks.findById(Long.parseLong(matcher.group(1)))
                .filter(found -> Objects.equals(found.getOwnerId(), owner))
                .orElseThrow(() -> new NoSuchElementException("할 일을 찾을 수 없습니다: " + taskId));
        StoredTask stored = read(entry);
        if (stored == null) throw new IllegalStateException("저장된 할 일을 읽을 수 없습니다: " + taskId);
        return stored;
    }

    private List<StoredTask> storedTasks(Long owner) {
        return tasks.findByOwnerIdOrderByIdDesc(owner).stream().map(this::read).filter(Objects::nonNull).toList();
    }

    private StoredTask read(TaskEntry entry) {
        try {
            return new StoredTask("t_" + entry.getId(), TaskDocument.parse(mapper.readTree(entry.getDocument()), mapper));
        } catch (Exception exception) {
            return null;
        }
    }

    private JsonNode readDocument(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readTree(json);
        } catch (Exception exception) {
            return null;
        }
    }

    private static TaskBrief brief(StoredTask stored, int submissionCount) {
        TaskDocument task = stored.task();
        return new TaskBrief(stored.taskId(), task.taskType(), task.content(), task.dueDate(), task.status(), task.noteId(),
                submissionCount);
    }

    private static String clip(String text) {
        String trimmed = text.strip();
        return trimmed.length() > MAX_TEXT ? trimmed.substring(0, MAX_TEXT) : trimmed;
    }

    private record StoredTask(String taskId, TaskDocument task) {}
}
