package com.salesmap.backend.minutes.service;

import com.salesmap.backend.minutes.client.CalendarHandoffClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회의록 확정이 커밋된 뒤에만 D(일정)로 넘긴다. 확정이 롤백되면 캘린더 같은 외부 효과도 일어나지 않고,
 * 외부 호출이 실패해도 이미 확정된 회의록은 되돌리지 않는다.
 */
@Component
public class MeetingNoteHandoffListener {

    private static final Logger log = LoggerFactory.getLogger(MeetingNoteHandoffListener.class);

    private final CalendarHandoffClient calendarHandoffClient;

    public MeetingNoteHandoffListener(CalendarHandoffClient calendarHandoffClient) {
        this.calendarHandoffClient = calendarHandoffClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfirmed(MeetingNoteConfirmedEvent event) {
        try {
            calendarHandoffClient.onMeetingNoteConfirmed(event.note(), event.userId());
        } catch (RuntimeException exception) {
            log.warn("[MINUTES] calendar handoff failed after confirm. noteId={}", event.note().noteId(), exception);
        }
    }
}
