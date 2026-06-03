package com.salesmap.backend.calendar.exception;

public class GoogleCalendarClientException extends RuntimeException {

    private final Object errorResponse;

    public GoogleCalendarClientException(String message) {
        super(message);
        this.errorResponse = null;
    }

    public GoogleCalendarClientException(String message, Throwable cause) {
        super(message, cause);
        this.errorResponse = null;
    }

    public GoogleCalendarClientException(String message, Object errorResponse) {
        super(message);
        this.errorResponse = errorResponse;
    }

    public Object getErrorResponse() {
        return errorResponse;
    }
}
