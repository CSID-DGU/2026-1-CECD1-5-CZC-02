package com.salesmap.backend.analysis.dto;

public record AnalysisUpdateRequest(
        String customerName,
        String contactName,
        String productName,
        Long amount,
        String attendees,
        String actionType,
        String targetScheduleTitle,
        String actionReason,
        String scheduleText,
        String followUpAction,
        String summary,
        // SALES_ACTIVITY / NON_BUSINESS / UNKNOWN. 없으면 기존 값 유지
        String businessType
) {
}
