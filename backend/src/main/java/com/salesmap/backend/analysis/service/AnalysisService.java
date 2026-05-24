package com.salesmap.backend.analysis.service;

import com.salesmap.backend.analysis.dto.AnalysisCreateRequest;
import com.salesmap.backend.analysis.dto.AnalysisResponse;
import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.entity.AnalysisStatus;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.repository.SourceRepository;
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
    public AnalysisResponse createAnalysis(AnalysisCreateRequest request) {
        Source source = sourceRepository.findById(request.sourceId())
                .orElseThrow(() -> new NoSuchElementException("원본 데이터를 찾을 수 없습니다."));

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
    public AnalysisResponse getAnalysis(Long analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("분석 결과를 찾을 수 없습니다."));

        return AnalysisResponse.from(analysis);
    }

    @Transactional(readOnly = true)
    public List<AnalysisResponse> getAnalysesBySource(Long sourceId) {
        if (!sourceRepository.existsById(sourceId)) {
            throw new NoSuchElementException("원본 데이터를 찾을 수 없습니다.");
        }

        return analysisRepository.findBySourceId(sourceId).stream()
                .map(AnalysisResponse::from)
                .toList();
    }
}
