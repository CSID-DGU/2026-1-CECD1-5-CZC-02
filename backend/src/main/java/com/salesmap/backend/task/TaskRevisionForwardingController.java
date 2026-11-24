package com.salesmap.backend.task;

import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import com.salesmap.backend.revision.dto.RevisionResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks/revisions")
public class TaskRevisionForwardingController {
    private final TaskRevisionForwardingService forwarding;

    public TaskRevisionForwardingController(TaskRevisionForwardingService forwarding) {
        this.forwarding = forwarding;
    }

    @PostMapping("/forward")
    public ApiResponse<List<RevisionResponse>> forward(
            @AuthenticationPrincipal CustomUserPrincipal user) {
        return ApiResponse.success(forwarding.forwardPending(user.getUserId()));
    }
}
