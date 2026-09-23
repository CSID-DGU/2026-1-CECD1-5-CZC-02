package com.salesmap.backend.source.service;

import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.entity.AnalysisStatus;
import com.salesmap.backend.analysis.entity.ReplyDraft;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.analysis.repository.ReplyDraftRepository;
import com.salesmap.backend.global.util.BusinessDayCalculator;
import com.salesmap.backend.source.dto.UnansweredSourceResponse;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceDirection;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.repository.SourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 답장하지 않은 영업 메일을 찾는다.
 * <p>
 * 대화(스레드)의 마지막 메일이 고객이 보낸 메일이고, 그 대화가 영업 메일로 분석됐으며,
 * 우리 서비스에서 그 이후에 답장을 보낸 기록도 없으면 "미답장"으로 본다.
 */
@Service
public class UnansweredMailService {

    private final SourceRepository sourceRepository;
    private final AnalysisRepository analysisRepository;
    private final ReplyDraftRepository replyDraftRepository;

    public UnansweredMailService(
            SourceRepository sourceRepository,
            AnalysisRepository analysisRepository,
            ReplyDraftRepository replyDraftRepository
    ) {
        this.sourceRepository = sourceRepository;
        this.analysisRepository = analysisRepository;
        this.replyDraftRepository = replyDraftRepository;
    }

    @Transactional(readOnly = true)
    public List<UnansweredSourceResponse> findUnanswered(Long userId) {
        Map<Long, List<Analysis>> analysesBySourceId = new LinkedHashMap<>();
        for (Analysis analysis : analysisRepository.findByUserId(userId)) {
            analysesBySourceId.computeIfAbsent(analysis.getSource().getId(), ignored -> new ArrayList<>()).add(analysis);
        }

        List<UnansweredSourceResponse> result = new ArrayList<>();
        for (List<Source> conversation : groupConversations(sourceRepository.findByUserId(userId))) {
            Source last = conversation.stream().max(Comparator.comparing(this::receivedAt).thenComparing(Source::getId)).orElseThrow();
            if (last.getDirection() != SourceDirection.RECEIVED) {
                continue;
            }

            Optional<Analysis> salesAnalysis = conversation.stream()
                    .flatMap(source -> analysesBySourceId.getOrDefault(source.getId(), List.of()).stream())
                    .filter(analysis -> "SALES_ACTIVITY".equals(analysis.getBusinessType()))
                    .filter(analysis -> analysis.getStatus() != AnalysisStatus.REJECTED
                            && analysis.getStatus() != AnalysisStatus.DELETED)
                    .max(Comparator.comparing(Analysis::getId));
            if (salesAnalysis.isEmpty() || repliedAfter(salesAnalysis.get(), receivedAt(last))) {
                continue;
            }

            LocalDateTime receivedAt = receivedAt(last);
            result.add(new UnansweredSourceResponse(
                    last.getId(),
                    last.getSourceGroup() == null ? null : last.getSourceGroup().getId(),
                    salesAnalysis.get().getId(),
                    last.getTitle(),
                    last.getSenderName(),
                    last.getSenderEmail(),
                    salesAnalysis.get().getCustomerName(),
                    receivedAt,
                    BusinessDayCalculator.businessDaysBetween(receivedAt.toLocalDate(), LocalDate.now())
            ));
        }

        result.sort(Comparator.comparingInt(UnansweredSourceResponse::waitingBusinessDays).reversed()
                .thenComparing(UnansweredSourceResponse::receivedAt));
        return result;
    }

    private List<List<Source>> groupConversations(List<Source> sources) {
        Map<String, List<Source>> conversations = new LinkedHashMap<>();
        for (Source source : sources) {
            if (source.getStatus() == SourceStatus.DELETED) {
                continue;
            }
            String key = source.getSourceGroup() == null
                    ? "source-" + source.getId()
                    : "group-" + source.getSourceGroup().getId();
            conversations.computeIfAbsent(key, ignored -> new ArrayList<>()).add(source);
        }
        return new ArrayList<>(conversations.values());
    }

    // Gmail 재수집 전에도 우리 서비스에서 보낸 답장은 즉시 반영한다.
    private boolean repliedAfter(Analysis analysis, LocalDateTime receivedAt) {
        return replyDraftRepository.findByAnalysisIdOrderByIdAsc(analysis.getId()).stream()
                .map(ReplyDraft::getSentAt)
                .filter(Objects::nonNull)
                .anyMatch(sentAt -> !sentAt.isBefore(receivedAt));
    }

    private LocalDateTime receivedAt(Source source) {
        if (source.getSentAt() != null) {
            return source.getSentAt();
        }
        if (source.getCollectedAt() != null) {
            return source.getCollectedAt();
        }
        return source.getCreatedAt();
    }
}
