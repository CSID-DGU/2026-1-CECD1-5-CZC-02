package com.salesmap.backend.source.service;

import com.salesmap.backend.integration.repository.IntegrationRepository;
import com.salesmap.backend.source.dto.SourceResponse;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.entity.UserStatus;
import com.salesmap.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

/** 메일함 목록에는 메일만 나온다. 미팅 메모(MEETING_NOTE) 원본은 회의록 화면에서 본다. */
class SourceServiceTest {

    private static final Long USER_ID = 1L;

    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SourceService sourceService = new SourceService(
            sourceRepository,
            userRepository,
            mock(IntegrationRepository.class)
    );

    @Test
    void 메일함_목록은_EMAIL_원본만_조회한다() {
        User user = new User("sales@example.com", "영업담당", "pw", "USER", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Source mail = new Source(user, null, SourceType.EMAIL, "msg-1", "견적 문의", "본문", SourceStatus.COLLECTED,
                LocalDateTime.of(2026, 10, 14, 10, 0));
        ReflectionTestUtils.setField(mail, "id", 2L);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(sourceRepository.findByUserIdAndSourceTypeAndStatusNot(eq(USER_ID), eq(SourceType.EMAIL), eq(SourceStatus.DELETED), any()))
                .thenReturn(new PageImpl<>(List.of(mail)));

        List<SourceResponse> sources = sourceService.getSourcesByUser(null, USER_ID, 0, 30);

        assertThat(sources).extracting(SourceResponse::sourceId).containsExactly(2L);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(sourceRepository).findByUserIdAndSourceTypeAndStatusNot(
                eq(USER_ID), eq(SourceType.EMAIL), eq(SourceStatus.DELETED), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(30);
        assertThat(pageable.getValue().getSort().getOrderFor("sentAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        verify(sourceRepository, never()).findByUserIdAndStatusNot(anyLong(), any(), any());
    }
}
