package com.salesmap.backend.salesmap.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.salesmap.client.dto.SalesmapApiRegisterRequest;
import com.salesmap.backend.salesmap.client.dto.SalesmapApiRegisterResponse;
import com.salesmap.backend.salesmap.exception.SalesmapClientException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "salesmap.api.mode", havingValue = "mock", matchIfMissing = true)
public class MockSalesmapClient implements SalesmapClient {

    private final ObjectMapper objectMapper;

    public MockSalesmapClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
