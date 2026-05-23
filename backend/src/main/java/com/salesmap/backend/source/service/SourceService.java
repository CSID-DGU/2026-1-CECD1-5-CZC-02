package com.salesmap.backend.source.service;

import com.salesmap.backend.source.dto.SourceCreateRequest;
import com.salesmap.backend.source.dto.SourceResponse;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class SourceService {

    private final AtomicLong sourceIdGenerator = new AtomicLong(1);

    public SourceResponse createSource(SourceCreateRequest request) {
        return new SourceResponse(
                sourceIdGenerator.getAndIncrement(),
                request.sourceType(),
                request.title(),
                request.content(),
                "CREATED"
        );
    }

    public SourceResponse getSource(Long sourceId) {
        return new SourceResponse(
                sourceId,
                "EMAIL",
                "고객 미팅 관련 이메일",
                "원본 이메일 또는 메시지 내용",
                "CREATED"
        );
    }
}
