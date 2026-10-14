package com.salesmap.backend.minutes.entity;

/** contracts/meeting_note.schema.json의 status. 사람이 확인 화면에서 확정하기 전에는 DRAFT. */
public enum MeetingNoteStatus {
    DRAFT,
    CONFIRMED
}
