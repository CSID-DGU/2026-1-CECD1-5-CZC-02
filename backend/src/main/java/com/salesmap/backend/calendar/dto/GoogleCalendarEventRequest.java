package com.salesmap.backend.calendar.dto;

import java.time.LocalDateTime;

public record GoogleCalendarEventRequest(
        String summary,
        String description,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime
) {
}
