package com.salesmap.backend.talent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * 제출 리스트 한 줄: 할 일(S-05 PROPOSAL_SUBMISSION 등)에 담당자가 고른 인력 후보.
 * 사람이 고른 것만 들어간다 (추천 결과를 자동으로 넣지 않는다). 후보 이력서를 지워도 남지 않게 읽을 때 걸러 낸다.
 */
@Entity
@Table(name = "talent_submissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "task_id", "profile_id"}),
        indexes = @Index(name = "idx_talent_submission_owner_task", columnList = "owner_id,task_id"))
public class TalentSubmission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "owner_id", nullable = false) private Long ownerId;
    @Column(name = "task_id", nullable = false, length = 40) private String taskId;
    @Column(name = "profile_id", nullable = false) private Long profileId;
    @Column(nullable = false) private int position;
    @Column(name = "added_at", nullable = false) private LocalDateTime addedAt;

    protected TalentSubmission() {}

    public TalentSubmission(Long ownerId, String taskId, Long profileId, int position, LocalDateTime addedAt) {
        this.ownerId = ownerId;
        this.taskId = taskId;
        this.profileId = profileId;
        this.position = position;
        this.addedAt = addedAt;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getTaskId() { return taskId; }
    public Long getProfileId() { return profileId; }
    public int getPosition() { return position; }
    public LocalDateTime getAddedAt() { return addedAt; }
}
