package com.salesmap.backend.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.revision.dto.RevisionResponse;
import com.salesmap.backend.revision.service.RevisionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TaskRevisionForwardingServiceTest {
    private final TaskRevisionRepository outbox = mock(TaskRevisionRepository.class);
    private final RevisionService revisions = mock(RevisionService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final TaskRevisionForwardingService service =
            new TaskRevisionForwardingService(outbox, revisions, mapper);

    @Test
    void forwardsPendingTaskRevisionToDAndMarksOutboxDelivered() {
        TaskRevision row = new TaskRevision(3L, """
                {"screen":"TASK_REVIEW","targetType":"TASK","targetId":"t_7",
                 "fieldName":"taskType","proposedValue":"NONE","finalValue":"VISIT_PREP",
                 "action":"EDITED","proposedBy":"RULE","modelVersion":null}
                """);
        RevisionResponse saved = new RevisionResponse(
                "rv_41", "TASK_REVIEW", "TASK", "t_7", "taskType",
                mapper.getNodeFactory().textNode("NONE"), mapper.getNodeFactory().textNode("VISIT_PREP"),
                "EDITED", "RULE", null, "user_3", LocalDateTime.now());
        when(outbox.findByOwnerIdOrderByIdAsc(3L)).thenReturn(List.of(row));
        when(revisions.create(any(), eq(3L))).thenReturn(saved);

        assertThat(service.forwardPending(3L)).containsExactly(saved);
        verify(revisions).create(any(), eq(3L));
        verify(outbox).save(row);
        assertThat(row.isDelivered()).isTrue();
        assertThat(row.getDeliveredRevisionId()).isEqualTo("rv_41");
    }

    @Test
    void doesNotForwardAlreadyDeliveredRows() {
        TaskRevision row = new TaskRevision(3L, "{}");
        row.markDelivered("rv_40", LocalDateTime.now());
        when(outbox.findByOwnerIdOrderByIdAsc(3L)).thenReturn(List.of(row));

        assertThat(service.forwardPending(3L)).isEmpty();
        verifyNoInteractions(revisions);
        verify(outbox, never()).save(any());
    }
}
