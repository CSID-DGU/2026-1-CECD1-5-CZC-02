package com.salesmap.backend.briefing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.briefing.dto.TaskScheduleRegistrationRequest;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleStatus;
import com.salesmap.backend.schedule.repository.ScheduleRepository;
import com.salesmap.backend.task.TaskDocument;
import com.salesmap.backend.task.TaskEntry;
import com.salesmap.backend.task.TaskRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/** Persists a calendar registration before any external calendar call is made. */
@Service
public class CalendarSchedulePersistenceService {
    private static final String TASK_MARKER_PREFIX = "[SALESMAP_TASK:";

    private final TaskRepository tasks;
    private final ScheduleRepository schedules;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public CalendarSchedulePersistenceService(
            TaskRepository tasks,
            ScheduleRepository schedules,
            UserRepository users,
            ObjectMapper mapper
    ) {
        this.tasks = tasks;
        this.schedules = schedules;
        this.users = users;
        this.mapper = mapper;
    }

    @Transactional
    public Schedule createOrFind(TaskScheduleRegistrationRequest request, Long ownerUserId) {
        if (request == null || ownerUserId == null) {
            throw new IllegalArgumentException("task and owner are required");
        }

        Long taskPk = parseTaskId(request.taskId());
        TaskEntry entry = tasks.lockOwned(taskPk, ownerUserId)
                .orElseThrow(() -> new NoSuchElementException("task not found"));
        TaskDocument task = parse(entry);
        if (!"CONFIRMED".equals(task.status())) {
            throw new IllegalArgumentException("only CONFIRMED tasks can be registered");
        }

        String marker = marker(request.taskId());
        Schedule existing = schedules.findByUserId(ownerUserId).stream()
                .filter(schedule -> schedule.getMemo() != null && schedule.getMemo().contains(marker))
                .findFirst()
                .orElse(null);
        if (existing != null) return existing;

        User user = users.findById(ownerUserId)
                .orElseThrow(() -> new NoSuchElementException("owner user not found"));
        Schedule schedule = new Schedule(
                user,
                null,
                task.content(),
                request.scheduleDateTime(),
                marker + "\n" + task.evidence(),
                null,
                ScheduleStatus.SCHEDULED
        );
        return schedules.save(schedule);
    }

    @Transactional
    public Schedule linkCalendarEvent(Long scheduleId, String eventId) {
        Schedule schedule = schedules.findById(scheduleId)
                .orElseThrow(() -> new NoSuchElementException("schedule not found"));
        schedule.linkGoogleCalendarEvent(eventId);
        return schedules.save(schedule);
    }

    private TaskDocument parse(TaskEntry entry) {
        try {
            return TaskDocument.parse(mapper.readTree(entry.getDocument()), mapper);
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid task document", exception);
        }
    }

    private Long parseTaskId(String taskId) {
        if (taskId == null || !taskId.matches("^t_[0-9]+$")) {
            throw new IllegalArgumentException("taskId must use t_<numeric id>");
        }
        try {
            return Long.parseLong(taskId.substring(2));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid taskId", exception);
        }
    }

    private String marker(String taskId) {
        return TASK_MARKER_PREFIX + taskId + "]";
    }
}
