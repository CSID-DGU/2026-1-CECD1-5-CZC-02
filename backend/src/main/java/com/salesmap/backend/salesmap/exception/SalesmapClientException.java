package com.salesmap.backend.salesmap.exception;

import com.salesmap.backend.salesmap.client.dto.SalesmapApiErrorResponse;

public class SalesmapClientException extends RuntimeException {

    private final SalesmapApiErrorResponse errorResponse;

    public SalesmapClientException(String message) {
        super(message);
        this.errorResponse = null;
    }

    public SalesmapClientException(String message, Throwable cause) {
        super(message, cause);
        this.errorResponse = null;
    }

    public SalesmapClientException(String message, SalesmapApiErrorResponse errorResponse) {
        super(message);
        this.errorResponse = errorResponse;
    }

    public SalesmapApiErrorResponse getErrorResponse() {
        return errorResponse;
    }
}
