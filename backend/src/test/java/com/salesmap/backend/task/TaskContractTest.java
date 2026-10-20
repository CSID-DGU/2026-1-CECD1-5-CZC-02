package com.salesmap.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 실제 공용 스키마로 C 출력의 필수키·타입·enum·패턴·범위를 검사한다. 외부 라이브러리/네트워크 없음. */
class TaskContractTest {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode schema(String name) throws Exception {
        return mapper.readTree(Files.readString(Path.of("..", "contracts", name + ".schema.json"), java.nio.charset.StandardCharsets.UTF_8));
    }
    void assertSchema(JsonNode value, JsonNode schema) {
        if (schema.has("type")) {
            JsonNode types = schema.get("type");
            boolean matches = false;
            for (JsonNode type : types.isArray() ? types : List.of(types)) {
                matches |= switch(type.asText()) {
                    case "object" -> value.isObject(); case "array" -> value.isArray();
                    case "string" -> value.isTextual(); case "number" -> value.isNumber();
                    case "boolean" -> value.isBoolean(); case "null" -> value.isNull(); default -> false;
                };
            }
            assertTrue(matches, "타입: " + value);
        }
        if (schema.has("enum")) {
            boolean found = false; for (JsonNode allowed : schema.get("enum")) found |= allowed.equals(value);
            assertTrue(found, "enum: " + value);
        }
        if (value.isObject()) {
            if (schema.has("required")) for (JsonNode required : schema.get("required")) assertTrue(value.has(required.asText()), required.asText());
            JsonNode props = schema.path("properties");
            value.fieldNames().forEachRemaining(key -> {
                if (schema.has("additionalProperties") && !schema.get("additionalProperties").asBoolean()) assertTrue(props.has(key), key);
                if (props.has(key)) assertSchema(value.get(key), props.get(key));
            });
        }
        if (value.isTextual()) {
            if (schema.has("minLength")) assertTrue(value.asText().length() >= schema.get("minLength").asInt());
            if (schema.has("pattern")) assertTrue(value.asText().matches(schema.get("pattern").asText()), value.asText());
        }
        if (value.isNumber()) {
            if (schema.has("minimum")) assertTrue(value.asDouble() >= schema.get("minimum").asDouble());
            if (schema.has("maximum")) assertTrue(value.asDouble() <= schema.get("maximum").asDouble());
        }
        if (value.isArray() && schema.has("items")) for (JsonNode item : value) assertSchema(item, schema.get("items"));
    }
    @Test void storedTaskAndEveryRevisionMatchSharedSchemas() throws Exception {
        TaskRepository tasks = mock(TaskRepository.class);
        TaskRevisionRepository revisions = mock(TaskRevisionRepository.class);
        TaskService service = new TaskService(tasks, revisions, mapper);
        ObjectNode sample = (ObjectNode) mapper.readTree("""
                {"taskId":"t_draft","noteId":"mn_1","content":"계약서 송부","dueDate":null,
                "dueDateIsDefault":false,"assignee":null,"counterpartPersonId":null,"commitmentKind":"EXPLICIT",
                "taskType":"CONTRACT_PREP","taskTypeConfidence":1.0,"taskTypeSource":"RULE",
                "evidence":"계약서를 보내기로 함","status":"DRAFT"}
                """);
        TaskEntry entry = new TaskEntry(1L, "mn_1", "key", mapper.writeValueAsString(sample));
        ReflectionTestUtils.setField(entry, "id", 3L);
        when(tasks.lockOwned(3L, 1L)).thenReturn(Optional.of(entry));
        java.util.ArrayList<TaskRevision> pending = new java.util.ArrayList<>();
        when(revisions.save(any())).thenAnswer(inv -> {
            TaskRevision row = inv.getArgument(0); ReflectionTestUtils.setField(row, "id", (long) pending.size() + 1);
            pending.add(row); return row;
        });
        TaskDocument before = service.document(entry);
        ObjectNode update = mapper.valueToTree(before); update.put("status", "CONFIRMED");
        TaskDocument after = service.update("t_3", update, service.etag(before), 1L);
        assertSchema(mapper.valueToTree(after), schema("task"));
        when(revisions.findByOwnerIdOrderByIdAsc(1L)).thenReturn(pending);
        List<JsonNode> records = service.pendingRevisions(1L);
        assertFalse(records.isEmpty());
        for (JsonNode record : records) assertSchema(record, schema("revision"));
        assertTrue(records.stream().anyMatch(r -> r.path("fieldName").asText().equals("status") && r.path("action").asText().equals("ACCEPTED")));
    }
}
