package com.salesmap.backend.schedule.dto;

import java.time.LocalDateTime;

public record ScheduleUpdateRequest(
        String title,
        LocalDateTime scheduleDateTime,
        String memo
) {
}
