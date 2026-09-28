package com.salesmap.backend.demo.dto;

import java.time.LocalDate;

public record DemoStatusResponse(
        boolean enabled,
        String demoAccountEmail,
        LocalDate anchorDate,
        int deliveredStage,
        int lastStage,
        int deliveredMailCount,
        int pendingMailCount
) {
}
