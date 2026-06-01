package com.salesmap.backend.integration.controller;

import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import com.salesmap.backend.integration.dto.IntegrationCreateRequest;
import com.salesmap.backend.integration.dto.IntegrationResponse;
import com.salesmap.backend.integration.service.IntegrationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {

    private final IntegrationService integrationService;

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @PostMapping
    public ApiResponse<IntegrationResponse> createIntegration(
            @Valid @RequestBody IntegrationCreateRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success("외부 서비스 연동 정보가 저장되었습니다.", integrationService.createIntegration(request, principal.getUserId()));
    }

    @GetMapping
    public ApiResponse<List<IntegrationResponse>> getIntegrations(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success(integrationService.getIntegrations(principal.getUserId()));
    }

    @GetMapping("/{provider}")
    public ApiResponse<IntegrationResponse> getIntegrationByProvider(
            @PathVariable String provider,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success(integrationService.getIntegrationByProvider(provider, principal.getUserId()));
    }
}
