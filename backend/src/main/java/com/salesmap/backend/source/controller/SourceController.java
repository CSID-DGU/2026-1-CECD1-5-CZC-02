package com.salesmap.backend.source.controller;

import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.source.dto.SourceCreateRequest;
import com.salesmap.backend.source.dto.SourceResponse;
import com.salesmap.backend.source.service.SourceService;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @PostMapping
    public ApiResponse<SourceResponse> createSource(
            @Valid @RequestBody SourceCreateRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success("원본 데이터가 생성되었습니다.", sourceService.createSource(request, principal.getUserId()));
    }

    @GetMapping
    public ApiResponse<List<SourceResponse>> getSources(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success(sourceService.getSourcesByUser(userId, principal.getUserId(), page, size));
    }

    @GetMapping("/{sourceId}")
    public ApiResponse<SourceResponse> getSource(
            @PathVariable Long sourceId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success(sourceService.getSource(sourceId, principal.getUserId()));
    }
}
