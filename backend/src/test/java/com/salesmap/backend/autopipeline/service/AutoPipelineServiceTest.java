package com.salesmap.backend.autopipeline.service;

import com.salesmap.backend.analysisjob.service.AnalysisJobService;
import com.salesmap.backend.gmail.service.GmailIntegrationService;
import com.salesmap.backend.integration.entity.Integration;
import com.salesmap.backend.integration.entity.IntegrationProvider;
import com.salesmap.backend.integration.entity.IntegrationStatus;
import com.salesmap.backend.integration.repository.IntegrationRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.entity.UserStatus;
import com.salesmap.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 자동 분석 후보(최근 N건)에 미팅 메모가 자리를 차지하지 않는다. */
class AutoPipelineServiceTest {

    private static final Long USER_ID = 1L;

    private final IntegrationRepository integrationRepository = mock(IntegrationRepository.class);
    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final AnalysisJobService analysisJobService = mock(AnalysisJobService.class);
    private final AutoPipelineService autoPipelineService = new AutoPipelineService(
            mock(GmailIntegrationService.class),
            integrationRepository,
            sourceRepository,
            analysisJobService,
            mock(UserRepository.class)
    );

    @Test
    @SuppressWarnings("unchecked")
    void 자동_분석_후보는_메일만_고른다() {
        ((Set<Long>) ReflectionTestUtils.getField(autoPipelineService, "enabledUserIds")).add(USER_ID);
        Integration gmail = mock(Integration.class);
        when(gmail.getStatus()).thenReturn(IntegrationStatus.CONNECTED);
        when(integrationRepository.findByUserIdAndProvider(USER_ID, IntegrationProvider.GMAIL)).thenReturn(Optional.of(gmail));
        LocalDateTime now = LocalDateTime.now();
        when(sourceRepository.findByUserId(USER_ID)).thenReturn(List.of(
                source(1L, SourceType.MEETING_NOTE, now.minusHours(1)),
                source(2L, SourceType.EMAIL, now.minusHours(2))
        ));

        autoPipelineService.runAutoPipeline();

        ArgumentCaptor<List<Source>> candidates = ArgumentCaptor.forClass(List.class);
        verify(analysisJobService).runAutoBatch(eq(USER_ID), candidates.capture(), anyInt());
        assertThat(candidates.getValue()).extracting(Source::getId).containsExactly(2L);
    }

    private Source source(Long id, SourceType sourceType, LocalDateTime collectedAt) {
        User user = new User("sales@example.com", "영업담당", "pw", "USER", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Source source = new Source(user, null, sourceType, null, "제목 " + id, "본문", SourceStatus.COLLECTED, collectedAt);
        ReflectionTestUtils.setField(source, "id", id);
        return source;
    }
}
