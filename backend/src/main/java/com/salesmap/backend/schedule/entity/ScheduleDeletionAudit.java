package com.salesmap.backend.schedule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Durable record of who deleted a schedule and when. */
@Entity
@Table(name = "schedule_deletion_audits")
public class ScheduleDeletionAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long scheduleId;

    @Column(nullable = false)
    private Long deletedByUserId;

    @Column(nullable = false)
    private LocalDateTime deletedAt;

    @Column(nullable = false, length = 255)
    private String title;

    protected ScheduleDeletionAudit() {
    }

    public ScheduleDeletionAudit(Long scheduleId, Long deletedByUserId, LocalDateTime deletedAt, String title) {
        this.scheduleId = scheduleId;
        this.deletedByUserId = deletedByUserId;
        this.deletedAt = deletedAt;
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public Long getDeletedByUserId() {
        return deletedByUserId;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public String getTitle() {
        return title;
    }
}
