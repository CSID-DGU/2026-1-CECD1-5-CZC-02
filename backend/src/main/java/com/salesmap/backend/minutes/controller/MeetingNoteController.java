package com.salesmap.backend.minutes.controller;

import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import com.salesmap.backend.minutes.dto.MeetingNoteCreateRequest;
import com.salesmap.backend.minutes.dto.MeetingNoteDraftResponse;
import com.salesmap.backend.minutes.dto.MeetingNoteResponse;
import com.salesmap.backend.minutes.dto.MeetingNoteSummaryResponse;
import com.salesmap.backend.minutes.dto.MeetingNoteUpdateRequest;
import com.salesmap.backend.minutes.service.MeetingNoteService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 회의록 API. 경로의 noteId는 "mn_" + PK. */
@RestController
@RequestMapping("/api/meeting-notes")
public class MeetingNoteController {

    private final MeetingNoteService meetingNoteService;

    public MeetingNoteController(MeetingNoteService meetingNoteService) {
        this.meetingNoteService = meetingNoteService;
    }

    @PostMapping
    public ApiResponse<MeetingNoteDraftResponse> createDraft(
            @Valid @RequestBody MeetingNoteCreateRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success("회의록 초안을 만들었습니다.", meetingNoteService.createDraft(request, principal.getUserId()));
    }

    @GetMapping
    public ApiResponse<List<MeetingNoteSummaryResponse>> getMeetingNotes(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success(meetingNoteService.getMeetingNotes(status, principal.getUserId()));
    }

    @GetMapping("/{noteId}")
    public ApiResponse<MeetingNoteResponse> getMeetingNote(
            @PathVariable String noteId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success(meetingNoteService.getMeetingNote(noteId, principal.getUserId()));
    }

    @PatchMapping("/{noteId}")
    public ApiResponse<MeetingNoteResponse> updateMeetingNote(
            @PathVariable String noteId,
            @RequestBody MeetingNoteUpdateRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success("회의록을 수정했습니다.", meetingNoteService.updateMeetingNote(noteId, request, principal.getUserId()));
    }

    @PostMapping("/{noteId}/confirm")
    public ApiResponse<MeetingNoteResponse> confirmMeetingNote(
            @PathVariable String noteId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.success("회의록을 확정했습니다.", meetingNoteService.confirmMeetingNote(noteId, principal.getUserId()));
    }
}
