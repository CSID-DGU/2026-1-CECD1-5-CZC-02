package com.salesmap.backend.minutes.dto;

/**
 * 메일에서 회의록 만들기 결과. 같은 메일(스레드)로 이미 만든 회의록이 있으면 새로 만들지 않고
 * created=false와 기존 noteId만 돌려준다 (draft는 null).
 */
public record MeetingNoteFromSourceResponse(
        boolean created,
        String noteId,
        MeetingNoteDraftResponse draft
) {
}
