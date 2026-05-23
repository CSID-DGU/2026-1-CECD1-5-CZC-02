package com.salesmap.backend.analysis.service;

import com.salesmap.backend.analysis.dto.AnalysisCreateRequest;
import com.salesmap.backend.analysis.dto.AnalysisResponse;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class AnalysisService {

    private final AtomicLong analysisIdGenerator = new AtomicLong(1);

    public AnalysisResponse createAnalysis(AnalysisCreateRequest request) {
        return buildMockAnalysis(analysisIdGenerator.getAndIncrement(), request.sourceId());
    }

    public AnalysisResponse getAnalysis(Long analysisId) {
        return buildMockAnalysis(analysisId, 1L);
    }

    private AnalysisResponse buildMockAnalysis(Long analysisId, Long sourceId) {
        return new AnalysisResponse(
                analysisId,
                sourceId,
                "ABC Corp",
                "홍길동",
                "Sales Solution",
                1_000_000L,
                "다음 주 수요일 미팅",
                "견적서 발송",
                "고객이 제품 도입을 검토 중이며 다음 미팅 예정",
                "ANALYZED"
        );
    }
}
