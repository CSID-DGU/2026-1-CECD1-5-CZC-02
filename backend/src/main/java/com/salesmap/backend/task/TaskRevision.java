package com.salesmap.backend.task;

import jakarta.persistence.*;

/** D로 전달 대기 기록. task 변경과 같은 DB 트랜잭션으로 보관한다. */
@Entity
@Table(name = "task_revision_outbox", indexes = @Index(name = "idx_task_revision_owner", columnList = "owner_id"))
public class TaskRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "owner_id", nullable = false) private Long ownerId;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String payload;
    protected TaskRevision() {}
    public TaskRevision(Long ownerId, String payload) { this.ownerId = ownerId; this.payload = payload; }
    public Long getId() { return id; }
    public String getPayload() { return payload; }
}
