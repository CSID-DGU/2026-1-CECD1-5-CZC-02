package com.salesmap.backend.ai.client;

import com.salesmap.backend.ai.config.AiModuleProperties;
import com.salesmap.backend.ai.dto.AiAnalysisRequest;
import com.salesmap.backend.ai.dto.AiAnalysisResponse;
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
        return new AiAnalysisResponse(
                "ABC Corp",
                "홍길동",
                "Sales Solution",
                1_000_000L,
                "CREATE",
                null,
                null,
                "Mock 분석 결과입니다.",
                "다음 주 수요일 미팅",
                null,
                "견적서 발송",
                "예산 검토 및 도입 일정 확인 필요",
                "고객이 제품 도입을 검토 중이며 다음 미팅 예정",
                0.95
        );
    }

    public String getBaseUrl() {
        return aiModuleProperties.getBaseUrl();
    }
}
