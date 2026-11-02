package com.salesmap.backend.minutes.dto;

import java.time.LocalDateTime;

/** 회의록 상세. meetingNote는 contracts 형식 그대로이고, 나머지는 화면·디버깅용 백엔드 정보다. */
public record MeetingNoteResponse(
        MeetingNoteDocument meetingNote,
        SourceInfo source,
        String llmProvider,
        String llmFallbackReason,
        LocalDateTime confirmedAt,
        Boolean confirmedWithoutEdit,
        // 보관(숨김)한 시각, 보관 안 했으면 null. 계약 status(DRAFT/CONFIRMED)와 따로 둔다
        LocalDateTime archivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** 확인 화면 왼쪽 원문. 근거 강조는 content에서 evidence를 찾는다. */
    public record SourceInfo(
            String sourceId,
            String sourceType,
            String title,
            String content
    ) {
    }
}
