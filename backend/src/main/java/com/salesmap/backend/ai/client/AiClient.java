package com.salesmap.backend.ai.client;

import com.salesmap.backend.ai.dto.AiAnalysisRequest;
import com.salesmap.backend.ai.dto.AiAnalysisResponse;

public interface AiClient {

    AiAnalysisResponse analyze(AiAnalysisRequest request);
}
