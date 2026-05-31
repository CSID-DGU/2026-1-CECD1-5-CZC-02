package com.salesmap.backend.analysis.service;

import com.salesmap.backend.ai.client.AiClient;
import com.salesmap.backend.ai.dto.AiAnalysisRequest;
import com.salesmap.backend.ai.dto.AiAnalysisResponse;
import com.salesmap.backend.ai.dto.AiGroupAnalysisRequest;
import com.salesmap.backend.ai.dto.AiSourceGroupPayload;
import com.salesmap.backend.ai.dto.AiSourceMessagePayload;
import com.salesmap.backend.analysis.dto.AnalysisCreateRequest;
import com.salesmap.backend.analysis.dto.AnalysisGroupCreateRequest;
import com.salesmap.backend.analysis.dto.AnalysisResponse;
import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.entity.AnalysisStatus;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceGroup;
import com.salesmap.backend.source.repository.SourceGroupRepository;
import com.salesmap.backend.source.repository.SourceRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final SourceRepository sourceRepository;
    private final SourceGroupRepository sourceGroupRepository;
    private final AiClient aiClient;

    public AnalysisService(
            AnalysisRepository analysisRepository,
            SourceRepository sourceRepository,
            SourceGroupRepository sourceGroupRepository,
            AiClient aiClient
    ) {
        this.analysisRepository = analysisRepository;
        this.sourceRepository = sourceRepository;
        this.sourceGroupRepository = sourceGroupRepository;
        this.aiClient = aiClient;
    }

    @Transactional
    public AnalysisResponse createAnalysis(AnalysisCreateRequest request, Long authenticatedUserId) {
        Source source = sourceRepository.findById(request.sourceId())
                .orElseThrow(() -> new NoSuchElementException("Source not found."));
        validateSourceOwnership(source, authenticatedUserId);

        AiAnalysisResponse aiResult = aiClient.analyze(toAiAnalysisRequest(source));
        Analysis analysis = createAnalysisEntity(source, aiResult);
        source.markAnalyzed();

        return AnalysisResponse.from(analysisRepository.save(analysis));
    }

    @Transactional
    public AnalysisResponse createGroupAnalysis(AnalysisGroupCreateRequest request, Long authenticatedUserId) {
        SourceGroup sourceGroup = sourceGroupRepository.findById(request.sourceGroupId())
                .orElseThrow(() -> new NoSuchElementException("Source group not found."));
        validateSourceGroupOwnership(sourceGroup, authenticatedUserId);

        List<Source> sources = sourceRepository.findBySourceGroupIdOrderBySentAtAscIdAsc(sourceGroup.getId());
        if (sources.isEmpty()) {
            throw new NoSuchElementException("Source group has no messages.");
        }

        AiAnalysisResponse aiResult = aiClient.analyzeGroup(toAiGroupAnalysisRequest(sourceGroup, sources));
        Source representativeSource = sources.get(0);
        Analysis analysis = createAnalysisEntity(representativeSource, aiResult);
        sources.forEach(Source::markAnalyzed);

        return AnalysisResponse.from(analysisRepository.save(analysis));
    }

    @Transactional(readOnly = true)
    public AnalysisResponse getAnalysis(Long analysisId, Long authenticatedUserId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Analysis not found."));
        validateAnalysisOwnership(analysis, authenticatedUserId);

        return AnalysisResponse.from(analysis);
    }

    @Transactional(readOnly = true)
    public List<AnalysisResponse> getAnalysesBySource(Long sourceId, Long authenticatedUserId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new NoSuchElementException("Source not found."));
        validateSourceOwnership(source, authenticatedUserId);

        return analysisRepository.findBySourceId(sourceId).stream()
                .map(AnalysisResponse::from)
                .toList();
    }

    private Analysis createAnalysisEntity(Source source, AiAnalysisResponse aiResult) {
        return new Analysis(
                source,
                aiResult.customerName(),
                aiResult.contactName(),
                aiResult.productName(),
                aiResult.amount(),
                buildScheduleText(aiResult),
                aiResult.todoContent(),
                aiResult.summary(),
                AnalysisStatus.ANALYZED,
                LocalDateTime.now(),
                null
        );
    }

    private AiAnalysisRequest toAiAnalysisRequest(Source source) {
        return new AiAnalysisRequest(
                source.getId(),
                source.getSourceType(),
                source.getExternalSourceId(),
                source.getTitle(),
                source.getContent(),
                source.getCollectedAt()
        );
    }

    private AiGroupAnalysisRequest toAiGroupAnalysisRequest(SourceGroup sourceGroup, List<Source> sources) {
        return new AiGroupAnalysisRequest(
                new AiSourceGroupPayload(
                        sourceGroup.getExternalGroupId(),
                        sourceGroup.getSourceType(),
                        sourceGroup.getTitle(),
                        sourceGroup.isDeduplicated()
                ),
                sources.stream()
                        .map(this::toAiSourceMessagePayload)
                        .toList()
        );
    }

    private AiSourceMessagePayload toAiSourceMessagePayload(Source source) {
        return new AiSourceMessagePayload(
                source.getId(),
                source.getExternalSourceId(),
                source.getDirection(),
                source.getSenderName(),
                source.getSenderEmail(),
                splitList(source.getReceiverNames()),
                splitList(source.getReceiverEmails()),
                source.getSentAt(),
                source.getContent()
        );
    }

    private List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return List.of(value.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String buildScheduleText(AiAnalysisResponse aiResult) {
        if (aiResult.scheduleDateTime() == null) {
            return aiResult.scheduleTitle();
        }

        return aiResult.scheduleTitle() + " (" + aiResult.scheduleDateTime() + ")";
    }

    private void validateAnalysisOwnership(Analysis analysis, Long authenticatedUserId) {
        validateSourceOwnership(analysis.getSource(), authenticatedUserId);
    }

    private void validateSourceOwnership(Source source, Long authenticatedUserId) {
        if (!source.getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("You do not have permission to access this analysis.");
        }
    }

    private void validateSourceGroupOwnership(SourceGroup sourceGroup, Long authenticatedUserId) {
        if (!sourceGroup.getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("You do not have permission to access this source group.");
        }
    }
}
