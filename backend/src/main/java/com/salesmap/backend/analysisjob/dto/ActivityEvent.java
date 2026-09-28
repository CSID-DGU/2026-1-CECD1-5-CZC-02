package com.salesmap.backend.analysisjob.dto;

import java.time.LocalDateTime;

/** type: COLLECTED(새 메일 수집) · ANALYSIS_STARTED · ANALYSIS_FINISHED */
public record ActivityEvent(long id, String type, String trigger, String message, LocalDateTime occurredAt) {
}
