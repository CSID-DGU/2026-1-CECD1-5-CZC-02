package com.salesmap.backend.minutes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.ai.client.AiClient;
import com.salesmap.backend.minutes.client.IdentityDecisionReader;
import com.salesmap.backend.minutes.client.IdentityResolutionClient;
import com.salesmap.backend.minutes.client.RevisionClient;
import com.salesmap.backend.minutes.client.TaskDraftClient;
import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.repository.MeetingNoteRepository;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.Query;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 회의록 확인 시간 누적. MySQL·네트워크 없음. */
class MeetingNoteReviewTimeTest {

    private static final Long USER_ID = 1L;
    private static final Long NOTE_ID = 7L;

    private final MeetingNoteRepository notes = mock(MeetingNoteRepository.class);
    private final RevisionClient revisions = mock(RevisionClient.class);
    private final MeetingNote note = mock(MeetingNote.class);
    private MeetingNoteService service;

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(note.getUser()).thenReturn(user);
        when(note.getId()).thenReturn(NOTE_ID);
        when(notes.findById(NOTE_ID)).thenReturn(Optional.of(note));
        service = new MeetingNoteService(
                notes,
                mock(SourceRepository.class),
                mock(UserRepository.class),
                mock(AiClient.class),
                mock(IdentityResolutionClient.class),
                mock(IdentityDecisionReader.class),
                mock(TaskDraftClient.class),
                revisions,
                mock(ApplicationEventPublisher.class),
                new ObjectMapper()
        );
    }

    @Test
    void 본_시간을_DB에서_더하고_수정_기록은_남기지_않는다() {
        service.recordReviewTime("mn_7", 15, USER_ID);

        verify(notes).addReviewSeconds(NOTE_ID, USER_ID, 15);
        verifyNoInteractions(revisions);
    }

    @Test
    void 범위를_벗어난_시간은_거절한다() {
        for (Integer seconds : new Integer[]{null, 0, -1, 3601}) {
            assertThatThrownBy(() -> service.recordReviewTime("mn_7", seconds, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(notes, never()).addReviewSeconds(anyLong(), anyLong(), anyInt());
    }

    @Test
    void 다른_사용자의_회의록에는_더하지_않는다() {
        assertThatThrownBy(() -> service.recordReviewTime("mn_7", 15, 2L))
                .isInstanceOf(AccessDeniedException.class);
        verify(notes, never()).addReviewSeconds(anyLong(), anyLong(), anyInt());
    }

    @Test
    void 확인_대기_중인_본인_회의록만_바뀌는_쿼리다() throws Exception {
        String jpql = MeetingNoteRepository.class
                .getMethod("addReviewSeconds", Long.class, Long.class, int.class)
                .getAnnotation(Query.class).value();

        assertThat(jpql).contains("n.user.id = :userId", "MeetingNoteStatus.DRAFT", "n.archivedAt is null",
                "coalesce(n.reviewActiveSeconds, 0) + :seconds");
    }
}
