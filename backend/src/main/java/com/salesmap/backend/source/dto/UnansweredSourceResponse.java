package com.salesmap.backend.source.dto;

import java.time.LocalDateTime;

/**
 * 고객이 보낸 영업 메일에 아직 답장하지 않은 대화.
 */
public record UnansweredSourceResponse(
        Long sourceId,
        Long sourceGroupId,
        Long analysisId,
        String title,
        String senderName,
        String senderEmail,
        String customerName,
        LocalDateTime receivedAt,
        int waitingBusinessDays
) {
}
