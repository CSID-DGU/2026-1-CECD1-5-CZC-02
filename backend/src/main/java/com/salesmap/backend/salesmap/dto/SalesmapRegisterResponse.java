package com.salesmap.backend.salesmap.dto;

public record SalesmapRegisterResponse(
        Long salesmapRecordId,
        Long analysisId,
        String externalRecordId,
        String status
) {
}
