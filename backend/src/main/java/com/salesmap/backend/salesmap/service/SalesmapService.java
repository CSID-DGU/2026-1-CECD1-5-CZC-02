package com.salesmap.backend.salesmap.service;

import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.salesmap.dto.SalesmapRegisterRequest;
import com.salesmap.backend.salesmap.dto.SalesmapRegisterResponse;
import com.salesmap.backend.salesmap.entity.SalesmapRecord;
import com.salesmap.backend.salesmap.entity.SalesmapRecordStatus;
import com.salesmap.backend.salesmap.repository.SalesmapRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class SalesmapService {

    private final SalesmapRecordRepository salesmapRecordRepository;
    private final AnalysisRepository analysisRepository;

    public SalesmapService(
            SalesmapRecordRepository salesmapRecordRepository,
            AnalysisRepository analysisRepository
    ) {
        this.salesmapRecordRepository = salesmapRecordRepository;
        this.analysisRepository = analysisRepository;
    }

    @Transactional
    public SalesmapRegisterResponse register(SalesmapRegisterRequest request) {
        Analysis analysis = analysisRepository.findById(request.analysisId())
                .orElseThrow(() -> new NoSuchElementException("분석 결과를 찾을 수 없습니다."));

        String externalRecordId = "mock-salesmap-" + request.analysisId();
        String requestPayload = "{\"analysisId\":" + request.analysisId() + "}";
        String responsePayload = "{\"externalRecordId\":\"" + externalRecordId + "\",\"status\":\"REGISTERED\"}";

        SalesmapRecord record = new SalesmapRecord(
                analysis,
                externalRecordId,
                requestPayload,
                responsePayload,
                SalesmapRecordStatus.REGISTERED,
                LocalDateTime.now()
        );

        analysis.markApproved();

        return SalesmapRegisterResponse.from(salesmapRecordRepository.save(record));
    }

    @Transactional(readOnly = true)
    public List<SalesmapRegisterResponse> getRecordsByAnalysis(Long analysisId) {
        if (!analysisRepository.existsById(analysisId)) {
            throw new NoSuchElementException("분석 결과를 찾을 수 없습니다.");
        }

        return salesmapRecordRepository.findByAnalysisId(analysisId).stream()
                .map(SalesmapRegisterResponse::from)
                .toList();
    }
}
