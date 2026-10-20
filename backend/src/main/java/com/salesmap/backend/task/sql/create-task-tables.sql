-- C 소유 테이블. 운영 DB 반영 전 기존 ddl-auto 설정과 중복 생성 여부를 확인한다.
CREATE TABLE IF NOT EXISTS task_entries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    note_id VARCHAR(255) NULL,
    draft_key VARCHAR(64) NOT NULL,
    document LONGTEXT NOT NULL,
    proposed LONGTEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_task_owner_draft UNIQUE (owner_id, draft_key),
    INDEX idx_task_owner_note (owner_id, note_id)
);
CREATE TABLE IF NOT EXISTS task_revision_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    payload LONGTEXT NOT NULL,
    INDEX idx_task_revision_owner (owner_id)
);
