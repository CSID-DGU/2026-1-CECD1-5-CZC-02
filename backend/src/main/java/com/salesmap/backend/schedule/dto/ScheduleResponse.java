package com.salesmap.backend.schedule.dto;

import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleOutcome;
import com.salesmap.backend.schedule.entity.ScheduleStatus;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

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
        String googleCalendarEventId,
        ScheduleStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        ScheduleOutcome outcome,
        LocalDateTime outcomeRecordedAt
) {
    private static final Pattern TASK_MARKER = Pattern.compile("(?m)^\\[SALESMAP_TASK:[^\\]]+\\]\\s*\\n?");

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
                displayMemo(schedule.getMemo()),
                schedule.getReminderDateTime(),
                schedule.getGoogleCalendarEventId(),
                schedule.getStatus(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt(),
                schedule.getOutcome(),
                schedule.getOutcomeRecordedAt()
        );
    }

    private static String displayMemo(String memo) {
        if (memo == null || memo.isBlank()) return "";
        return TASK_MARKER.matcher(memo).replaceFirst("").trim();
    }
}
