package com.salesmap.backend.analysisjob.dto;

/** result: STARTED · ALREADY_RUNNING(자동 모드 등 다른 분석이 진행 중) · NOTHING_TO_ANALYZE */
public record AnalysisJobStartResponse(String result, AnalysisJobStatus job) {
}
