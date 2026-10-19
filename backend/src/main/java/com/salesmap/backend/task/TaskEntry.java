package com.salesmap.backend.task;

import jakarta.persistence.*;

@Entity
@Table(name = "task_entries", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "draft_key"}),
        indexes = @Index(name = "idx_task_owner_note", columnList = "owner_id,note_id"))
public class TaskEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "owner_id", nullable = false) private Long ownerId;
    @Column(name = "note_id") private String noteId;
    @Column(name = "draft_key", nullable = false, length = 64) private String draftKey;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String document;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String proposed;
    @Version private Long version;

    protected TaskEntry() {}
    public TaskEntry(Long ownerId, String noteId, String draftKey, String document) {
        this.ownerId = ownerId; this.noteId = noteId; this.draftKey = draftKey;
        this.document = document; this.proposed = document;
    }
    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getDocument() { return document; }
    public String getProposed() { return proposed; }
    public void update(String document) { this.document = document; }
}
