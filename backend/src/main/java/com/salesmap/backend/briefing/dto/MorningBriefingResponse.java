package com.salesmap.backend.briefing.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MorningBriefingResponse(
        LocalDate briefingDate,
        LocalDateTime generatedAt,
        boolean bundled,
        List<BriefingItem> items
) {
    public record BriefingItem(
            String taskId,
            String taskType,
            String content,
            String dueDate,
            List<String> preparationItems
    ) {
    }
}
