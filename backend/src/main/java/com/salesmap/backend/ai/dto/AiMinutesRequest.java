package com.salesmap.backend.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * AI 모듈 POST /minutes 요청 (ai-module/app/minutes/schemas.py의 MinutesRequest).
 * 시각은 "YYYY-MM-DDTHH:mm:ss" 문자열로 보낸다 (contracts 공통 규칙).
 */
public record AiMinutesRequest(
        // 저장 전이라 PK가 없으므로 임시 ID. 저장 후 백엔드가 mn_<PK>로 바꾼다.
        String noteId,
        List<SourceRef> sourceRefs,
        String text,
        String occurredAt,
        String location,
        // FULL | LIGHT | null(AI 모듈이 규칙으로 정함)
        String mode,
        List<String> attendees,
        // A의 identity_resolution 배열 그대로. AI 모듈은 resolutionId·mention.name·personId만 읽는다.
        List<JsonNode> identityResolutions
) {

    public record SourceRef(
            String sourceId,
            String sourceType
    ) {
    }
}
