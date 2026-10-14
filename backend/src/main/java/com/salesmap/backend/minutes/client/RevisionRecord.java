package com.salesmap.backend.minutes.client;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * contracts/revision.schema.json 한 줄에서 revisionId를 뺀 값. revisionId(rv_ + PK)는 저장하는 D가 붙인다.
 */
public record RevisionRecord(
        // MEETING_NOTE_REVIEW
        String screen,
        // MEETING_NOTE
        String targetType,
        // mn_12
        String targetId,
        // fields.commitments, perPerson[0].status
        String fieldName,
        // AI가 처음 제안한 값 (없으면 JSON null)
        JsonNode proposedValue,
        // 사람이 최종 선택한 값
        JsonNode finalValue,
        // EDITED / ACCEPTED
        String action,
        // RULE / LLM
        String proposedBy,
        String modelVersion,
        // user_<PK>
        String actorId,
        // "YYYY-MM-DDTHH:mm:ss"
        String decidedAt
) {
}
