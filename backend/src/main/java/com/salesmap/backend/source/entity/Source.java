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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SourceType sourceType;

    @Column(length = 255)
    private String externalSourceId;

    @Column(nullable = false, length = 255)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SourceStatus status;

    @Column
    private LocalDateTime collectedAt;

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

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Integration getIntegration() {
        return integration;
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

    public void markAnalyzed() {
        this.status = SourceStatus.ANALYZED;
    }
}
