package com.salesmap.backend.gmail.controller;

import com.salesmap.backend.gmail.dto.GmailAuthorizeResponse;
import com.salesmap.backend.gmail.dto.GmailCollectResponse;
import com.salesmap.backend.gmail.dto.GmailOAuthCallbackResponse;
import com.salesmap.backend.gmail.service.GmailIntegrationService;
import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/gmail")
public class GmailIntegrationController {

    private final GmailIntegrationService gmailIntegrationService;

    public GmailIntegrationController(GmailIntegrationService gmailIntegrationService) {
        this.gmailIntegrationService = gmailIntegrationService;
    }

    @GetMapping("/authorize")
    public ApiResponse<GmailAuthorizeResponse> authorize(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success(gmailIntegrationService.createAuthorizationUrl(principal.getUserId()));
    }

    @GetMapping("/callback")
    public ApiResponse<GmailOAuthCallbackResponse> callback(
            @RequestParam String code,
            @RequestParam String state
    ) {
        return ApiResponse.success("Gmail 계정 연동이 완료되었습니다.", gmailIntegrationService.handleCallback(code, state));
    }

    @PostMapping("/collect")
    public ApiResponse<GmailCollectResponse> collect(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success("Gmail 메일 수집이 완료되었습니다.", gmailIntegrationService.collectMessages(principal.getUserId()));
    }
}
