package com.salesmap.backend.schedule.dto;

import com.salesmap.backend.schedule.entity.ScheduleOutcome;
import jakarta.validation.constraints.NotNull;

public record ScheduleOutcomeRequest(
        @NotNull ScheduleOutcome outcome
) {
}
