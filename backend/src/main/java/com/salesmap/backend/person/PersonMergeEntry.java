package com.salesmap.backend.person;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** D-026 감사 기록. 원본 인물·이력은 삭제하지 않으며 AI 학습용 revision을 만들지 않는다. */
@Entity
@Table(name="person_merge_entries", indexes=@Index(name="idx_merge_owner_target",columnList="user_id,target_id,id"))
public class PersonMergeEntry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="source_id",nullable=false) private Long sourceId;
    @Column(name="target_id",nullable=false) private Long targetId;
    @Lob @Column(name="source_before",nullable=false,columnDefinition="LONGTEXT") private String sourceBefore;
    @Lob @Column(name="target_before",nullable=false,columnDefinition="LONGTEXT") private String targetBefore;
    @Column(nullable=false) private Long mergedBy;
    @Column(nullable=false) private LocalDateTime mergedAt;
    private Long undoneBy;
    private LocalDateTime undoneAt;
    protected PersonMergeEntry() {}
    PersonMergeEntry(Long owner,Long source,Long target,String sourceBefore,String targetBefore,Long actor,LocalDateTime now){
        this.userId=owner;this.sourceId=source;this.targetId=target;this.sourceBefore=sourceBefore;
        this.targetBefore=targetBefore;this.mergedBy=actor;this.mergedAt=now;
    }
    public Long getId(){return id;}
    public Long getSourceId(){return sourceId;}
    public Long getTargetId(){return targetId;}
    public String getSourceBefore(){return sourceBefore;}
    public String getTargetBefore(){return targetBefore;}
    public Long getMergedBy(){return mergedBy;}
    public LocalDateTime getMergedAt(){return mergedAt;}
    public Long getUndoneBy(){return undoneBy;}
    public LocalDateTime getUndoneAt(){return undoneAt;}
    boolean active(){return undoneAt==null;}
    void undo(Long actor,LocalDateTime now){this.undoneBy=actor;this.undoneAt=now;}
}
