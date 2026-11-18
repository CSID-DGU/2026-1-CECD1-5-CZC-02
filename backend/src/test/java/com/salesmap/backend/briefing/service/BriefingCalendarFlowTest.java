package com.salesmap.backend.briefing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.calendar.service.GoogleCalendarEventService;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.repository.ScheduleRepository;
import com.salesmap.backend.task.TaskEntry;
import com.salesmap.backend.task.TaskRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BriefingCalendarFlowTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GoogleCalendarEventService calendar = mock(GoogleCalendarEventService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final BriefingService briefings = new BriefingService(tasks, mapper);
    private final CalendarRegistrationService registrations = new CalendarRegistrationService(
            tasks, schedules, users, calendar, mapper);

    @Test
    void preparationTaskIdCanBePassedToCalendarRegistration() {
        TaskEntry entry = new TaskEntry(7L, "mn_001", "task-1", """
                {"taskId":"t_1","noteId":"mn_001","content":"가상전자 방문 준비",
                "dueDate":"2026-11-20","dueDateIsDefault":false,"assignee":"user_001",
                "counterpartPersonId":null,"commitmentKind":"EXPLICIT","taskType":"VISIT_PREP",
                "taskTypeConfidence":1.0,"taskTypeSource":"HUMAN","evidence":"가상전자 방문 준비",
                "status":"CONFIRMED"}
                """);
        ReflectionTestUtils.setField(entry, "id", 1L);
        User user = mock(User.class);
        when(tasks.findByOwnerIdOrderByIdDesc(7L)).thenReturn(List.of(entry));
        when(tasks.lockOwned(1L, 7L)).thenReturn(Optional.of(entry));
        when(schedules.findByUserId(7L)).thenReturn(List.of());
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(schedules.save(any(Schedule.class))).thenAnswer(invocation -> {
            Schedule schedule = invocation.getArgument(0);
            ReflectionTestUtils.setField(schedule, "id", 21L);
            return schedule;
        });

        var preparation = briefings.preparation(7L, LocalDate.of(2026, 11, 19));
        var item = preparation.items().get(0);
        var registered = registrations.register(
                new com.salesmap.backend.briefing.dto.TaskScheduleRegistrationRequest(
                        item.taskId(), LocalDateTime.of(2026, 11, 20, 14, 0)),
                7L);

        assertThat(item.taskId()).isEqualTo("t_1");
        assertThat(registered.title()).isEqualTo(item.content());
        assertThat(registered.scheduleDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 20, 14, 0));
    }
}

