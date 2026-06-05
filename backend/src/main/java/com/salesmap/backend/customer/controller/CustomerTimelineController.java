package com.salesmap.backend.customer.controller;

import com.salesmap.backend.customer.dto.CustomerSummaryResponse;
import com.salesmap.backend.customer.dto.CustomerTimelineResponse;
import com.salesmap.backend.customer.service.CustomerTimelineService;
import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerTimelineController {

    private final CustomerTimelineService customerTimelineService;

    public CustomerTimelineController(CustomerTimelineService customerTimelineService) {
        this.customerTimelineService = customerTimelineService;
    }

    @GetMapping
    public ApiResponse<List<CustomerSummaryResponse>> getCustomers(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success(customerTimelineService.getCustomers(principal.getUserId()));
    }

    @GetMapping("/{customerContactId}/timeline")
    public ApiResponse<CustomerTimelineResponse> getTimeline(
            @PathVariable Long customerContactId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success(customerTimelineService.getTimeline(customerContactId, principal.getUserId()));
    }
}
