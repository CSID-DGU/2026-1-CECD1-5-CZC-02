package com.salesmap.backend.autopipeline.controller;

import com.salesmap.backend.autopipeline.dto.AutoPipelineStatusResponse;
import com.salesmap.backend.autopipeline.service.AutoPipelineService;
import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auto-pipeline")
public class AutoPipelineController {

    private final AutoPipelineService autoPipelineService;

    public AutoPipelineController(AutoPipelineService autoPipelineService) {
        this.autoPipelineService = autoPipelineService;
    }

    @GetMapping("/status")
    public ApiResponse<AutoPipelineStatusResponse> getStatus(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success(new AutoPipelineStatusResponse(autoPipelineService.isEnabled(principal.getUserId())));
    }

    @PostMapping("/enable")
    public ApiResponse<AutoPipelineStatusResponse> enable(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        autoPipelineService.enable(principal.getUserId());
        return ApiResponse.success("자동 수집·분석을 켰습니다.", new AutoPipelineStatusResponse(true));
    }

    @PostMapping("/disable")
    public ApiResponse<AutoPipelineStatusResponse> disable(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        autoPipelineService.disable(principal.getUserId());
        return ApiResponse.success("자동 수집·분석을 껐습니다.", new AutoPipelineStatusResponse(false));
    }
}
