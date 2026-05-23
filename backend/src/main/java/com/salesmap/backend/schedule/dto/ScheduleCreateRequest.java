package com.salesmap.backend.schedule.dto;

import java.time.LocalDateTime;

public record ScheduleCreateRequest(
        Long analysisId,
        String title,
        LocalDateTime scheduleDateTime,
        String memo
) {
}
