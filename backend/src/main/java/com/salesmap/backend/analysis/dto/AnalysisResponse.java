package com.salesmap.backend.analysis.dto;

import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.entity.AnalysisStatus;

import java.time.LocalDateTime;

public record AnalysisResponse(
        Long analysisId,
        Long sourceId,
        String customerName,
        String contactName,
        String productName,
        Long amount,
        String actionType,
        Long targetScheduleId,
        String targetScheduleTitle,
        String actionReason,
        String scheduleText,
        String followUpAction,
        String summary,
        AnalysisStatus status,
        LocalDateTime analyzedAt,
        LocalDateTime approvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AnalysisResponse from(Analysis analysis) {
        return new AnalysisResponse(
                analysis.getId(),
                analysis.getSource().getId(),
                analysis.getCustomerName(),
                analysis.getContactName(),
                analysis.getProductName(),
                analysis.getAmount(),
                analysis.getActionType(),
                analysis.getTargetScheduleId(),
                analysis.getTargetScheduleTitle(),
                analysis.getActionReason(),
                analysis.getScheduleText(),
                analysis.getFollowUpAction(),
                analysis.getSummary(),
                analysis.getStatus(),
                analysis.getAnalyzedAt(),
                analysis.getApprovedAt(),
                analysis.getCreatedAt(),
                analysis.getUpdatedAt()
        );
    }
}
