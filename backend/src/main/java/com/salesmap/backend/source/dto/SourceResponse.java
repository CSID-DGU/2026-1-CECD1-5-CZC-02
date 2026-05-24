package com.salesmap.backend.source.dto;

import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;

import java.time.LocalDateTime;

public record SourceResponse(
        Long sourceId,
        Long userId,
        Long integrationId,
        SourceType sourceType,
        String externalSourceId,
        String title,
        String content,
        SourceStatus status,
        LocalDateTime collectedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SourceResponse from(Source source) {
        Long integrationId = source.getIntegration() == null ? null : source.getIntegration().getId();

        return new SourceResponse(
                source.getId(),
                source.getUser().getId(),
                integrationId,
                source.getSourceType(),
                source.getExternalSourceId(),
                source.getTitle(),
                source.getContent(),
                source.getStatus(),
                source.getCollectedAt(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
    }
}
