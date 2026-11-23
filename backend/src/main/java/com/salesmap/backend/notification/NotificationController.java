package com.salesmap.backend.notification;

import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 앱 안 알림의 시간 설정과 횟수 판정. 외부로 아무것도 보내지 않는다. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    public record SettingsRequest(String morningTime) {
    }

    public record ImmediateRequest(String key) {
    }

    @GetMapping("/settings")
    public ApiResponse<NotificationService.Settings> settings(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success(notifications.settings(principal.getUserId()));
    }

    @PutMapping("/settings")
    public ApiResponse<NotificationService.Settings> updateSettings(@RequestBody SettingsRequest request,
                                                                    @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success("알림 시간을 저장했습니다.",
                notifications.updateSettings(request.morningTime(), principal.getUserId()));
    }

    @PostMapping("/morning-bundle/claim")
    public ApiResponse<NotificationService.MorningClaim> claimMorningBundle(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success(notifications.claimMorningBundle(principal.getUserId()));
    }

    @PostMapping("/immediate/claim")
    public ApiResponse<NotificationService.ImmediateClaim> claimImmediate(@RequestBody ImmediateRequest request,
                                                                          @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success(notifications.claimImmediate(request.key(), principal.getUserId()));
    }
}
