package com.salesmap.backend.schedule.exception;

import com.salesmap.backend.schedule.dto.ScheduleConflictResponse;

public class ScheduleConflictException extends RuntimeException {

    private final ScheduleConflictResponse conflictResponse;

    public ScheduleConflictException(String message, ScheduleConflictResponse conflictResponse) {
        super(message);
        this.conflictResponse = conflictResponse;
    }

    public ScheduleConflictResponse getConflictResponse() {
        return conflictResponse;
    }
}
