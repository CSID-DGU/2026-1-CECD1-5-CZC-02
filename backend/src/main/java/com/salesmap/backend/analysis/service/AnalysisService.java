package com.salesmap.backend.analysis.service;

import com.salesmap.backend.ai.client.AiClient;
import com.salesmap.backend.ai.dto.AiAnalysisRequest;
import com.salesmap.backend.ai.dto.AiAnalysisResponse;
import com.salesmap.backend.analysis.dto.AnalysisCreateRequest;
import com.salesmap.backend.analysis.dto.AnalysisResponse;
import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.entity.AnalysisStatus;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.repository.ScheduleRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.repository.SourceRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final SourceRepository sourceRepository;
    private final ScheduleRepository scheduleRepository;
    private final AiClient aiClient;

    public AnalysisService(
            AnalysisRepository analysisRepository,
            SourceRepository sourceRepository,
            ScheduleRepository scheduleRepository,
            AiClient aiClient
    ) {
        this.analysisRepository = analysisRepository;
        this.sourceRepository = sourceRepository;
        this.scheduleRepository = scheduleRepository;
        this.aiClient = aiClient;
    }

    @Transactional
    public AnalysisResponse createAnalysis(AnalysisCreateRequest request, Long authenticatedUserId) {
        Source source = sourceRepository.findById(request.sourceId())
                .orElseThrow(() -> new NoSuchElementException("원본 데이터를 찾을 수 없습니다."));
        validateSourceOwnership(source, authenticatedUserId);
        AiAnalysisResponse aiResult = aiClient.analyze(toAiAnalysisRequest(source));

        Analysis analysis = new Analysis(
                source,
                aiResult.customerName(),
                aiResult.contactName(),
                aiResult.productName(),
                aiResult.amount(),
                aiResult.actionType(),
                aiResult.targetScheduleId(),
                aiResult.targetScheduleTitle(),
                aiResult.actionReason(),
                buildScheduleText(aiResult),
                aiResult.todoContent(),
                aiResult.summary(),
                AnalysisStatus.ANALYZED,
                LocalDateTime.now(),
                null
        );

        source.markAnalyzed();
        applyScheduleAction(aiResult, authenticatedUserId);

        return AnalysisResponse.from(analysisRepository.save(analysis));
    }

    @Transactional(readOnly = true)
    public AnalysisResponse getAnalysis(Long analysisId, Long authenticatedUserId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("분석 결과를 찾을 수 없습니다."));
        validateAnalysisOwnership(analysis, authenticatedUserId);

        return AnalysisResponse.from(analysis);
    }

    @Transactional(readOnly = true)
    public List<AnalysisResponse> getAnalysesBySource(Long sourceId, Long authenticatedUserId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new NoSuchElementException("원본 데이터를 찾을 수 없습니다."));
        validateSourceOwnership(source, authenticatedUserId);

        return analysisRepository.findBySourceId(sourceId).stream()
                .map(AnalysisResponse::from)
                .toList();
    }

    private AiAnalysisRequest toAiAnalysisRequest(Source source) {
        AiAnalysisRequest.RequesterInfo requester = new AiAnalysisRequest.RequesterInfo(
                source.getUser().getId(),
                source.getUser().getName(),
                source.getUser().getEmail()
        );
        AiAnalysisRequest.SourceGroupInfo sourceGroup = new AiAnalysisRequest.SourceGroupInfo(
                "source-" + source.getId(),
                source.getSourceType(),
                source.getTitle(),
                false
        );
        AiAnalysisRequest.MessageItem message = new AiAnalysisRequest.MessageItem(
                source.getId(),
                source.getExternalSourceId(),
                "UNKNOWN",
                source.getUser().getName(),
                source.getUser().getEmail(),
                Collections.emptyList(),
                Collections.emptyList(),
                source.getCollectedAt(),
                source.getContent()
        );

        return new AiAnalysisRequest(
                source.getId(),
                source.getSourceType(),
                source.getExternalSourceId(),
                source.getTitle(),
                source.getContent(),
                source.getCollectedAt(),
                requester,
                sourceGroup,
                List.of(message),
                getExistingSchedules(source.getUser().getId())
        );
    }

    private List<AiAnalysisRequest.ExistingScheduleInfo> getExistingSchedules(Long userId) {
        return scheduleRepository.findByUserId(userId).stream()
                .map(this::toExistingScheduleInfo)
                .toList();
    }

    private AiAnalysisRequest.ExistingScheduleInfo toExistingScheduleInfo(Schedule schedule) {
        return new AiAnalysisRequest.ExistingScheduleInfo(
                schedule.getId(),
                schedule.getTitle(),
                schedule.getScheduleDateTime(),
                Collections.emptyList()
        );
    }

    private String buildScheduleText(AiAnalysisResponse aiResult) {
        if (aiResult.scheduleDateTime() == null) {
            return aiResult.scheduleTitle();
        }

        return aiResult.scheduleTitle() + " (" + aiResult.scheduleDateTime() + ")";
    }

    private void applyScheduleAction(AiAnalysisResponse aiResult, Long authenticatedUserId) {
        if (aiResult.actionType() == null || aiResult.targetScheduleId() == null) {
            return;
        }

        Schedule schedule = scheduleRepository.findById(aiResult.targetScheduleId())
                .orElseThrow(() -> new NoSuchElementException("대상 일정을 찾을 수 없습니다."));
        validateScheduleOwnership(schedule, authenticatedUserId);

        if ("CANCEL".equals(aiResult.actionType())) {
            schedule.cancel();
            return;
        }

        if ("UPDATE".equals(aiResult.actionType()) && aiResult.scheduleDateTime() != null) {
            String title = aiResult.scheduleTitle() == null ? schedule.getTitle() : aiResult.scheduleTitle();
            String memo = aiResult.summary() == null ? schedule.getMemo() : aiResult.summary();
            schedule.update(title, aiResult.scheduleDateTime(), memo);
        }
    }

    private void validateAnalysisOwnership(Analysis analysis, Long authenticatedUserId) {
        validateSourceOwnership(analysis.getSource(), authenticatedUserId);
    }

    private void validateSourceOwnership(Source source, Long authenticatedUserId) {
        if (!source.getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("해당 분석 결과에 접근할 권한이 없습니다.");
        }
    }

    private void validateScheduleOwnership(Schedule schedule, Long authenticatedUserId) {
        if (!schedule.getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("해당 일정에 접근할 권한이 없습니다.");
        }
    }
}
