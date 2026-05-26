package com.salesmap.backend.analysis.service;

import com.salesmap.backend.analysis.dto.AnalysisCreateRequest;
import com.salesmap.backend.analysis.dto.AnalysisResponse;
import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.entity.AnalysisStatus;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.source.entity.Source;
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

    public AnalysisService(AnalysisRepository analysisRepository, SourceRepository sourceRepository) {
        this.analysisRepository = analysisRepository;
        this.sourceRepository = sourceRepository;
    }

    @Transactional
    public AnalysisResponse createAnalysis(AnalysisCreateRequest request, Long authenticatedUserId) {
        Source source = sourceRepository.findById(request.sourceId())
                .orElseThrow(() -> new NoSuchElementException("원본 데이터를 찾을 수 없습니다."));
        validateSourceOwnership(source, authenticatedUserId);

        Analysis analysis = new Analysis(
                source,
                "ABC Corp",
                "홍길동",
                "Sales Solution",
                1_000_000L,
                "다음 주 수요일 미팅",
                "견적서 발송",
                "고객이 제품 도입을 검토 중이며 다음 미팅 예정",
                AnalysisStatus.ANALYZED,
                LocalDateTime.now(),
                null
        );

        source.markAnalyzed();

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

    private void validateAnalysisOwnership(Analysis analysis, Long authenticatedUserId) {
        validateSourceOwnership(analysis.getSource(), authenticatedUserId);
    }

    private void validateSourceOwnership(Source source, Long authenticatedUserId) {
        if (!source.getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("해당 분석 결과에 접근할 권한이 없습니다.");
        }
    }
}
