package com.salesmap.backend.schedule.dto;

import java.time.LocalDateTime;

public record ScheduleResponse(
        Long scheduleId,
        Long analysisId,
        String title,
        LocalDateTime scheduleDateTime,
        String memo,
        String status
) {
}
