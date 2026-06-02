package com.salesmap.backend.schedule.dto;

import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleStatus;

import java.time.LocalDateTime;

public record ScheduleResponse(
        Long scheduleId,
        Long userId,
        Long analysisId,
        Long sourceId,
        String actionType,
        String title,
        LocalDateTime scheduleDateTime,
        String memo,
        LocalDateTime reminderDateTime,
        ScheduleStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ScheduleResponse from(Schedule schedule) {
        Long analysisId = schedule.getAnalysis() == null ? null : schedule.getAnalysis().getId();
        Long sourceId = schedule.getAnalysis() == null ? null : schedule.getAnalysis().getSource().getId();
        String actionType = schedule.getAnalysis() == null ? null : schedule.getAnalysis().getActionType();

        return new ScheduleResponse(
                schedule.getId(),
                schedule.getUser().getId(),
                analysisId,
                sourceId,
                actionType,
                schedule.getTitle(),
                schedule.getScheduleDateTime(),
                schedule.getMemo(),
                schedule.getReminderDateTime(),
                schedule.getStatus(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt()
        );
    }
}
