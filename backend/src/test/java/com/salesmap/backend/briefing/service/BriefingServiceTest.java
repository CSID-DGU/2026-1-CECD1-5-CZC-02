package com.salesmap.backend.briefing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmap.backend.task.TaskEntry;
import com.salesmap.backend.task.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BriefingServiceTest {
    private final TaskRepository repository = mock(TaskRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final BriefingService service = new BriefingService(repository, mapper);

    @Test
    void bundlesOnlyConfirmedTasksDueTodayOrTomorrow() throws Exception {
        JsonNode sample = mapper.readTree(Files.readString(
                Path.of("..", "contracts", "examples", "task.example.json"), StandardCharsets.UTF_8));
        List<TaskEntry> entries = new java.util.ArrayList<>();
        long id = 1L;
        for (JsonNode task : sample) entries.add(entry(id++, task.toString()));
        when(repository.findByOwnerIdOrderByIdDesc(7L)).thenReturn(entries);

        var response = service.morning(7L, LocalDate.of(2026, 10, 15));

        assertThat(response.bundled()).isTrue();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).taskType()).isEqualTo("CONTRACT_PREP");
        assertThat(response.items().get(0).preparationItems()).hasSize(2);
    }

    @Test
    void returnsEmptyBundleWhenNoTaskIsDue() {
        when(repository.findByOwnerIdOrderByIdDesc(7L)).thenReturn(List.of());

        var response = service.morning(7L, LocalDate.of(2026, 10, 15));

        assertThat(response.bundled()).isTrue();
        assertThat(response.items()).isEmpty();
    }

    @Test
    void ignoresBlankPrerequisitesAndUsesFallbackPreparation() throws Exception {
        JsonNode sample = mapper.readTree(Files.readString(
                Path.of("..", "contracts", "examples", "task.example.json"), StandardCharsets.UTF_8)).get(0);
        JsonNode withoutPrerequisites = sample.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) withoutPrerequisites)
                .putArray("prerequisites").add(" ").add("");

        TaskEntry entry = entry(1L, withoutPrerequisites.toString());
        when(repository.findByOwnerIdOrderByIdDesc(7L)).thenReturn(List.of(entry));

        var response = service.morning(7L, LocalDate.of(2026, 10, 15));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).preparationItems()).isNotEmpty();
    }

    @Test
    void preparationIncludesOnlyConfirmedTasksDueTomorrow() throws Exception {
        JsonNode sample = mapper.readTree(Files.readString(
                Path.of("..", "contracts", "examples", "task.example.json"), StandardCharsets.UTF_8));
        List<TaskEntry> entries = new java.util.ArrayList<>();
        long id = 1L;
        for (JsonNode task : sample) entries.add(entry(id++, task.toString()));
        when(repository.findByOwnerIdOrderByIdDesc(7L)).thenReturn(entries);

        var response = service.preparation(7L, LocalDate.of(2026, 10, 20));

        assertThat(response.items()).isEmpty();

        JsonNode tomorrowTask = sample.get(0).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) tomorrowTask)
                .put("dueDate", "2026-10-21")
                .remove("prerequisites");
        TaskEntry tomorrowEntry = entry(4L, tomorrowTask.toString());
        when(repository.findByOwnerIdOrderByIdDesc(7L)).thenReturn(List.of(tomorrowEntry));

        var tomorrowResponse = service.preparation(7L, LocalDate.of(2026, 10, 20));

        assertThat(tomorrowResponse.items()).hasSize(1);
        assertThat(tomorrowResponse.items().get(0).preparationItems()).hasSize(3);
    }

    @Test
    void todayBundlesDueOverduePendingAndTomorrowTasks() throws Exception {
        JsonNode sample = mapper.readTree(Files.readString(
                Path.of("..", "contracts", "examples", "task.example.json"), StandardCharsets.UTF_8));
        JsonNode today = sample.get(0).deepCopy();
        JsonNode pending = sample.get(1).deepCopy();
        JsonNode tomorrow = sample.get(2).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) today).put("dueDate", "2026-10-20");
        ((com.fasterxml.jackson.databind.node.ObjectNode) pending).put("dueDate", "2026-10-20");
        ((com.fasterxml.jackson.databind.node.ObjectNode) tomorrow)
                .put("dueDate", "2026-10-21")
                .put("status", "CONFIRMED");
        when(repository.findByOwnerIdOrderByIdDesc(7L)).thenReturn(List.of(
                entry(1L, today.toString()), entry(2L, pending.toString()), entry(3L, tomorrow.toString())));

        var response = service.today(7L, LocalDate.of(2026, 10, 20));

        assertThat(response.dueToday()).hasSize(1);
        assertThat(response.overdue()).isEmpty();
        assertThat(response.pendingConfirmation()).hasSize(1);
        assertThat(response.tomorrowPreparation()).hasSize(1);
    }

    @Test
    void usesScenarioPreparationRulesForVisitAndContractTasks() throws Exception {
        JsonNode sample = mapper.readTree(Files.readString(
                Path.of("..", "contracts", "examples", "task.example.json"), StandardCharsets.UTF_8));
        JsonNode contract = sample.get(0).deepCopy();
        JsonNode visit = sample.get(2).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) contract)
                .put("dueDate", "2026-10-16")
                .put("status", "CONFIRMED")
                .put("taskType", "CONTRACT_PREP")
                .remove("prerequisites");
        ((com.fasterxml.jackson.databind.node.ObjectNode) visit)
                .put("dueDate", "2026-10-16")
                .put("status", "CONFIRMED")
                .put("taskType", "VISIT_PREP")
                .remove("prerequisites");
        when(repository.findByOwnerIdOrderByIdDesc(7L)).thenReturn(List.of(
                entry(1L, contract.toString()), entry(2L, visit.toString())));

        var response = service.morning(7L, LocalDate.of(2026, 10, 15));

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().stream()
                .filter(item -> "CONTRACT_PREP".equals(item.taskType()))
                .findFirst()
                .orElseThrow()
                .preparationItems())
                .containsExactly(
                        "계약서 초안과 주요 조건 확인",
                        "필요 서류 목록 확인",
                        "이전 합의 내용과 변경 사항 대조");
        assertThat(response.items().stream()
                .filter(item -> "VISIT_PREP".equals(item.taskType()))
                .findFirst()
                .orElseThrow()
                .preparationItems())
                .containsExactly(
                        "방문 장소와 이동 시간 확인",
                        "현장 점검 자료와 필요한 장비 준비",
                        "방문 중 확인할 질문 정리");
    }

    private TaskEntry entry(Long id, String document) {
        TaskEntry entry = new TaskEntry(7L, "mn_1", "key-" + id, document);
        ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }
}
