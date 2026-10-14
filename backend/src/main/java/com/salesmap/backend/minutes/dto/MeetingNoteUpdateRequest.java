package com.salesmap.backend.minutes.dto;

import java.util.List;

/**
 * ③ 확인 화면의 문항 수정. 보낸 값으로 통째로 바꾸고, 보내지 않은(null) 값은 그대로 둔다.
 * <p>
 * FULL 회의록은 fields만, LIGHT 회의록은 perPerson만 고칠 수 있다.
 * perPerson은 기존과 같은 순서·같은 인원으로 보내고, 이름·personId는 무시한다(참석자는 A 영역).
 */
public record MeetingNoteUpdateRequest(
        FieldsUpdate fields,
        List<MeetingNoteDocument.PerPerson> perPerson
) {

    /** 문항별로 null이면 그대로 둔다. 빈 배열이면 비운다. */
    public record FieldsUpdate(
            List<MeetingNoteDocument.Item> discussion,
            List<MeetingNoteDocument.Item> customerRequests,
            List<MeetingNoteDocument.Item> concerns,
            List<MeetingNoteDocument.Item> commitments,
            List<MeetingNoteDocument.Item> nextContact
    ) {
    }
}
