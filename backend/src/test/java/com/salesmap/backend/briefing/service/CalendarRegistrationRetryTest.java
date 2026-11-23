package com.salesmap.backend.briefing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.briefing.dto.TaskScheduleRegistrationRequest;
import com.salesmap.backend.calendar.dto.GoogleCalendarEventResponse;
import com.salesmap.backend.calendar.service.GoogleCalendarEventService;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleOutcome;
import com.salesmap.backend.schedule.entity.ScheduleStatus;
import com.salesmap.backend.schedule.repository.ScheduleRepository;
import com.salesmap.backend.task.TaskEntry;
import com.salesmap.backend.task.TaskRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CalendarRegistrationRetryTest {
    private static final LocalDateTime FIRST = LocalDateTime.of(2026, 12, 1, 10, 0);
    private static final LocalDateTime SECOND = LocalDateTime.of(2026, 12, 2, 15, 0);

    private final TaskRepository tasks = mock(TaskRepository.class);
    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GoogleCalendarEventService calendar = mock(GoogleCalendarEventService.class);
    private final CalendarRegistrationService service = new CalendarRegistrationService(
            new CalendarSchedulePersistenceService(tasks, schedules, users, new ObjectMapper()), calendar);
    private User user;
    private Schedule existing;

    @BeforeEach
    void setUp() {
        TaskEntry entry = new TaskEntry(7L, "mn_001", "retry", """
                {"taskId":"t_1","noteId":"mn_001","content":"가짜 준비","dueDate":null,
                "dueDateIsDefault":false,"assignee":"user_001","counterpartPersonId":null,
                "commitmentKind":"IMPLICIT","taskType":"NONE","taskTypeConfidence":0.5,
                "taskTypeSource":"HUMAN","evidence":"가짜 근거","status":"CONFIRMED"}
                """);
        user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        existing = new Schedule(user, null, "가짜 준비", FIRST, "[SALESMAP_TASK:t_1]\n가짜 근거", null,
                ScheduleStatus.SCHEDULED);
        ReflectionTestUtils.setField(existing, "id", 12L);
        when(tasks.lockOwned(1L, 7L)).thenReturn(Optional.of(entry));
        when(schedules.findByUserId(7L)).thenReturn(List.of(existing));
        when(schedules.findById(anyLong())).thenReturn(Optional.of(existing));
        when(schedules.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void retryOfUnlinkedScheduleUsesNewlySelectedTime() {
        when(calendar.createEventForApprovedSchedule(any(), eq(existing), eq(7L)))
                .thenReturn(new GoogleCalendarEventResponse("salesmap12", null, "confirmed", "가짜 준비"));

        var response = service.register(request(SECOND), 7L);

        assertThat(response.scheduleDateTime()).isEqualTo(SECOND);
        assertThat(response.googleCalendarEventId()).isEqualTo("salesmap12");
    }

    @Test
    void linkedScheduleKeepsItsTimeAndDoesNotCallGoogle() {
        existing.linkGoogleCalendarEvent("salesmap12");

        var response = service.register(request(SECOND), 7L);

        assertThat(response.scheduleDateTime()).isEqualTo(FIRST);
        verify(calendar, never()).createEventForApprovedSchedule(any(), any(), any());
    }

    @Test
    void completedScheduleIsNotReopenedOrSentToGoogle() {
        existing.recordOutcome(ScheduleOutcome.values()[0]);

        var response = service.register(request(SECOND), 7L);

        assertThat(response.status()).isEqualTo(ScheduleStatus.COMPLETED);
        assertThat(response.scheduleDateTime()).isEqualTo(FIRST);
        verify(calendar, never()).createEventForApprovedSchedule(any(), any(), any());
    }

    @Test
    void cancelledGoogleEventLeavesScheduleUnlinked() {
        when(calendar.createEventForApprovedSchedule(any(), eq(existing), eq(7L))).thenReturn(null);

        var response = service.register(request(FIRST), 7L);

        assertThat(response.googleCalendarEventId()).isNull();
    }

    private TaskScheduleRegistrationRequest request(LocalDateTime time) {
        return new TaskScheduleRegistrationRequest("t_1", time);
    }
}
