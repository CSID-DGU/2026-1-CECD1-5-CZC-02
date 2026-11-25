package com.salesmap.backend.talent;

import com.salesmap.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 인력 후보 한 명 (이력서 한 건에서 시작). 고객 담당자(person_entries)와 섞지 않는다.
 * document는 contracts/resume_profile.schema.json 형식 그대로이고, 나머지 칸은 목록·필터용 사본이다.
 * 원본 이력서 파일은 저장하지 않는다 (근거 확인용 원문 글만 둔다).
 */
@Entity
@Table(name = "talent_profiles", indexes = @Index(name = "idx_talent_owner_id", columnList = "user_id,id"))
public class TalentProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TalentStatus status;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Lob @Column(columnDefinition = "LONGTEXT")
    private String document;

    @Lob @Column(name = "source_text", columnDefinition = "LONGTEXT")
    private String sourceText;

    @Column(name = "llm_provider", length = 20) private String llmProvider;
    @Column(name = "llm_model", length = 100) private String llmModel;
    // LLM이 실패해 규칙 결과만 남았을 때 화면에 알린다
    @Column(name = "llm_fallback_reason", length = 500) private String llmFallbackReason;
    @Column(name = "error_message", length = 500) private String errorMessage;

    // 목록·필터·같은 사람 제안용 사본 (document에서 뽑음)
    @Column(length = 100) private String name;
    @Column(name = "birth_year") private Integer birthYear;
    @Column(name = "career_months") private Integer careerMonths;
    @Column(name = "available_from") private LocalDate availableFrom;
    @Column(name = "available_basis", length = 30) private String availableBasis;
    @Lob @Column(name = "search_text", columnDefinition = "LONGTEXT") private String searchText;

    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "confirmed_at") private LocalDateTime confirmedAt;
    @Column(name = "confirmed_by") private Long confirmedBy;

    protected TalentProfile() {}

    TalentProfile(User user, String fileName, LocalDateTime now) {
        this.user = user;
        this.fileName = fileName;
        this.status = TalentStatus.PROCESSING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void markExtracted(String document, String sourceText, String llmProvider, String llmModel, String fallbackReason,
                       TalentFields fields, LocalDateTime now) {
        this.status = TalentStatus.DRAFT;
        this.document = document;
        this.sourceText = sourceText;
        this.llmProvider = llmProvider;
        this.llmModel = llmModel;
        this.llmFallbackReason = cut(fallbackReason);
        this.errorMessage = null;
        applyFields(fields);
        this.updatedAt = now;
    }

    void markFailed(String message, LocalDateTime now) {
        this.status = TalentStatus.FAILED;
        this.errorMessage = cut(message);
        this.updatedAt = now;
    }

    void replaceDocument(String document, TalentFields fields, LocalDateTime now) {
        this.document = document;
        applyFields(fields);
        this.updatedAt = now;
    }

    void confirm(String document, Long actor, LocalDateTime now) {
        this.status = TalentStatus.CONFIRMED;
        this.document = document;
        this.confirmedAt = now;
        this.confirmedBy = actor;
        this.updatedAt = now;
    }

    private void applyFields(TalentFields fields) {
        this.name = fields.name();
        this.birthYear = fields.birthYear();
        this.careerMonths = fields.careerMonths();
        this.availableFrom = fields.availableFrom();
        this.availableBasis = fields.availableBasis();
        this.searchText = fields.searchText();
    }

    private static String cut(String value) {
        return value == null ? null : value.length() > 500 ? value.substring(0, 500) : value;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public TalentStatus getStatus() { return status; }
    public String getFileName() { return fileName; }
    public String getDocument() { return document; }
    public String getSourceText() { return sourceText; }
    public String getLlmProvider() { return llmProvider; }
    public String getLlmModel() { return llmModel; }
    public String getLlmFallbackReason() { return llmFallbackReason; }
    public String getErrorMessage() { return errorMessage; }
    public String getName() { return name; }
    public Integer getBirthYear() { return birthYear; }
    public Integer getCareerMonths() { return careerMonths; }
    public LocalDate getAvailableFrom() { return availableFrom; }
    public String getAvailableBasis() { return availableBasis; }
    public String getSearchText() { return searchText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
}
