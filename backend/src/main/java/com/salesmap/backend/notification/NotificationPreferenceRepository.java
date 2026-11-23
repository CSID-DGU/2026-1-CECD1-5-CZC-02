package com.salesmap.backend.notification;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    /** 처음 쓰는 사용자의 행을 만든다. 동시에 들어와도 한 행만 생긴다. */
    @Modifying
    @Query(value = "insert ignore into notification_preferences (user_id, morning_time) values (:userId, :morningTime)",
            nativeQuery = true)
    int createIfAbsent(@Param("userId") Long userId, @Param("morningTime") String morningTime);

    /** 같은 사용자의 알림 판정을 한 번에 하나씩 처리한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from NotificationPreference p where p.userId = :userId")
    Optional<NotificationPreference> lockByUserId(@Param("userId") Long userId);
}
