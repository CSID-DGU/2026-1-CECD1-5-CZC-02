package com.salesmap.backend.analysis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AnalysisGroupCreateRequest(
        @NotNull(message = "sourceGroupId is required.")
        @Positive(message = "sourceGroupId must be greater than 0.")
        Long sourceGroupId
) {
}
