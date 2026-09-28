package com.salesmap.backend.analysisjob.controller;

import com.salesmap.backend.analysisjob.dto.ActivityFeedResponse;
import com.salesmap.backend.analysisjob.dto.AnalysisJobStartResponse;
import com.salesmap.backend.analysisjob.service.AnalysisJobService;
import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis-jobs")
public class AnalysisJobController {

    private final AnalysisJobService analysisJobService;

    public AnalysisJobController(AnalysisJobService analysisJobService) {
        this.analysisJobService = analysisJobService;
    }

    @PostMapping("/bulk")
    public ApiResponse<AnalysisJobStartResponse> startBulk(@AuthenticationPrincipal CustomUserPrincipal principal) {
        AnalysisJobStartResponse response = analysisJobService.startManual(principal.getUserId());
        String message = switch (response.result()) {
            case "STARTED" -> "AI 일괄 분석을 시작했습니다.";
            case "ALREADY_RUNNING" -> "이미 AI 분석이 진행 중입니다.";
            default -> "분석할 새 메일이 없습니다.";
        };
        return ApiResponse.success(message, response);
    }

    @GetMapping("/activity")
    public ApiResponse<ActivityFeedResponse> activity(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(defaultValue = "0") long since
    ) {
        return ApiResponse.success(analysisJobService.getActivity(principal.getUserId(), since));
    }
}
