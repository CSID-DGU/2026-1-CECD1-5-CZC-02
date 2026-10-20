package com.salesmap.backend.revision.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.analysis.entity.AnalysisRevision;

import java.time.LocalDateTime;

public record RevisionResponse(
        String revisionId,
        String screen,
        String targetType,
        String targetId,
        String fieldName,
        JsonNode proposedValue,
        JsonNode finalValue,
        String action,
        String proposedBy,
        String modelVersion,
        String actorId,
        LocalDateTime decidedAt
) {
    public static RevisionResponse from(AnalysisRevision revision, ObjectMapper mapper) {
        return new RevisionResponse(
                "rv_" + revision.getId(), revision.getScreen(), revision.getTargetType(),
                revision.getTargetId(), revision.getFieldName(), read(mapper, revision.getProposedValue()),
                read(mapper, revision.getFinalValue()), revision.getAction(), revision.getProposedBy(),
                revision.getModelVersion(), revision.getActorId(), revision.getDecidedAt());
    }

    private static JsonNode read(ObjectMapper mapper, String value) {
        try { return value == null ? mapper.nullNode() : mapper.readTree(value); }
        catch (Exception exception) { throw new IllegalStateException("저장된 수정 값이 JSON이 아닙니다.", exception); }
    }
}
