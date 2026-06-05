package com.salesmap.backend.ai.dto;

public record AiReplyDraftResponse(
        String subject,
        String body,
        String generatedBy
) {
}
