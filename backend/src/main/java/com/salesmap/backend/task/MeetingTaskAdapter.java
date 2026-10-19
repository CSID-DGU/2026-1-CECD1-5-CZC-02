package com.salesmap.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.minutes.client.TaskDraftClient;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;
import java.util.List;

/**
 * B의 기존 인터페이스용 실제 어댑터. Fake 제거·빈 등록 전에는 활성화하지 않는다.
 * B 서비스에 다시 의존하지 않아 순환 의존성이 없다. 할 일을 자동 확정하지 않는다.
 */
public class MeetingTaskAdapter implements TaskDraftClient {
    private final TaskAiClient ai;
    private final TaskService tasks;
    private final ObjectMapper mapper;

    public MeetingTaskAdapter(TaskAiClient ai, TaskService tasks, ObjectMapper mapper) {
        this.ai = ai;
        this.tasks = tasks;
        this.mapper = mapper;
    }

    @Override
    public List<JsonNode> draftTasks(MeetingNoteDocument note, Long userId) {
        List<TaskDocument> stored = tasks.importDrafts(ai.extract(note, userId), note.noteId(), userId);
        return stored.stream().map(task -> (JsonNode) mapper.valueToTree(task)).toList();
    }

    @Override
    public void confirmTasks(MeetingNoteDocument note, Long userId) {
        boolean unreviewed = tasks.list(userId).stream()
                .anyMatch(task -> note.noteId().equals(task.noteId()) && "DRAFT".equals(task.status()));
        if (unreviewed) {
            throw new IllegalArgumentException("회의록의 할 일을 먼저 확인하여 확정하거나 취소해 주세요.");
        }
    }
}
