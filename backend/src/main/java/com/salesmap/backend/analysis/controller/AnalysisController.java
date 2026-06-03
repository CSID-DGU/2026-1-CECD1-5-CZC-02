package com.salesmap.backend.analysis.controller;

import com.salesmap.backend.analysis.dto.AnalysisCreateRequest;
import com.salesmap.backend.analysis.dto.AnalysisGroupCreateRequest;
import com.salesmap.backend.analysis.dto.AnalysisResponse;
import com.salesmap.backend.analysis.dto.AnalysisUpdateRequest;
import com.salesmap.backend.analysis.service.AnalysisService;
import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    public ApiResponse<AnalysisResponse> createAnalysis(
            @Valid @RequestBody AnalysisCreateRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success("AI analysis completed.", analysisService.createAnalysis(request, principal.getUserId()));
    }

    @PostMapping("/group")
    public ApiResponse<AnalysisResponse> createGroupAnalysis(
            @Valid @RequestBody AnalysisGroupCreateRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success("AI group analysis completed.", analysisService.createGroupAnalysis(request, principal.getUserId()));
    }

    @GetMapping("/{analysisId}")
    public ApiResponse<AnalysisResponse> getAnalysis(
            @PathVariable Long analysisId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success(analysisService.getAnalysis(analysisId, principal.getUserId()));
    }

    @PatchMapping("/{analysisId}")
    public ApiResponse<AnalysisResponse> updateAnalysis(
            @PathVariable Long analysisId,
            @RequestBody AnalysisUpdateRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success("AI 분석 결과가 수정되었습니다.", analysisService.updateAnalysis(analysisId, request, principal.getUserId()));
    }

    @GetMapping("/source/{sourceId}")
    public ApiResponse<List<AnalysisResponse>> getAnalysesBySource(
            @PathVariable Long sourceId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success(analysisService.getAnalysesBySource(sourceId, principal.getUserId()));
    }
}
