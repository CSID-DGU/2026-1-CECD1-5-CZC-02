package com.salesmap.backend.analysisjob.service;

import com.salesmap.backend.analysis.dto.AnalysisCreateRequest;
import com.salesmap.backend.analysis.dto.AnalysisGroupCreateRequest;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.analysis.service.AnalysisService;
import com.salesmap.backend.analysisjob.dto.ActivityEvent;
import com.salesmap.backend.analysisjob.dto.ActivityFeedResponse;
import com.salesmap.backend.analysisjob.dto.AnalysisJobStartResponse;
import com.salesmap.backend.analysisjob.dto.AnalysisJobStatus;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceDirection;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;
import com.salesmap.backend.source.repository.SourceRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 수집 메일 AI 일괄 분석을 서버에서 실행하고 진행 상황을 보관한다.
 * <p>
 * 브라우저 루프로 돌리면 화면을 옮기는 순간 진행 표시가 사라지므로, 작업은 서버가 들고 있고 화면은 진행률만 조회한다.
 * 수동 일괄 분석과 자동 수집·분석 모드가 같은 작업 슬롯을 쓰므로 한 사용자에 대해 동시에 두 분석이 돌지 않는다
 * (같은 메일을 중복 분석하지 않고, 로컬 LLM도 한 번에 한 건만 처리).
 * 상태는 메모리에만 두며 서버 재시작 시 초기화된다.
 */
@Service
public class AnalysisJobService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobService.class);
    // 메일 목록 화면이 보여주는 최신 30건과 같은 범위를 분석한다.
    private static final int MANUAL_SOURCE_WINDOW = 30;
    private static final int MAX_EVENTS_PER_USER = 30;

    private final SourceRepository sourceRepository;
    private final AnalysisRepository analysisRepository;
    private final AnalysisService analysisService;

    private final Map<Long, JobState> jobs = new ConcurrentHashMap<>();
    private final Map<Long, Deque<ActivityEvent>> events = new ConcurrentHashMap<>();
    private final AtomicLong eventSequence = new AtomicLong();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "analysis-job");
        thread.setDaemon(true);
        return thread;
    });

    public AnalysisJobService(
            SourceRepository sourceRepository,
            AnalysisRepository analysisRepository,
            AnalysisService analysisService
    ) {
        this.sourceRepository = sourceRepository;
        this.analysisRepository = analysisRepository;
        this.analysisService = analysisService;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public AnalysisJobStartResponse startManual(Long userId) {
        JobState running = jobs.get(userId);
        if (running != null && running.isRunning()) {
            return new AnalysisJobStartResponse("ALREADY_RUNNING", running.toStatus());
        }

        List<Source> candidates = sourceRepository.findByUserIdAndSourceTypeAndStatusNot(
                userId,
                SourceType.EMAIL,
                SourceStatus.DELETED,
                PageRequest.of(0, MANUAL_SOURCE_WINDOW, Sort.by(Sort.Direction.DESC, "sentAt")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt")))
        ).getContent();
        List<Source> targets = pendingTargets(candidates, Integer.MAX_VALUE);
        if (targets.isEmpty()) {
            return new AnalysisJobStartResponse("NOTHING_TO_ANALYZE", null);
        }

        JobState job = tryBegin(userId, "MANUAL", targets.size());
        if (job == null) {
            return new AnalysisJobStartResponse("ALREADY_RUNNING", jobs.get(userId).toStatus());
        }
        executor.submit(() -> runTargets(userId, job, targets));
        return new AnalysisJobStartResponse("STARTED", job.toStatus());
    }

    /** 자동 모드 틱에서 호출된다. 다른 분석이 진행 중이면 이번 틱은 건너뛴다. 호출한 스레드에서 끝까지 실행한다. */
    public void runAutoBatch(Long userId, List<Source> candidates, int limit) {
        List<Source> targets = pendingTargets(candidates, limit);
        if (targets.isEmpty()) {
            return;
        }
        JobState job = tryBegin(userId, "AUTO", targets.size());
        if (job != null) {
            runTargets(userId, job, targets);
        }
    }

    public void recordEvent(Long userId, String type, String trigger, String message) {
        Deque<ActivityEvent> userEvents = events.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (userEvents) {
            userEvents.addLast(new ActivityEvent(eventSequence.incrementAndGet(), type, trigger, message, LocalDateTime.now()));
            while (userEvents.size() > MAX_EVENTS_PER_USER) {
                userEvents.removeFirst();
            }
        }
    }

    public ActivityFeedResponse getActivity(Long userId, long sinceEventId) {
        JobState job = jobs.get(userId);
        Deque<ActivityEvent> userEvents = events.getOrDefault(userId, new ArrayDeque<>());
        List<ActivityEvent> fresh;
        long latest;
        synchronized (userEvents) {
            fresh = userEvents.stream().filter(event -> event.id() > sinceEventId).toList();
            latest = userEvents.isEmpty() ? 0 : userEvents.getLast().id();
        }
        return new ActivityFeedResponse(job == null ? null : job.toStatus(), fresh, latest);
    }

    private JobState tryBegin(Long userId, String trigger, int total) {
        JobState next = new JobState(trigger, total);
        JobState current = jobs.compute(userId, (id, existing) ->
                existing != null && existing.isRunning() ? existing : next);
        return current == next ? next : null;
    }

    private void runTargets(Long userId, JobState job, List<Source> targets) {
        recordEvent(userId, "ANALYSIS_STARTED", job.trigger, triggerLabel(job.trigger) + "AI 분석을 시작합니다 · " + targets.size() + "건");
        try {
            for (Source source : targets) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                job.startItem(source.getTitle());
                if (isAlreadyAnalyzed(source)) {
                    job.finishItem(Outcome.SKIPPED);
                    continue;
                }
                try {
                    Long groupId = source.getSourceGroup() == null ? null : source.getSourceGroup().getId();
                    if (groupId != null) {
                        analysisService.createGroupAnalysis(new AnalysisGroupCreateRequest(groupId, "ollama"), userId);
                    } else {
                        analysisService.createAnalysis(new AnalysisCreateRequest(source.getId(), "ollama"), userId);
                    }
                    job.finishItem(Outcome.ANALYZED);
                } catch (RuntimeException exception) {
                    job.finishItem(Outcome.FAILED);
                    log.warn("[ANALYSIS_JOB] failed. userId={}, sourceId={}, reason={}", userId, source.getId(), exception.getMessage());
                }
            }
        } finally {
            job.finish();
            recordEvent(userId, "ANALYSIS_FINISHED", job.trigger, finishedMessage(job));
            log.info("[ANALYSIS_JOB] finished. userId={}, status={}", userId, job.toStatus());
        }
    }

    /**
     * 받은 메일만, 보낸 시각 오름차순(발신자 기억이 시간순으로 쌓이도록), 대화(스레드)당 한 번, 아직 분석 안 된 것만.
     * 미팅 메모(MEETING_NOTE) 등 메일이 아닌 원본은 회의록 쪽에서 다루므로 메일 분석 대상이 아니다.
     */
    private List<Source> pendingTargets(List<Source> candidates, int limit) {
        List<Source> ordered = candidates.stream()
                .filter(source -> source.getSourceType() == SourceType.EMAIL)
                .filter(source -> source.getDirection() != SourceDirection.SENT)
                .filter(source -> source.getStatus() != SourceStatus.DELETED)
                .sorted(Comparator.comparing(this::orderingTime))
                .toList();

        Set<Object> seenConversations = new HashSet<>();
        List<Source> targets = new ArrayList<>();
        for (Source source : ordered) {
            if (targets.size() >= limit) {
                break;
            }
            Object conversation = source.getSourceGroup() == null
                    ? "source-" + source.getId()
                    : source.getSourceGroup().getId();
            if (seenConversations.add(conversation) && !isAlreadyAnalyzed(source)) {
                targets.add(source);
            }
        }
        return targets;
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

    private String triggerLabel(String trigger) {
        return "AUTO".equals(trigger) ? "[자동] " : "";
    }

    private String finishedMessage(JobState job) {
        AnalysisJobStatus status = job.toStatus();
        StringBuilder message = new StringBuilder(triggerLabel(job.trigger))
                .append("AI 분석 완료 · ").append(status.analyzed()).append("건 분석");
        if (status.failed() > 0) {
            message.append(", ").append(status.failed()).append("건 실패");
        }
        if (status.analyzed() > 0) {
            message.append(" — 결과를 확인하고 승인해 주세요");
        }
        return message.toString();
    }

    private enum Outcome {
        ANALYZED, SKIPPED, FAILED
    }

    private static final class JobState {

        private final String trigger;
        private final int total;
        private final LocalDateTime startedAt = LocalDateTime.now();
        private int analyzed;
        private int skipped;
        private int failed;
        private String currentTitle;
        private LocalDateTime finishedAt;

        private JobState(String trigger, int total) {
            this.trigger = trigger;
            this.total = total;
        }

        synchronized boolean isRunning() {
            return finishedAt == null;
        }

        synchronized void startItem(String title) {
            this.currentTitle = title;
        }

        synchronized void finishItem(Outcome outcome) {
            switch (outcome) {
                case ANALYZED -> analyzed++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }

        synchronized void finish() {
            this.currentTitle = null;
            this.finishedAt = LocalDateTime.now();
        }

        synchronized AnalysisJobStatus toStatus() {
            return new AnalysisJobStatus(
                    trigger,
                    finishedAt == null,
                    total,
                    analyzed + skipped + failed,
                    analyzed,
                    skipped,
                    failed,
                    currentTitle,
                    startedAt,
                    finishedAt
            );
        }
    }
}
