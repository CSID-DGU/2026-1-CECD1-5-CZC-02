package com.salesmap.backend.ai.dto;

import com.salesmap.backend.source.entity.SourceType;

import java.time.LocalDateTime;

public record AiAnalysisRequest(
        Long sourceId,
        SourceType sourceType,
        String externalSourceId,
        String title,
        String content,
        LocalDateTime collectedAt
) {
}
