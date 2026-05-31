package com.salesmap.backend.integration.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record IntegrationCreateRequest(
        @NotBlank(message = "연동 서비스는 필수입니다.")
        String provider,

        @NotBlank(message = "외부 계정 ID는 필수입니다.")
        String externalAccountId,

        @NotBlank(message = "access token은 필수입니다.")
        String accessToken,

        @NotBlank(message = "refresh token은 필수입니다.")
        String refreshToken,

        @NotNull(message = "토큰 만료 시각은 필수입니다.")
        @Future(message = "토큰 만료 시각은 현재 이후여야 합니다.")
        LocalDateTime tokenExpiresAt
) {
}
