package com.salesmap.backend.talent;

import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import com.salesmap.backend.talent.TalentMatchViews.MatchRequest;
import com.salesmap.backend.talent.TalentMatchViews.MatchResult;
import com.salesmap.backend.talent.TalentMatchViews.SubmissionBrief;
import com.salesmap.backend.talent.TalentMatchViews.SubmissionRequest;
import com.salesmap.backend.talent.TalentMatchViews.SubmittedCandidate;
import com.salesmap.backend.talent.TalentMatchViews.TaskBrief;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 후보 추천 · 제출 리스트 (인력 메뉴). 추천은 규칙 계산이고, 제출 리스트는 담당자가 고른 것만 저장한다. */
@RestController
@RequestMapping("/api/talents")
public class TalentMatchController {
    private final TalentMatchService matches;

    public TalentMatchController(TalentMatchService matches) {
        this.matches = matches;
    }

    @GetMapping("/match/tasks")
    public ApiResponse<List<TaskBrief>> matchTasks(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success(matches.matchTasks(owner(principal)));
    }

    @GetMapping("/match")
    public ApiResponse<MatchResult> matchForTask(@RequestParam String taskId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success(matches.matchForTask(taskId, null, owner(principal)));
    }

    /** {"taskId": "t_12", "text": "고친 요청 글"} 또는 할 일 없이 {"text": "..."} */
    @PostMapping("/match")
    public ApiResponse<MatchResult> match(@RequestBody MatchRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        Long owner = owner(principal);
        if (request.taskId() != null && !request.taskId().isBlank())
            return ApiResponse.success(matches.matchForTask(request.taskId(), request.text(), owner));
        return ApiResponse.success(matches.matchText(request.text(), owner));
    }

    @GetMapping("/submissions/{taskId}")
    public ApiResponse<List<SubmittedCandidate>> submission(@PathVariable String taskId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success(matches.submission(taskId, owner(principal)));
    }

    @PutMapping("/submissions/{taskId}")
    public ApiResponse<List<SubmittedCandidate>> saveSubmission(@PathVariable String taskId,
            @RequestBody SubmissionRequest request, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success("제출 리스트를 저장했습니다.",
                matches.saveSubmission(taskId, request.profileIds(), owner(principal)));
    }

    /** 미팅 준비 브리핑 한 칸: 이 할 일과 이어진(같은 할 일·회의록·고객 담당자) 제출 리스트. */
    @GetMapping("/submissions/{taskId}/related")
    public ApiResponse<List<SubmissionBrief>> related(@PathVariable String taskId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success(matches.related(taskId, owner(principal)));
    }

    private static Long owner(CustomUserPrincipal principal) {
        if (principal == null) throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
        return principal.getUserId();
    }
}
