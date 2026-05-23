package com.salesmap.backend.source.dto;

public record SourceResponse(
        Long sourceId,
        String sourceType,
        String title,
        String content,
        String status
) {
}
