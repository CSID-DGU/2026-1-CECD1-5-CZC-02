package com.salesmap.backend.ai.dto;

import java.util.List;

public record AiGroupAnalysisRequest(
        AiAnalysisRequest.RequesterInfo requester,
        AiAnalysisRequest.SourceGroupInfo sourceGroup,
        List<AiAnalysisRequest.MessageItem> messages,
        List<AiAnalysisRequest.ExistingScheduleInfo> existingSchedules
) {
}
