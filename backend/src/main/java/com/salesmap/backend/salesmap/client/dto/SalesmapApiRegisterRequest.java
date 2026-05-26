package com.salesmap.backend.salesmap.client.dto;

public record SalesmapApiRegisterRequest(
        Long analysisId,
        Long sourceId,
        String customerName,
        String contactName,
        String productName,
        Long amount,
        String scheduleText,
        String followUpAction,
        String summary
) {
}
