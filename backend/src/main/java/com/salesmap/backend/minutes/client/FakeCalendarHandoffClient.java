package com.salesmap.backend.minutes.client;

import com.salesmap.backend.minutes.dto.MeetingNoteDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** D의 일정 연결이 생기기 전까지 쓰는 가짜. 로그만 남기고 캘린더에는 아무것도 만들지 않는다. */
@Component
public class FakeCalendarHandoffClient implements CalendarHandoffClient {

    private static final Logger log = LoggerFactory.getLogger(FakeCalendarHandoffClient.class);

    @Override
    public void onMeetingNoteConfirmed(MeetingNoteDocument note, Long userId) {
        log.info("[MINUTES][FAKE_CALENDAR] 확정 회의록 전달 (D 미구현). noteId={}, nextContact={}건, userId={}",
                note.noteId(),
                note.fields().nextContact().size(),
                userId
        );
    }
}
