package com.salesmap.backend.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeetingTaskAdapterTest {
    @Test void confirmationRequiresHumanTaskReviewAndNeverUpdatesStatusItself() {
        TaskAiClient ai = mock(TaskAiClient.class);
        TaskService tasks = mock(TaskService.class);
        MeetingNoteDocument note = mock(MeetingNoteDocument.class);
        when(note.noteId()).thenReturn("mn_1");
        TaskDocument draft = new TaskDocument("t_1", "mn_1", "확인할 할 일", null, false, null, null,
                "EXPLICIT", "NONE", 0.0, "RULE", "후속 조치를 하기로 함", null, "DRAFT");
        when(tasks.list(7L)).thenReturn(List.of(draft));
        MeetingTaskAdapter adapter = new MeetingTaskAdapter(ai, tasks, new ObjectMapper());
        assertThrows(IllegalArgumentException.class, () -> adapter.confirmTasks(note, 7L));
        when(tasks.list(7L)).thenReturn(List.of());
        assertDoesNotThrow(() -> adapter.confirmTasks(note, 7L));
        verifyNoInteractions(ai);
        verify(tasks, times(2)).list(7L);
        verifyNoMoreInteractions(tasks);
    }
}
