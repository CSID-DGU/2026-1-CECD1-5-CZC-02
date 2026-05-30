package com.salesmap.backend.ai.exception;

import com.salesmap.backend.ai.dto.AiErrorResponse;

public class AiClientException extends RuntimeException {

    private final AiErrorResponse errorResponse;

    public AiClientException(String message) {
        super(message);
        this.errorResponse = null;
    }

    public AiClientException(String message, Throwable cause) {
        super(message, cause);
        this.errorResponse = null;
    }

    public AiClientException(String message, AiErrorResponse errorResponse) {
        super(message);
        this.errorResponse = errorResponse;
    }

    public AiErrorResponse getErrorResponse() {
        return errorResponse;
    }
}
