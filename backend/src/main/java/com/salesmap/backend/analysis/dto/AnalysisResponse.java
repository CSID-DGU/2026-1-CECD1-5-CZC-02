package com.salesmap.backend.analysis.dto;

public record AnalysisResponse(
        Long analysisId,
        Long sourceId,
        String customerName,
        String contactName,
        String productName,
        Long amount,
        String scheduleText,
        String followUpAction,
        String summary,
        String status
) {
}
