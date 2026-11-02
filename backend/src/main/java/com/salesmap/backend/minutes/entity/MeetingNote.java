package com.salesmap.backend.minutes.entity;

import com.salesmap.backend.global.entity.BaseEntity;
import com.salesmap.backend.source.entity.Source;
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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 회의록 한 건. 문항·참석자는 contracts/meeting_note.schema.json의 같은 이름 값을 JSON 그대로 저장한다.
 * <p>
 * 원본(메모·메일)은 sources에 두고 지우지 않는다. API에서 ID는 "mn_" + PK.
 */
@Entity
@Table(
        name = "meeting_notes",
        indexes = @Index(name = "idx_meeting_notes_user", columnList = "user_id"),
        // 같은 메일(스레드)로 회의록을 두 번 만들지 않는다 (B-5). 메모 회의록은 origin_key가 null이라 제한 없음
        uniqueConstraints = @UniqueConstraint(name = "uk_meeting_notes_user_origin", columnNames = {"user_id", "origin_key"})
)
public class MeetingNote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    // 접촉 시각 (KST). 모르면 null
    @Column
    private LocalDateTime occurredAt;

    @Column(length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingNoteMode mode;

    // participants 배열 JSON
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String participantsJson;

    // fields 객체 JSON (5문항)
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String fieldsJson;

    // perPerson 배열 JSON. FULL 모드면 null
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String perPersonJson;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String missingFieldsJson;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String uncertainSegmentsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingNoteStatus status;

    // RULE / LLM / HUMAN
    @Column(length = 20)
    private String generatedBy;

    // 초안을 만든 엔진 (ollama / openai / rule / mock)
    @Column(length = 50)
    private String llmProvider;

    @Column(length = 100)
    private String llmModel;

    @Column(length = 1000)
    private String llmFallbackReason;

    // AI가 처음 만든 수정 가능 값({"fields.discussion": [...], ...})의 JSON 스냅샷. 수정 기록의 proposedValue가 된다.
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String aiSnapshot;

    @Column
    private LocalDateTime confirmedAt;

    // 사용자가 보관(숨김)한 시각. null이면 보관 안 함. 영구 삭제는 하지 않는다 (D-025 ③)
    @Column
    private LocalDateTime archivedAt;

    // 마지막 보관·해제를 누가·언제 결정했나 (감사 기록, D-026). AI 제안이 없는 결정이라 수정 기록(revision)에는 넣지 않는다.
    // 지금 보관 상태인지는 archivedAt으로 보고, 보관 중이면 보관한 사람, 아니면 해제한 사람이다
    @Column
    private Long archiveDecidedBy;

    @Column
    private LocalDateTime archiveDecidedAt;

    // 메일에서 만든 회의록의 출처: "group-<스레드 PK>" 또는 "source-<메일 PK>". 메모는 null
    @Column(name = "origin_key", length = 100)
    private String originKey;

    // 원본 여러 개(메일 스레드)일 때 sourceRefs 배열 JSON. null이면 source 하나로 만든다
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String sourceRefsJson;

    // AI 모듈에 보낸 원문 그대로(스레드면 메일 구분 줄 포함, 인용·서명 제거 전). null이면 source.content
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String sourceText;

    // 한 번도 고치지 않고 확정했는지
    @Column
    private Boolean confirmedWithoutEdit;

    protected MeetingNote() {
    }

    public MeetingNote(
            User user,
            Source source,
            LocalDateTime occurredAt,
            String location,
            MeetingNoteMode mode,
            String participantsJson,
            String fieldsJson,
            String perPersonJson,
            String missingFieldsJson,
            String uncertainSegmentsJson,
            String generatedBy,
            String llmProvider,
            String llmModel,
            String llmFallbackReason
    ) {
        this.user = user;
        this.source = source;
        this.occurredAt = occurredAt;
        this.location = location;
        this.mode = mode;
        this.participantsJson = participantsJson;
        this.fieldsJson = fieldsJson;
        this.perPersonJson = perPersonJson;
        this.missingFieldsJson = missingFieldsJson;
        this.uncertainSegmentsJson = uncertainSegmentsJson;
        this.status = MeetingNoteStatus.DRAFT;
        this.generatedBy = generatedBy;
        this.llmProvider = llmProvider;
        this.llmModel = llmModel;
        this.llmFallbackReason = llmFallbackReason;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Source getSource() {
        return source;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getLocation() {
        return location;
    }

    public MeetingNoteMode getMode() {
        return mode;
    }

    public String getParticipantsJson() {
        return participantsJson;
    }

    public String getFieldsJson() {
        return fieldsJson;
    }

    public String getPerPersonJson() {
        return perPersonJson;
    }

    public String getMissingFieldsJson() {
        return missingFieldsJson;
    }

    public String getUncertainSegmentsJson() {
        return uncertainSegmentsJson;
    }

    public MeetingNoteStatus getStatus() {
        return status;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public String getLlmProvider() {
        return llmProvider;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public String getLlmFallbackReason() {
        return llmFallbackReason;
    }

    public String getAiSnapshot() {
        return aiSnapshot;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public Boolean getConfirmedWithoutEdit() {
        return confirmedWithoutEdit;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public Long getArchiveDecidedBy() {
        return archiveDecidedBy;
    }

    public LocalDateTime getArchiveDecidedAt() {
        return archiveDecidedAt;
    }

    /** 보관: 기본 목록·확인 대기함에서 숨긴다. 상태(DRAFT/CONFIRMED)와 내용은 그대로 둔다. */
    public void archive(Long decidedBy) {
        LocalDateTime now = LocalDateTime.now();
        this.archivedAt = now;
        this.archiveDecidedBy = decidedBy;
        this.archiveDecidedAt = now;
    }

    public void unarchive(Long decidedBy) {
        this.archivedAt = null;
        this.archiveDecidedBy = decidedBy;
        this.archiveDecidedAt = LocalDateTime.now();
    }

    public String getOriginKey() {
        return originKey;
    }

    public String getSourceRefsJson() {
        return sourceRefsJson;
    }

    public String getSourceText() {
        return sourceText;
    }

    /** 메일에서 만든 회의록의 출처를 남긴다 (저장 전에 부른다). */
    public void linkOrigin(String originKey, String sourceRefsJson, String sourceText) {
        this.originKey = originKey;
        this.sourceRefsJson = sourceRefsJson;
        this.sourceText = sourceText;
    }

    public void recordAiSnapshot(String aiSnapshot) {
        this.aiSnapshot = aiSnapshot;
    }

    public void updateContent(String fieldsJson, String perPersonJson, String missingFieldsJson) {
        this.fieldsJson = fieldsJson;
        this.perPersonJson = perPersonJson;
        this.missingFieldsJson = missingFieldsJson;
    }

    public void markConfirmed(boolean withoutEdit) {
        this.status = MeetingNoteStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
        this.confirmedWithoutEdit = withoutEdit;
    }
}
