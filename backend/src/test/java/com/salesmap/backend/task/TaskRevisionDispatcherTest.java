package com.salesmap.backend.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.revision.dto.RevisionCreateRequest;
import com.salesmap.backend.revision.service.RevisionService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskRevisionDispatcherTest {
    TaskRevisionRepository outbox = mock(TaskRevisionRepository.class);
    RevisionService revisions = mock(RevisionService.class);
    TaskRevisionDispatcher dispatcher = new TaskRevisionDispatcher(outbox, revisions, new ObjectMapper());
    TaskRevision row = new TaskRevision(7L, """
        {"screen":"TASK_REVIEW","targetType":"TASK","targetId":"t_12","fieldName":"taskType",
        "proposedValue":"NONE","finalValue":"MEETING_PREP","action":"EDITED",
        "proposedBy":"LLM","modelVersion":null}
        """);

    @Test void forwardsOwnerAndProposalThenRemovesDeliveredRow() {
        when(outbox.lockPending(any())).thenReturn(List.of(row));
        dispatcher.deliver();
        var order = inOrder(revisions, outbox);
        order.verify(revisions).create(argThat(request -> request.targetId().equals("t_12")
                && request.proposedBy().equals("LLM") && request.modelVersion() == null
                && request.proposedValue().asText().equals("NONE")), eq(7L));
        order.verify(outbox).delete(row);
    }
    @Test void failureKeepsQueueAndPropagatesForTransactionRollback() {
        when(outbox.lockPending(any())).thenReturn(List.of(row));
        when(revisions.create(any(RevisionCreateRequest.class), eq(7L))).thenThrow(new IllegalStateException("DB failure"));
        assertThrows(IllegalStateException.class, dispatcher::deliver);
        verify(outbox, never()).delete(any());
    }
}
