package com.salesmap.backend.gmail.dto;

public record GmailOAuthCallbackResponse(
        Long integrationId,
        Long userId,
        String provider,
        String externalAccountId
) {
}
