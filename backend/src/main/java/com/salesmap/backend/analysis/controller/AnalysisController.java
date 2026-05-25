package com.salesmap.backend.analysis.controller;

import com.salesmap.backend.analysis.dto.AnalysisCreateRequest;
import com.salesmap.backend.analysis.dto.AnalysisResponse;
import com.salesmap.backend.analysis.service.AnalysisService;
import com.salesmap.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ApiResponse<AnalysisResponse> createAnalysis(@Valid @RequestBody AnalysisCreateRequest request) {
        return ApiResponse.success("AI 분석이 완료되었습니다.", analysisService.createAnalysis(request));
    }

    @GetMapping("/{analysisId}")
    public ApiResponse<AnalysisResponse> getAnalysis(@PathVariable Long analysisId) {
        return ApiResponse.success(analysisService.getAnalysis(analysisId));
    }

    @GetMapping("/source/{sourceId}")
    public ApiResponse<List<AnalysisResponse>> getAnalysesBySource(@PathVariable Long sourceId) {
        return ApiResponse.success(analysisService.getAnalysesBySource(sourceId));
    }
}
