package com.salesmap.backend.briefing.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record TaskScheduleRegistrationRequest(
        @NotBlank String taskId,
        @NotNull @FutureOrPresent LocalDateTime scheduleDateTime
) {
}
