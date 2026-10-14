package com.salesmap.backend.minutes.client;

import com.salesmap.backend.minutes.dto.MeetingNoteDocument;

/**
 * D(일정·캘린더) 호출 자리. 회의록을 확정하면 다음 접촉(nextContact) 등을 D가 일정으로 다룬다.
 * <p>
 * D가 실제 구현을 빈으로 등록하면 {@link FakeCalendarHandoffClient}를 지운다.
 */
public interface CalendarHandoffClient {

    void onMeetingNoteConfirmed(MeetingNoteDocument note, Long userId);
}
