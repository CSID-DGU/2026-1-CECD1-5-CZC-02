package com.salesmap.backend.salesmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.analysis.service.AnalysisService;
import com.salesmap.backend.calendar.service.GoogleCalendarEventService;
import com.salesmap.backend.customer.service.CustomerTimelineService;
import com.salesmap.backend.salesmap.client.SalesmapClient;
import com.salesmap.backend.salesmap.repository.SalesmapRecordRepository;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleDeletionAudit;
import com.salesmap.backend.schedule.entity.ScheduleStatus;
import com.salesmap.backend.schedule.repository.ScheduleDeletionAuditRepository;
import com.salesmap.backend.schedule.repository.ScheduleRepository;
import com.salesmap.backend.schedule.service.ScheduleConflictService;
import com.salesmap.backend.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class SalesmapScheduleDeletionAuditTest {
    @Test
    void cancelActionRecordsWhoDeletedTheSchedule() {
        ScheduleRepository schedules = mock(ScheduleRepository.class);
        ScheduleDeletionAuditRepository audits = mock(ScheduleDeletionAuditRepository.class);
        SalesmapService service = new SalesmapService(
                mock(SalesmapRecordRepository.class),
                mock(AnalysisRepository.class),
                mock(AnalysisService.class),
                mock(SalesmapClient.class),
                mock(GoogleCalendarEventService.class),
                schedules,
                audits,
                mock(ScheduleConflictService.class),
                mock(CustomerTimelineService.class)
        );
        Analysis analysis = mock(Analysis.class);
        when(analysis.getActionType()).thenReturn("CANCEL");
        Schedule schedule = new Schedule(mock(User.class), null, "가짜 취소 일정",
                LocalDateTime.of(2026, 12, 1, 10, 0), null, null, ScheduleStatus.SCHEDULED);
        ReflectionTestUtils.setField(schedule, "id", 12L);

        ReflectionTestUtils.invokeMethod(service, "syncCalendarByAction", analysis, schedule, 7L);

        ArgumentCaptor<ScheduleDeletionAudit> audit = ArgumentCaptor.forClass(ScheduleDeletionAudit.class);
        InOrder order = inOrder(audits, schedules);
        order.verify(audits).save(audit.capture());
        order.verify(schedules).delete(schedule);
        assertThat(audit.getValue().getScheduleId()).isEqualTo(12L);
        assertThat(audit.getValue().getDeletedByUserId()).isEqualTo(7L);
        assertThat(audit.getValue().getDeletedAt()).isNotNull();
        assertThat(audit.getValue().getTitle()).isEqualTo("가짜 취소 일정");
    }
}
