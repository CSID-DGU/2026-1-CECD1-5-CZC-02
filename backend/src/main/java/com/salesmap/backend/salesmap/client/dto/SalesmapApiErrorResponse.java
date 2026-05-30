package com.salesmap.backend.salesmap.client.dto;

import java.util.Map;

public record SalesmapApiErrorResponse(
        String errorCode,
        String message,
        Map<String, Object> details
) {
}
