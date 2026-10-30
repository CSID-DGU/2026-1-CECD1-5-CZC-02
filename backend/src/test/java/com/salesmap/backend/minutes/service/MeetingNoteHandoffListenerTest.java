package com.salesmap.backend.minutes.service;

import com.salesmap.backend.minutes.client.CalendarHandoffClient;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 회의록 확정 뒤 D(일정)로 넘기는 일은 커밋 뒤에만 한다. */
class MeetingNoteHandoffListenerTest {

    private final CalendarHandoffClient calendarHandoffClient = mock(CalendarHandoffClient.class);
    private final MeetingNoteHandoffListener listener = new MeetingNoteHandoffListener(calendarHandoffClient);

    @Test
    void 커밋_뒤에만_실행되도록_등록돼_있다() throws Exception {
        TransactionalEventListener annotation = MeetingNoteHandoffListener.class
                .getMethod("onConfirmed", MeetingNoteConfirmedEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void 확정된_회의록을_일정_쪽에_넘긴다() {
        MeetingNoteDocument note = note();

        listener.onConfirmed(new MeetingNoteConfirmedEvent(note, 1L));

        verify(calendarHandoffClient).onMeetingNoteConfirmed(eq(note), eq(1L));
    }

    @Test
    void 일정_쪽이_실패해도_이미_확정된_회의록에는_영향이_없다() {
        doThrow(new IllegalStateException("캘린더 연결 실패")).when(calendarHandoffClient).onMeetingNoteConfirmed(any(), any());

        assertThatCode(() -> listener.onConfirmed(new MeetingNoteConfirmedEvent(note(), 1L))).doesNotThrowAnyException();
    }

    private MeetingNoteDocument note() {
        return new MeetingNoteDocument(
                "mn_7",
                List.of(new MeetingNoteDocument.SourceRef("src_101", "MEETING_NOTE")),
                null,
                null,
                "FULL",
                List.of(),
                new MeetingNoteDocument.Fields(List.of(), List.of(), List.of(), List.of(), List.of()),
                null,
                List.of(),
                List.of(),
                "CONFIRMED",
                "LLM",
                null
        );
    }
}
