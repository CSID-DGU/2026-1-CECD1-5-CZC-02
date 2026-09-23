package com.salesmap.backend.analysis.dto;

import com.salesmap.backend.analysis.entity.AnalysisRevision;

import java.time.LocalDateTime;

public record AnalysisRevisionResponse(
        Long revisionId,
        String fieldName,
        String aiValue,
        String previousValue,
        String userValue,
        LocalDateTime revisedAt
) {

    public static AnalysisRevisionResponse from(AnalysisRevision revision) {
        return new AnalysisRevisionResponse(
                revision.getId(),
                revision.getFieldName(),
                revision.getAiValue(),
                revision.getPreviousValue(),
                revision.getUserValue(),
                revision.getCreatedAt()
        );
    }
}
