package com.salesmap.backend.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.ai.config.AiModuleProperties;
import com.salesmap.backend.ai.dto.AiAnalysisRequest;
import com.salesmap.backend.ai.dto.AiAnalysisResponse;
import com.salesmap.backend.ai.dto.AiErrorResponse;
import com.salesmap.backend.ai.dto.AiGroupAnalysisRequest;
import com.salesmap.backend.ai.exception.AiClientException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "ai.module.mode", havingValue = "http")
public class HttpAiClient implements AiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpAiClient(AiModuleProperties aiModuleProperties, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(aiModuleProperties.getBaseUrl())
                .requestFactory(createRequestFactory(aiModuleProperties))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AiAnalysisResponse analyze(AiAnalysisRequest request) {
        return postAnalyze(request);
    }

    @Override
    public AiAnalysisResponse analyzeGroup(AiGroupAnalysisRequest request) {
        return postAnalyze(request);
    }

    private AiAnalysisResponse postAnalyze(Object request) {
        try {
            AiAnalysisResponse response = restClient.post()
                    .uri("/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), (httpRequest, httpResponse) -> {
                        throw toAiClientException(httpResponse);
                    })
                    .body(AiAnalysisResponse.class);

            if (response == null) {
                throw new AiClientException("AI Module 응답이 비어 있습니다.");
            }

            return response;
        } catch (AiClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AiClientException("AI Module 호출에 실패했습니다.", exception);
        }
    }

    private SimpleClientHttpRequestFactory createRequestFactory(AiModuleProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return requestFactory;
    }

    private AiClientException toAiClientException(ClientHttpResponse response) {
        try {
            AiErrorResponse errorResponse = objectMapper.readValue(response.getBody(), AiErrorResponse.class);
            return new AiClientException(
                    "AI Module 오류: " + errorResponse.message(),
                    errorResponse
            );
        } catch (IOException exception) {
            return new AiClientException("AI Module 오류 응답을 처리할 수 없습니다.", exception);
        }
    }
}
