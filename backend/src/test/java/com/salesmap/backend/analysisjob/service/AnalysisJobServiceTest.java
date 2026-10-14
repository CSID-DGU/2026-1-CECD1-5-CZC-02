package com.salesmap.backend.analysisjob.service;

import com.salesmap.backend.analysis.dto.AnalysisCreateRequest;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.analysis.service.AnalysisService;
import com.salesmap.backend.analysisjob.dto.AnalysisJobStartResponse;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.entity.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 미팅 메모(MEETING_NOTE) 원본은 메일 일괄·자동 분석 대상이 아니다. */
class AnalysisJobServiceTest {

    private static final Long USER_ID = 1L;

    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final AnalysisService analysisService = mock(AnalysisService.class);
    private final AnalysisJobService analysisJobService = new AnalysisJobService(
            sourceRepository,
            mock(AnalysisRepository.class),
            analysisService
    );

    @AfterEach
    void tearDown() {
        analysisJobService.shutdown();
    }

    @Test
    void 자동_분석은_메일만_분석하고_미팅_메모는_건너뛴다() {
        Source memo = source(1L, SourceType.MEETING_NOTE, LocalDateTime.of(2026, 10, 14, 9, 0));
        Source mail = source(2L, SourceType.EMAIL, LocalDateTime.of(2026, 10, 14, 10, 0));

        analysisJobService.runAutoBatch(USER_ID, List.of(memo, mail), 5);

        ArgumentCaptor<AnalysisCreateRequest> captor = ArgumentCaptor.forClass(AnalysisCreateRequest.class);
        verify(analysisService).createAnalysis(captor.capture(), eq(USER_ID));
        assertThat(captor.getAllValues()).extracting(AnalysisCreateRequest::sourceId).containsExactly(2L);
    }

    @Test
    void 미팅_메모만_있으면_분석을_시작하지_않는다() {
        Source memo = source(1L, SourceType.MEETING_NOTE, LocalDateTime.of(2026, 10, 14, 9, 0));

        analysisJobService.runAutoBatch(USER_ID, List.of(memo), 5);

        verify(analysisService, never()).createAnalysis(any(), anyLong());
        verify(analysisService, never()).createGroupAnalysis(any(), anyLong());
    }

    @Test
    void 수동_일괄_분석은_메일함과_같은_메일_목록에서_고른다() {
        when(sourceRepository.findByUserIdAndSourceTypeAndStatusNot(eq(USER_ID), eq(SourceType.EMAIL), eq(SourceStatus.DELETED), any()))
                .thenReturn(new PageImpl<>(List.of()));

        AnalysisJobStartResponse response = analysisJobService.startManual(USER_ID);

        assertThat(response.result()).isEqualTo("NOTHING_TO_ANALYZE");
        verify(sourceRepository).findByUserIdAndSourceTypeAndStatusNot(eq(USER_ID), eq(SourceType.EMAIL), eq(SourceStatus.DELETED), any());
        verify(sourceRepository, never()).findByUserIdAndStatusNot(anyLong(), any(), any());
    }

    private Source source(Long id, SourceType sourceType, LocalDateTime collectedAt) {
        User user = new User("sales@example.com", "영업담당", "pw", "USER", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Source source = new Source(user, null, sourceType, null, "제목 " + id, "본문", SourceStatus.COLLECTED, collectedAt);
        ReflectionTestUtils.setField(source, "id", id);
        return source;
    }
}
