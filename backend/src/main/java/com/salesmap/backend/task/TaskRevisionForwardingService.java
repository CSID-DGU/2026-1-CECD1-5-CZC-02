package com.salesmap.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.revision.dto.RevisionCreateRequest;
import com.salesmap.backend.revision.dto.RevisionResponse;
import com.salesmap.backend.revision.service.RevisionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class TaskRevisionForwardingService {
    private final TaskRevisionRepository outbox;
    private final RevisionService revisions;
    private final ObjectMapper mapper;

    public TaskRevisionForwardingService(TaskRevisionRepository outbox,
                                         RevisionService revisions,
                                         ObjectMapper mapper) {
        this.outbox = outbox;
        this.revisions = revisions;
        this.mapper = mapper;
    }

    /** C outbox를 D 공식 revision 저장소로 전달한다. 같은 DB 트랜잭션 안에서 완료 표시까지 처리한다. */
    @Transactional
    public List<RevisionResponse> forwardPending(Long ownerId) {
        return outbox.findByOwnerIdOrderByIdAsc(ownerId).stream()
                .filter(row -> !row.isDelivered())
                .map(row -> forward(row, ownerId))
                .toList();
    }

    private RevisionResponse forward(TaskRevision row, Long ownerId) {
        JsonNode payload = read(row.getPayload());
        RevisionResponse saved = revisions.create(new RevisionCreateRequest(
                required(payload, "screen"),
                required(payload, "targetType"),
                required(payload, "targetId"),
                required(payload, "fieldName"),
                payload.get("proposedValue"),
                payload.get("finalValue"),
                required(payload, "action"),
                required(payload, "proposedBy"),
                textOrNull(payload, "modelVersion")
        ), ownerId);
        row.markDelivered(saved.revisionId(), LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0));
        outbox.save(row);
        return saved;
    }

    private JsonNode read(String payload) {
        try {
            return mapper.readTree(payload);
        } catch (Exception exception) {
            throw new IllegalArgumentException("revision outbox payload가 JSON이 아닙니다.", exception);
        }
    }

    private String required(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("revision outbox의 " + field + "가 필요합니다.");
        }
        return value.asText();
    }

    private String textOrNull(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
