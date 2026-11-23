package com.salesmap.backend.briefing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.calendar.service.GoogleCalendarEventService;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.repository.ScheduleRepository;
import com.salesmap.backend.task.TaskEntry;
import com.salesmap.backend.task.TaskRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import com.salesmap.backend.briefing.dto.TaskScheduleRegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalendarRegistrationServiceTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GoogleCalendarEventService calendar = mock(GoogleCalendarEventService.class);
    private final CalendarSchedulePersistenceService persistence =
            new CalendarSchedulePersistenceService(tasks, schedules, users, new ObjectMapper());
    private final CalendarRegistrationService service = new CalendarRegistrationService(
            persistence, calendar);

    @Test
    void registersOnlyConfirmedTaskWithHumanSelectedTime() {
        TaskEntry entry = new TaskEntry(7L, "mn_001", "draft-1", """
                {"taskId":"t_1","noteId":"mn_001","content":"계약서 초안 송부","dueDate":"2026-11-20",
                "dueDateIsDefault":false,"assignee":"user_001","counterpartPersonId":null,
                "commitmentKind":"EXPLICIT","taskType":"CONTRACT_PREP","taskTypeConfidence":1.0,
                "taskTypeSource":"HUMAN","evidence":"계약서 초안을 보내기로 함","status":"CONFIRMED"}
                """);
        User user = mock(User.class);
        when(tasks.lockOwned(1L, 7L)).thenReturn(Optional.of(entry));
        when(schedules.findByUserId(7L)).thenReturn(List.of());
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(schedules.save(any(Schedule.class))).thenAnswer(invocation -> {
            Schedule schedule = invocation.getArgument(0);
            ReflectionTestUtils.setField(schedule, "id", 11L);
            return schedule;
        });

        var response = service.register(
                new TaskScheduleRegistrationRequest("t_1", LocalDateTime.of(2026, 11, 20, 10, 0)), 7L);

        assertThat(response.title()).isEqualTo("계약서 초안 송부");
        assertThat(response.scheduleDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 20, 10, 0));
        verify(schedules).save(any(Schedule.class));
    }

    @Test
    void rejectsDraftTaskBeforeCreatingSchedule() {
        TaskEntry entry = new TaskEntry(7L, "mn_001", "draft-1", """
                {"taskId":"t_1","noteId":"mn_001","content":"초안","dueDate":null,
                "dueDateIsDefault":false,"assignee":"user_001","counterpartPersonId":null,
                "commitmentKind":"IMPLICIT","taskType":"NONE","taskTypeConfidence":0.5,
                "taskTypeSource":"MODEL","evidence":"검토하기로 함","status":"DRAFT"}
                """);
        when(tasks.lockOwned(1L, 7L)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.register(
                new TaskScheduleRegistrationRequest("t_1", LocalDateTime.of(2026, 11, 20, 10, 0)), 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    void keepsSavedScheduleWhenExternalCalendarRegistrationFails() {
        TaskEntry entry = new TaskEntry(7L, "mn_001", "draft-1", """
                {"taskId":"t_1","noteId":"mn_001","content":"준비","dueDate":null,
                "dueDateIsDefault":false,"assignee":"user_001","counterpartPersonId":null,
                "commitmentKind":"IMPLICIT","taskType":"NONE","taskTypeConfidence":0.5,
                "taskTypeSource":"HUMAN","evidence":"원문 근거","status":"CONFIRMED"}
                """);
        User user = mock(User.class);
        when(tasks.lockOwned(1L, 7L)).thenReturn(Optional.of(entry));
        when(schedules.findByUserId(7L)).thenReturn(List.of());
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(schedules.save(any(Schedule.class))).thenAnswer(invocation -> {
            Schedule schedule = invocation.getArgument(0);
            ReflectionTestUtils.setField(schedule, "id", 12L);
            return schedule;
        });
        when(calendar.createEventForApprovedSchedule(any(), any(), any()))
                .thenThrow(new IllegalStateException("calendar unavailable"));

        var response = service.register(
                new TaskScheduleRegistrationRequest("t_1", LocalDateTime.of(2026, 11, 20, 10, 0)), 7L);

        assertThat(response.googleCalendarEventId()).isNull();
        verify(schedules).save(any(Schedule.class));
    }
}
