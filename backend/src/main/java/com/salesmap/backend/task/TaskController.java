package com.salesmap.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService tasks;
    private final TaskDraftService drafts;
    public TaskController(TaskService tasks, TaskDraftService drafts) { this.tasks = tasks; this.drafts = drafts; }

    @GetMapping
    public ApiResponse<List<TaskDocument>> list(@AuthenticationPrincipal CustomUserPrincipal user) {
        return ApiResponse.success(tasks.list(user.getUserId()));
    }
    @GetMapping("/revisions/pending")
    public ApiResponse<List<JsonNode>> revisions(@AuthenticationPrincipal CustomUserPrincipal user) {
        return ApiResponse.success(tasks.pendingRevisions(user.getUserId()));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskDocument>> get(@PathVariable String id, @AuthenticationPrincipal CustomUserPrincipal user) {
        TaskDocument doc = tasks.get(id, user.getUserId());
        return ResponseEntity.ok().header("Access-Control-Expose-Headers", "ETag").eTag(tasks.etag(doc)).body(ApiResponse.success(doc));
    }
    @PostMapping
    public ApiResponse<TaskDocument> create(@RequestBody JsonNode body, @AuthenticationPrincipal CustomUserPrincipal user) {
        return ApiResponse.success(tasks.createManual(body, user.getUserId()));
    }
    @PostMapping("/from-note/{noteId}")
    public ApiResponse<List<TaskDocument>> draft(@PathVariable String noteId, @AuthenticationPrincipal CustomUserPrincipal user) {
        return ApiResponse.success(drafts.draft(noteId, user.getUserId()));
    }
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskDocument>> update(@PathVariable String id, @RequestBody JsonNode body,
            @RequestHeader("If-Match") String etag, @AuthenticationPrincipal CustomUserPrincipal user) {
        TaskDocument doc = tasks.update(id, body, etag, user.getUserId());
        return ResponseEntity.ok().header("Access-Control-Expose-Headers", "ETag").eTag(tasks.etag(doc)).body(ApiResponse.success(doc));
    }
}
