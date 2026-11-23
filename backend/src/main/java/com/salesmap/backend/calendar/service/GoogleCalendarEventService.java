package com.salesmap.backend.calendar.service;

import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.calendar.client.GoogleCalendarClient;
import com.salesmap.backend.calendar.config.GoogleCalendarProperties;
import com.salesmap.backend.calendar.dto.GoogleCalendarEventRequest;
import com.salesmap.backend.calendar.dto.GoogleCalendarEventResponse;
import com.salesmap.backend.calendar.exception.GoogleCalendarEventConflictException;
import com.salesmap.backend.demo.service.DemoMailbox;
import com.salesmap.backend.gmail.service.GmailIntegrationService;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleMemoMarker;
import com.salesmap.backend.schedule.entity.ScheduleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class GoogleCalendarEventService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarEventService.class);
    private static final String EVENT_ID_PREFIX = "salesmap";

    private final GoogleCalendarProperties properties;
    private final GmailIntegrationService gmailIntegrationService;
    private final GoogleCalendarClient googleCalendarClient;
    private final DemoMailbox demoMailbox;

    public GoogleCalendarEventService(
            GoogleCalendarProperties properties,
            GmailIntegrationService gmailIntegrationService,
            GoogleCalendarClient googleCalendarClient,
            DemoMailbox demoMailbox
    ) {
        this.properties = properties;
        this.gmailIntegrationService = gmailIntegrationService;
        this.googleCalendarClient = googleCalendarClient;
        this.demoMailbox = demoMailbox;
    }

    // 데모 모드의 일정은 앱 안에서만 관리하고 실제 Google Calendar에는 쓰지 않는다.
    private boolean skipsGoogleCalendar(Long userId) {
        return !properties.isEnabled() || demoMailbox.isDemoUser(userId);
    }

    public GoogleCalendarEventResponse createEventForApprovedSchedule(
            Analysis analysis,
            Schedule schedule,
            Long authenticatedUserId
    ) {
        if (skipsGoogleCalendar(authenticatedUserId) || schedule == null || schedule.getStatus() == ScheduleStatus.CANCELED) {
            return null;
        }

        String accessToken = gmailIntegrationService.getValidAccessTokenForUser(authenticatedUserId);
        LocalDateTime startDateTime = schedule.getScheduleDateTime();
        LocalDateTime endDateTime = startDateTime.plusHours(1);
        GoogleCalendarEventRequest request = new GoogleCalendarEventRequest(
                schedule.getTitle(),
                buildDescription(analysis, schedule),
                startDateTime,
                endDateTime
        );
        String eventId = eventIdFor(schedule);

        try {
            return googleCalendarClient.insertEvent(accessToken, eventId, request);
        } catch (GoogleCalendarEventConflictException conflict) {
            return reuseExistingEvent(accessToken, eventId, schedule, request);
        }
    }

    // Google 이벤트 ID는 a-v, 0-9만 허용한다. 같은 일정은 항상 같은 ID라 재시도해도 이벤트가 늘지 않는다.
    private String eventIdFor(Schedule schedule) {
        return schedule.getId() == null ? null : EVENT_ID_PREFIX + schedule.getId();
    }

    private GoogleCalendarEventResponse reuseExistingEvent(
            String accessToken,
            String eventId,
            Schedule schedule,
            GoogleCalendarEventRequest request
    ) {
        GoogleCalendarEventResponse existing = googleCalendarClient.getEvent(accessToken, eventId);
        if (existing == null || "cancelled".equalsIgnoreCase(existing.status())) {
            log.warn("Google event {} is cancelled; schedule {} stays unregistered", eventId, schedule.getId());
            return null;
        }
        // DB를 초기화해 일정 번호가 다시 쓰이면 같은 ID가 다른 일정의 이벤트일 수 있으므로 덮어쓰지 않는다.
        if (existing.summary() == null || !existing.summary().trim().equals(schedule.getTitle().trim())) {
            log.warn("Google event {} does not match schedule {}; left untouched", eventId, schedule.getId());
            return null;
        }
        return googleCalendarClient.updateEvent(accessToken, eventId, request);
    }

    public GoogleCalendarEventResponse updateEventForSchedule(Schedule schedule, Long authenticatedUserId) {
        if (skipsGoogleCalendar(authenticatedUserId) || schedule == null || !schedule.hasGoogleCalendarEvent()) {
            return null;
        }

        String accessToken = gmailIntegrationService.getValidAccessTokenForUser(authenticatedUserId);
        LocalDateTime startDateTime = schedule.getScheduleDateTime();
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        return googleCalendarClient.updateEvent(
                accessToken,
                schedule.getGoogleCalendarEventId(),
                new GoogleCalendarEventRequest(
                        schedule.getTitle(),
                        buildDescription(schedule.getAnalysis(), schedule),
                        startDateTime,
                        endDateTime
                )
        );
    }

    public void deleteEventForSchedule(Schedule schedule, Long authenticatedUserId) {
        if (skipsGoogleCalendar(authenticatedUserId) || schedule == null || !schedule.hasGoogleCalendarEvent()) {
            return;
        }

        String accessToken = gmailIntegrationService.getValidAccessTokenForUser(authenticatedUserId);
        googleCalendarClient.deleteEvent(accessToken, schedule.getGoogleCalendarEventId());
    }

    private String buildDescription(Analysis analysis, Schedule schedule) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "출처", "SALESMAP 활동 자동화 AI Agent");
        if (analysis != null) {
            appendLine(builder, "분석 번호", analysis.getId());
            appendLine(builder, "고객사", analysis.getCustomerName());
            appendLine(builder, "제품", analysis.getProductName());
            appendLine(builder, "참석자", analysis.getAttendees());
            appendLine(builder, "다음 행동", analysis.getFollowUpAction());
            appendLine(builder, "요약", analysis.getSummary());
        }
        appendLine(builder, "일정 메모", ScheduleMemoMarker.strip(schedule.getMemo()));
        return builder.toString().trim();
    }

    private void appendLine(StringBuilder builder, String label, Object value) {
        if (value == null || value.toString().isBlank()) {
            return;
        }

        builder.append(label).append(": ").append(value).append('\n');
    }
}
