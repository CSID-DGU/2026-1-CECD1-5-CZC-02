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

    public void markApproved() {
        this.status = AnalysisStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
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
            String summary
    ) {
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
