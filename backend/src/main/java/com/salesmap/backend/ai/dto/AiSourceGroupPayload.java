package com.salesmap.backend.ai.dto;

import com.salesmap.backend.source.entity.SourceType;

public record AiSourceGroupPayload(
        String groupId,
        SourceType sourceType,
        String title,
        boolean deduplicated
) {
}
