package com.salesmap.backend.schedule.controller;

import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.schedule.dto.ScheduleCreateRequest;
import com.salesmap.backend.schedule.dto.ScheduleResponse;
import com.salesmap.backend.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping
    public ApiResponse<ScheduleResponse> createSchedule(@Valid @RequestBody ScheduleCreateRequest request) {
        return ApiResponse.success("일정이 등록되었습니다.", scheduleService.createSchedule(request));
    }

    @GetMapping
    public ApiResponse<List<ScheduleResponse>> getSchedules(@RequestParam Long userId) {
        return ApiResponse.success(scheduleService.getSchedulesByUser(userId));
    }
}
