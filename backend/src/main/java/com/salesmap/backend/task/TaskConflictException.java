package com.salesmap.backend.task;

public class TaskConflictException extends RuntimeException {
    public TaskConflictException(String message) { super(message); }
}
