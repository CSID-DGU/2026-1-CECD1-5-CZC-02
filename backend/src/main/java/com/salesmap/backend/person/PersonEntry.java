package com.salesmap.backend.person;

import com.salesmap.backend.user.entity.User;
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

/** A 전용 인물 대장. 같은 이름·공유 연락처를 자동으로 합치지 않는다. */
@Entity
@Table(name = "person_entries", indexes = @Index(name = "idx_person_owner_id", columnList = "user_id,id"))
public class PersonEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String document;

    @Lob @Column(name = "search_text", nullable = false, columnDefinition = "LONGTEXT")
    private String searchText;

    // AI 제안 없이 직접 등록한 결정의 감사 정보 (D-025·D-026). 외부 Person 계약에는 넣지 않는다.
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PersonEntry() {}

    PersonEntry(User user, String document, String searchText, Long createdBy, LocalDateTime createdAt) {
        this.user = user;
        this.document = document;
        this.searchText = searchText;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getDocument() { return document; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
