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

    private TaskEntry entry(Long id, String document) {
        TaskEntry entry = new TaskEntry(7L, "mn_1", "key-" + id, document);
        ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }
}
