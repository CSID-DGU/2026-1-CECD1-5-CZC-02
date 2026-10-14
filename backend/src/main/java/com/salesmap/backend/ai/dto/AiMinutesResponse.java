package com.salesmap.backend.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * AI 모듈 POST /minutes 응답. meetingNote는 contracts/meeting_note.schema.json 형식 그대로다.
 */
public record AiMinutesResponse(
        JsonNode meetingNote,
        // ollama / openai, LLM을 못 썼으면 rule, ai.module.mode=mock이면 mock
        String llmProvider,
        String llmModel,
        String llmFallbackReason,
        List<DroppedItem> droppedItems
) {

    /** 규칙 검증에서 버린 LLM 항목. 디버깅·평가용이며 회의록에는 들어가지 않는다. */
    public record DroppedItem(
            String field,
            String text,
            String evidence,
            String reason
    ) {
    }
}
