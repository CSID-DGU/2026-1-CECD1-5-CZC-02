package com.salesmap.backend.customer.service;

import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.entity.AnalysisStatus;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.customer.dto.CustomerPipelineResponse;
import com.salesmap.backend.customer.dto.CustomerSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 고객사별 딜 파이프라인을 계산한다.
 * <p>
 * 별도의 단계 컬럼을 저장하지 않고, 이미 쌓인 AI 분석 결과를 고객사(발신 이메일) 기준으로 묶어
 * "가장 최근 메일의 처리 유형(actionType)"으로 현재 딜 단계를 판정한다. 규칙 기반이라 근거를 그대로 설명할 수 있다.
 */
@Service
public class PipelineService {

    private static final int STALE_DAYS = 3;

    private final CustomerTimelineService customerTimelineService;
    private final AnalysisRepository analysisRepository;

    public PipelineService(
            CustomerTimelineService customerTimelineService,
            AnalysisRepository analysisRepository
    ) {
        this.customerTimelineService = customerTimelineService;
        this.analysisRepository = analysisRepository;
    }

    @Transactional
    public List<CustomerPipelineResponse> getPipeline(Long authenticatedUserId) {
        // getCustomers()가 기존 분석을 타임라인 연락처로 backfill 하므로 재사용한다.
        List<CustomerSummaryResponse> customers = customerTimelineService.getCustomers(authenticatedUserId);
        Map<String, List<Analysis>> analysesByEmail = groupBusinessAnalysesByEmail(authenticatedUserId);

        List<CustomerPipelineResponse> pipeline = new ArrayList<>();
        for (CustomerSummaryResponse customer : customers) {
            String key = normalizeEmail(customer.email());
            List<Analysis> analyses = key == null ? List.of() : analysesByEmail.getOrDefault(key, List.of());
            if (analyses.isEmpty()) {
                continue;
            }
            pipeline.add(toPipelineCard(customer, analyses));
        }

        pipeline.sort(Comparator
                .comparingInt(CustomerPipelineResponse::stageOrder)
                .thenComparing(CustomerPipelineResponse::lastActivityAt, Comparator.nullsLast(Comparator.reverseOrder())));

        return pipeline;
    }

    private Map<String, List<Analysis>> groupBusinessAnalysesByEmail(Long userId) {
        // findByUserId는 id 내림차순(최신 우선)으로 반환한다. 그 순서를 그대로 유지한다.
        Map<String, List<Analysis>> grouped = new LinkedHashMap<>();
        for (Analysis analysis : analysisRepository.findByUserId(userId)) {
            if (analysis.getStatus() == AnalysisStatus.DELETED) {
                continue;
            }
            if ("NON_BUSINESS".equals(analysis.getBusinessType())) {
                continue;
            }
            String email = normalizeEmail(analysis.getSource().getSenderEmail());
            if (email == null) {
                continue;
            }
            grouped.computeIfAbsent(email, ignored -> new ArrayList<>()).add(analysis);
        }
        return grouped;
    }

    private CustomerPipelineResponse toPipelineCard(CustomerSummaryResponse customer, List<Analysis> analyses) {
        Analysis latest = analyses.get(0);
        Stage stage = classifyStage(latest.getActionType());

        String productName = firstNonBlank(analyses, Analysis::getProductName);
        Long amount = firstNonNullAmount(analyses);
        LocalDateTime lastActivityAt = firstNotNull(latest.getAnalyzedAt(), latest.getCreatedAt(), customer.lastSeenAt());
        boolean stale = isStale(stage, lastActivityAt);
        String nextAction = resolveNextAction(stage, latest.getFollowUpAction(), stale);

        return new CustomerPipelineResponse(
                customer.customerContactId(),
                customer.customerName(),
                customer.contactName(),
                customer.email(),
                productName,
                amount,
                stage.name(),
                stage.label,
                stage.order,
                latest.getActionType(),
                nextAction,
                stale,
                analyses.size(),
                lastActivityAt
        );
    }

    private Stage classifyStage(String actionType) {
        if (actionType == null) {
            return Stage.LEAD;
        }
        return switch (actionType) {
            case "CANCEL" -> Stage.CANCELED;
            case "CONFIRM" -> Stage.CONFIRMED;
            case "UPDATE" -> Stage.ADJUSTING;
            case "CREATE" -> Stage.MEETING;
            default -> Stage.LEAD;
        };
    }

    private String resolveNextAction(Stage stage, String aiFollowUp, boolean stale) {
        if (aiFollowUp != null && !aiFollowUp.isBlank() && !"-".equals(aiFollowUp.trim())) {
            return aiFollowUp.trim();
        }
        if (stale && (stage == Stage.LEAD || stage == Stage.MEETING || stage == Stage.ADJUSTING)) {
            return "응답이 " + STALE_DAYS + "일 이상 없어요. 진행 상황을 확인하는 팔로업 메일을 보내세요.";
        }
        return stage.defaultNextAction;
    }

    private boolean isStale(Stage stage, LocalDateTime lastActivityAt) {
        if (lastActivityAt == null) {
            return false;
        }
        if (stage == Stage.CONFIRMED || stage == Stage.CANCELED) {
            return false;
        }
        return lastActivityAt.isBefore(LocalDateTime.now().minusDays(STALE_DAYS));
    }

    private String firstNonBlank(List<Analysis> analyses, java.util.function.Function<Analysis, String> getter) {
        for (Analysis analysis : analyses) {
            String value = getter.apply(analysis);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Long firstNonNullAmount(List<Analysis> analyses) {
        for (Analysis analysis : analyses) {
            if (analysis.getAmount() != null && analysis.getAmount() > 0) {
                return analysis.getAmount();
            }
        }
        return null;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    @SafeVarargs
    private <T> T firstNotNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private enum Stage {
        LEAD(0, "리드", "보낸 자료 검토 여부를 확인하는 팔로업 메일을 보내세요."),
        MEETING(1, "미팅 예정", "미팅 일정을 캘린더에 확정하고 리마인드를 보내세요."),
        ADJUSTING(2, "일정 조율", "변경된 시간으로 일정을 재확정하는 회신을 보내세요."),
        CONFIRMED(3, "확정", "미팅 준비 자료와 아젠다를 발송하세요."),
        CANCELED(4, "보류·취소", "재접촉 시점을 기록하고 2주 후 팔로업하세요.");

        private final int order;
        private final String label;
        private final String defaultNextAction;

        Stage(int order, String label, String defaultNextAction) {
            this.order = order;
            this.label = label;
            this.defaultNextAction = defaultNextAction;
        }
    }
}
