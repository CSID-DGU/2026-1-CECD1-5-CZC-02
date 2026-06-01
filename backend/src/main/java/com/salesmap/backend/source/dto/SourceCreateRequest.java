package com.salesmap.backend.source.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record SourceCreateRequest(
        @Positive(message = "userId는 1 이상이어야 합니다.")
        Long userId,

        @Positive(message = "integrationId는 1 이상이어야 합니다.")
        Long integrationId,

        @NotBlank(message = "원본 데이터 타입은 필수입니다.")
        String sourceType,

        String externalSourceId,

        @NotBlank(message = "제목은 필수입니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        String content,

        LocalDateTime collectedAt
) {
}
