package com.salesmap.backend.minutes.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;

import java.util.List;

/**
 * C(할 일) 호출 자리. 실제 구현은 C의 {@code task.MeetingTaskAdapter}이고 {@link TaskDraftClientConfig}에서 빈으로 등록한다.
 */
public interface TaskDraftClient {

    /** 회의록 초안에서 할 일 초안을 뽑아 C가 저장한다. @return contracts/task.schema.json 형식 배열 (status=DRAFT) */
    List<JsonNode> draftTasks(MeetingNoteDocument note, Long userId);

    /**
     * 회의록을 확정하기 전에 부른다. 할 일은 사람이 하나씩 확정하므로 일괄 확정하지 않고,
     * 이 회의록에 아직 DRAFT 할 일이 남아 있으면 IllegalArgumentException으로 확정을 막는다 (D-023 ⑤).
     */
    void confirmTasks(MeetingNoteDocument note, Long userId);
}
