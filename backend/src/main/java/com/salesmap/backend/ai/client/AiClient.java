package com.salesmap.backend.ai.client;

import com.salesmap.backend.ai.dto.AiAnalysisRequest;
import com.salesmap.backend.ai.dto.AiAnalysisResponse;
import com.salesmap.backend.ai.dto.AiGroupAnalysisRequest;
import com.salesmap.backend.ai.dto.AiReplyDraftRequest;
import com.salesmap.backend.ai.dto.AiReplyDraftResponse;

public interface AiClient {

    AiAnalysisResponse analyze(AiAnalysisRequest request);

    AiAnalysisResponse analyzeGroup(AiGroupAnalysisRequest request);

    AiReplyDraftResponse generateReplyDraft(AiReplyDraftRequest request);
}
