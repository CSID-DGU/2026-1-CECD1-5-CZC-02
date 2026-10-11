package com.salesmap.backend.analysis.dto;

public record ReplySendResponse(
        String sentTo,
        String subject,
        String messageId
) {
}
