package com.salesmap.backend.revision.controller;

import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import com.salesmap.backend.revision.dto.RevisionCreateRequest;
import com.salesmap.backend.revision.dto.RevisionResponse;
import com.salesmap.backend.revision.service.RevisionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/revisions")
public class RevisionController {
    private final RevisionService revisions;

    public RevisionController(RevisionService revisions) { this.revisions = revisions; }

    @PostMapping
    public ApiResponse<RevisionResponse> create(@Valid @RequestBody RevisionCreateRequest request,
                                                 @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success(revisions.create(request, principal.getUserId()));
    }

    @GetMapping
    public ApiResponse<List<RevisionResponse>> list(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success(revisions.list(principal.getUserId()));
    }
}
