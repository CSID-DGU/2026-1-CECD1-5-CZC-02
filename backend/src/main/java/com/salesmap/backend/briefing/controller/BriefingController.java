package com.salesmap.backend.briefing.controller;

import com.salesmap.backend.briefing.dto.MorningBriefingResponse;
import com.salesmap.backend.briefing.service.BriefingService;
import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/briefings")
public class BriefingController {
    private final BriefingService briefings;

    public BriefingController(BriefingService briefings) { this.briefings = briefings; }

    @GetMapping("/morning")
    public ApiResponse<MorningBriefingResponse> morning(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        LocalDate briefingDate = date == null ? LocalDate.now(ZoneId.of("Asia/Seoul")) : date;
        return ApiResponse.success(briefings.morning(principal.getUserId(), briefingDate));
    }
}
