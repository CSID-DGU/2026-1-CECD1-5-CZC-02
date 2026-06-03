package com.salesmap.backend.calendar.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.calendar.config.GoogleCalendarProperties;
import com.salesmap.backend.calendar.dto.GoogleCalendarEventRequest;
import com.salesmap.backend.calendar.dto.GoogleCalendarEventResponse;
import com.salesmap.backend.calendar.exception.GoogleCalendarClientException;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class HttpGoogleCalendarClient implements GoogleCalendarClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final GoogleCalendarProperties properties;

    public HttpGoogleCalendarClient(GoogleCalendarProperties properties, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(createRequestFactory(properties))
                .build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public GoogleCalendarEventResponse insertEvent(String accessToken, GoogleCalendarEventRequest request) {
        try {
            String response = restClient.post()
                    .uri("/calendars/{calendarId}/events", properties.getCalendarId())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(toGoogleEventBody(request))
                    .retrieve()
                    .onStatus(status -> status.isError(), (httpRequest, httpResponse) -> {
                        throw toGoogleCalendarClientException(httpResponse);
                    })
                    .body(String.class);

            return parseEventResponse(response);
        } catch (GoogleCalendarClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new GoogleCalendarClientException("Google Calendar API 호출에 실패했습니다.", exception);
        }
    }

    @Override
    public GoogleCalendarEventResponse updateEvent(String accessToken, String eventId, GoogleCalendarEventRequest request) {
        try {
            String response = restClient.patch()
                    .uri("/calendars/{calendarId}/events/{eventId}", properties.getCalendarId(), eventId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(toGoogleEventBody(request))
                    .retrieve()
                    .onStatus(status -> status.isError(), (httpRequest, httpResponse) -> {
                        throw toGoogleCalendarClientException(httpResponse);
                    })
                    .body(String.class);

            return parseEventResponse(response);
        } catch (GoogleCalendarClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new GoogleCalendarClientException("Google Calendar 이벤트 수정에 실패했습니다.", exception);
        }
    }

    @Override
    public void deleteEvent(String accessToken, String eventId) {
        try {
            restClient.delete()
                    .uri("/calendars/{calendarId}/events/{eventId}", properties.getCalendarId(), eventId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(status -> status.isError(), (httpRequest, httpResponse) -> {
                        throw toGoogleCalendarClientException(httpResponse);
                    })
                    .toBodilessEntity();
        } catch (GoogleCalendarClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new GoogleCalendarClientException("Google Calendar 이벤트 삭제에 실패했습니다.", exception);
        }
    }

    private Map<String, Object> toGoogleEventBody(GoogleCalendarEventRequest request) {
        return Map.of(
                "summary", nullToDefault(request.summary(), "Salesmap 일정"),
                "description", nullToDefault(request.description(), ""),
                "start", Map.of(
                        "dateTime", toOffsetDateTime(request.startDateTime()),
                        "timeZone", properties.getTimeZone()
                ),
                "end", Map.of(
                        "dateTime", toOffsetDateTime(request.endDateTime()),
                        "timeZone", properties.getTimeZone()
                )
        );
    }

    private String toOffsetDateTime(LocalDateTime dateTime) {
        return dateTime
                .atZone(ZoneId.of(properties.getTimeZone()))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private GoogleCalendarEventResponse parseEventResponse(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            return new GoogleCalendarEventResponse(
                    optionalText(node, "id"),
                    optionalText(node, "htmlLink"),
                    optionalText(node, "status"),
                    optionalText(node, "summary")
            );
        } catch (JsonProcessingException exception) {
            throw new GoogleCalendarClientException("Google Calendar 응답을 해석하지 못했습니다.", exception);
        }
    }

    private GoogleCalendarClientException toGoogleCalendarClientException(ClientHttpResponse response) {
        try {
            JsonNode errorResponse = objectMapper.readTree(response.getBody());
            String message = errorResponse.path("error").path("message").asText("Google Calendar API 오류가 발생했습니다.");
            return new GoogleCalendarClientException("Google Calendar API 오류: " + message, errorResponse);
        } catch (IOException exception) {
            return new GoogleCalendarClientException("Google Calendar API 오류 응답을 처리하지 못했습니다.", exception);
        }
    }

    private ClientHttpRequestFactory createRequestFactory(GoogleCalendarProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return requestFactory;
    }

    private String optionalText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }

        return value.asText();
    }

    private String nullToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
