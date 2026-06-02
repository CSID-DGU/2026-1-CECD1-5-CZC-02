package com.salesmap.backend.gmail.dto;

public record GmailMessagePreview(
        String id,
        String threadId,
        String subject,
        String internalDate
) {
}
