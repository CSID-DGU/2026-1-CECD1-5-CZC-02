package com.salesmap.backend.calendar.client;

import com.salesmap.backend.calendar.dto.GoogleCalendarEventRequest;
import com.salesmap.backend.calendar.dto.GoogleCalendarEventResponse;

public interface GoogleCalendarClient {

    /** eventId가 이미 있으면 {@code GoogleCalendarEventConflictException}을 던진다. */
    GoogleCalendarEventResponse insertEvent(String accessToken, String eventId, GoogleCalendarEventRequest request);

    GoogleCalendarEventResponse getEvent(String accessToken, String eventId);

    GoogleCalendarEventResponse updateEvent(String accessToken, String eventId, GoogleCalendarEventRequest request);

    void deleteEvent(String accessToken, String eventId);
}
