package com.salesmap.backend.ai.dto;

import java.time.LocalDateTime;

public record AiAnalysisResponse(
        String customerName,
        String contactName,
        String productName,
        Long amount,
        String actionType,
        Long targetScheduleId,
        String targetScheduleTitle,
        String actionReason,
        String scheduleTitle,
        LocalDateTime scheduleDateTime,
        String todoContent,
        String keyIssues,
        String summary,
        Double confidenceScore
) {
}
