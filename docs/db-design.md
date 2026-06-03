# DB Design

최종 시연 기준 DB 구조 요약입니다. 실제 테이블은 Spring Data JPA와 `ddl-auto=update`로 생성/갱신됩니다.

## 공통 정책

- PK는 `BIGINT AUTO_INCREMENT`
- 시간 컬럼은 `DATETIME`
- enum은 Java enum을 `EnumType.STRING`으로 저장
- 주요 Entity는 `BaseEntity`를 상속해 `created_at`, `updated_at`을 가짐

## users

사용자 계정 정보입니다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | 사용자 ID |
| `email` | 로그인 이메일, unique |
| `name` | 사용자 이름 |
| `password` | BCrypt 암호화 비밀번호 |
| `role` | 사용자 권한 |
| `status` | 사용자 상태 |
| `created_at` | 생성 시각 |
| `updated_at` | 수정 시각 |

관계:

- `users` 1:N `integrations`
- `users` 1:N `sources`
- `users` 1:N `schedules`

## integrations

Gmail 등 외부 계정 연동 정보입니다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | 연동 ID |
| `user_id` | 사용자 FK |
| `provider` | `GMAIL`, `JANDI`, `SALESMAP` |
| `external_account_id` | 외부 계정 이메일/ID |
| `access_token` | 외부 API access token |
| `refresh_token` | 외부 API refresh token |
| `token_expires_at` | token 만료 시각 |
| `last_synced_at` | 마지막 동기화 시각 |
| `status` | 연동 상태 |
| `created_at` | 생성 시각 |
| `updated_at` | 수정 시각 |

제약:

- `user_id + provider` unique

## source_groups

Gmail thread 단위 그룹입니다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | 그룹 ID |
| `user_id` | 사용자 FK |
| `integration_id` | Gmail Integration FK |
| `source_type` | 원본 타입 |
| `external_group_id` | Gmail threadId |
| `title` | 그룹 대표 제목 |
| `status` | 그룹 상태 |
| `created_at` | 생성 시각 |
| `updated_at` | 수정 시각 |

관계:

- `source_groups` 1:N `sources`

## sources

수집된 Gmail 메일 또는 수동 입력 원본입니다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | Source ID |
| `user_id` | 사용자 FK |
| `integration_id` | Integration FK, nullable |
| `source_group_id` | SourceGroup FK, nullable |
| `source_type` | `EMAIL`, `JANDI_MESSAGE`, `MEETING_NOTE`, `MANUAL_INPUT` |
| `external_source_id` | Gmail messageId |
| `title` | 메일 제목 |
| `content` | 메일 본문 |
| `sender` | 발신자 |
| `receiver` | 수신자 |
| `direction` | 수신/발신 방향 |
| `sent_at` | 메일 발송 시각 |
| `status` | 수집/분석 상태 |
| `collected_at` | 수집 시각 |
| `created_at` | 생성 시각 |
| `updated_at` | 수정 시각 |

관계:

- `sources` N:1 `users`
- `sources` N:1 `integrations`
- `sources` N:1 `source_groups`
- `sources` 1:N `analyses`

## analyses

AI 분석 결과입니다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | Analysis ID |
| `source_id` | Source FK |
| `customer_name` | 고객사 |
| `contact_name` | 담당자 |
| `product_name` | 제품/서비스 |
| `amount` | 금액 |
| `schedule_text` | 일정 정보 |
| `follow_up_action` | 다음 행동 |
| `summary` | 분석 요약 |
| `attendees` | 참석자 |
| `action_type` | `CREATE`, `UPDATE`, `CANCEL`, `CONFIRM`, `UNKNOWN` |
| `target_schedule_id` | 대상 Schedule ID |
| `target_schedule_title` | 대상 일정명 |
| `action_reason` | 판단 근거 |
| `status` | 분석 상태 |
| `analyzed_at` | 분석 시각 |
| `approved_at` | 승인 시각 |
| `created_at` | 생성 시각 |
| `updated_at` | 수정 시각 |

관계:

- `analyses` N:1 `sources`
- `analyses` 1:N `salesmap_records`
- `analyses` 1:N `schedules`

## schedules

내부 캘린더 일정입니다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | Schedule ID |
| `user_id` | 사용자 FK |
| `analysis_id` | Analysis FK, nullable |
| `title` | 일정 제목 |
| `schedule_date_time` | 일정 시작 시각 |
| `memo` | 메모 |
| `reminder_date_time` | 리마인드 시각 |
| `status` | 일정 상태 |
| `schedule_type` | 미팅/업무/전화/이메일 등 표시 유형 |
| `google_calendar_event_id` | Google Calendar 이벤트 ID |
| `created_at` | 생성 시각 |
| `updated_at` | 수정 시각 |

관계:

- `schedules` N:1 `users`
- `schedules` N:1 `analyses`

## salesmap_records

Salesmap 등록/변경/삭제 승인 이력입니다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | 등록 이력 ID |
| `analysis_id` | Analysis FK |
| `external_record_id` | 외부 등록 ID 또는 Google Calendar event ID |
| `request_payload` | 요청 payload, LONGTEXT |
| `response_payload` | 응답 payload, LONGTEXT |
| `status` | 등록 상태 |
| `registered_at` | 등록 시각 |
| `created_at` | 생성 시각 |
| `updated_at` | 수정 시각 |

관계:

- `salesmap_records` N:1 `analyses`

## ERD 요약

```text
users
  ├─ integrations
  ├─ source_groups
  │    └─ sources
  ├─ sources
  │    └─ analyses
  │         ├─ schedules
  │         └─ salesmap_records
  └─ schedules
```
