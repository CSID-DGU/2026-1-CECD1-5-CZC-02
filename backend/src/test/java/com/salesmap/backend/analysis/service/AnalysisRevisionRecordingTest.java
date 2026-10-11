package com.salesmap.backend.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.ai.client.AiClient;
import com.salesmap.backend.analysis.dto.AnalysisUpdateRequest;
import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.entity.AnalysisRevision;
import com.salesmap.backend.analysis.entity.AnalysisStatus;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.analysis.repository.AnalysisRevisionRepository;
import com.salesmap.backend.analysis.repository.ReplyDraftRepository;
import com.salesmap.backend.customer.service.CustomerTimelineService;
import com.salesmap.backend.gmail.service.GmailIntegrationService;
import com.salesmap.backend.schedule.repository.ScheduleRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;
import com.salesmap.backend.source.repository.SourceGroupRepository;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisRevisionRecordingTest {

    private static final Long USER_ID = 1L;
    private static final Long ANALYSIS_ID = 10L;

    private final AnalysisRepository analysisRepository = mock(AnalysisRepository.class);
    private final AnalysisRevisionRepository revisionRepository = mock(AnalysisRevisionRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AnalysisService analysisService;
    private Analysis analysis;

    @BeforeEach
    void setUp() throws Exception {
        analysisService = new AnalysisService(
                analysisRepository,
                revisionRepository,
                mock(ReplyDraftRepository.class),
                mock(SourceRepository.class),
                mock(SourceGroupRepository.class),
                mock(ScheduleRepository.class),
                mock(AiClient.class),
                mock(CustomerTimelineService.class),
                mock(GmailIntegrationService.class),
                objectMapper
        );

        User user = new User("sales@example.com", "영업담당", "pw", "USER", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Source source = new Source(user, null, SourceType.EMAIL, "msg-1", "미팅 요청", "본문", SourceStatus.COLLECTED,
                LocalDateTime.now());
        ReflectionTestUtils.setField(source, "id", 100L);

        analysis = new Analysis(source, "대성물류", "김현우", "배차 시스템", null, "김현우", "CREATE", null, null,
                "근거", "SALES_ACTIVITY", 0.9, "영업 근거", "미팅 (2026-09-15T10:00)", "미팅 준비", "요약",
                AnalysisStatus.ANALYZED, LocalDateTime.now(), null);
        ReflectionTestUtils.setField(analysis, "id", ANALYSIS_ID);
        analysis.recordAiOrigin(objectMapper.writeValueAsString(AnalysisEditableFields.valuesOf(analysis)),
                "ollama", "qwen2.5:7b", null);

        when(analysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.of(analysis));
    }

    @Test
    void 바뀐_필드만_AI_원본값과_함께_기록한다() {
        analysisService.updateAnalysis(ANALYSIS_ID, requestWith("대성물류(주)", "SALES_ACTIVITY"), USER_ID);

        ArgumentCaptor<AnalysisRevision> captor = ArgumentCaptor.forClass(AnalysisRevision.class);
        verify(revisionRepository, times(1)).save(captor.capture());
        AnalysisRevision revision = captor.getValue();
        assertThat(revision.getFieldName()).isEqualTo("customerName");
        assertThat(revision.getAiValue()).isEqualTo("대성물류");
        assertThat(revision.getPreviousValue()).isEqualTo("대성물류");
        assertThat(revision.getUserValue()).isEqualTo("대성물류(주)");
    }

    @Test
    void 영업_비영업_분류를_바꾸면_기록된다() {
        analysisService.updateAnalysis(ANALYSIS_ID, requestWith("대성물류", "NON_BUSINESS"), USER_ID);

        ArgumentCaptor<AnalysisRevision> captor = ArgumentCaptor.forClass(AnalysisRevision.class);
        verify(revisionRepository).save(captor.capture());
        assertThat(captor.getValue().getFieldName()).isEqualTo("businessType");
        assertThat(captor.getValue().getUserValue()).isEqualTo("NON_BUSINESS");
        assertThat(analysis.getBusinessType()).isEqualTo("NON_BUSINESS");
    }

    @Test
    void 아무것도_안_바꾸면_기록하지_않고_분류가_없으면_기존값을_유지한다() {
        analysisService.updateAnalysis(ANALYSIS_ID, requestWith("대성물류", null), USER_ID);

        verify(revisionRepository, never()).save(any());
        assertThat(analysis.getBusinessType()).isEqualTo("SALES_ACTIVITY");
    }

    @Test
    void 잘못된_분류값은_거부한다() {
        assertThatThrownBy(() -> analysisService.updateAnalysis(ANALYSIS_ID, requestWith("대성물류", "SALES"), USER_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 수정_없이_승인하면_무수정_승인으로_표시한다() {
        when(revisionRepository.existsByAnalysisId(ANALYSIS_ID)).thenReturn(false);

        analysisService.approve(analysis);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.APPROVED);
        assertThat(analysis.getApprovedWithoutEdit()).isTrue();
    }

    @Test
    void 승인된_분석은_거절할_수_없다() {
        analysis.markApproved(true);

        assertThatThrownBy(() -> analysisService.rejectAnalysis(ANALYSIS_ID, USER_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 거절하면_REJECTED_상태가_된다() {
        analysisService.rejectAnalysis(ANALYSIS_ID, USER_ID);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.REJECTED);
    }

    private AnalysisUpdateRequest requestWith(String customerName, String businessType) {
        // 나머지 필드는 현재 값 그대로 보낸다(프론트 수정 폼과 같은 방식)
        return new AnalysisUpdateRequest(
                customerName, "김현우", "배차 시스템", null, "김현우", "CREATE", null, "근거",
                "미팅 (2026-09-15T10:00)", "미팅 준비", "요약", businessType
        );
    }
}
