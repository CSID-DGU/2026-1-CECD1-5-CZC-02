package com.salesmap.backend.revision.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record RevisionCreateRequest(
        @NotBlank String screen,
        @NotBlank String targetType,
        @NotBlank String targetId,
        @NotBlank String fieldName,
        JsonNode proposedValue,
        JsonNode finalValue,
        @NotBlank String action,
        @NotBlank String proposedBy,
        String modelVersion
) {
}
