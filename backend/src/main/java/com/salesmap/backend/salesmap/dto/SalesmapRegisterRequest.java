package com.salesmap.backend.salesmap.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SalesmapRegisterRequest(
        @NotNull(message = "analysisId는 필수입니다.")
        @Positive(message = "analysisId는 1 이상이어야 합니다.")
        Long analysisId
) {
}
