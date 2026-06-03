package com.salesmap.backend.ai.dto;

import java.util.Map;

public record AiErrorResponse(
        String errorCode,
        String message,
        Map<String, Object> details
) {
}
