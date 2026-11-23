package com.salesmap.backend.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 횟수 규칙 (overview 절대 규칙 3, AGENTS 규칙 6): 아침 묶음은 하루 1번, 즉시 알림은 하루 3건까지.
 * 앱 안에서 보여 줄지만 판정하고 메일·메시지는 보내지 않는다.
 * 설정 행을 먼저 만들어 커밋한 뒤 판정 트랜잭션을 시작한다. 한 트랜잭션에 묶으면 동시 요청이 교착 상태에 빠진다.
 */
@Service
public class NotificationService {

    static final String DEFAULT_MORNING_TIME = "09:00";

    private final NotificationPreferenceRepository preferences;
    private final NotificationPreferenceInitializer initializer;
    private final NotificationDecisions decisions;

    public NotificationService(NotificationPreferenceRepository preferences, NotificationPreferenceInitializer initializer,
                               NotificationDecisions decisions) {
        this.preferences = preferences;
        this.initializer = initializer;
        this.decisions = decisions;
    }

    public record Settings(String morningTime) {
    }

    public record MorningClaim(boolean show) {
    }

    public record ImmediateClaim(boolean allowed, int remainingToday) {
    }

    @Transactional(readOnly = true)
    public Settings settings(Long owner) {
        return new Settings(preferences.findById(owner)
                .map(NotificationPreference::getMorningTime)
                .orElse(DEFAULT_MORNING_TIME));
    }

    public Settings updateSettings(String morningTime, Long owner) {
        NotificationDecisions.requireTime(morningTime);
        initializer.ensure(owner);
        return decisions.updateSettings(morningTime, owner);
    }

    /** 설정한 시간이 지났고 오늘 아직 안 보여 줬을 때만 true. 여러 탭이 동시에 물어도 한 곳만 받는다. */
    public MorningClaim claimMorningBundle(Long owner) {
        initializer.ensure(owner);
        return decisions.claimMorningBundle(owner);
    }

    /** 같은 알림은 한 번만, 하루 3건까지만 허용한다. */
    public ImmediateClaim claimImmediate(String key, Long owner) {
        NotificationDecisions.requireKey(key);
        initializer.ensure(owner);
        return decisions.claimImmediate(key, owner);
    }
}
