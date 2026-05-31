package com.salesmap.backend.ai.dto;

import java.util.List;

public record AiGroupAnalysisRequest(
        AiSourceGroupPayload sourceGroup,
        List<AiSourceMessagePayload> messages
) {
}
