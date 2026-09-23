package com.salesmap.backend.salesmap.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.salesmap.client.dto.SalesmapApiRegisterRequest;
import com.salesmap.backend.salesmap.client.dto.SalesmapApiRegisterResponse;
import com.salesmap.backend.salesmap.exception.SalesmapClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "salesmap.api.mode", havingValue = "mock", matchIfMissing = true)
public class MockSalesmapClient implements SalesmapClient {

    private static final Logger log = LoggerFactory.getLogger(MockSalesmapClient.class);

    private final ObjectMapper objectMapper;

    public MockSalesmapClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        log.warn("[SALESMAP] salesmap.api.mode=mock — Salesmap API를 호출하지 않고 등록 결과를 가짜로 기록합니다. "
                + "현재 Salesmap 반영은 Google Calendar 양방향 연동으로 이루어집니다.");
    }

    @Override
    public SalesmapApiRegisterResponse register(SalesmapApiRegisterRequest request) {
        String externalRecordId = "mock-salesmap-" + request.analysisId();
        String requestPayload = toJson(request);
        String responsePayload = "{\"externalRecordId\":\"" + externalRecordId + "\",\"status\":\"REGISTERED\"}";

        return new SalesmapApiRegisterResponse(
                externalRecordId,
                "REGISTERED",
                requestPayload,
                responsePayload,
                LocalDateTime.now()
        );
    }

    private String toJson(SalesmapApiRegisterRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new SalesmapClientException("Salesmap 요청 payload 생성에 실패했습니다.", exception);
        }
    }
}
