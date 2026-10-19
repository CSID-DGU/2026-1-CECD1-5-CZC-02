package com.salesmap.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TaskService {
    private final TaskRepository tasks;
    private final TaskRevisionRepository revisions;
    private final ObjectMapper mapper;
    public TaskService(TaskRepository tasks, TaskRevisionRepository revisions, ObjectMapper mapper) {
        this.tasks = tasks; this.revisions = revisions; this.mapper = mapper;
    }
    @Transactional(readOnly = true)
    public List<TaskDocument> list(Long owner) {
        return tasks.findByOwnerIdOrderByIdDesc(owner).stream().map(this::document).toList();
    }
    @Transactional(readOnly = true)
    public TaskDocument get(String id, Long owner) { return document(owned(id, owner)); }

    /** 같은 내용·근거·상대를 재수집하면 저장본을 반환하고 사람 수정을 덮어쓰지 않는다. */
    @Transactional
    public List<TaskDocument> importDrafts(List<JsonNode> drafts, String noteId, Long owner) {
        List<TaskDocument> result = new ArrayList<>();
        Map<String, Integer> occurrences = new HashMap<>();
        for (JsonNode node : drafts) {
            TaskDocument doc = TaskDocument.parse(node, mapper);
            if (!Objects.equals(doc.noteId(), noteId) || !"DRAFT".equals(doc.status()))
                throw new IllegalArgumentException("출처 회의록의 DRAFT만 가져올 수 있습니다.");
            String identity = write(java.util.Arrays.asList(noteId, doc.content(), doc.evidence(), doc.counterpartPersonId()));
            int occurrence = occurrences.merge(identity, 1, Integer::sum);
            String key = hash(identity + ":" + occurrence);
            TaskEntry entry = tasks.findByOwnerIdAndDraftKey(owner, key).orElseGet(() ->
                    tasks.save(new TaskEntry(owner, noteId, key, write(doc))));
            result.add(document(entry));
        }
        return result;
    }
    @Transactional
    public TaskDocument createManual(JsonNode node, Long owner) {
        TaskDocument doc = TaskDocument.parse(node, mapper);
        if (doc.noteId() != null || !"DRAFT".equals(doc.status()) || !"HUMAN".equals(doc.taskTypeSource()) || Boolean.TRUE.equals(doc.dueDateIsDefault()))
            throw new IllegalArgumentException("직접 추가는 noteId=null, DRAFT, HUMAN이어야 합니다.");
        TaskEntry entry = tasks.save(new TaskEntry(owner, null, UUID.randomUUID().toString(), write(doc)));
        TaskDocument stored = document(entry);
        record(entry, "content", mapper.nullNode(), mapper.valueToTree(stored.content()), "CREATED_NEW", owner);
        return stored;
    }
    @Transactional
    public TaskDocument update(String id, JsonNode node, String expectedEtag, Long owner) {
        TaskEntry entry = tasks.lockOwned(parseId(id), owner).orElseThrow(() -> new NoSuchElementException("할 일이 없습니다."));
        TaskDocument before = document(entry);
        if (!etag(before).equals(expectedEtag)) throw new TaskConflictException("다른 수정이 있습니다. 새로고침 후 다시 확인하세요.");
        TaskDocument after = TaskDocument.parse(node, mapper);
        if (!Objects.equals(before.taskId(), after.taskId()) || !Objects.equals(before.noteId(), after.noteId())
                || !Objects.equals(before.evidence(), after.evidence()))
            throw new IllegalArgumentException("ID·출처·원문 근거는 수정할 수 없습니다.");
        if (!Objects.equals(before.dueDate(), after.dueDate()) && Boolean.TRUE.equals(after.dueDateIsDefault()))
            after = new TaskDocument(after.taskId(), after.noteId(), after.content(), after.dueDate(), false,
                    after.assignee(), after.counterpartPersonId(), after.commitmentKind(), after.taskType(),
                    after.taskTypeConfidence(), after.taskTypeSource(), after.evidence(), after.prerequisites(), after.status());
        boolean typeEdited = !before.taskType().equals(after.taskType());
        if (typeEdited) after = new TaskDocument(after.taskId(), after.noteId(), after.content(), after.dueDate(),
                after.dueDateIsDefault(), after.assignee(), after.counterpartPersonId(), after.commitmentKind(),
                after.taskType(), 1.0, "HUMAN", after.evidence(), after.prerequisites(), after.status());
        else if (!before.taskTypeSource().equals(after.taskTypeSource()) || !before.taskTypeConfidence().equals(after.taskTypeConfidence()))
            throw new IllegalArgumentException("종류를 바꾸지 않고 분류 메타데이터만 고칠 수 없습니다.");
        if ("CANCELLED".equals(before.status()) && !before.equals(after))
            throw new IllegalArgumentException("취소한 할 일은 수정할 수 없습니다.");
        validateTransition(before.status(), after.status());
        JsonNode old = mapper.valueToTree(before), next = mapper.valueToTree(after);
        JsonNode proposal = read(entry.getProposed());
        boolean accepting = "DRAFT".equals(before.status()) && "CONFIRMED".equals(after.status());
        final TaskDocument finalAfter = after;
        next.fieldNames().forEachRemaining(field -> {
            boolean changed = !Objects.equals(old.get(field), next.get(field));
            if (changed || (accepting && !Set.of("taskId", "noteId", "evidence").contains(field))) {
                String action;
                if ("status".equals(field) && "CANCELLED".equals(finalAfter.status())) action = "REJECTED";
                else if (accepting && "status".equals(field)) action = "ACCEPTED";
                else action = Objects.equals(proposal.path(field), next.get(field)) ? "ACCEPTED" : "EDITED";
                record(entry, field, proposal.path(field), next.get(field), action, owner);
            }
        });
        entry.update(write(after));
        return after;
    }
    static void validateTransition(String before, String after) {
        if (before.equals(after)) return;
        boolean valid = switch (before) {
            case "DRAFT" -> Set.of("CONFIRMED", "CANCELLED").contains(after);
            case "CONFIRMED" -> Set.of("DONE", "CANCELLED").contains(after);
            case "DONE" -> "CONFIRMED".equals(after);
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("허용되지 않은 할 일 상태 전이입니다.");
    }
    @Transactional(readOnly = true)
    public List<JsonNode> pendingRevisions(Long owner) {
        return revisions.findByOwnerIdOrderByIdAsc(owner).stream().map(row -> {
            ObjectNode payload = (ObjectNode) read(row.getPayload());
            payload.put("revisionId", "rv_task_" + row.getId());
            return (JsonNode) payload;
        }).toList();
    }
    private void record(TaskEntry entry, String field, JsonNode proposed, JsonNode value, String action, Long owner) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("screen", "TASK_REVIEW"); payload.put("targetType", "TASK");
        payload.put("targetId", "t_" + entry.getId()); payload.put("fieldName", field);
        payload.set("proposedValue", proposed); payload.set("finalValue", value);
        payload.put("action", action);
        String origin = read(entry.getProposed()).path("taskTypeSource").asText("RULE");
        payload.put("proposedBy", "HUMAN".equals(origin) ? "RULE" : origin);
        payload.putNull("modelVersion"); payload.put("actorId", "user_" + owner);
        payload.put("decidedAt", LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        revisions.save(new TaskRevision(owner, write(payload)));
    }
    private TaskEntry owned(String id, Long owner) {
        TaskEntry entry = tasks.findById(parseId(id)).orElseThrow(() -> new NoSuchElementException("할 일이 없습니다."));
        if (!entry.getOwnerId().equals(owner)) throw new AccessDeniedException("해당 할 일에 접근할 수 없습니다.");
        return entry;
    }
    private Long parseId(String id) {
        if (id == null || !id.matches("t_[1-9][0-9]*")) throw new IllegalArgumentException("t_<DB PK> ID가 필요합니다.");
        return Long.parseLong(id.substring(2));
    }
    TaskDocument document(TaskEntry entry) {
        return TaskDocument.parse(read(entry.getDocument()), mapper).withId("t_" + entry.getId());
    }
    public String etag(TaskDocument doc) { return "\"" + hash(write(doc)) + "\""; }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    private JsonNode read(String value) {
        try { return mapper.readTree(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
