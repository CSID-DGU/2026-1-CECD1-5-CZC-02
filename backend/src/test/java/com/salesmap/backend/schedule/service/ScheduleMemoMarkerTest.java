package com.salesmap.backend.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.calendar.service.GoogleCalendarEventService;
import com.salesmap.backend.customer.service.CustomerTimelineService;
import com.salesmap.backend.schedule.dto.ScheduleUpdateRequest;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleStatus;
import com.salesmap.backend.schedule.repository.ScheduleDeletionAuditRepository;
import com.salesmap.backend.schedule.repository.ScheduleRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleMemoMarkerTest {
    private static final String MARKER = "[SALESMAP_TASK:t_1]";

    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final ScheduleService service = new ScheduleService(
            schedules,
            mock(ScheduleDeletionAuditRepository.class),
            mock(UserRepository.class),
            mock(AnalysisRepository.class),
            mock(GoogleCalendarEventService.class),
            mock(CustomerTimelineService.class)
    );
    private Schedule schedule;

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        schedule = new Schedule(user, null, "가짜 준비", LocalDateTime.of(2026, 12, 1, 10, 0),
                MARKER + "\n가짜 근거", null, ScheduleStatus.SCHEDULED);
        when(schedules.findById(12L)).thenReturn(Optional.of(schedule));
    }

    @Test
    void editedMemoKeepsMarkerInDbButNotInResponse() {
        var response = service.updateSchedule(12L, new ScheduleUpdateRequest(null, null, "고친 메모"), 7L);

        assertThat(schedule.getMemo()).isEqualTo(MARKER + "\n고친 메모");
        assertThat(response.memo()).isEqualTo("고친 메모");
    }

    @Test
    void markerSurvivesLengthLimit() {
        service.updateSchedule(12L, new ScheduleUpdateRequest(null, null, "가".repeat(3000)), 7L);

        assertThat(schedule.getMemo()).startsWith(MARKER).hasSize(2000);
    }

    @Test
    void memoWithoutMarkerIsUnchanged() {
        schedule.update("가짜 준비", schedule.getScheduleDateTime(), "일반 메모");

        service.updateSchedule(12L, new ScheduleUpdateRequest(null, null, "고친 메모"), 7L);

        assertThat(schedule.getMemo()).isEqualTo("고친 메모");
    }
}
