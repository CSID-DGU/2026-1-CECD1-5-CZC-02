package com.salesmap.backend.person;

import com.salesmap.backend.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A 제안 원본과 사람 결정의 영구 저장. B 회의록의 참석자 필드는 직접 변경하지 않는다. */
@Entity
@Table(name="person_identity_resolutions", uniqueConstraints={
        @UniqueConstraint(name="uk_identity_resolution_id",columnNames="resolution_id"),
        @UniqueConstraint(name="uk_identity_source_mention",columnNames={"user_id","source_ref","mention_index"})})
public class IdentityResolutionEntry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
    @Column(name="resolution_id",nullable=false,length=64) private String resolutionId;
    @Column(name="source_ref",nullable=false,length=64) private String sourceRef;
    @Column(name="mention_index",nullable=false) private int mentionIndex;
    @Lob @Column(nullable=false,columnDefinition="LONGTEXT") private String proposal;
    @Lob @Column(nullable=false,columnDefinition="LONGTEXT") private String document;
    @Version private Long version;
    @Column(nullable=false) private LocalDateTime createdAt;
    private Long selectedBy;
    private LocalDateTime selectedAt;

    protected IdentityResolutionEntry() {}
    IdentityResolutionEntry(User user,String sourceRef,int mentionIndex,String resolutionId,String proposal,LocalDateTime now) {
        this.user=user;this.sourceRef=sourceRef;this.mentionIndex=mentionIndex;this.resolutionId=resolutionId;
        this.proposal=proposal;this.document=proposal;this.createdAt=now;
    }
    public String getResolutionId(){return resolutionId;}
    public String getSourceRef(){return sourceRef;}
    public String getProposal(){return proposal;}
    public String getDocument(){return document;}
    public Long getVersion(){return version;}
    public Long getSelectedBy(){return selectedBy;}
    void decide(String document,Long actor,LocalDateTime now){this.document=document;this.selectedBy=actor;this.selectedAt=now;}
}
