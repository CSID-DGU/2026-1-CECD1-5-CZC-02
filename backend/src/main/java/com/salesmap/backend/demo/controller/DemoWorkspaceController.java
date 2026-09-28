package com.salesmap.backend.demo.controller;

import com.salesmap.backend.demo.dto.DemoStatusResponse;
import com.salesmap.backend.demo.service.DemoWorkspaceService;
import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 데모 모드 중에도 켜고 끄는 주체는 로그인한 실제 사용자이므로 ownerUserId를 쓴다.
@RestController
@RequestMapping("/api/dev/demo")
public class DemoWorkspaceController {

    private final DemoWorkspaceService demoWorkspaceService;

    public DemoWorkspaceController(DemoWorkspaceService demoWorkspaceService) {
        this.demoWorkspaceService = demoWorkspaceService;
    }

    @GetMapping("/status")
    public ApiResponse<DemoStatusResponse> status(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success(demoWorkspaceService.getStatus(principal.getOwnerUserId()));
    }

    @PostMapping("/enable")
    public ApiResponse<DemoStatusResponse> enable(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success("데모 모드를 켰습니다.", demoWorkspaceService.enable(principal.getOwnerUserId()));
    }

    @PostMapping("/disable")
    public ApiResponse<DemoStatusResponse> disable(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success("데모 모드를 껐습니다.", demoWorkspaceService.disable(principal.getOwnerUserId()));
    }

    @PostMapping("/reset")
    public ApiResponse<DemoStatusResponse> reset(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success("데모 데이터를 초기화했습니다.", demoWorkspaceService.reset(principal.getOwnerUserId()));
    }
}
