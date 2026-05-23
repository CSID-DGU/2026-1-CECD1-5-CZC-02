package com.salesmap.backend.salesmap.service;

import com.salesmap.backend.salesmap.dto.SalesmapRegisterRequest;
import com.salesmap.backend.salesmap.dto.SalesmapRegisterResponse;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class SalesmapService {

    private final AtomicLong salesmapRecordIdGenerator = new AtomicLong(1);

    public SalesmapRegisterResponse register(SalesmapRegisterRequest request) {
        Long salesmapRecordId = salesmapRecordIdGenerator.getAndIncrement();

        return new SalesmapRegisterResponse(
                salesmapRecordId,
                request.analysisId(),
                "mock-salesmap-%03d".formatted(salesmapRecordId),
                "REGISTERED"
        );
    }
}
