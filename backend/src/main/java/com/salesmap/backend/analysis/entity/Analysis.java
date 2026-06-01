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

    @Column(length = 50)
    private String actionType;

    @Column
    private Long targetScheduleId;

    @Column(length = 255)
    private String targetScheduleTitle;

    @Column(length = 255)
    private String actionReason;

    @Column(length = 255)
    private String scheduleText;

    @Column(length = 255)
    private String followUpAction;

    @Lob
    @Column
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
            String actionType,
            Long targetScheduleId,
            String targetScheduleTitle,
            String actionReason,
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
        this.actionType = actionType;
        this.targetScheduleId = targetScheduleId;
        this.targetScheduleTitle = targetScheduleTitle;
        this.actionReason = actionReason;
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
}
