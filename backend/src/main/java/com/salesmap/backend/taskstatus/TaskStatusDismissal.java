package com.salesmap.backend.taskstatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/** 사람이 "아니에요"라고 한 제안. 같은 메일로 다시 묻지 않고, 새 메일이 오면 다시 제안할 수 있다. */
@Entity
@Table(name = "task_status_dismissals", uniqueConstraints = @UniqueConstraint(
        name = "uk_task_status_dismissal", columnNames = {"user_id", "task_id", "source_id"}))
public class TaskStatusDismissal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_id", nullable = false, length = 40)
    private String taskId;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "dismissed_at", nullable = false)
    private LocalDateTime dismissedAt;

    protected TaskStatusDismissal() {
    }

    TaskStatusDismissal(Long userId, String taskId, Long sourceId, LocalDateTime dismissedAt) {
        this.userId = userId;
        this.taskId = taskId;
        this.sourceId = sourceId;
        this.dismissedAt = dismissedAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public Long getSourceId() {
        return sourceId;
    }
}
