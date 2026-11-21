package com.salesmap.backend.briefing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.briefing.dto.MorningBriefingResponse;
import com.salesmap.backend.briefing.dto.TodayBriefingResponse;
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
        return new MorningBriefingResponse(date, LocalDateTime.now(KST).withNano(0), true,
                collect(ownerUserId, date, date.plusDays(1)));
    }

    @Transactional(readOnly = true)
    public MorningBriefingResponse preparation(Long ownerUserId, LocalDate date) {
        Objects.requireNonNull(ownerUserId, "owner user id is required");
        Objects.requireNonNull(date, "briefing date is required");
        LocalDate tomorrow = date.plusDays(1);
        return new MorningBriefingResponse(date, LocalDateTime.now(KST).withNano(0), true,
                collect(ownerUserId, tomorrow, tomorrow));
    }

    @Transactional(readOnly = true)
    public TodayBriefingResponse today(Long ownerUserId, LocalDate date) {
        Objects.requireNonNull(ownerUserId, "owner user id is required");
        Objects.requireNonNull(date, "briefing date is required");
        List<StoredTask> storedTasks = storedTasks(ownerUserId);
        return new TodayBriefingResponse(
                date,
                LocalDateTime.now(KST).withNano(0),
                true,
                collect(storedTasks, task -> "CONFIRMED".equals(task.task().status())
                        && hasDueDateInRange(task.task(), date, date)),
                collect(storedTasks, task -> "CONFIRMED".equals(task.task().status())
                        && isDueBefore(task.task(), date)),
                collect(storedTasks, task -> "DRAFT".equals(task.task().status())),
                collect(storedTasks, task -> "CONFIRMED".equals(task.task().status())
                        && hasDueDateInRange(task.task(), date.plusDays(1), date.plusDays(1)))
        );
    }

    private List<MorningBriefingResponse.BriefingItem> collect(
            Long ownerUserId, LocalDate startDate, LocalDate endDate) {
        return collect(storedTasks(ownerUserId),
                item -> "CONFIRMED".equals(item.task().status())
                        && hasDueDateInRange(item.task(), startDate, endDate));
    }

    private List<StoredTask> storedTasks(Long ownerUserId) {
        return tasks.findByOwnerIdOrderByIdDesc(ownerUserId).stream()
                .map(this::read)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<MorningBriefingResponse.BriefingItem> collect(
            List<StoredTask> storedTasks, java.util.function.Predicate<StoredTask> condition) {
        return storedTasks.stream()
                .filter(condition)
                .map(item -> new MorningBriefingResponse.BriefingItem(
                        item.taskId(), item.task().taskType(), item.task().content(), item.task().dueDate(),
                        preparationItems(item.task())))
                .toList();
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
        LocalDate dueDate = dueDate(task);
        return dueDate != null && !dueDate.isBefore(start) && !dueDate.isAfter(end);
    }

    private boolean isDueBefore(TaskDocument task, LocalDate date) {
        LocalDate dueDate = dueDate(task);
        return dueDate != null && dueDate.isBefore(date);
    }

    private LocalDate dueDate(TaskDocument task) {
        if (task.dueDate() == null) return null;
        try {
            return LocalDate.parse(task.dueDate());
        } catch (RuntimeException exception) {
            return null;
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
        if (result.isEmpty()) result.addAll(rulePreparationItems(task.taskType()));
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

    private List<String> rulePreparationItems(String taskType) {
        return switch (taskType) {
            case "MEETING_PREP" -> List.of(
                    "지난 미팅 요지와 미완료 약속 확인",
                    "참석자와 미팅 목적 확인",
                    "미팅에 필요한 자료 준비");
            case "VISIT_PREP" -> List.of(
                    "방문 장소와 이동 시간 확인",
                    "현장 점검 자료와 필요한 장비 준비",
                    "방문 중 확인할 질문 정리");
            case "CONTRACT_PREP" -> List.of(
                    "계약서 초안과 주요 조건 확인",
                    "필요 서류 목록 확인",
                    "이전 합의 내용과 변경 사항 대조");
            default -> List.of();
        };
    }

    private record StoredTask(String taskId, TaskDocument task) { }
}
