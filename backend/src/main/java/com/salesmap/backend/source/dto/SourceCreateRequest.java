package com.salesmap.backend.source.dto;

import jakarta.validation.constraints.NotBlank;

public record SourceCreateRequest(
        @NotBlank(message = "원본 데이터 타입은 필수입니다.")
        String sourceType,

        @NotBlank(message = "제목은 필수입니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        String content
) {
}
