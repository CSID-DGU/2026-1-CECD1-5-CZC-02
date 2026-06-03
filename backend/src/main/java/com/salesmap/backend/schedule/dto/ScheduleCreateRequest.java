package com.salesmap.backend.schedule.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record ScheduleCreateRequest(
        @Positive(message = "userId는 1 이상이어야 합니다.")
        Long userId,

        @Positive(message = "analysisId는 1 이상이어야 합니다.")
        Long analysisId,

        @NotBlank(message = "일정 제목은 필수입니다.")
        String title,

        @NotNull(message = "일정 날짜와 시간은 필수입니다.")
        @FutureOrPresent(message = "일정 날짜와 시간은 현재 이후여야 합니다.")
        LocalDateTime scheduleDateTime,

        @NotBlank(message = "메모는 필수입니다.")
        String memo
) {
}
