package com.salesmap.backend.ai.client;

import com.salesmap.backend.ai.dto.AiAnalysisRequest;
import com.salesmap.backend.ai.dto.AiAnalysisResponse;
import com.salesmap.backend.ai.dto.AiGroupAnalysisRequest;
import com.salesmap.backend.ai.dto.AiMinutesRequest;
import com.salesmap.backend.ai.dto.AiMinutesResponse;
import com.salesmap.backend.ai.dto.AiReplyDraftRequest;
import com.salesmap.backend.ai.dto.AiReplyDraftResponse;

public interface AiClient {

    AiAnalysisResponse analyze(AiAnalysisRequest request);

    AiAnalysisResponse analyzeGroup(AiGroupAnalysisRequest request);

    AiReplyDraftResponse generateReplyDraft(AiReplyDraftRequest request);

    /** 메모·메일 텍스트 → meeting_note 초안(DRAFT). AI 모듈 POST /minutes */
    AiMinutesResponse generateMinutes(AiMinutesRequest request);
}
