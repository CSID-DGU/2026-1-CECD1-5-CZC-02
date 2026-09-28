package com.salesmap.backend.analysisjob.dto;

import java.util.List;

public record ActivityFeedResponse(AnalysisJobStatus job, List<ActivityEvent> events, long latestEventId) {
}
