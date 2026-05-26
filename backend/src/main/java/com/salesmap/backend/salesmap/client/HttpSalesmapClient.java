package com.salesmap.backend.salesmap.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.salesmap.client.dto.SalesmapApiErrorResponse;
import com.salesmap.backend.salesmap.client.dto.SalesmapApiRegisterRequest;
import com.salesmap.backend.salesmap.client.dto.SalesmapApiRegisterResponse;
import com.salesmap.backend.salesmap.config.SalesmapApiProperties;
import com.salesmap.backend.salesmap.exception.SalesmapClientException;
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
@ConditionalOnProperty(name = "salesmap.api.mode", havingValue = "http")
public class HttpSalesmapClient implements SalesmapClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SalesmapApiProperties salesmapApiProperties;

    public HttpSalesmapClient(SalesmapApiProperties salesmapApiProperties, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(salesmapApiProperties.getBaseUrl())
                .requestFactory(createRequestFactory(salesmapApiProperties))
                .build();
        this.objectMapper = objectMapper;
        this.salesmapApiProperties = salesmapApiProperties;
    }

    @Override
    public SalesmapApiRegisterResponse register(SalesmapApiRegisterRequest request) {
        String requestPayload = toJson(request);

        try {
            SalesmapApiRegisterResponse response = restClient.post()
                    .uri(salesmapApiProperties.getRegisterPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), (httpRequest, httpResponse) -> {
                        throw toSalesmapClientException(httpResponse);
                    })
                    .body(SalesmapApiRegisterResponse.class);

            if (response == null) {
                throw new SalesmapClientException("Salesmap API 응답이 비어 있습니다.");
            }

            return response
                    .withRequestPayloadIfBlank(requestPayload)
                    .withResponsePayloadIfBlank(toJson(response));
        } catch (SalesmapClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new SalesmapClientException("Salesmap API 호출에 실패했습니다.", exception);
        }
    }

    private SimpleClientHttpRequestFactory createRequestFactory(SalesmapApiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return requestFactory;
    }

    private SalesmapClientException toSalesmapClientException(ClientHttpResponse response) {
        try {
            SalesmapApiErrorResponse errorResponse = objectMapper.readValue(response.getBody(), SalesmapApiErrorResponse.class);
            return new SalesmapClientException(
                    "Salesmap API 오류: " + errorResponse.message(),
                    errorResponse
            );
        } catch (IOException exception) {
            return new SalesmapClientException("Salesmap API 오류 응답을 처리할 수 없습니다.", exception);
        }
    }

    private String toJson(SalesmapApiRegisterRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new SalesmapClientException("Salesmap 요청 payload 생성에 실패했습니다.", exception);
        }
    }

    private String toJson(SalesmapApiRegisterResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new SalesmapClientException("Salesmap 응답 payload 생성에 실패했습니다.", exception);
        }
    }
}
