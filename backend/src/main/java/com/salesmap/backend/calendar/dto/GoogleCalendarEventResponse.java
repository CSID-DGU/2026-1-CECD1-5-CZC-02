package com.salesmap.backend.calendar.dto;

public record GoogleCalendarEventResponse(
        String id,
        String htmlLink,
        String status,
        String summary
) {
}
