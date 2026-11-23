package com.salesmap.backend.briefing.service;

import com.salesmap.backend.briefing.dto.TaskScheduleRegistrationRequest;
import com.salesmap.backend.calendar.dto.GoogleCalendarEventResponse;
import com.salesmap.backend.calendar.service.GoogleCalendarEventService;
import com.salesmap.backend.schedule.dto.ScheduleResponse;
import com.salesmap.backend.schedule.entity.Schedule;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CalendarRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(CalendarRegistrationService.class);

    private final CalendarSchedulePersistenceService persistence;
    private final GoogleCalendarEventService calendar;

    public CalendarRegistrationService(
            CalendarSchedulePersistenceService persistence,
            GoogleCalendarEventService calendar
    ) {
        this.persistence = persistence;
        this.calendar = calendar;
    }

    public ScheduleResponse register(TaskScheduleRegistrationRequest request, Long ownerUserId) {
        Schedule saved = persistence.createOrFind(request, ownerUserId);
        if (saved.hasGoogleCalendarEvent() || !saved.isActive()) return ScheduleResponse.from(saved);

        try {
            GoogleCalendarEventResponse event = calendar.createEventForApprovedSchedule(null, saved, ownerUserId);
            if (event != null && event.id() != null && !event.id().isBlank()) {
                saved = persistence.linkCalendarEvent(saved.getId(), event.id());
            }
        } catch (RuntimeException exception) {
            // The schedule is intentionally kept; a null event id signals unregistered state to the client.
            log.warn("Calendar registration failed after schedule {} was saved", saved.getId(), exception);
        }
        return ScheduleResponse.from(saved);
    }
}
