package com.salesmap.backend.task;

import com.salesmap.backend.minutes.service.MeetingNoteService;
import org.springframework.stereotype.Service;
import java.util.List;

/** AI 호출 동안 DB 트랜잭션을 유지하지 않는다. B의 Fake 빈은 아직 교체하지 않는다. */
@Service
public class TaskDraftService {
    private final MeetingNoteService notes;
    private final TaskAiClient ai;
    private final TaskService tasks;
    public TaskDraftService(MeetingNoteService notes, TaskAiClient ai, TaskService tasks) {
        this.notes = notes; this.ai = ai; this.tasks = tasks;
    }
    public List<TaskDocument> draft(String noteId, Long owner) {
        var note = notes.getMeetingNote(noteId, owner).meetingNote();
        var drafts = ai.extract(note, owner);
        return tasks.importDrafts(drafts, noteId, owner);
    }
}
