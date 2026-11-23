package com.salesmap.backend.briefing.controller;

import com.salesmap.backend.briefing.dto.MorningBriefingResponse;
import com.salesmap.backend.briefing.dto.TaskScheduleRegistrationRequest;
import com.salesmap.backend.briefing.dto.TodayBriefingResponse;
import com.salesmap.backend.briefing.service.BriefingService;
import com.salesmap.backend.briefing.service.CalendarRegistrationService;
import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/briefings")
public class BriefingController {
    private final BriefingService briefings;
    private final CalendarRegistrationService calendarRegistration;

    public BriefingController(BriefingService briefings, CalendarRegistrationService calendarRegistration) {
        this.briefings = briefings;
        this.calendarRegistration = calendarRegistration;
    }

    @GetMapping("/morning")
    public ApiResponse<MorningBriefingResponse> morning(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        LocalDate briefingDate = date == null ? LocalDate.now(ZoneId.of("Asia/Seoul")) : date;
        return ApiResponse.success(briefings.morning(principal.getUserId(), briefingDate));
    }

    @GetMapping("/preparation")
    public ApiResponse<MorningBriefingResponse> preparation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        LocalDate briefingDate = date == null ? LocalDate.now(ZoneId.of("Asia/Seoul")) : date;
        return ApiResponse.success(briefings.preparation(principal.getUserId(), briefingDate));
    }

    @GetMapping("/today")
    public ApiResponse<TodayBriefingResponse> today(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        LocalDate briefingDate = date == null ? LocalDate.now(ZoneId.of("Asia/Seoul")) : date;
        return ApiResponse.success(briefings.today(principal.getUserId(), briefingDate));
    }

    @GetMapping("/confirmed-tasks")
    public ApiResponse<MorningBriefingResponse> confirmedTasks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        LocalDate briefingDate = date == null ? LocalDate.now(ZoneId.of("Asia/Seoul")) : date;
        return ApiResponse.success(briefings.confirmedTasks(principal.getUserId(), briefingDate));
    }

    @PostMapping("/calendar-registration")
    public ApiResponse<com.salesmap.backend.schedule.dto.ScheduleResponse> registerCalendar(
            @Valid @RequestBody TaskScheduleRegistrationRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success(calendarRegistration.register(request, principal.getUserId()));
    }
}
