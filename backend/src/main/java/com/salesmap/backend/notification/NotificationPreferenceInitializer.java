package com.salesmap.backend.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 처음 쓰는 사용자의 설정 행을 판정 트랜잭션 전에 만들어 커밋한다 ({@link NotificationService} 참고). */
@Component
public class NotificationPreferenceInitializer {

    private final NotificationPreferenceRepository preferences;

    public NotificationPreferenceInitializer(NotificationPreferenceRepository preferences) {
        this.preferences = preferences;
    }

    @Transactional
    public void ensure(Long owner) {
        if (!preferences.existsById(owner)) {
            preferences.createIfAbsent(owner, NotificationService.DEFAULT_MORNING_TIME);
        }
    }
}
