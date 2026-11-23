package com.salesmap.backend.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.regex.Pattern;

/** 사용자 행을 잠그고 알림을 보여 줄지 판정한다. 같은 사용자의 요청은 한 번에 하나씩 처리된다. */
@Component
public class NotificationDecisions {

    static final int MAX_IMMEDIATE_PER_DAY = 3;
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final Pattern CLAIM_KEY = Pattern.compile("^[A-Za-z0-9:._-]{1,120}$");

    private final NotificationPreferenceRepository preferences;
    private final ImmediateNotificationClaimRepository claims;
    Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));

    public NotificationDecisions(NotificationPreferenceRepository preferences, ImmediateNotificationClaimRepository claims) {
        this.preferences = preferences;
        this.claims = claims;
    }

    static void requireTime(String morningTime) {
        if (morningTime == null || !TIME.matcher(morningTime).matches()) {
            throw new IllegalArgumentException("아침 알림 시간은 HH:mm 형식이어야 합니다.");
        }
    }

    static void requireKey(String key) {
        if (key == null || !CLAIM_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("알림 키 형식이 올바르지 않습니다.");
        }
    }

    @Transactional
    public NotificationService.Settings updateSettings(String morningTime, Long owner) {
        locked(owner).changeMorningTime(morningTime);
        return new NotificationService.Settings(morningTime);
    }

    @Transactional
    public NotificationService.MorningClaim claimMorningBundle(Long owner) {
        NotificationPreference preference = locked(owner);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalTime morning = LocalTime.parse(preference.getMorningTime());
        if (now.toLocalTime().isBefore(morning) || today.equals(preference.getLastBundleDate())) {
            return new NotificationService.MorningClaim(false);
        }
        preference.markBundleShown(today);
        return new NotificationService.MorningClaim(true);
    }

    @Transactional
    public NotificationService.ImmediateClaim claimImmediate(String key, Long owner) {
        locked(owner);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        int used = (int) claims.countByUserIdAndClaimDate(owner, today);
        if (used >= MAX_IMMEDIATE_PER_DAY || claims.existsByUserIdAndClaimDateAndClaimKey(owner, today, key)) {
            return new NotificationService.ImmediateClaim(false, Math.max(0, MAX_IMMEDIATE_PER_DAY - used));
        }
        claims.save(new ImmediateNotificationClaim(owner, today, key, now.withNano(0)));
        return new NotificationService.ImmediateClaim(true, MAX_IMMEDIATE_PER_DAY - used - 1);
    }

    private NotificationPreference locked(Long owner) {
        return preferences.lockByUserId(owner)
                .orElseThrow(() -> new IllegalStateException("알림 설정을 불러오지 못했습니다."));
    }
}
