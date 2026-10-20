package com.salesmap.backend.briefing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.briefing.dto.MorningBriefingResponse;
import com.salesmap.backend.task.TaskDocument;
import com.salesmap.backend.task.TaskEntry;
import com.salesmap.backend.task.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class BriefingService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TaskRepository tasks;
    private final ObjectMapper mapper;

    public BriefingService(TaskRepository tasks, ObjectMapper mapper) {
        this.tasks = tasks;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public MorningBriefingResponse morning(Long ownerUserId, LocalDate date) {
        Objects.requireNonNull(ownerUserId, "owner user id is required");
        Objects.requireNonNull(date, "briefing date is required");
        LocalDate endDate = date.plusDays(1);
        List<MorningBriefingResponse.BriefingItem> items = tasks.findByOwnerIdOrderByIdDesc(ownerUserId).stream()
                .map(this::read)
                .filter(item -> item != null)
                .filter(item -> "CONFIRMED".equals(item.task().status()))
                .filter(item -> hasDueDateInRange(item.task(), date, endDate))
                .map(item -> new MorningBriefingResponse.BriefingItem(
                        item.taskId(), item.task().taskType(), item.task().content(), item.task().dueDate(),
                        preparationItems(item.task())))
                .toList();
        return new MorningBriefingResponse(date, LocalDateTime.now(KST).withNano(0), true, items);
    }

    private StoredTask read(TaskEntry entry) {
        try {
            TaskDocument task = TaskDocument.parse(mapper.readTree(entry.getDocument()), mapper);
            return new StoredTask("t_" + entry.getId(), task);
        } catch (RuntimeException exception) {
            return null;
        } catch (Exception exception) {
            throw new IllegalStateException("저장된 task를 읽을 수 없습니다.", exception);
        }
    }

    private boolean hasDueDateInRange(TaskDocument task, LocalDate start, LocalDate end) {
        if (task.dueDate() == null) return false;
        try {
            LocalDate dueDate = LocalDate.parse(task.dueDate());
            return !dueDate.isBefore(start) && !dueDate.isAfter(end);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private List<String> preparationItems(TaskDocument task) {
        List<String> result = new ArrayList<>();
        if (task.prerequisites() != null) {
            task.prerequisites().stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(String::trim)
                    .forEach(result::add);
        }
        if (result.isEmpty()) {
            switch (task.taskType()) {
                case "MEETING_PREP" -> result.add("최근 고객 활동과 회의 안건 확인");
                case "VISIT_PREP" -> result.add("방문 장소와 이동 시간 확인");
                case "CONTRACT_PREP" -> result.add("계약서 초안과 필수 서류 확인");
                default -> result.add("할 일의 근거와 필요한 자료 확인");
            }
        }
        return List.copyOf(result);
    }

    private record StoredTask(String taskId, TaskDocument task) { }
}
