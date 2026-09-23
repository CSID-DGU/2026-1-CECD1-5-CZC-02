package com.salesmap.backend.autopipeline.service;

import com.salesmap.backend.analysis.dto.AnalysisCreateRequest;
import com.salesmap.backend.analysis.dto.AnalysisGroupCreateRequest;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.analysis.service.AnalysisService;
import com.salesmap.backend.gmail.service.GmailIntegrationService;
import com.salesmap.backend.integration.entity.Integration;
import com.salesmap.backend.integration.entity.IntegrationProvider;
import com.salesmap.backend.integration.entity.IntegrationStatus;
import com.salesmap.backend.integration.repository.IntegrationRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceDirection;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * "자동 수집·분석" 모드.
 * <p>
 * 사용자가 켜두면 서버가 주기적으로 (1) Gmail 새 메일을 증분 수집하고 (2) 아직 분석되지 않은 메일을
 * AI로 자동 분석해 "승인 대기" 상태로 쌓아둔다. 담당자는 웹에서 결과를 확인하고 승인(등록)만 하면 된다.
 * <p>
 * on/off 상태는 users.auto_pipeline_enabled에 저장하고, 서버 기동 시 다시 읽어 메모리에 올린다.
 * 로컬 LLM은 건당 수십 초가 걸리므로 한 틱에 처리하는 건수를 제한해 CPU를 보호한다.
 */
@Service
public class AutoPipelineService {

    private static final Logger log = LoggerFactory.getLogger(AutoPipelineService.class);
    private static final int MAX_ANALYSES_PER_TICK = 5;
    // 자동 분석 상한: 오래된 대량 백로그를 무한정 갈지 않도록 최근 N일 + 최신 N건만 대상으로 삼는다.
    // (범위를 넘는 오래된 메일은 자동 분석하지 않는다. 필요하면 수동 "수집 메일 AI 분석"으로 처리.)
    private static final int AUTO_ANALYZE_RECENT_DAYS = 14;
    private static final int MAX_PENDING_CONSIDERED = 25;

    private final Set<Long> enabledUserIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final GmailIntegrationService gmailIntegrationService;
    private final IntegrationRepository integrationRepository;
    private final SourceRepository sourceRepository;
    private final AnalysisRepository analysisRepository;
    private final AnalysisService analysisService;
    private final UserRepository userRepository;

    public AutoPipelineService(
            GmailIntegrationService gmailIntegrationService,
            IntegrationRepository integrationRepository,
            SourceRepository sourceRepository,
            AnalysisRepository analysisRepository,
            AnalysisService analysisService,
            UserRepository userRepository
    ) {
        this.gmailIntegrationService = gmailIntegrationService;
        this.integrationRepository = integrationRepository;
        this.sourceRepository = sourceRepository;
        this.analysisRepository = analysisRepository;
        this.analysisService = analysisService;
        this.userRepository = userRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreEnabledUsers() {
        userRepository.findByAutoPipelineEnabledTrue()
                .forEach(user -> enabledUserIds.add(user.getId()));
        log.info("[AUTO_PIPELINE] restored enabled users={}", enabledUserIds);
    }

    public boolean isEnabled(Long userId) {
        return enabledUserIds.contains(userId);
    }

    @Transactional
    public void enable(Long userId) {
        saveEnabled(userId, true);
        enabledUserIds.add(userId);
        log.info("[AUTO_PIPELINE] enabled for userId={}", userId);
    }

    @Transactional
    public void disable(Long userId) {
        saveEnabled(userId, false);
        enabledUserIds.remove(userId);
        log.info("[AUTO_PIPELINE] disabled for userId={}", userId);
    }

    private void saveEnabled(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
        user.changeAutoPipelineEnabled(enabled);
    }

    @Scheduled(fixedDelayString = "${auto.pipeline.interval-ms:45000}", initialDelay = 15000)
    public void runAutoPipeline() {
        if (enabledUserIds.isEmpty()) {
            return;
        }
        // 이전 실행이 끝나기 전엔 겹쳐 돌지 않는다(단일 스레드 스케줄러이므로 안전장치).
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            for (Long userId : Set.copyOf(enabledUserIds)) {
                try {
                    processUser(userId);
                } catch (RuntimeException exception) {
                    log.warn("[AUTO_PIPELINE] failed for userId={}, reason={}", userId, exception.getMessage());
                }
            }
        } finally {
            running.set(false);
        }
    }

    private void processUser(Long userId) {
        Integration gmail = integrationRepository.findByUserIdAndProvider(userId, IntegrationProvider.GMAIL)
                .orElse(null);
        if (gmail == null || gmail.getStatus() != IntegrationStatus.CONNECTED) {
            return;
        }

        // 1) 새 메일 증분 수집 (마지막 동기화 이후 것만)
        try {
            gmailIntegrationService.collectMessages(userId, "auto", null, false, false, null);
        } catch (RuntimeException exception) {
            log.warn("[AUTO_PIPELINE] auto collect failed. userId={}, reason={}", userId, exception.getMessage());
        }

        // 2) 미분석 메일 자동 분석 (오래된 순, 한 틱 최대 N건)
        analyzePendingSources(userId);
    }

    private void analyzePendingSources(Long userId) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(AUTO_ANALYZE_RECENT_DAYS);

        // 최근 N일 메일 중 "최신 MAX_PENDING_CONSIDERED건"만 자동 분석 후보로 삼고,
        // 그 안에서는 오래된 순으로 처리해 발신자별 기억이 시간순으로 누적되게 한다.
        List<Source> sources = sourceRepository.findByUserId(userId).stream()
                // 분석(정보 추출) 대상은 "받은 메일"만. 내가 보낸 답장은 대화 맥락일 뿐 분석하지 않는다.
                .filter(source -> source.getDirection() != SourceDirection.SENT)
                .filter(source -> orderingTime(source).isAfter(cutoff))
                .sorted(Comparator.comparing(this::orderingTime).reversed())
                .limit(MAX_PENDING_CONSIDERED)
                .sorted(Comparator.comparing(this::orderingTime))
                .toList();

        Set<Long> handledGroups = new HashSet<>();
        int analyzed = 0;

        for (Source source : sources) {
            if (analyzed >= MAX_ANALYSES_PER_TICK) {
                break;
            }
            if (isAlreadyAnalyzed(source)) {
                continue;
            }

            Long groupId = source.getSourceGroup() == null ? null : source.getSourceGroup().getId();
            try {
                if (groupId != null) {
                    if (!handledGroups.add(groupId)) {
                        continue;
                    }
                    analysisService.createGroupAnalysis(new AnalysisGroupCreateRequest(groupId, "ollama"), userId);
                } else {
                    analysisService.createAnalysis(new AnalysisCreateRequest(source.getId(), "ollama"), userId);
                }
                analyzed++;
                log.info("[AUTO_PIPELINE] auto-analyzed. userId={}, sourceId={}, groupId={}", userId, source.getId(), groupId);
            } catch (RuntimeException exception) {
                log.warn("[AUTO_PIPELINE] auto analyze failed. userId={}, sourceId={}, reason={}",
                        userId, source.getId(), exception.getMessage());
            }
        }
    }

    private boolean isAlreadyAnalyzed(Source source) {
        if (source.getSourceGroup() != null) {
            List<Long> groupSourceIds = sourceRepository
                    .findBySourceGroupIdOrderBySentAtAscIdAsc(source.getSourceGroup().getId())
                    .stream()
                    .map(Source::getId)
                    .toList();
            return !analysisRepository.findBySourceIdIn(groupSourceIds).isEmpty();
        }
        return !analysisRepository.findBySourceId(source.getId()).isEmpty();
    }

    private LocalDateTime orderingTime(Source source) {
        if (source.getSentAt() != null) {
            return source.getSentAt();
        }
        if (source.getCollectedAt() != null) {
            return source.getCollectedAt();
        }
        return LocalDateTime.now();
    }
}
