package com.salesmap.backend.task;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** D로 전달 대기 기록. task 변경과 같은 DB 트랜잭션으로 보관한다. */
@Entity
@Table(name = "task_revision_outbox", indexes = @Index(name = "idx_task_revision_owner", columnList = "owner_id"))
public class TaskRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "owner_id", nullable = false) private Long ownerId;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String payload;
    @Column(nullable = false) private boolean delivered;
    @Column(name = "delivered_revision_id") private String deliveredRevisionId;
    @Column(name = "delivered_at") private LocalDateTime deliveredAt;
    protected TaskRevision() {}
    public TaskRevision(Long ownerId, String payload) { this.ownerId = ownerId; this.payload = payload; }
    public Long getId() { return id; }
    public String getPayload() { return payload; }
    public boolean isDelivered() { return delivered; }
    public String getDeliveredRevisionId() { return deliveredRevisionId; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void markDelivered(String revisionId, LocalDateTime deliveredAt) {
        this.delivered = true;
        this.deliveredRevisionId = revisionId;
        this.deliveredAt = deliveredAt;
    }
}
