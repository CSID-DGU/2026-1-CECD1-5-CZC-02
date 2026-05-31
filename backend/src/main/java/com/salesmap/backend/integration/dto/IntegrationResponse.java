package com.salesmap.backend.integration.dto;

import com.salesmap.backend.integration.entity.Integration;
import com.salesmap.backend.integration.entity.IntegrationProvider;
import com.salesmap.backend.integration.entity.IntegrationStatus;

import java.time.LocalDateTime;

public record IntegrationResponse(
        Long integrationId,
        Long userId,
        IntegrationProvider provider,
        String externalAccountId,
        LocalDateTime tokenExpiresAt,
        LocalDateTime lastSyncedAt,
        IntegrationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static IntegrationResponse from(Integration integration) {
        return new IntegrationResponse(
                integration.getId(),
                integration.getUser().getId(),
                integration.getProvider(),
                integration.getExternalAccountId(),
                integration.getTokenExpiresAt(),
                integration.getLastSyncedAt(),
                integration.getStatus(),
                integration.getCreatedAt(),
                integration.getUpdatedAt()
        );
    }
}
