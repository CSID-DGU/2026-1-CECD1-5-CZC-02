package com.salesmap.backend.analysis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AnalysisCreateRequest(
        @NotNull(message = "sourceId는 필수입니다.")
        @Positive(message = "sourceId는 1 이상이어야 합니다.")
        Long sourceId
) {
}
