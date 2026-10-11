package com.salesmap.backend.analysis.entity;

import com.salesmap.backend.global.entity.BaseEntity;
import com.salesmap.backend.source.entity.Source;
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
@Table(name = "analyses")
public class Analysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(length = 255)
    private String customerName;

    @Column(length = 100)
    private String contactName;

    @Column(length = 255)
    private String productName;

    @Column
    private Long amount;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String attendees;

    @Column(length = 50)
    private String actionType;

    @Column
    private Long targetScheduleId;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String targetScheduleTitle;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String actionReason;

    @Column(length = 50)
    private String businessType;

    @Column
    private Double businessRelevanceScore;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String businessReason;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String scheduleText;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String followUpAction;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AnalysisStatus status;

    @Column
    private LocalDateTime analyzedAt;

    @Column
    private LocalDateTime approvedAt;

    // AI가 처음 만든 결과(수정 가능 필드)의 JSON 스냅샷. 사용자가 고쳐도 바뀌지 않는다 → 학습 데이터의 "AI 원본".
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String aiSnapshot;

    // 의미 필드를 보정한 엔진 (rule / ollama / openai / mock)
    @Column(length = 50)
    private String llmProvider;

    @Column(length = 100)
    private String llmModel;

    @Column(length = 1000)
    private String llmFallbackReason;

    // 한 번도 수정하지 않고 승인했는지 (true면 AI 결과가 그대로 정답으로 쓰였다는 뜻)
    @Column
    private Boolean approvedWithoutEdit;

    protected Analysis() {
    }

    public Analysis(
            Source source,
            String customerName,
            String contactName,
            String productName,
            Long amount,
            String attendees,
            String actionType,
            Long targetScheduleId,
            String targetScheduleTitle,
            String actionReason,
            String businessType,
            Double businessRelevanceScore,
            String businessReason,
            String scheduleText,
            String followUpAction,
            String summary,
            AnalysisStatus status,
            LocalDateTime analyzedAt,
            LocalDateTime approvedAt
    ) {
        this.source = source;
        this.customerName = customerName;
        this.contactName = contactName;
        this.productName = productName;
        this.amount = amount;
        this.attendees = attendees;
        this.actionType = actionType;
        this.targetScheduleId = targetScheduleId;
        this.targetScheduleTitle = targetScheduleTitle;
        this.actionReason = actionReason;
        this.businessType = businessType;
        this.businessRelevanceScore = businessRelevanceScore;
        this.businessReason = businessReason;
        this.scheduleText = scheduleText;
        this.followUpAction = followUpAction;
        this.summary = summary;
        this.status = status;
        this.analyzedAt = analyzedAt;
        this.approvedAt = approvedAt;
    }

    public Long getId() {
        return id;
    }

    public Source getSource() {
        return source;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getContactName() {
        return contactName;
    }

    public String getProductName() {
        return productName;
    }

    public Long getAmount() {
        return amount;
    }

    public String getAttendees() {
        return attendees;
    }

    public String getActionType() {
        return actionType;
    }

    public Long getTargetScheduleId() {
        return targetScheduleId;
    }

    public String getTargetScheduleTitle() {
        return targetScheduleTitle;
    }

    public String getActionReason() {
        return actionReason;
    }

    public String getBusinessType() {
        return businessType;
    }

    public Double getBusinessRelevanceScore() {
        return businessRelevanceScore;
    }

    public String getBusinessReason() {
        return businessReason;
    }

    public String getScheduleText() {
        return scheduleText;
    }

    public String getFollowUpAction() {
        return followUpAction;
    }

    public String getSummary() {
        return summary;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public String getAiSnapshot() {
        return aiSnapshot;
    }

    public String getLlmProvider() {
        return llmProvider;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public String getLlmFallbackReason() {
        return llmFallbackReason;
    }

    public Boolean getApprovedWithoutEdit() {
        return approvedWithoutEdit;
    }

    public void recordAiOrigin(String aiSnapshot, String llmProvider, String llmModel, String llmFallbackReason) {
        this.aiSnapshot = aiSnapshot;
        this.llmProvider = llmProvider;
        this.llmModel = llmModel;
        this.llmFallbackReason = llmFallbackReason;
    }

    public void markApproved(boolean withoutEdit) {
        this.status = AnalysisStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
        this.approvedWithoutEdit = withoutEdit;
    }

    public void markRejected() {
        this.status = AnalysisStatus.REJECTED;
    }

    public void markDeleted() {
        this.status = AnalysisStatus.DELETED;
    }

    public void updateEditableFields(
            String customerName,
            String contactName,
            String productName,
            Long amount,
            String attendees,
            String actionType,
            String targetScheduleTitle,
            String actionReason,
            String scheduleText,
            String followUpAction,
            String summary,
            String businessType
    ) {
        if (businessType != null && !businessType.isBlank()) {
            // 영업/비영업 분류는 사용자가 바꿀 때만 반영한다(요청에 없으면 기존 값 유지).
            this.businessType = businessType;
        }
        this.customerName = customerName;
        this.contactName = contactName;
        this.productName = productName;
        this.amount = amount;
        this.attendees = attendees;
        this.actionType = actionType;
        this.targetScheduleTitle = targetScheduleTitle;
        this.actionReason = actionReason;
        this.scheduleText = scheduleText;
        this.followUpAction = followUpAction;
        this.summary = summary;
    }

    public void linkCreatedSchedule(Long scheduleId, String scheduleTitle) {
        this.targetScheduleId = scheduleId;
        this.targetScheduleTitle = scheduleTitle;
        this.actionReason = appendActionReason(this.actionReason, "Created scheduleId=" + scheduleId);
    }

    public void linkTargetSchedule(Long scheduleId, String scheduleTitle, String actionResult) {
        this.targetScheduleId = scheduleId;
        this.targetScheduleTitle = scheduleTitle;
        this.actionReason = appendActionReason(this.actionReason, actionResult + " scheduleId=" + scheduleId);
    }

    private String appendActionReason(String currentReason, String reasonToAppend) {
        String nextReason = currentReason == null || currentReason.isBlank()
                ? reasonToAppend
                : currentReason + " / " + reasonToAppend;

        if (nextReason.length() <= 255) {
            return nextReason;
        }

        return nextReason.substring(0, 255);
    }
}
