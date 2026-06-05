package com.salesmap.backend.ai.dto;

public record AiReplyDraftRequest(
        String emailTitle,
        String emailContent,
        String senderEmail,
        String customerName,
        String contactName,
        String productName,
        String attendees,
        String actionType,
        String scheduleInfo,
        String summary,
        String nextAction
) {
}
