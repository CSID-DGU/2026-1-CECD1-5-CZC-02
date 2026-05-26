package com.salesmap.backend.ai.dto;

import com.salesmap.backend.source.entity.SourceType;

public record AiAnalysisRequest(
        Long sourceId,
        SourceType sourceType,
        String title,
        String content
) {
}
