package com.salesmap.backend.minutes.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmap.backend.ai.dto.AiMinutesResponse;

import java.util.List;

/**
 * 초안 만들기(A 식별 → B 회의록 → C 할 일) 결과 묶음.
 * identityResolutions·tasks는 A·C의 contracts 형식 그대로 전달만 한다(B는 내용을 해석하지 않음).
 */
public record MeetingNoteDraftResponse(
        MeetingNoteResponse note,
        List<JsonNode> identityResolutions,
        List<JsonNode> tasks,
        // AI 모듈 규칙 검증에서 버린 항목 (근거가 원문에 없음 등). 회의록에는 들어가지 않는다
        List<AiMinutesResponse.DroppedItem> droppedItems
) {
}
