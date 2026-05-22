package com.salesmap.backend.global.health;

import org.springframework.stereotype.Service;

@Service
public class HealthService {

    public String checkHealth() {
        return "OK";
    }
}
