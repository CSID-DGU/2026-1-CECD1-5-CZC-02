package com.salesmap.backend.integration.entity;

import com.salesmap.backend.global.entity.BaseEntity;
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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "integrations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_integrations_user_provider",
                        columnNames = {"user_id", "provider"}
                )
        }
)
public class Integration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IntegrationProvider provider;

    @Column(nullable = false, length = 255)
    private String externalAccountId;

    @Lob
    @Column(nullable = false)
    private String accessToken;

    @Lob
    @Column(nullable = false)
    private String refreshToken;

    @Column(nullable = false)
    private LocalDateTime tokenExpiresAt;

    @Column
    private LocalDateTime lastSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IntegrationStatus status;

    protected Integration() {
    }

    public Integration(
            User user,
            IntegrationProvider provider,
            String externalAccountId,
            String accessToken,
            String refreshToken,
            LocalDateTime tokenExpiresAt,
            IntegrationStatus status
    ) {
        this.user = user;
        this.provider = provider;
        this.externalAccountId = externalAccountId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenExpiresAt = tokenExpiresAt;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public IntegrationProvider getProvider() {
        return provider;
    }

    public String getExternalAccountId() {
        return externalAccountId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public LocalDateTime getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public IntegrationStatus getStatus() {
        return status;
    }

    public void updateToken(
            String externalAccountId,
            String accessToken,
            String refreshToken,
            LocalDateTime tokenExpiresAt,
            IntegrationStatus status
    ) {
        this.externalAccountId = externalAccountId;
        this.accessToken = accessToken;
        if (refreshToken != null && !refreshToken.isBlank()) {
            this.refreshToken = refreshToken;
        }
        this.tokenExpiresAt = tokenExpiresAt;
        this.status = status;
    }

    public void updateLastSyncedAt(LocalDateTime lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
