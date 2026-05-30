package com.salesmap.backend.ai.dto;

import java.time.LocalDateTime;

public record AiAnalysisResponse(
        String customerName,
        String contactName,
        String productName,
        Long amount,
        String scheduleTitle,
        LocalDateTime scheduleDateTime,
        String todoContent,
        String keyIssues,
        String summary,
        Double confidenceScore
) {
}
