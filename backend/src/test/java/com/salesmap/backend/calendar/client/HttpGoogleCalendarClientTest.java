package com.salesmap.backend.calendar.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.calendar.config.GoogleCalendarProperties;
import com.salesmap.backend.calendar.dto.GoogleCalendarEventRequest;
import com.salesmap.backend.calendar.exception.GoogleCalendarEventConflictException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpGoogleCalendarClientTest {
    private static final String EVENTS_URL = "https://calendar.test/calendars/primary/events";
    private static final GoogleCalendarEventRequest REQUEST = new GoogleCalendarEventRequest(
            "가짜 준비", "가짜 설명", LocalDateTime.of(2026, 12, 1, 10, 0), LocalDateTime.of(2026, 12, 1, 11, 0));

    private HttpGoogleCalendarClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        GoogleCalendarProperties properties = mock(GoogleCalendarProperties.class);
        when(properties.getBaseUrl()).thenReturn("https://calendar.test");
        when(properties.getCalendarId()).thenReturn("primary");
        when(properties.getTimeZone()).thenReturn("Asia/Seoul");
        when(properties.getConnectTimeoutMs()).thenReturn(1000);
        when(properties.getReadTimeoutMs()).thenReturn(1000);
        client = new HttpGoogleCalendarClient(properties, new ObjectMapper());
        RestClient.Builder builder = RestClient.builder().baseUrl("https://calendar.test");
        server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(client, "restClient", builder.build());
    }

    @Test
    void insertSendsClientEventId() {
        server.expect(requestTo(EVENTS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.id").value("salesmap12"))
                .andRespond(withSuccess("{\"id\":\"salesmap12\",\"status\":\"confirmed\"}", MediaType.APPLICATION_JSON));

        assertThat(client.insertEvent("fake-token", "salesmap12", REQUEST).id()).isEqualTo("salesmap12");
        server.verify();
    }

    @Test
    void insertConflictBecomesConflictException() {
        server.expect(requestTo(EVENTS_URL))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"duplicate\"}}"));

        assertThatThrownBy(() -> client.insertEvent("fake-token", "salesmap12", REQUEST))
                .isInstanceOf(GoogleCalendarEventConflictException.class);
    }

    @Test
    void getReadsCancelledStatus() {
        server.expect(requestTo(EVENTS_URL + "/salesmap12"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"salesmap12\",\"status\":\"cancelled\"}", MediaType.APPLICATION_JSON));

        assertThat(client.getEvent("fake-token", "salesmap12").status()).isEqualTo("cancelled");
    }
}
