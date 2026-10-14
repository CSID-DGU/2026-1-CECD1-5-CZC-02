package com.salesmap.backend.minutes.dto;

import com.salesmap.backend.minutes.entity.MeetingNoteMode;
import com.salesmap.backend.minutes.entity.MeetingNoteStatus;

import java.time.LocalDateTime;
import java.util.List;

/** ② 미팅 기록 화면 오른쪽·확인 대기함 목록 한 줄. */
public record MeetingNoteSummaryResponse(
        String noteId,
        MeetingNoteStatus status,
        MeetingNoteMode mode,
        LocalDateTime occurredAt,
        String location,
        List<String> participantNames,
        String sourceTitle,
        int missingFieldCount,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt
) {
}
