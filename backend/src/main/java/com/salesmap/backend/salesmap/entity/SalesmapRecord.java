package com.salesmap.backend.salesmap.entity;

import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.global.entity.BaseEntity;
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
@Table(name = "salesmap_records")
public class SalesmapRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @Column(length = 255)
    private String externalRecordId;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String requestPayload;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String responsePayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SalesmapRecordStatus status;

    @Column
    private LocalDateTime registeredAt;

    protected SalesmapRecord() {
    }

    public SalesmapRecord(
            Analysis analysis,
            String externalRecordId,
            String requestPayload,
            String responsePayload,
            SalesmapRecordStatus status,
            LocalDateTime registeredAt
    ) {
        this.analysis = analysis;
        this.externalRecordId = externalRecordId;
        this.requestPayload = requestPayload;
        this.responsePayload = responsePayload;
        this.status = status;
        this.registeredAt = registeredAt;
    }

    public Long getId() {
        return id;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public String getExternalRecordId() {
        return externalRecordId;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public SalesmapRecordStatus getStatus() {
        return status;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }
}
