package com.salesmap.backend.calendar.client;

import com.salesmap.backend.calendar.dto.GoogleCalendarEventRequest;
import com.salesmap.backend.calendar.dto.GoogleCalendarEventResponse;

public interface GoogleCalendarClient {

    GoogleCalendarEventResponse insertEvent(String accessToken, GoogleCalendarEventRequest request);

    GoogleCalendarEventResponse updateEvent(String accessToken, String eventId, GoogleCalendarEventRequest request);

    void deleteEvent(String accessToken, String eventId);
}
