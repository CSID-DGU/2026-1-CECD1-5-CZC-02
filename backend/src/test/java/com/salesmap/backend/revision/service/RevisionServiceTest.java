package com.salesmap.backend.revision.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.salesmap.backend.analysis.entity.AnalysisRevision;
import com.salesmap.backend.analysis.repository.AnalysisRevisionRepository;
import com.salesmap.backend.revision.dto.RevisionCreateRequest;
import com.salesmap.backend.revision.dto.RevisionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevisionServiceTest {
    private final AnalysisRevisionRepository repository = mock(AnalysisRevisionRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final RevisionService service = new RevisionService(repository, mapper);

    @Test
    void storesContractFieldsAndServerAuditValues() throws Exception {
        when(repository.save(any(AnalysisRevision.class))).thenAnswer(invocation -> {
            AnalysisRevision revision = invocation.getArgument(0);
            ReflectionTestUtils.setField(revision, "id", 17L);
            return revision;
        });

        var sample = mapper.readTree(Files.readString(
                Path.of("..", "contracts", "examples", "revision.example.json"), StandardCharsets.UTF_8)).get(1);
        RevisionResponse response = service.create(new RevisionCreateRequest(
                sample.get("screen").asText(), sample.get("targetType").asText(), sample.get("targetId").asText(),
                sample.get("fieldName").asText(), sample.get("proposedValue"), sample.get("finalValue"),
                sample.get("action").asText(), sample.get("proposedBy").asText(), sample.get("modelVersion").asText()), 3L);

        assertThat(response.revisionId()).isEqualTo("rv_17");
        assertThat(response.actorId()).isEqualTo("user_3");
        assertThat(response.proposedValue()).isEqualTo(sample.get("proposedValue"));
        assertThat(response.finalValue()).isEqualTo(sample.get("finalValue"));
        verify(repository).save(any(AnalysisRevision.class));
    }

    @Test
    void rejectsValuesOutsideRevisionContract() {
        RevisionCreateRequest request = new RevisionCreateRequest(
                "TASK_REVIEW", "TASK", "t_001", "taskType", TextNode.valueOf("NONE"),
                TextNode.valueOf("CONTRACT_PREP"), "UNKNOWN", "MODEL", null);

        assertThatThrownBy(() -> service.create(request, 3L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankRequiredTextFields() {
        RevisionCreateRequest request = new RevisionCreateRequest(
                "", "TASK", "t_001", "taskType", TextNode.valueOf("NONE"),
                TextNode.valueOf("CONTRACT_PREP"), "EDITED", "MODEL", null);

        assertThatThrownBy(() -> service.create(request, 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("screen");
    }

    @Test
    void preservesNullJsonValuesAsContractNulls() {
        when(repository.save(any(AnalysisRevision.class))).thenAnswer(invocation -> {
            AnalysisRevision revision = invocation.getArgument(0);
            ReflectionTestUtils.setField(revision, "id", 18L);
            return revision;
        });

        RevisionResponse response = service.create(new RevisionCreateRequest(
                "BRIEFING", "BRIEFING_ITEM", "t_001", "opened", null,
                null, "OPENED", "RULE", null), 3L);

        assertThat(response.proposedValue().isNull()).isTrue();
        assertThat(response.finalValue().isNull()).isTrue();
    }

    @Test
    void listsOnlyOwnerRevisionsThroughRepositoryQuery() {
        when(repository.findByOwnerUserIdOrderByIdAsc(3L)).thenReturn(List.of());

        assertThat(service.list(3L)).isEmpty();
        verify(repository).findByOwnerUserIdOrderByIdAsc(3L);
    }
}
