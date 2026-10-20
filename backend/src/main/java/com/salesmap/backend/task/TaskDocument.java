package com.salesmap.backend.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/** contracts/task.schema.json 그대로. 저장 메타데이터는 응답 본문에 추가하지 않는다. */
public record TaskDocument(
        String taskId, String noteId, String content, String dueDate, Boolean dueDateIsDefault,
        String assignee, String counterpartPersonId, String commitmentKind, String taskType,
        Double taskTypeConfidence, String taskTypeSource, String evidence,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> prerequisites, String status
) {
    public static final Set<String> TYPES = Set.of("MEETING_PREP", "VISIT_PREP", "CONTRACT_PREP",
            "RENEWAL_INTERVIEW", "PROPOSAL_SUBMISSION", "REJECTION_NOTICE", "DOC_COLLECTION",
            "END_EVALUATION", "PROFITABILITY_REVIEW", "SOURCING_START", "NONE");
    public static final Set<String> STATUSES = Set.of("DRAFT", "CONFIRMED", "DONE", "CANCELLED");
    private static final Set<String> REQUIRED = Set.of("taskId", "noteId", "content", "dueDate",
            "dueDateIsDefault", "assignee", "counterpartPersonId", "commitmentKind", "taskType",
            "taskTypeConfidence", "taskTypeSource", "evidence", "status");
    private static final Set<String> NULLABLE = Set.of("noteId", "dueDate", "assignee", "counterpartPersonId");

    public static TaskDocument parse(JsonNode node, ObjectMapper mapper) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("task 객체가 필요합니다.");
        node.fieldNames().forEachRemaining(key -> {
            if (!REQUIRED.contains(key) && !"prerequisites".equals(key))
                throw new IllegalArgumentException("정의되지 않은 task 필드: " + key);
        });
        for (String key : REQUIRED) {
            JsonNode value = node.get(key);
            if (value == null) throw new IllegalArgumentException("필수 task 필드: " + key);
            if (NULLABLE.contains(key) && value.isNull()) continue;
            boolean valid = switch (key) {
                case "dueDateIsDefault" -> value.isBoolean();
                case "taskTypeConfidence" -> value.isNumber();
                default -> value.isTextual();
            };
            if (!valid) throw new IllegalArgumentException("task 필드 타입 오류: " + key);
        }
        if (node.has("prerequisites")) {
            if (!node.get("prerequisites").isArray()) throw new IllegalArgumentException("prerequisites 배열 필요");
            node.get("prerequisites").forEach(item -> {
                if (!item.isTextual()) throw new IllegalArgumentException("prerequisites 문자열 필요");
            });
        }
        TaskDocument doc = mapper.convertValue(node, TaskDocument.class);
        doc.validate();
        return doc;
    }

    public void validate() {
        if (taskId == null || !taskId.matches("^t_[0-9a-z_]+$") || content == null || content.isBlank()
                || evidence == null || evidence.isBlank() || dueDateIsDefault == null
                || commitmentKind == null || !Set.of("EXPLICIT", "IMPLICIT").contains(commitmentKind)
                || taskType == null || !TYPES.contains(taskType) || status == null || !STATUSES.contains(status)
                || taskTypeSource == null || !Set.of("RULE", "MODEL", "LLM", "HUMAN").contains(taskTypeSource)
                || taskTypeConfidence == null || !Double.isFinite(taskTypeConfidence)
                || taskTypeConfidence < 0 || taskTypeConfidence > 1)
            throw new IllegalArgumentException("task 계약에 맞지 않는 값입니다.");
        if (dueDate != null) {
            try {
                if (!dueDate.matches("\\d{4}-\\d{2}-\\d{2}")) throw new DateTimeParseException("형식", dueDate, 0);
                LocalDate.parse(dueDate);
            } catch (DateTimeParseException e) { throw new IllegalArgumentException("유효한 기한 날짜가 필요합니다."); }
        }
        if (Boolean.TRUE.equals(dueDateIsDefault) && dueDate == null)
            throw new IllegalArgumentException("기본 기한 표시에는 날짜가 필요합니다.");
    }

    public TaskDocument withId(String id) {
        return new TaskDocument(id, noteId, content, dueDate, dueDateIsDefault, assignee, counterpartPersonId,
                commitmentKind, taskType, taskTypeConfidence, taskTypeSource, evidence, prerequisites, status);
    }
}
