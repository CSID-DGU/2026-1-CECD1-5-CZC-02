package com.salesmap.backend.salesmap.client.dto;

import java.time.LocalDateTime;

public record SalesmapApiRegisterResponse(
        String externalRecordId,
        String status,
        String requestPayload,
        String responsePayload,
        LocalDateTime registeredAt
) {

    public SalesmapApiRegisterResponse withRequestPayloadIfBlank(String fallbackRequestPayload) {
        if (requestPayload != null && !requestPayload.isBlank()) {
            return this;
        }

        return new SalesmapApiRegisterResponse(
                externalRecordId,
                status,
                fallbackRequestPayload,
                responsePayload,
                registeredAt
        );
    }

    public SalesmapApiRegisterResponse withResponsePayloadIfBlank(String fallbackResponsePayload) {
        if (responsePayload != null && !responsePayload.isBlank()) {
            return this;
        }

        return new SalesmapApiRegisterResponse(
                externalRecordId,
                status,
                requestPayload,
                fallbackResponsePayload,
                registeredAt
        );
    }
}
