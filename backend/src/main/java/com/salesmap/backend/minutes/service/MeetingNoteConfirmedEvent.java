package com.salesmap.backend.minutes.service;

import com.salesmap.backend.minutes.dto.MeetingNoteDocument;

/** 회의록이 확정되어 커밋된 뒤 외부 효과(캘린더 등)를 일으키기 위한 이벤트. */
public record MeetingNoteConfirmedEvent(
        MeetingNoteDocument note,
        Long userId
) {
}
