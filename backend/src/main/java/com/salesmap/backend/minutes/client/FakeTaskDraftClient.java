package com.salesmap.backend.minutes.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * C의 할 일 API가 생기기 전까지 쓰는 가짜. 회의록 내용과 상관없이 contracts/examples/task 샘플 3건을
 * noteId만 바꾸고 status=DRAFT로 돌려준다. 확정은 로그만 남긴다.
 */
@Component
public class FakeTaskDraftClient implements TaskDraftClient {

    private static final Logger log = LoggerFactory.getLogger(FakeTaskDraftClient.class);

    private final ObjectMapper objectMapper;

    public FakeTaskDraftClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        log.warn("[MINUTES] C 할 일 API가 아직 없어 contracts 샘플을 돌려주는 가짜 구현을 씁니다.");
    }

    @Override
    public List<JsonNode> draftTasks(MeetingNoteDocument note, Long userId) {
        return ContractSamples.readArray(objectMapper, ContractSamples.TASK).stream()
                .map(task -> {
                    ((ObjectNode) task).put("noteId", note.noteId());
                    ((ObjectNode) task).put("status", "DRAFT");
                    return task;
                })
                .toList();
    }

    @Override
    public void confirmTasks(MeetingNoteDocument note, Long userId) {
        log.info("[MINUTES][FAKE_TASK] 할 일 확정 요청 (C 미구현). noteId={}, userId={}", note.noteId(), userId);
    }
}
