package com.salesmap.backend.schedule.service;

import com.salesmap.backend.schedule.dto.ScheduleCreateRequest;
import com.salesmap.backend.schedule.dto.ScheduleResponse;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class ScheduleService {

    private final AtomicLong scheduleIdGenerator = new AtomicLong(1);

    public ScheduleResponse createSchedule(ScheduleCreateRequest request) {
        return new ScheduleResponse(
                scheduleIdGenerator.getAndIncrement(),
                request.analysisId(),
                request.title(),
                request.scheduleDateTime(),
                request.memo(),
                "SCHEDULED"
        );
    }
}
