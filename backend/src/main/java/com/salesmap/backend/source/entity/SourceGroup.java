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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "source_groups",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_source_groups_integration_type_external",
                        columnNames = {"integration_id", "source_type", "external_group_id"}
                )
        }
)
public class SourceGroup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integration_id", nullable = false)
    private Integration integration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SourceType sourceType;

    @Column(nullable = false, length = 255)
    private String externalGroupId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false)
    private boolean deduplicated;

    protected SourceGroup() {
    }

    public SourceGroup(
            User user,
            Integration integration,
            SourceType sourceType,
            String externalGroupId,
            String title,
            boolean deduplicated
    ) {
        this.user = user;
        this.integration = integration;
        this.sourceType = sourceType;
        this.externalGroupId = externalGroupId;
        this.title = title;
        this.deduplicated = deduplicated;
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

    public String getExternalGroupId() {
        return externalGroupId;
    }

    public String getTitle() {
        return title;
    }

    public boolean isDeduplicated() {
        return deduplicated;
    }
}
