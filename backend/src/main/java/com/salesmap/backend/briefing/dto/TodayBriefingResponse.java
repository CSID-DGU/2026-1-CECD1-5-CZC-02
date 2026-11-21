package com.salesmap.backend.briefing.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TodayBriefingResponse(
        LocalDate briefingDate,
        LocalDateTime generatedAt,
        boolean bundled,
        List<MorningBriefingResponse.BriefingItem> dueToday,
        List<MorningBriefingResponse.BriefingItem> overdue,
        List<MorningBriefingResponse.BriefingItem> pendingConfirmation,
        List<MorningBriefingResponse.BriefingItem> tomorrowPreparation
) {
}
