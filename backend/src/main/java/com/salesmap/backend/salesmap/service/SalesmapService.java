package com.salesmap.backend.salesmap.service;

import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.salesmap.client.SalesmapClient;
import com.salesmap.backend.salesmap.client.dto.SalesmapApiRegisterRequest;
import com.salesmap.backend.salesmap.client.dto.SalesmapApiRegisterResponse;
import com.salesmap.backend.salesmap.dto.SalesmapRegisterRequest;
import com.salesmap.backend.salesmap.dto.SalesmapRegisterResponse;
import com.salesmap.backend.salesmap.entity.SalesmapRecord;
import com.salesmap.backend.salesmap.entity.SalesmapRecordStatus;
import com.salesmap.backend.salesmap.repository.SalesmapRecordRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class SalesmapService {

    private final SalesmapRecordRepository salesmapRecordRepository;
    private final AnalysisRepository analysisRepository;
    private final SalesmapClient salesmapClient;

    public SalesmapService(
            SalesmapRecordRepository salesmapRecordRepository,
            AnalysisRepository analysisRepository,
            SalesmapClient salesmapClient
    ) {
        this.salesmapRecordRepository = salesmapRecordRepository;
        this.analysisRepository = analysisRepository;
        this.salesmapClient = salesmapClient;
    }

    @Transactional
    public SalesmapRegisterResponse register(SalesmapRegisterRequest request, Long authenticatedUserId) {
        Analysis analysis = analysisRepository.findById(request.analysisId())
                .orElseThrow(() -> new NoSuchElementException("분석 결과를 찾을 수 없습니다."));
        validateAnalysisOwnership(analysis, authenticatedUserId);
        SalesmapApiRegisterResponse salesmapResult = salesmapClient.register(toSalesmapApiRegisterRequest(analysis));

        SalesmapRecord record = new SalesmapRecord(
                analysis,
                salesmapResult.externalRecordId(),
                salesmapResult.requestPayload(),
                salesmapResult.responsePayload(),
                SalesmapRecordStatus.REGISTERED,
                salesmapResult.registeredAt() == null ? LocalDateTime.now() : salesmapResult.registeredAt()
        );

        analysis.markApproved();

        return SalesmapRegisterResponse.from(salesmapRecordRepository.save(record));
    }

    @Transactional(readOnly = true)
    public List<SalesmapRegisterResponse> getRecordsByAnalysis(Long analysisId, Long authenticatedUserId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("분석 결과를 찾을 수 없습니다."));
        validateAnalysisOwnership(analysis, authenticatedUserId);

        return salesmapRecordRepository.findByAnalysisId(analysisId).stream()
                .map(SalesmapRegisterResponse::from)
                .toList();
    }

    private SalesmapApiRegisterRequest toSalesmapApiRegisterRequest(Analysis analysis) {
        return new SalesmapApiRegisterRequest(
                analysis.getId(),
                analysis.getSource().getId(),
                analysis.getCustomerName(),
                analysis.getContactName(),
                analysis.getProductName(),
                analysis.getAmount(),
                analysis.getScheduleText(),
                analysis.getFollowUpAction(),
                analysis.getSummary()
        );
    }

    private void validateAnalysisOwnership(Analysis analysis, Long authenticatedUserId) {
        if (!analysis.getSource().getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("해당 분석 결과에 접근할 권한이 없습니다.");
        }
    }
}
