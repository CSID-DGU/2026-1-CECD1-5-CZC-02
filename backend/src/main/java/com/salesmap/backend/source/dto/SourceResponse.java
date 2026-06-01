package com.salesmap.backend.source.dto;

import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceDirection;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;

import java.time.LocalDateTime;

public record SourceResponse(
        Long sourceId,
        Long userId,
        Long integrationId,
        Long sourceGroupId,
        String externalGroupId,
        SourceType sourceType,
        String externalSourceId,
        String title,
        String content,
        SourceStatus status,
        LocalDateTime collectedAt,
        SourceDirection direction,
        String senderName,
        String senderEmail,
        String receiverNames,
        String receiverEmails,
        LocalDateTime sentAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SourceResponse from(Source source) {
        Long integrationId = source.getIntegration() == null ? null : source.getIntegration().getId();
        Long sourceGroupId = source.getSourceGroup() == null ? null : source.getSourceGroup().getId();
        String externalGroupId = source.getSourceGroup() == null ? null : source.getSourceGroup().getExternalGroupId();

        return new SourceResponse(
                source.getId(),
                source.getUser().getId(),
                integrationId,
                sourceGroupId,
                externalGroupId,
                source.getSourceType(),
                source.getExternalSourceId(),
                source.getTitle(),
                source.getContent(),
                source.getStatus(),
                source.getCollectedAt(),
                source.getDirection(),
                source.getSenderName(),
                source.getSenderEmail(),
                source.getReceiverNames(),
                source.getReceiverEmails(),
                source.getSentAt(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
    }
}
