package com.salesmap.backend.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ImmediateNotificationClaimRepository extends JpaRepository<ImmediateNotificationClaim, Long> {

    boolean existsByUserIdAndClaimDateAndClaimKey(Long userId, LocalDate claimDate, String claimKey);

    long countByUserIdAndClaimDate(Long userId, LocalDate claimDate);
}
