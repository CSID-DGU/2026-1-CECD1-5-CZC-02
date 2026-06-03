package com.salesmap.backend.salesmap.controller;

import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import com.salesmap.backend.salesmap.dto.SalesmapRegisterRequest;
import com.salesmap.backend.salesmap.dto.SalesmapRegisterResponse;
import com.salesmap.backend.salesmap.service.SalesmapService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/salesmap")
public class SalesmapController {

    private static final Logger log = LoggerFactory.getLogger(SalesmapController.class);

    private final SalesmapService salesmapService;

    public SalesmapController(SalesmapService salesmapService) {
        this.salesmapService = salesmapService;
    }

    @PostMapping("/register")
    public ApiResponse<SalesmapRegisterResponse> register(
            @Valid @RequestBody SalesmapRegisterRequest request,
            Authentication authentication
    ) {
        log.debug("[SALESMAP DEBUG] register entered, analysisId={}, authenticationPresent={}, principalType={}",
                request.analysisId(),
                authentication != null,
                authentication == null || authentication.getPrincipal() == null
                        ? null
                        : authentication.getPrincipal().getClass().getName()
        );

        Long authenticatedUserId = getAuthenticatedUserId(authentication);

        log.debug("[SALESMAP DEBUG] register authenticatedUserId={}", authenticatedUserId);

        return ApiResponse.success("Salesmap 등록 요청이 완료되었습니다.", salesmapService.register(request, authenticatedUserId));
    }

    @GetMapping("/analysis/{analysisId}")
    public ApiResponse<List<SalesmapRegisterResponse>> getRecordsByAnalysis(
            @PathVariable Long analysisId,
            Authentication authentication
    ) {
        Long authenticatedUserId = getAuthenticatedUserId(authentication);

        return ApiResponse.success(salesmapService.getRecordsByAnalysis(analysisId, authenticatedUserId));
    }

    private Long getAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("인증이 필요합니다.");
        }

        return principal.getUserId();
    }
}
