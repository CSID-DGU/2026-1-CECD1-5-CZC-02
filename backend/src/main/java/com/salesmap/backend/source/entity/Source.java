package com.salesmap.backend.source.entity;

import com.salesmap.backend.global.entity.BaseEntity;
import com.salesmap.backend.integration.entity.Integration;
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
@Table(name = "sources")
public class Source extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id")
    private Integration integration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_group_id")
    private SourceGroup sourceGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SourceType sourceType;

    @Column(length = 255)
    private String externalSourceId;

    @Column(nullable = false, length = 255)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SourceStatus status;

    @Column
    private LocalDateTime collectedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private SourceDirection direction;

    @Column(length = 255)
    private String senderName;

    @Column(length = 255)
    private String senderEmail;

    @Column(length = 1000)
    private String receiverNames;

    @Column(length = 1000)
    private String receiverEmails;

    @Column
    private LocalDateTime sentAt;

    protected Source() {
    }

    public Source(
            User user,
            Integration integration,
            SourceType sourceType,
            String externalSourceId,
            String title,
            String content,
            SourceStatus status,
            LocalDateTime collectedAt
    ) {
        this.user = user;
        this.integration = integration;
        this.sourceType = sourceType;
        this.externalSourceId = externalSourceId;
        this.title = title;
        this.content = content;
        this.status = status;
        this.collectedAt = collectedAt;
    }

    public Source(
            User user,
            Integration integration,
            SourceGroup sourceGroup,
            SourceType sourceType,
            String externalSourceId,
            String title,
            String content,
            SourceStatus status,
            LocalDateTime collectedAt,
            SourceDirection direction,
            String senderName,
            String senderEmail,
            String receiverNames,
            String receiverEmails,
            LocalDateTime sentAt
    ) {
        this.user = user;
        this.integration = integration;
        this.sourceGroup = sourceGroup;
        this.sourceType = sourceType;
        this.externalSourceId = externalSourceId;
        this.title = title;
        this.content = content;
        this.status = status;
        this.collectedAt = collectedAt;
        this.direction = direction;
        this.senderName = senderName;
        this.senderEmail = senderEmail;
        this.receiverNames = receiverNames;
        this.receiverEmails = receiverEmails;
        this.sentAt = sentAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Integration getIntegration() {
        return integration;
    }

    public SourceGroup getSourceGroup() {
        return sourceGroup;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getExternalSourceId() {
        return externalSourceId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public SourceStatus getStatus() {
        return status;
    }

    public LocalDateTime getCollectedAt() {
        return collectedAt;
    }

    public SourceDirection getDirection() {
        return direction;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public String getReceiverNames() {
        return receiverNames;
    }

    public String getReceiverEmails() {
        return receiverEmails;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void markAnalyzed() {
        this.status = SourceStatus.ANALYZED;
    }

    public void markDeleted() {
        this.status = SourceStatus.DELETED;
    }
}
