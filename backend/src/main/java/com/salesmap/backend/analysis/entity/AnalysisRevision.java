package com.salesmap.backend.analysis.entity;

import com.salesmap.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 사용자가 AI 분석 결과의 필드 하나를 고친 기록.
 * <p>
 * aiValue(AI 원본) → userValue(사용자 최종값) 쌍이 학습 데이터가 된다.
 * previousValue는 여러 번 고친 경우 직전 값이다.
 */
@Entity
@Table(name = "analysis_revisions", indexes = {
        @Index(name = "idx_analysis_revisions_analysis", columnList = "analysis_id"),
        @Index(name = "idx_analysis_revisions_owner", columnList = "owner_user_id")
})
public class AnalysisRevision extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id")
    private Analysis analysis;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(length = 40)
    private String screen;

    @Column(name = "target_type", length = 40)
    private String targetType;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(nullable = false, length = 50)
    private String fieldName;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String aiValue;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String previousValue;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String userValue;

    @Lob
    @Column(name = "proposed_value", columnDefinition = "LONGTEXT")
    private String proposedValue;

    @Lob
    @Column(name = "final_value", columnDefinition = "LONGTEXT")
    private String finalValue;

    @Column(length = 30)
    private String action;

    @Column(name = "proposed_by", length = 20)
    private String proposedBy;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    protected AnalysisRevision() {
    }

    public AnalysisRevision(Analysis analysis, String fieldName, String aiValue, String previousValue, String userValue) {
        this.analysis = analysis;
        this.fieldName = fieldName;
        this.aiValue = aiValue;
        this.previousValue = previousValue;
        this.userValue = userValue;
    }

    public AnalysisRevision(Long ownerUserId, String screen, String targetType, String targetId,
                            String fieldName, String proposedValue, String finalValue, String action,
                            String proposedBy, String modelVersion, String actorId, LocalDateTime decidedAt) {
        this.ownerUserId = ownerUserId;
        this.screen = screen;
        this.targetType = targetType;
        this.targetId = targetId;
        this.fieldName = fieldName;
        this.proposedValue = proposedValue;
        this.finalValue = finalValue;
        this.action = action;
        this.proposedBy = proposedBy;
        this.modelVersion = modelVersion;
        this.actorId = actorId;
        this.decidedAt = decidedAt;
    }

    public Long getId() {
        return id;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getAiValue() {
        return aiValue;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getUserValue() {
        return userValue;
    }

    public Long getOwnerUserId() { return ownerUserId; }
    public String getScreen() { return screen; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getProposedValue() { return proposedValue; }
    public String getFinalValue() { return finalValue; }
    public String getAction() { return action; }
    public String getProposedBy() { return proposedBy; }
    public String getModelVersion() { return modelVersion; }
    public String getActorId() { return actorId; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
}
