package com.salesmap.backend.ai.client;

import com.salesmap.backend.ai.config.AiModuleProperties;
import com.salesmap.backend.ai.dto.AiAnalysisRequest;
import com.salesmap.backend.ai.dto.AiAnalysisResponse;
import com.salesmap.backend.ai.dto.AiGroupAnalysisRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ai.module.mode", havingValue = "mock", matchIfMissing = true)
public class MockAiClient implements AiClient {

    private final AiModuleProperties aiModuleProperties;

    public MockAiClient(AiModuleProperties aiModuleProperties) {
        this.aiModuleProperties = aiModuleProperties;
    }

    @Override
    public AiAnalysisResponse analyze(AiAnalysisRequest request) {
        return mockResponse();
    }

    @Override
    public AiAnalysisResponse analyzeGroup(AiGroupAnalysisRequest request) {
        return mockResponse();
    }

    private AiAnalysisResponse mockResponse() {
        return new AiAnalysisResponse(
                "ABC Corp",
                "홍길동",
                "Sales Solution",
                1_000_000L,
                "ABC 고객사 박지훈, 영업팀 이민재",
                "CREATE",
                null,
                null,
                "Mock 분석 결과입니다.",
                "SALES_ACTIVITY",
                0.95,
                "고객사와 제품 도입 관련 일정 요청이 포함되어 있습니다.",
                "다음 주 수요일 미팅",
                null,
                "견적서 발송",
                "예산 검토 및 도입 일정 확인 필요",
                "고객이 제품 도입을 검토 중이며 후속 미팅이 필요합니다.",
                0.95
        );
    }

    public String getBaseUrl() {
        return aiModuleProperties.getBaseUrl();
    }
}
