package com.salesmap.backend.calendar.exception;

/** 같은 ID의 Google 이벤트가 이미 있을 때(HTTP 409). */
public class GoogleCalendarEventConflictException extends GoogleCalendarClientException {

    public GoogleCalendarEventConflictException(String message) {
        super(message);
    }
}
