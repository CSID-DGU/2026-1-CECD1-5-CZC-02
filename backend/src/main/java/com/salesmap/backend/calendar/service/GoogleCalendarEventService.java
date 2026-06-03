package com.salesmap.backend.calendar.service;

import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.calendar.client.GoogleCalendarClient;
import com.salesmap.backend.calendar.config.GoogleCalendarProperties;
import com.salesmap.backend.calendar.dto.GoogleCalendarEventRequest;
import com.salesmap.backend.calendar.dto.GoogleCalendarEventResponse;
import com.salesmap.backend.gmail.service.GmailIntegrationService;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class GoogleCalendarEventService {

    private final GoogleCalendarProperties properties;
    private final GmailIntegrationService gmailIntegrationService;
    private final GoogleCalendarClient googleCalendarClient;

    public GoogleCalendarEventService(
            GoogleCalendarProperties properties,
            GmailIntegrationService gmailIntegrationService,
            GoogleCalendarClient googleCalendarClient
    ) {
        this.properties = properties;
        this.gmailIntegrationService = gmailIntegrationService;
        this.googleCalendarClient = googleCalendarClient;
    }

    public GoogleCalendarEventResponse createEventForApprovedSchedule(
            Analysis analysis,
            Schedule schedule,
            Long authenticatedUserId
    ) {
        if (!properties.isEnabled() || schedule == null || schedule.getStatus() == ScheduleStatus.CANCELED) {
            return null;
        }

        String accessToken = gmailIntegrationService.getValidAccessTokenForUser(authenticatedUserId);
        LocalDateTime startDateTime = schedule.getScheduleDateTime();
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        return googleCalendarClient.insertEvent(
                accessToken,
                new GoogleCalendarEventRequest(
                        schedule.getTitle(),
                        buildDescription(analysis, schedule),
                        startDateTime,
                        endDateTime
                )
        );
    }

    public GoogleCalendarEventResponse updateEventForSchedule(Schedule schedule, Long authenticatedUserId) {
        if (!properties.isEnabled() || schedule == null || !schedule.hasGoogleCalendarEvent()) {
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
        if (!properties.isEnabled() || schedule == null || !schedule.hasGoogleCalendarEvent()) {
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
        appendLine(builder, "일정 메모", schedule.getMemo());
        return builder.toString().trim();
    }

    private void appendLine(StringBuilder builder, String label, Object value) {
        if (value == null || value.toString().isBlank()) {
            return;
        }

        builder.append(label).append(": ").append(value).append('\n');
    }
}
