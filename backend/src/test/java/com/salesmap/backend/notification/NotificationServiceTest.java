package com.salesmap.backend.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 아침 묶음 하루 1번, 즉시 알림 하루 3건. MySQL·네트워크 없음. */
class NotificationServiceTest {

    private static final Long OWNER = 1L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NotificationPreferenceRepository preferences = mock(NotificationPreferenceRepository.class);
    private final ImmediateNotificationClaimRepository claims = mock(ImmediateNotificationClaimRepository.class);
    private final NotificationPreferenceInitializer initializer = mock(NotificationPreferenceInitializer.class);
    private final NotificationPreference preference = new NotificationPreference(OWNER, "09:00");
    private final Set<String> claimed = new HashSet<>();
    private NotificationDecisions decisions;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        when(preferences.lockByUserId(OWNER)).thenReturn(Optional.of(preference));
        when(claims.countByUserIdAndClaimDate(eq(OWNER), any())).thenAnswer(call -> (long) claimed.size());
        when(claims.existsByUserIdAndClaimDateAndClaimKey(eq(OWNER), any(), anyString()))
                .thenAnswer(call -> claimed.contains(call.getArgument(2, String.class)));
        decisions = new NotificationDecisions(preferences, claims);
        service = new NotificationService(preferences, initializer, decisions);
    }

    private void at(int day, int hour, int minute) {
        decisions.clock = Clock.fixed(ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, KST).toInstant(), KST);
    }

    private boolean claim(String key) {
        NotificationService.ImmediateClaim result = service.claimImmediate(key, OWNER);
        if (result.allowed()) {
            claimed.add(key);
        }
        return result.allowed();
    }

    @Test
    void 아침_묶음은_설정_시간_이후_하루_한_번만_보여_준다() {
        at(24, 8, 59);
        assertThat(service.claimMorningBundle(OWNER).show()).isFalse();
        assertThat(preference.getLastBundleDate()).isNull();

        at(24, 9, 0);
        assertThat(service.claimMorningBundle(OWNER).show()).isTrue();
        assertThat(preference.getLastBundleDate()).isEqualTo(LocalDate.of(2026, 11, 24));

        at(24, 18, 0);
        assertThat(service.claimMorningBundle(OWNER).show()).isFalse();

        at(25, 9, 30);
        assertThat(service.claimMorningBundle(OWNER).show()).isTrue();
    }

    @Test
    void 바꾼_아침_시간을_따른다() {
        service.updateSettings("10:30", OWNER);
        assertThat(preference.getMorningTime()).isEqualTo("10:30");

        at(24, 10, 0);
        assertThat(service.claimMorningBundle(OWNER).show()).isFalse();
        at(24, 10, 30);
        assertThat(service.claimMorningBundle(OWNER).show()).isTrue();
    }

    @Test
    void 잘못된_시간은_저장하지_않는다() {
        for (String value : new String[]{null, "", "9:00", "24:00", "09:60", "오전 9시"}) {
            assertThatThrownBy(() -> service.updateSettings(value, OWNER)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(preference.getMorningTime()).isEqualTo("09:00");
    }

    @Test
    void 설정이_없으면_기본_시간이고_조회만으로_저장하지_않는다() {
        when(preferences.findById(OWNER)).thenReturn(Optional.empty());

        assertThat(service.settings(OWNER).morningTime()).isEqualTo("09:00");
        verify(initializer, never()).ensure(anyLong());
    }

    @Test
    void 즉시_알림은_하루_세_건까지만_허용한다() {
        at(24, 14, 0);
        assertThat(claim("schedule:1")).isTrue();
        assertThat(claim("schedule:2")).isTrue();
        assertThat(claim("schedule:3")).isTrue();
        assertThat(claim("schedule:4")).isFalse();
        assertThat(claimed).hasSize(3);
    }

    @Test
    void 같은_알림은_두_번_허용하지_않는다() {
        at(24, 14, 0);
        assertThat(claim("schedule:1")).isTrue();
        assertThat(claim("schedule:1")).isFalse();
        assertThat(claimed).hasSize(1);
    }

    @Test
    void 이미_있는_설정은_다시_만들지_않는다() {
        NotificationPreferenceInitializer real = new NotificationPreferenceInitializer(preferences);
        when(preferences.existsById(OWNER)).thenReturn(true);
        real.ensure(OWNER);
        verify(preferences, never()).createIfAbsent(anyLong(), anyString());

        when(preferences.existsById(OWNER)).thenReturn(false);
        real.ensure(OWNER);
        verify(preferences).createIfAbsent(OWNER, "09:00");
    }

    @Test
    void 잘못된_알림_키는_거절한다() {
        for (String key : new String[]{null, "", "한글키", "a b", "x".repeat(121)}) {
            assertThatThrownBy(() -> service.claimImmediate(key, OWNER)).isInstanceOf(IllegalArgumentException.class);
        }
        verify(claims, never()).save(any());
    }
}
