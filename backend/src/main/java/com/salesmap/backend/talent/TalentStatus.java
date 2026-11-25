package com.salesmap.backend.talent;

/** PROCESSING=AI가 읽는 중, DRAFT=사람 확인 전, CONFIRMED=후보 풀 등록, FAILED=읽지 못함(다시 올리기). */
public enum TalentStatus {
    PROCESSING, DRAFT, CONFIRMED, FAILED
}
