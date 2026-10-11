package com.salesmap.backend.source.dto;

import jakarta.validation.constraints.Email;
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

        LocalDateTime collectedAt,

        // 직접 입력한 메일·통화 기록의 상대방 정보(선택). 있으면 받은 메일(RECEIVED)로 저장된다.
        String senderName,

        @Email(message = "senderEmail 형식이 올바르지 않습니다.")
        String senderEmail,

        LocalDateTime sentAt
) {
}
