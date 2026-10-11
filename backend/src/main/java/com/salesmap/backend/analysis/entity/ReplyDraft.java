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

import java.time.LocalDateTime;

/**
 * AI가 만든 답장 초안과 실제로 보낸 답장을 함께 보관한다.
 * 두 값을 비교하면 사용자가 초안을 어떻게 고쳐 쓰는지 학습할 수 있다.
 */
@Entity
@Table(name = "reply_drafts", indexes = @Index(name = "idx_reply_drafts_analysis", columnList = "analysis_id"))
public class ReplyDraft extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @Column(length = 500)
    private String aiSubject;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String aiBody;

    // hybrid-template / ollama:모델명 / openai:모델명 / mock
    @Column(length = 100)
    private String generatedBy;

    @Column(length = 500)
    private String sentSubject;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String sentBody;

    @Column
    private LocalDateTime sentAt;

    protected ReplyDraft() {
    }

    public ReplyDraft(Analysis analysis, String aiSubject, String aiBody, String generatedBy) {
        this.analysis = analysis;
        this.aiSubject = aiSubject;
        this.aiBody = aiBody;
        this.generatedBy = generatedBy;
    }

    public void markSent(String sentSubject, String sentBody) {
        this.sentSubject = sentSubject;
        this.sentBody = sentBody;
        this.sentAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public String getAiSubject() {
        return aiSubject;
    }

    public String getAiBody() {
        return aiBody;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public String getSentSubject() {
        return sentSubject;
    }

    public String getSentBody() {
        return sentBody;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }
}
