# DB Design Draft

SALESMAP 활동 자동화 AI Agent의 DB 테이블 설계 초안입니다.

현재 문서는 실제 구현된 JPA Entity와 Repository 기준입니다.

## Design Notes

- 기본 PK는 `BIGINT` auto increment입니다.
- 모든 Entity는 `BaseEntity`를 상속하며 `created_at`, `updated_at`을 JPA Auditing으로 자동 관리합니다.
- Java enum은 `@Enumerated(EnumType.STRING)`으로 저장합니다.
- 관계는 현재 모두 `ManyToOne(fetch = FetchType.LAZY)` 기준입니다.
- MySQL 테이블은 `spring.jpa.hibernate.ddl-auto=update` 설정으로 생성/갱신됩니다.

## users

사용자 계정 정보를 저장합니다.

목적:
서비스 사용자 식별, 로그인/권한 관리, 사용자별 데이터 소유 관계의 기준 테이블입니다.

주요 컬럼:

| Column | Type Example | Key | Description |
| --- | --- | --- | --- |
| id | BIGINT | PK, NOT NULL | 사용자 ID |
| email | VARCHAR(255) | UNIQUE, NOT NULL | 사용자 이메일 |
| name | VARCHAR(100) | NOT NULL | 사용자 이름 |
| password | VARCHAR(255) | NOT NULL | 비밀번호. 현재는 암호화 전 단계 |
| role | VARCHAR(50) | NOT NULL | 사용자 권한. 현재 생성 API 기본값은 `USER` |
| status | VARCHAR(50) | NOT NULL | 사용자 상태 |
| created_at | DATETIME | NOT NULL | 생성 시각 |
| updated_at | DATETIME | NOT NULL | 수정 시각 |

제약 조건:
- PK: `id`
- UNIQUE: `email`

상태값:
- `ACTIVE`
- `INACTIVE`
- `DELETED`

관계:
- `users` 1:N `integrations`
- `users` 1:N `sources`
- `users` 1:N `schedules`

## integrations

Gmail, Jandi, Salesmap 등 외부 서비스 연동 정보를 저장합니다.

목적:
사용자별 외부 계정 연동 상태와 인증 토큰 정보를 관리합니다.

주요 컬럼:

| Column | Type Example | Key | Description |
| --- | --- | --- | --- |
| id | BIGINT | PK, NOT NULL | 연동 ID |
| user_id | BIGINT | FK, NOT NULL | 사용자 ID |
| provider | VARCHAR(50) | NOT NULL | 연동 서비스 종류 |
| external_account_id | VARCHAR(255) | NOT NULL | 외부 서비스 계정 ID |
| access_token | TEXT | NOT NULL | 접근 토큰. 실제 운영 저장 시 암호화 필요 |
| refresh_token | TEXT | NOT NULL | 갱신 토큰. 실제 운영 저장 시 암호화 필요 |
| token_expires_at | DATETIME | NOT NULL | 토큰 만료 시각 |
| status | VARCHAR(50) | NOT NULL | 연동 상태 |
| created_at | DATETIME | NOT NULL | 생성 시각 |
| updated_at | DATETIME | NOT NULL | 수정 시각 |

PK/FK:
- PK: `id`
- FK: `user_id` -> `users.id`

제약 조건:
- UNIQUE: `user_id`, `provider`
- JPA constraint name: `uk_integrations_user_provider`

provider 값:
- `GMAIL`
- `JANDI`
- `SALESMAP`

상태값:
- `CONNECTED`
- `DISCONNECTED`
- `EXPIRED`
- `ERROR`

관계:
- 하나의 사용자는 여러 외부 서비스를 연동할 수 있습니다.
- `sources.integration_id`를 통해 어떤 연동에서 수집된 원본 데이터인지 추적할 수 있습니다.

## sources

이메일, 잔디 메시지, 회의록 등 AI 분석 전 원본 데이터를 저장합니다.

목적:
외부 서비스 또는 사용자가 입력한 원본 커뮤니케이션 데이터를 보관하고 분석 요청의 기준 데이터로 사용합니다.

주요 컬럼:

| Column | Type Example | Key | Description |
| --- | --- | --- | --- |
| id | BIGINT | PK, NOT NULL | 원본 데이터 ID |
| user_id | BIGINT | FK, NOT NULL | 사용자 ID |
| integration_id | BIGINT | FK, nullable | 원본 데이터를 가져온 외부 연동 ID |
| source_type | VARCHAR(50) | NOT NULL | 원본 데이터 종류 |
| external_source_id | VARCHAR(255) | nullable | Gmail message ID, Jandi message ID 등 외부 원본 ID |
| title | VARCHAR(255) | NOT NULL | 원본 데이터 제목 |
| content | TEXT | NOT NULL | 원본 데이터 내용 |
| status | VARCHAR(50) | NOT NULL | 원본 데이터 처리 상태 |
| collected_at | DATETIME | nullable | 외부 서비스에서 수집한 시각 |
| created_at | DATETIME | NOT NULL | 생성 시각 |
| updated_at | DATETIME | NOT NULL | 수정 시각 |

PK/FK:
- PK: `id`
- FK: `user_id` -> `users.id`
- FK: `integration_id` -> `integrations.id`

source_type 값:
- `EMAIL`
- `JANDI_MESSAGE`
- `MEETING_NOTE`
- `MANUAL_INPUT`

상태값:
- `CREATED`
- `COLLECTED`
- `ANALYSIS_REQUESTED`
- `ANALYZED`
- `FAILED`

관계:
- 하나의 사용자는 여러 원본 데이터를 가질 수 있습니다.
- 하나의 연동 계정에서 여러 원본 데이터가 수집될 수 있습니다.
- 하나의 원본 데이터는 하나 이상의 분석 결과를 가질 수 있습니다.

## analyses

AI 분석 요청과 분석 결과를 저장합니다.

목적:
원본 데이터에서 추출한 고객사명, 담당자, 제품명, 금액, 일정, 후속 조치, 요약 등을 관리합니다.

주요 컬럼:

| Column | Type Example | Key | Description |
| --- | --- | --- | --- |
| id | BIGINT | PK, NOT NULL | 분석 결과 ID |
| source_id | BIGINT | FK, NOT NULL | 원본 데이터 ID |
| customer_name | VARCHAR(255) | nullable | 고객사명 |
| contact_name | VARCHAR(100) | nullable | 담당자명 |
| product_name | VARCHAR(255) | nullable | 제품명 |
| amount | BIGINT | nullable | 금액 |
| schedule_text | VARCHAR(255) | nullable | 원문에서 추출한 일정 표현 |
| follow_up_action | VARCHAR(255) | nullable | 후속 조치 |
| summary | TEXT | nullable | 요약 내용 |
| status | VARCHAR(50) | NOT NULL | 분석 상태 |
| analyzed_at | DATETIME | nullable | 분석 완료 시각 |
| approved_at | DATETIME | nullable | 사용자 승인 시각 |
| created_at | DATETIME | NOT NULL | 생성 시각 |
| updated_at | DATETIME | NOT NULL | 수정 시각 |

PK/FK:
- PK: `id`
- FK: `source_id` -> `sources.id`

상태값:
- `REQUESTED`
- `ANALYZING`
- `ANALYZED`
- `APPROVED`
- `REJECTED`
- `FAILED`

관계:
- 하나의 원본 데이터는 여러 번 분석될 수 있습니다.
- 하나의 분석 결과에서 일정 또는 Salesmap 등록 기록이 생성될 수 있습니다.

## schedules

분석 결과에서 추출되거나 사용자가 등록한 일정과 리마인드 정보를 저장합니다.

목적:
영업 미팅, 후속 조치, 리마인드 일정을 관리합니다.

주요 컬럼:

| Column | Type Example | Key | Description |
| --- | --- | --- | --- |
| id | BIGINT | PK, NOT NULL | 일정 ID |
| user_id | BIGINT | FK, NOT NULL | 사용자 ID |
| analysis_id | BIGINT | FK, nullable | 분석 결과 ID |
| title | VARCHAR(255) | NOT NULL | 일정 제목 |
| schedule_date_time | DATETIME | NOT NULL | 일정 날짜와 시간 |
| memo | TEXT | nullable | 일정 메모 |
| reminder_date_time | DATETIME | nullable | 리마인드 시각 |
| status | VARCHAR(50) | NOT NULL | 일정 상태 |
| created_at | DATETIME | NOT NULL | 생성 시각 |
| updated_at | DATETIME | NOT NULL | 수정 시각 |

PK/FK:
- PK: `id`
- FK: `user_id` -> `users.id`
- FK: `analysis_id` -> `analyses.id`

상태값:
- `SCHEDULED`
- `COMPLETED`
- `CANCELED`
- `REMINDED`

관계:
- 하나의 사용자는 여러 일정을 가질 수 있습니다.
- 하나의 분석 결과에서 여러 일정이 생성될 수 있습니다.

## salesmap_records

Salesmap API 등록 요청과 등록 결과를 저장합니다.

목적:
사용자가 승인한 분석 결과를 Salesmap에 등록한 이력과 외부 Salesmap 레코드 ID를 관리합니다.

주요 컬럼:

| Column | Type Example | Key | Description |
| --- | --- | --- | --- |
| id | BIGINT | PK, NOT NULL | Salesmap 등록 기록 ID |
| analysis_id | BIGINT | FK, NOT NULL | 분석 결과 ID |
| external_record_id | VARCHAR(255) | nullable | Salesmap에서 반환한 외부 레코드 ID |
| request_payload | TEXT | nullable | Salesmap 등록 요청 payload. 현재 JPA `@Lob String` |
| response_payload | TEXT | nullable | Salesmap 응답 payload. 현재 JPA `@Lob String` |
| status | VARCHAR(50) | NOT NULL | 등록 상태 |
| registered_at | DATETIME | nullable | 등록 완료 시각 |
| created_at | DATETIME | NOT NULL | 생성 시각 |
| updated_at | DATETIME | NOT NULL | 수정 시각 |

PK/FK:
- PK: `id`
- FK: `analysis_id` -> `analyses.id`

상태값:
- `REQUESTED`
- `REGISTERED`
- `FAILED`
- `CANCELED`

관계:
- 하나의 분석 결과는 Salesmap 등록 기록을 가질 수 있습니다.
- 재시도 이력을 보관하려면 하나의 분석 결과에 여러 등록 기록을 허용할 수 있습니다.

## ERD Summary

텍스트 기준 관계 요약:

```text
users 1 ── N integrations
users 1 ── N sources
users 1 ── N schedules

integrations 1 ── N sources

sources 1 ── N analyses

analyses 1 ── N schedules
analyses 1 ── N salesmap_records
```

흐름 요약:

```text
users
  -> integrations
  -> sources
  -> analyses
  -> schedules
  -> salesmap_records
```

일반 처리 흐름:

```text
사용자 생성
-> 외부 계정 연동
-> 원본 데이터 수집
-> AI 분석
-> 사용자가 분석 결과 확인 및 승인
-> 일정 등록 또는 Salesmap 등록
```

## Current Implementation Summary

현재 DB 구현 완료 상태:

| Domain | Table | Entity | Repository | Implemented |
| --- | --- | --- | --- | --- |
| user | `users` | `User` | `UserRepository` | Yes |
| integration | `integrations` | `Integration` | `IntegrationRepository` | Yes |
| source | `sources` | `Source` | `SourceRepository` | Yes |
| analysis | `analyses` | `Analysis` | `AnalysisRepository` | Yes |
| schedule | `schedules` | `Schedule` | `ScheduleRepository` | Yes |
| salesmap | `salesmap_records` | `SalesmapRecord` | `SalesmapRecordRepository` | Yes |

현재 unique 제약:
- `users.email`
- `integrations.user_id + integrations.provider`

현재 실제 API 구현 상태:
- `User API`는 DB 기반 생성/조회까지 구현되어 있습니다.
- `Source`, `Analysis`, `Schedule`, `Salesmap` API는 아직 mock Service 기반입니다.
