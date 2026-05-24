package com.salesmap.backend.schedule.entity;

import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.global.entity.BaseEntity;
import com.salesmap.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedules")
public class Schedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id")
    private Analysis analysis;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false)
    private LocalDateTime scheduleDateTime;

    @Lob
    @Column
    private String memo;

    @Column
    private LocalDateTime reminderDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ScheduleStatus status;

    protected Schedule() {
    }

    public Schedule(
            User user,
            Analysis analysis,
            String title,
            LocalDateTime scheduleDateTime,
            String memo,
            LocalDateTime reminderDateTime,
            ScheduleStatus status
    ) {
        this.user = user;
        this.analysis = analysis;
        this.title = title;
        this.scheduleDateTime = scheduleDateTime;
        this.memo = memo;
        this.reminderDateTime = reminderDateTime;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getScheduleDateTime() {
        return scheduleDateTime;
    }

    public String getMemo() {
        return memo;
    }

    public LocalDateTime getReminderDateTime() {
        return reminderDateTime;
    }

    public ScheduleStatus getStatus() {
        return status;
    }
}
