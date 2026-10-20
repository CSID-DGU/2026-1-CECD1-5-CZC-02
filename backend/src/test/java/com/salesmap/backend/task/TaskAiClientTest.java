package com.salesmap.backend.task;

import com.salesmap.backend.minutes.dto.MeetingNoteDocument;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TaskAiClientTest {
    @Test void postsNoteWithAssigneeWithoutNetwork() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://task.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://task.test/tasks/extract?assignee=user_7"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertTrue(new TaskAiClient(builder.build()).extract(mock(MeetingNoteDocument.class), 7L).isEmpty());
        server.verify();
    }
    @Test void draftServiceChecksNoteOwnershipBeforeCallingAi() {
        var notes = mock(com.salesmap.backend.minutes.service.MeetingNoteService.class);
        var ai = mock(TaskAiClient.class);
        var tasks = mock(TaskService.class);
        when(notes.getMeetingNote("mn_1", 2L)).thenThrow(new org.springframework.security.access.AccessDeniedException("권한 없음"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> new TaskDraftService(notes, ai, tasks).draft("mn_1", 2L));
        verifyNoInteractions(ai, tasks);
    }
}
