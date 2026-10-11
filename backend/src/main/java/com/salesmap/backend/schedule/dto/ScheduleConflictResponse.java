package com.salesmap.backend.schedule.dto;

import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ScheduleConflictResponse(
        String type,
        CandidateSchedule newSchedule,
        List<ExistingSchedule> conflicts
) {

    public record CandidateSchedule(
            String title,
            LocalDateTime scheduleDateTime
    ) {
    }

    public record ExistingSchedule(
            Long scheduleId,
            String title,
            LocalDateTime scheduleDateTime,
            ScheduleStatus status
    ) {
        public static ExistingSchedule from(Schedule schedule) {
            return new ExistingSchedule(
                    schedule.getId(),
                    schedule.getTitle(),
                    schedule.getScheduleDateTime(),
                    schedule.getStatus()
            );
        }
    }
}
