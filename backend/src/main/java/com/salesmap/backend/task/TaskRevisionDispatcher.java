package com.salesmap.backend.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.revision.dto.RevisionCreateRequest;
import com.salesmap.backend.revision.service.RevisionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공식 저장과 큐 삭제를 한 트랜잭션으로 처리한다. 실패하면 큐를 보존한다. */
@Service
public class TaskRevisionDispatcher {
    private final TaskRevisionRepository outbox;
    private final RevisionService revisions;
    private final ObjectMapper mapper;

    public TaskRevisionDispatcher(TaskRevisionRepository outbox, RevisionService revisions, ObjectMapper mapper) {
        this.outbox = outbox; this.revisions = revisions; this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${task.revision.interval-ms:10000}")
    @Transactional
    public void deliver() {
        for (TaskRevision row : outbox.lockPending(PageRequest.of(0, 100))) {
            try {
                var node = mapper.readTree(row.getPayload());
                var request = new RevisionCreateRequest(node.path("screen").asText(), node.path("targetType").asText(),
                        node.path("targetId").asText(), node.path("fieldName").asText(),
                        node.get("proposedValue"), node.get("finalValue"), node.path("action").asText(),
                        node.path("proposedBy").asText(), node.path("modelVersion").isNull() ? null : node.path("modelVersion").asText());
                revisions.create(request, row.getOwnerId());
                outbox.delete(row);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("할 일 수정 기록을 읽을 수 없습니다.", e);
            }
        }
    }
}
