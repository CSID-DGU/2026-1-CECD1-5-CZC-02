package com.salesmap.backend.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** 사용자별 아침 묶음 시간과 오늘 묶음을 이미 보여 줬는지. */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "morning_time", nullable = false, length = 5)
    private String morningTime;

    @Column(name = "last_bundle_date")
    private LocalDate lastBundleDate;

    protected NotificationPreference() {
    }

    NotificationPreference(Long userId, String morningTime) {
        this.userId = userId;
        this.morningTime = morningTime;
    }

    public String getMorningTime() {
        return morningTime;
    }

    public LocalDate getLastBundleDate() {
        return lastBundleDate;
    }

    void changeMorningTime(String morningTime) {
        this.morningTime = morningTime;
    }

    void markBundleShown(LocalDate date) {
        this.lastBundleDate = date;
    }
}
