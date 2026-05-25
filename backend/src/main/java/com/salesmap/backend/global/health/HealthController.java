package com.salesmap.backend.global.health;

import com.salesmap.backend.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public ApiResponse<String> checkHealth() {
        return ApiResponse.success("Backend server is running", healthService.checkHealth());
    }
}
