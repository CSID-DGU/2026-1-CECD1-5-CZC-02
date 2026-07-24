package com.salesmap.backend.customer.dto;

import java.time.LocalDateTime;

/**
 * 고객사별 딜 파이프라인 카드 한 장에 필요한 집계 결과.
 * AI 분석(actionType/product/amount/followUpAction)을 고객사 기준으로 묶어
 * 현재 딜 단계(stage)와 다음 할 일(nextAction)을 계산해 담는다.
 */
public record CustomerPipelineResponse(
        Long customerContactId,
        String customerName,
        String contactName,
        String email,
        String productName,
        Long amount,
        String stage,
        String stageLabel,
        int stageOrder,
        String lastActionType,
        String nextAction,
        boolean stale,
        long dealCount,
        LocalDateTime lastActivityAt
) {
}
