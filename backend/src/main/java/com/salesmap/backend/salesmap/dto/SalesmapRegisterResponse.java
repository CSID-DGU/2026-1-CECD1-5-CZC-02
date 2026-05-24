package com.salesmap.backend.salesmap.dto;

import com.salesmap.backend.salesmap.entity.SalesmapRecord;
import com.salesmap.backend.salesmap.entity.SalesmapRecordStatus;

import java.time.LocalDateTime;

public record SalesmapRegisterResponse(
        Long salesmapRecordId,
        Long analysisId,
        String externalRecordId,
        String requestPayload,
        String responsePayload,
        SalesmapRecordStatus status,
        LocalDateTime registeredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SalesmapRegisterResponse from(SalesmapRecord record) {
        return new SalesmapRegisterResponse(
                record.getId(),
                record.getAnalysis().getId(),
                record.getExternalRecordId(),
                record.getRequestPayload(),
                record.getResponsePayload(),
                record.getStatus(),
                record.getRegisteredAt(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }
}
