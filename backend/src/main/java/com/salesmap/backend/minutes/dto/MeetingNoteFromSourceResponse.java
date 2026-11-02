package com.salesmap.backend.minutes.dto;

/**
 * 메일에서 회의록 만들기 결과. 같은 메일(스레드)로 이미 만든 회의록이 있으면 새로 만들지 않고
 * created=false와 기존 noteId만 돌려준다 (draft는 null). 그 회의록이 보관돼 있으면 archived=true — 화면이 보관 해제를 안내한다.
 */
public record MeetingNoteFromSourceResponse(
        boolean created,
        String noteId,
        boolean archived,
        MeetingNoteDraftResponse draft
) {
}
