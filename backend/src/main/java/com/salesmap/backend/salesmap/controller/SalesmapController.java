package com.salesmap.backend.salesmap.controller;

import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.salesmap.dto.SalesmapRegisterRequest;
import com.salesmap.backend.salesmap.dto.SalesmapRegisterResponse;
import com.salesmap.backend.salesmap.service.SalesmapService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/salesmap")
public class SalesmapController {

    private final SalesmapService salesmapService;

    public SalesmapController(SalesmapService salesmapService) {
        this.salesmapService = salesmapService;
    }

    @PostMapping("/register")
    public ApiResponse<SalesmapRegisterResponse> register(@RequestBody SalesmapRegisterRequest request) {
        return ApiResponse.success("Salesmap 등록 요청이 완료되었습니다.", salesmapService.register(request));
    }
}
