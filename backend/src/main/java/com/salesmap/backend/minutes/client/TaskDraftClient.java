package com.salesmap.backend.minutes.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;

import java.util.List;

/**
 * C(할 일) 호출 자리. 회의록 초안을 넘겨 할 일 초안을 받고, 회의록을 확정하면 할 일 확정도 C에 넘긴다.
 * <p>
 * C가 실제 구현을 빈으로 등록하면 {@link FakeTaskDraftClient}를 지운다.
 */
public interface TaskDraftClient {

    /** @return contracts/task.schema.json 형식 배열 (status=DRAFT) */
    List<JsonNode> draftTasks(MeetingNoteDocument note, Long userId);

    /** 회의록이 CONFIRMED가 된 뒤 부른다. 어떤 할 일을 확정할지는 C가 정한다. */
    void confirmTasks(MeetingNoteDocument note, Long userId);
}
