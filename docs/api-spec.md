# API Specification

최종 시연 기준 주요 API 명세입니다. 모든 보호 API는 JWT가 필요합니다.

공통 헤더:

```http
Authorization: Bearer {accessToken}
Content-Type: application/json
```

공통 응답:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

## Auth

### POST /api/auth/signup

회원가입 후 JWT를 발급합니다.

Authorization: 불필요

Request:

```json
{
  "email": "test@example.com",
  "password": "12345678",
  "name": "이민재"
}
```

### POST /api/auth/login

로그인 후 JWT를 발급합니다.

Authorization: 불필요

Request:

```json
{
  "email": "test@example.com",
  "password": "12345678"
}
```

### GET /api/auth/me

현재 로그인 사용자 정보를 조회합니다.

Authorization: 필요

## Gmail Integration

### GET /api/integrations/gmail/authorize

Google OAuth authorization URL을 발급합니다.

Authorization: 필요

Response data 예시:

```json
{
  "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?...",
  "redirectUri": "http://localhost:5173/settings/gmail/callback"
}
```

### GET /api/integrations/gmail/callback

Google OAuth callback code를 처리하고 Gmail Integration을 저장합니다.

Authorization: 필요

Query:

| Name | Required | 설명 |
| --- | --- | --- |
| `code` | yes | Google OAuth authorization code |
| `state` | no | OAuth state |

### DELETE /api/integrations/gmail

Gmail 연결을 해제합니다.

Authorization: 필요

### POST /api/integrations/gmail/collect

Gmail 메일을 수집합니다.

Authorization: 필요

Query 예시:

```text
mode=manual&recentDays=30&debug=true
```

Response data 주요 필드:

```json
{
  "gmailQuery": "newer_than:30d",
  "totalFetched": 20,
  "savedCount": 3,
  "skippedDuplicateCount": 17,
  "rawFetchedMessageIds": ["message-id"],
  "latestMessageDate": "2026-06-03T10:00:00",
  "collectedAt": "2026-06-03T10:05:00"
}
```

## Sources

### GET /api/sources

로그인 사용자의 수집 메일 목록을 조회합니다.

Authorization: 필요

Query:

| Name | Default | 설명 |
| --- | --- | --- |
| `page` | `0` | 페이지 번호 |
| `size` | `20` | 페이지 크기 |

### GET /api/sources/{sourceId}

메일 상세를 조회합니다.

Authorization: 필요

소유자가 아니면 `403`을 반환합니다.

### POST /api/sources

수동 Source를 생성합니다. 최종 시연에서는 주로 Gmail collect가 Source를 생성합니다.

Authorization: 필요

Request:

```json
{
  "integrationId": null,
  "sourceType": "EMAIL",
  "title": "고객 미팅 관련 이메일",
  "content": "원본 이메일 내용"
}
```

## Analysis

### POST /api/analysis

단일 Source를 분석합니다.

Authorization: 필요

Request:

```json
{
  "sourceId": 1
}
```

### POST /api/analysis/group

Gmail thread 단위 SourceGroup을 분석합니다. Gmail 수집 메일은 보통 이 API를 사용합니다.

Authorization: 필요

Request:

```json
{
  "sourceGroupId": 1
}
```

Response data 주요 필드:

```json
{
  "analysisId": 1,
  "sourceId": 10,
  "summary": "Nimbus Tech / CRM Automation / 도입 상담 미팅 / 2026-06-12 / 오전 10시",
  "nextAction": "미팅 일정 등록 및 미팅 준비",
  "scheduleInfo": "CRM Automation 관련 미팅 (2026-06-12T10:00)",
  "customerName": "Nimbus Tech",
  "productName": "CRM Automation",
  "amount": null,
  "attendees": "Nimbus Tech 김도윤, 영업팀 이민재",
  "actionType": "CREATE",
  "actionReason": "일정 생성 요청 표현과 날짜/시간 정보가 포함되어 있습니다.",
  "targetScheduleId": null,
  "targetScheduleTitle": null,
  "status": "ANALYZED"
}
```

### PATCH /api/analysis/{analysisId}

사용자가 분석 결과를 수동 수정합니다.

Authorization: 필요

Request 예시:

```json
{
  "summary": "수정된 분석 요약",
  "nextAction": "수정된 다음 행동",
  "scheduleInfo": "2026-06-12 오전 10시 미팅",
  "customerName": "Nimbus Tech",
  "productName": "CRM Automation",
  "amount": null,
  "attendees": "Nimbus Tech 김도윤, 영업팀 이민재",
  "actionType": "CREATE",
  "actionReason": "사용자 수정",
  "targetScheduleId": null,
  "targetScheduleTitle": "CRM Automation 관련 미팅"
}
```

### GET /api/analysis/source/{sourceId}

Source 기준 분석 목록을 조회합니다.

Authorization: 필요

현재 프론트는 최신 분석 결과를 중심으로 표시합니다.

## Schedules

### GET /api/schedules

로그인 사용자의 일정 목록을 조회합니다.

Authorization: 필요

Query:

| Name | Default | 설명 |
| --- | --- | --- |
| `page` | `0` | 페이지 번호 |
| `size` | `10` | 페이지 크기 |

### POST /api/schedules

일정을 직접 생성합니다.

Authorization: 필요

Request:

```json
{
  "analysisId": null,
  "title": "테스트 일정",
  "scheduleDateTime": "2026-06-12T10:00:00",
  "memo": "일정 메모"
}
```

### PATCH /api/schedules/{scheduleId}

Dashboard에서 일정을 수정합니다. Google Calendar 이벤트가 연결되어 있으면 함께 수정합니다.

Authorization: 필요

Request 예시:

```json
{
  "title": "수정된 미팅",
  "scheduleDateTime": "2026-06-12T14:00:00",
  "memo": "수정된 메모",
  "scheduleType": "MEETING"
}
```

### DELETE /api/schedules/{scheduleId}

Dashboard에서 일정을 삭제합니다. Google Calendar 이벤트가 연결되어 있으면 함께 삭제합니다.

Authorization: 필요

## Salesmap

### POST /api/salesmap/register

분석 결과를 사용자가 승인합니다.

Authorization: 필요

Request:

```json
{
  "analysisId": 1
}
```

actionType별 동작:

| actionType | 동작 |
| --- | --- |
| `CREATE` | 내부 Schedule 생성, Google Calendar 이벤트 생성 |
| `UPDATE` | 내부 Schedule 수정, Google Calendar 이벤트 수정 |
| `CANCEL` | 내부 Schedule 삭제, Google Calendar 이벤트 삭제 |
| `UNKNOWN` | 등록 이력 저장 중심 |

Response data 예시:

```json
{
  "salesmapRecordId": 1,
  "analysisId": 1,
  "externalRecordId": "google-calendar-event-id",
  "status": "REGISTERED",
  "registeredAt": "2026-06-03T14:16:57"
}
```

### GET /api/salesmap/analysis/{analysisId}

분석 결과 기준 등록 이력을 조회합니다.

Authorization: 필요

## Status Codes

| Status | 의미 |
| --- | --- |
| 200 | 성공 |
| 400 | 요청값 오류 또는 validation 실패 |
| 401 | 로그인 필요 |
| 403 | 소유권 없음 |
| 404 | 데이터 없음 |
| 500 | 서버 내부 오류 |
| 502 | AI Module 또는 Google Calendar 등 외부 연동 실패 |
