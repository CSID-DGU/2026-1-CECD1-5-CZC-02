package com.salesmap.backend.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 그날 보여 준 즉시 알림 한 건. 하루 3건 제한을 서버에서 센다 (overview 절대 규칙 3). */
@Entity
@Table(name = "immediate_notification_claims", uniqueConstraints = @UniqueConstraint(
        name = "uk_immediate_notification", columnNames = {"user_id", "claim_date", "claim_key"}))
public class ImmediateNotificationClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "claim_date", nullable = false)
    private LocalDate claimDate;

    @Column(name = "claim_key", nullable = false, length = 120)
    private String claimKey;

    @Column(name = "claimed_at", nullable = false)
    private LocalDateTime claimedAt;

    protected ImmediateNotificationClaim() {
    }

    ImmediateNotificationClaim(Long userId, LocalDate claimDate, String claimKey, LocalDateTime claimedAt) {
        this.userId = userId;
        this.claimDate = claimDate;
        this.claimKey = claimKey;
        this.claimedAt = claimedAt;
    }
}
