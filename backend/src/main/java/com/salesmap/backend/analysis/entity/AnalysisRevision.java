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

/**
 * 사용자가 AI 분석 결과의 필드 하나를 고친 기록.
 * <p>
 * aiValue(AI 원본) → userValue(사용자 최종값) 쌍이 학습 데이터가 된다.
 * previousValue는 여러 번 고친 경우 직전 값이다.
 */
@Entity
@Table(name = "analysis_revisions", indexes = @Index(name = "idx_analysis_revisions_analysis", columnList = "analysis_id"))
public class AnalysisRevision extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

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

    protected AnalysisRevision() {
    }

    public AnalysisRevision(Analysis analysis, String fieldName, String aiValue, String previousValue, String userValue) {
        this.analysis = analysis;
        this.fieldName = fieldName;
        this.aiValue = aiValue;
        this.previousValue = previousValue;
        this.userValue = userValue;
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
}
