package com.salesmap.backend.analysisjob.dto;

import java.time.LocalDateTime;

public record AnalysisJobStatus(
        String trigger,
        boolean running,
        int total,
        int done,
        int analyzed,
        int skipped,
        int failed,
        String currentTitle,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
