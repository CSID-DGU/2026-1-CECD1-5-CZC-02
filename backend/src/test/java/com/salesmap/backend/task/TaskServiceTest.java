package com.salesmap.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskServiceTest {
    ObjectMapper mapper = new ObjectMapper();
    TaskRepository tasks = mock(TaskRepository.class);
    TaskRevisionRepository revisions = mock(TaskRevisionRepository.class);
    TaskService service = new TaskService(tasks, revisions, mapper);
    TaskEntry entry;
    ObjectNode sample;

    @BeforeEach void setup() throws Exception {
        sample = (ObjectNode) mapper.readTree("""
                {"taskId":"t_draft_1","noteId":"mn_1","content":"계약서 초안 송부",
                "dueDate":null,"dueDateIsDefault":false,"assignee":null,"counterpartPersonId":null,
                "commitmentKind":"EXPLICIT","taskType":"CONTRACT_PREP","taskTypeConfidence":1.0,
                "taskTypeSource":"RULE","evidence":"계약서 초안을 보내기로 함","status":"DRAFT"}
                """);
        entry = new TaskEntry(1L, "mn_1", "key", mapper.writeValueAsString(sample));
        ReflectionTestUtils.setField(entry, "id", 12L);
        when(tasks.findById(12L)).thenReturn(Optional.of(entry));
        when(tasks.lockOwned(12L, 1L)).thenReturn(Optional.of(entry));
        when(tasks.save(any())).thenAnswer(inv -> {
            TaskEntry saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 12L);
            return saved;
        });
    }
    ObjectNode editable() { return mapper.valueToTree(service.document(entry)); }

    @Test void isolatesOwners() {
        assertThrows(AccessDeniedException.class, () -> service.get("t_12", 2L));
        assertThrows(java.util.NoSuchElementException.class,
                () -> service.update("t_12", sample, "stale", 2L));
        verifyNoInteractions(revisions);
    }
    @Test void rejectsStaleEditBeforeAuditOrMutation() {
        String before = entry.getDocument();
        assertThrows(TaskConflictException.class, () -> service.update("t_12", editable(), "stale", 1L));
        assertEquals(before, entry.getDocument());
        verifyNoInteractions(revisions);
    }
    @Test void typeEditsBecomeHumanAndCaptureOriginalProposal() throws Exception {
        ObjectNode update = editable(); update.put("taskType", "NONE");
        TaskDocument after = service.update("t_12", update, service.etag(service.document(entry)), 1L);
        assertEquals("HUMAN", after.taskTypeSource());
        assertEquals(1.0, after.taskTypeConfidence());
        ArgumentCaptor<TaskRevision> capture = ArgumentCaptor.forClass(TaskRevision.class);
        verify(revisions, atLeastOnce()).save(capture.capture());
        JsonNode typeRevision = capture.getAllValues().stream().map(row -> {
            try { return mapper.readTree(row.getPayload()); } catch (Exception e) { throw new RuntimeException(e); }
        }).filter(row -> row.path("fieldName").asText().equals("taskType")).findFirst().orElseThrow();
        assertEquals("CONTRACT_PREP", typeRevision.path("proposedValue").asText());
        assertEquals("NONE", typeRevision.path("finalValue").asText());
        assertEquals("user_1", typeRevision.path("actorId").asText());
        assertEquals("TASK_REVIEW", typeRevision.path("screen").asText());
        assertFalse(typeRevision.path("decidedAt").asText().contains("+09"));
    }
    @Test void recordsAllAcceptanceAndProtectsEvidence() {
        ObjectNode update = editable(); update.put("status", "CONFIRMED");
        TaskDocument doc = service.update("t_12", update, service.etag(service.document(entry)), 1L);
        assertEquals("CONFIRMED", doc.status());
        verify(revisions, atLeast(10)).save(any());
        update.put("evidence", "다른 근거");
        assertThrows(IllegalArgumentException.class, () -> service.update("t_12", update, service.etag(doc), 1L));
    }
    @Test void rejectsSkippingHumanConfirmation() {
        ObjectNode update = editable(); update.put("status", "DONE");
        assertThrows(IllegalArgumentException.class, () -> service.update("t_12", update, service.etag(service.document(entry)), 1L));
        verifyNoInteractions(revisions);
    }
    @Test void auditFailurePreventsDocumentMutation() {
        String before = entry.getDocument();
        when(revisions.save(any())).thenThrow(new IllegalStateException("저장 실패"));
        ObjectNode update = editable(); update.put("content", "수정된 계약서 송부");
        assertThrows(IllegalStateException.class, () -> service.update("t_12", update, service.etag(service.document(entry)), 1L));
        assertEquals(before, entry.getDocument());
    }
    @Test void reimportDoesNotOverwriteHumanEdits() {
        when(tasks.findByOwnerIdAndDraftKey(eq(1L), anyString())).thenReturn(Optional.of(entry));
        assertEquals("t_12", service.importDrafts(List.of(sample), "mn_1", 1L).get(0).taskId());
        verify(tasks, never()).save(any());
    }
    @Test void importChecksNoteAndDraftState() {
        assertThrows(IllegalArgumentException.class, () -> service.importDrafts(List.of(sample), "mn_2", 1L));
        sample.put("status", "CONFIRMED");
        assertThrows(IllegalArgumentException.class, () -> service.importDrafts(List.of(sample), "mn_1", 1L));
    }
    @Test void validatesStrictTypesAndUnknownFields() {
        sample.put("surprise", true);
        assertThrows(IllegalArgumentException.class, () -> TaskDocument.parse(sample, mapper));
        sample.remove("surprise"); sample.put("taskTypeConfidence", "1");
        assertThrows(IllegalArgumentException.class, () -> TaskDocument.parse(sample, mapper));
        sample.put("taskTypeConfidence", 1.0); sample.remove("assignee");
        assertThrows(IllegalArgumentException.class, () -> TaskDocument.parse(sample, mapper));
    }
    @Test void validatesDatesAndUnsupportedStatus() {
        sample.put("dueDate", "2026-02-30");
        assertThrows(IllegalArgumentException.class, () -> TaskDocument.parse(sample, mapper));
        sample.putNull("dueDate"); sample.put("status", "IN_PROGRESS");
        assertThrows(IllegalArgumentException.class, () -> TaskDocument.parse(sample, mapper));
    }
    @Test void noChangeDoesNotCreateRevision() {
        service.update("t_12", editable(), service.etag(service.document(entry)), 1L);
        verifyNoInteractions(revisions);
    }
    @Test void pendingRecordsHaveContractIds() {
        TaskRevision revision = new TaskRevision(1L, "{\"targetType\":\"TASK\"}");
        ReflectionTestUtils.setField(revision, "id", 5L);
        when(revisions.findByOwnerIdOrderByIdAsc(1L)).thenReturn(List.of(revision));
        assertEquals("rv_task_5", service.pendingRevisions(1L).get(0).get("revisionId").asText());
    }
    @Test void manualCreationRequiresHumanDraftAndRecordsCreation() {
        sample.putNull("noteId"); sample.put("taskTypeSource", "HUMAN");
        assertEquals("t_12", service.createManual(sample, 1L).taskId());
        verify(revisions).save(any());
    }
    @Test void confirmingPreviouslyEditedFieldKeepsEditedAction() throws Exception {
        ObjectNode edit = editable(); edit.put("content", "바꾼 계약서 송부");
        TaskDocument updated = service.update("t_12", edit, service.etag(service.document(entry)), 1L);
        clearInvocations(revisions);
        ObjectNode confirm = mapper.valueToTree(updated); confirm.put("status", "CONFIRMED");
        service.update("t_12", confirm, service.etag(updated), 1L);
        ArgumentCaptor<TaskRevision> capture = ArgumentCaptor.forClass(TaskRevision.class);
        verify(revisions, atLeastOnce()).save(capture.capture());
        boolean found = false;
        for (TaskRevision row : capture.getAllValues()) {
            JsonNode payload = mapper.readTree(row.getPayload());
            if (payload.path("fieldName").asText().equals("content")) {
                assertEquals("EDITED", payload.path("action").asText());
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test void retainsIdenticalFollowupsForDifferentUnresolvedPeople() {
        service.importDrafts(List.of(sample.deepCopy(), sample.deepCopy()), "mn_1", 1L);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(tasks, times(2)).findByOwnerIdAndDraftKey(eq(1L), keys.capture());
        assertEquals(2, new java.util.HashSet<>(keys.getAllValues()).size());
        verify(tasks, times(2)).save(any());
    }

}
