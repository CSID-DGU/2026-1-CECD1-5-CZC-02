package com.salesmap.backend.taskstatus;

import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import com.salesmap.backend.task.TaskConflictException;
import com.salesmap.backend.task.TaskDocument;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 할 일 상태 변경 제안. 조회는 제안만 돌려주고, 상태는 사람이 accept를 눌러야 바뀐다. */
@RestController
@RequestMapping("/api/task-status-suggestions")
public class TaskStatusSuggestionController {

    private final TaskStatusSuggestionService suggestions;

    public TaskStatusSuggestionController(TaskStatusSuggestionService suggestions) {
        this.suggestions = suggestions;
    }

    public record DecisionRequest(String sourceId) {
    }

    @GetMapping
    public ApiResponse<List<TaskStatusSuggestion>> list(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success(suggestions.list(principal.getUserId()));
    }

    @PostMapping("/{taskId}/accept")
    public ApiResponse<TaskDocument> accept(@PathVariable String taskId, @RequestBody DecisionRequest request,
                                            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success("할 일을 완료로 옮겼습니다.",
                suggestions.accept(taskId, request.sourceId(), principal.getUserId()));
    }

    @PostMapping("/{taskId}/dismiss")
    public ApiResponse<Void> dismiss(@PathVariable String taskId, @RequestBody DecisionRequest request,
                                     @AuthenticationPrincipal CustomUserPrincipal principal) {
        suggestions.dismiss(taskId, request.sourceId(), principal.getUserId());
        return ApiResponse.success("제안을 넘겼습니다.", null);
    }

    @ExceptionHandler(TaskConflictException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(TaskConflictException error) {
        return ResponseEntity.status(409).body(ApiResponse.fail(error.getMessage()));
    }
}
