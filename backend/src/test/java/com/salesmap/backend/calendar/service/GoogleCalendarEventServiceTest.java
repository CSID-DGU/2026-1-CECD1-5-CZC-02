package com.salesmap.backend.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.salesmap.backend.calendar.client.GoogleCalendarClient;
import com.salesmap.backend.calendar.config.GoogleCalendarProperties;
import com.salesmap.backend.calendar.dto.GoogleCalendarEventRequest;
import com.salesmap.backend.calendar.dto.GoogleCalendarEventResponse;
import com.salesmap.backend.calendar.exception.GoogleCalendarEventConflictException;
import com.salesmap.backend.demo.service.DemoMailbox;
import com.salesmap.backend.gmail.service.GmailIntegrationService;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleStatus;
import com.salesmap.backend.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class GoogleCalendarEventServiceTest {
    private static final String EVENT_ID = "salesmap12";

    private final GoogleCalendarProperties properties = mock(GoogleCalendarProperties.class);
    private final GmailIntegrationService gmail = mock(GmailIntegrationService.class);
    private final GoogleCalendarClient client = mock(GoogleCalendarClient.class);
    private final DemoMailbox demo = mock(DemoMailbox.class);
    private final GoogleCalendarEventService service =
            new GoogleCalendarEventService(properties, gmail, client, demo);
    private Schedule schedule;

    @BeforeEach
    void setUp() {
        when(properties.isEnabled()).thenReturn(true);
        when(gmail.getValidAccessTokenForUser(7L)).thenReturn("fake-token");
        schedule = new Schedule(mock(User.class), null, "가짜 준비", LocalDateTime.of(2026, 12, 1, 10, 0),
                "[SALESMAP_TASK:t_1]\n가짜 근거", null, ScheduleStatus.SCHEDULED);
        ReflectionTestUtils.setField(schedule, "id", 12L);
    }

    @Test
    void insertsWithDeterministicIdAndHidesInternalMarker() {
        service.createEventForApprovedSchedule(null, schedule, 7L);

        ArgumentCaptor<String> eventId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<GoogleCalendarEventRequest> request = ArgumentCaptor.forClass(GoogleCalendarEventRequest.class);
        verify(client).insertEvent(eq("fake-token"), eventId.capture(), request.capture());
        assertThat(eventId.getValue()).isEqualTo(EVENT_ID).matches("[a-v0-9]{5,}");
        assertThat(request.getValue().description()).contains("가짜 근거").doesNotContain("SALESMAP_TASK");
    }

    @Test
    void conflictWithLiveEventUpdatesAndReturnsThatEvent() {
        GoogleCalendarEventResponse updated = event("confirmed", "가짜 준비");
        when(client.insertEvent(any(), any(), any())).thenThrow(new GoogleCalendarEventConflictException("409"));
        when(client.getEvent("fake-token", EVENT_ID)).thenReturn(event("confirmed", "가짜 준비"));
        when(client.updateEvent(eq("fake-token"), eq(EVENT_ID), any())).thenReturn(updated);

        assertThat(service.createEventForApprovedSchedule(null, schedule, 7L)).isSameAs(updated);
    }

    @Test
    void conflictWithCancelledEventIsNotReportedAsRegistered() {
        when(client.insertEvent(any(), any(), any())).thenThrow(new GoogleCalendarEventConflictException("409"));
        when(client.getEvent("fake-token", EVENT_ID)).thenReturn(event("cancelled", "가짜 준비"));

        assertThat(service.createEventForApprovedSchedule(null, schedule, 7L)).isNull();
        verify(client, never()).updateEvent(any(), any(), any());
    }

    @Test
    void conflictWithDifferentEventIsLeftUntouched() {
        when(client.insertEvent(any(), any(), any())).thenThrow(new GoogleCalendarEventConflictException("409"));
        when(client.getEvent("fake-token", EVENT_ID)).thenReturn(event("confirmed", "다른 가짜 일정"));

        assertThat(service.createEventForApprovedSchedule(null, schedule, 7L)).isNull();
        verify(client, never()).updateEvent(any(), any(), any());
    }

    private GoogleCalendarEventResponse event(String status, String summary) {
        return new GoogleCalendarEventResponse(EVENT_ID, null, status, summary);
    }
}
