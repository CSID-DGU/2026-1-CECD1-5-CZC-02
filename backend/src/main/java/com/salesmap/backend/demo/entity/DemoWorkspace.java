package com.salesmap.backend.demo.entity;

import com.salesmap.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/**
 * 개발자용 데모 모드. 켜져 있으면 소유자의 요청이 숨은 데모 사용자(demoUserId)의 데이터로 처리된다.
 * anchorDate는 시나리오 메일의 발송 시각과 본문 속 미팅 날짜를 계산하는 기준일이다.
 */
@Entity
@Table(
        name = "demo_workspaces",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_demo_workspaces_owner", columnNames = {"owner_user_id"}),
                @UniqueConstraint(name = "uk_demo_workspaces_demo_user", columnNames = {"demo_user_id"})
        }
)
public class DemoWorkspace extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "demo_user_id", nullable = false)
    private Long demoUserId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private int deliveredStage;

    @Column(nullable = false)
    private LocalDate anchorDate;

    protected DemoWorkspace() {
    }

    public DemoWorkspace(Long ownerUserId, Long demoUserId, LocalDate anchorDate) {
        this.ownerUserId = ownerUserId;
        this.demoUserId = demoUserId;
        this.anchorDate = anchorDate;
        this.deliveredStage = 0;
        this.enabled = false;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public Long getDemoUserId() {
        return demoUserId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getDeliveredStage() {
        return deliveredStage;
    }

    public LocalDate getAnchorDate() {
        return anchorDate;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void markStageDelivered(int stage) {
        this.deliveredStage = stage;
    }

    public void restart(LocalDate anchorDate) {
        this.anchorDate = anchorDate;
        this.deliveredStage = 0;
    }
}
