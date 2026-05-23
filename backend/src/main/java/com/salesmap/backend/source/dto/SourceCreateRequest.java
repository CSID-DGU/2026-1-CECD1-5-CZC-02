package com.salesmap.backend.source.dto;

public record SourceCreateRequest(
        String sourceType,
        String title,
        String content
) {
}
