# API Specification

현재 코드 기준 API 명세입니다 (2026-10 갱신: 컨트롤러의 모든 엔드포인트 포함). 모든 보호 API는 JWT가 필요합니다. 2학기 새 데이터 형식은 `contracts/`가 기준입니다.

공통 헤더:

```http
Authorization: Bearer {accessToken}
Content-Type: application/json
```
다음 행동, 판단 근
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

## Integrations (공통)

### GET /api/integrations

내 외부 연동 목록을 조회합니다. Authorization: 필요

### GET /api/integrations/{provider}

`provider`(`GMAIL`, `JANDI`, `SALESMAP`)별 연동 정보를 조회합니다. Authorization: 필요

### POST /api/integrations

연동 정보를 직접 저장합니다 (1학기 초기 테스트용, Gmail은 OAuth 흐름을 사용). Authorization: 필요

Request: `provider`, `externalAccountId`, `accessToken`, `refreshToken`, `tokenExpiresAt`(미래 시각) — 모두 필수

## Sources

### GET /api/sources

로그인 사용자의 수집 메일 목록을 조회합니다.

Authorization: 필요

Query:

| Name | Default | 설명 |
| --- | --- | --- |
| `page` | `0` | 페이지 번호 |
| `size` | `10` | 페이지 크기. 프론트 메일 목록은 `size=30`으로 요청 |

### GET /api/sources/{sourceId}

메일 상세를 조회합니다.

Authorization: 필요

소유자가 아니면 `403`을 반환합니다.

### DELETE /api/sources/{sourceId}

수집된 메일을 웹에서 숨기기 위해 Source를 삭제합니다.

Authorization: 필요

Response data 예시:

```json
123
```

### POST /api/sources

수동 Source를 생성합니다. 최종 시연에서는 주로 Gmail collect가 Source를 생성합니다.

Authorization: 필요

Request:

```json
{
  "integrationId": null,
  "sourceType": "EMAIL",
  "title": "고객 미팅 관련 이메일",
  "content": "원본 이메일 내용",
  "senderName": "김현우",
  "senderEmail": "kim@example.com",
  "sentAt": "2026-09-22T10:00:00"
}
```

- `senderName`, `senderEmail`, `sentAt`은 선택입니다. `senderEmail`이 있으면 받은 메일(`RECEIVED`)로 저장되어 고객 타임라인·파이프라인·미답장 목록에 포함됩니다.

### GET /api/sources/unanswered

고객이 보낸 영업 메일 중 아직 답장하지 않은 대화를 오래 기다린 순으로 조회합니다.
대화의 마지막 메일이 받은 메일이고, 영업 메일(SALES_ACTIVITY)로 분석됐으며(거절·삭제 제외), 이후 답장을 보낸 기록이 없으면 포함됩니다.

Authorization: 필요

Response data 예시:

```json
[
  {
    "sourceId": 12,
    "sourceGroupId": 5,
    "analysisId": 20,
    "title": "스마트 배차 관제 시스템 도입 상담 미팅 요청드립니다",
    "senderName": "김현우",
    "senderEmail": "kim@example.com",
    "customerName": "대성물류",
    "receivedAt": "2026-09-22T10:00:00",
    "waitingBusinessDays": 1
  }
]
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
  "businessType": "SALES_ACTIVITY",
  "businessRelevanceScore": 0.95,
  "businessReason": "일정 생성 표현과 고객사/제품 정보가 포함된 영업 메일입니다.",
  "status": "ANALYZED"
}
```

AI 모듈 처리 방식:

- 규칙 기반 일정/날짜/금액/의도 추출
- BGE-M3 임베딩 기반 영업 메일/의도 보조 분류
- GLiNER 기반 고객사/제품/참석자 추출 보조
- 필요 시 Ollama LLM 보정
- 시연 핵심 메일 패턴 보정

업무 외 메일은 `businessType=NON_BUSINESS`, `actionType=UNKNOWN`으로 내려가며 일정 등록 버튼 대신 삭제/제외 흐름으로 처리합니다.

### POST /api/analysis/{analysisId}/reply-draft

분석 결과와 원본 메일을 기반으로 답장 초안을 생성합니다.

Authorization: 필요

Request Body: 없음

Response data 예시:

```json
{
  "subject": "Re: Delta Systems Sales Platform 견적서 검토 요청",
  "body": "안녕하세요, 최유진님.\n\nSales Platform 도입 견적서 검토 내용 확인했습니다.\n요청주신 대시보드 제공 범위와 데이터 연동 방식에 대한 추가 자료를 준비해 공유드리겠습니다.\n\n감사합니다.",
  "generatedBy": "ollama-guided-template"
}
```

동작:

- 시연 핵심 케이스는 안정적인 템플릿을 우선 사용합니다.
- 그 외 메일은 Ollama 호출을 시도하고, 실패하거나 결과 품질이 낮으면 템플릿 fallback을 사용합니다.
- 프론트에서는 생성 중 상태를 보여주기 위해 약간의 지연 후 결과가 표시됩니다.

### PATCH /api/analysis/{analysisId}

사용자가 분석 결과를 수동 수정합니다.

Authorization: 필요

Request 예시:

```json
{
  "summary": "수정된 분석 요약",
  "followUpAction": "수정된 다음 행동",
  "scheduleText": "CRM Automation 도입 상담 (2026-06-12T10:00)",
  "customerName": "Nimbus Tech",
  "contactName": "김도윤",
  "productName": "CRM Automation",
  "amount": null,
  "attendees": "김도윤, 이민재",
  "actionType": "CREATE",
  "actionReason": "사용자 수정",
  "targetScheduleTitle": "CRM Automation 관련 미팅",
  "businessType": "SALES_ACTIVITY"
}
```

- `businessType`(SALES_ACTIVITY / NON_BUSINESS / UNKNOWN)은 선택입니다. 없으면 기존 값을 유지합니다.
- 바뀐 필드마다 `analysis_revisions`에 AI 원본값·직전값·사용자값이 기록됩니다(학습 데이터).

### POST /api/analysis/{analysisId}/reject

분석 결과를 거절합니다. 상태가 `REJECTED`가 되고 딜 파이프라인·고객 타임라인·미답장 목록에서 제외됩니다. 이미 승인(`APPROVED`)된 분석은 거절할 수 없습니다(400).

Authorization: 필요

### GET /api/analysis/{analysisId}/revisions

분석 결과의 수정 기록을 조회합니다.

Authorization: 필요

Response data 예시:

```json
[
  {
    "revisionId": 1,
    "fieldName": "customerName",
    "aiValue": "한빛소프트",
    "previousValue": "한빛소프트",
    "userValue": "한빛소프트(주)",
    "revisedAt": "2026-09-23T12:00:00"
  }
]
```

분석 응답(`AnalysisResponse`)에는 다음 필드가 추가되었습니다.

| 필드 | 설명 |
| --- | --- |
| `llmProvider` | 의미 필드를 보정한 엔진: `rule` / `ollama` / `openai` / `mock` |
| `llmModel` | 사용한 모델명 (예: `qwen2.5:7b`) |
| `llmFallbackReason` | 상위 엔진을 못 쓰고 대체된 이유 |
| `approvedWithoutEdit` | 수정 없이 승인했으면 `true` |

### GET /api/analysis/source/{sourceId}

Source 기준 분석 목록을 조회합니다.

Authorization: 필요

현재 프론트는 최신 분석 결과를 중심으로 표시합니다.

### GET /api/analysis/{analysisId}

분석 결과 하나를 조회합니다. Authorization: 필요

### POST /api/analysis/{analysisId}/send-reply

사용자가 초안을 확인·수정한 뒤 **전송 버튼을 눌렀을 때** 답장을 Gmail로 보냅니다 (자동 발송 아님). 받는 사람은 서버가 원본 메일 발신자로 정합니다 (임의 주소로 보낼 수 없음). Authorization: 필요

Request:

```json
{ "subject": "Re: 견적 문의", "body": "본문" }
```

Response data: `sentTo`, `subject`, `messageId`

> 사람이 눌러야 발송되므로 사람 확인 원칙은 지켜집니다. 2학기에 발송 대기함(초안을 서버에 쌓아 두고 목록에서 확인 후 발송)을 만들 때 이 기능을 그쪽으로 옮길 예정입니다 (`docs/project/overview.md` "바꿔야 함").

## Analysis Jobs

### POST /api/analysis-jobs/bulk

아직 분석하지 않은 메일을 백그라운드에서 일괄 분석합니다. Authorization: 필요

Response data: `result`(`STARTED` / `ALREADY_RUNNING` / `NOTHING_TO_ANALYZE`), `job`(아래 진행 상태)

### GET /api/analysis-jobs/activity?since={eventId}

분석 진행률과 활동 피드(새 메일 수집·분석 시작·끝)를 조회합니다. 프론트가 주기적으로 호출합니다. Authorization: 필요

Response data:

```json
{
  "job": { "trigger": "MANUAL", "running": true, "total": 12, "done": 5, "analyzed": 4, "skipped": 1, "failed": 0,
           "currentTitle": "견적 문의", "startedAt": "2026-10-13T10:00:00", "finishedAt": null },
  "events": [ { "id": 7, "type": "ANALYSIS_STARTED", "trigger": "MANUAL", "message": "…", "occurredAt": "2026-10-13T10:00:00" } ],
  "latestEventId": 7
}
```

이벤트 `type`: `COLLECTED`, `ANALYSIS_STARTED`, `ANALYSIS_FINISHED`

## Auto Pipeline (자동 수집·분석)

켜 두면 서버가 주기적으로(기본 45초, `auto.pipeline.interval-ms`) Gmail 새 메일을 수집하고 분석합니다. 모두 Authorization: 필요, Response data `{ "enabled": true }`.

### GET /api/auto-pipeline/status

### POST /api/auto-pipeline/enable

### POST /api/auto-pipeline/disable

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

충돌 처리:

- 같은 시간대에 이미 일정이 있으면 `409 Conflict`가 반환됩니다.
- 같은 제목/시간으로 보이는 일정은 중복 일정으로 판단합니다.
- 등록하려는 일정 전후 약 3시간 안에 일정이 있으면 근접 일정 경고가 반환되며, 프론트에서 사용자가 계속 진행할지 확인합니다.

409 Response data 예시:

```json
{
  "type": "TIME_CONFLICT",
  "newSchedule": {
    "title": "CRM Automation 관련 미팅",
    "scheduleDateTime": "2026-06-12T10:00:00"
  },
  "conflicts": [
    {
      "scheduleId": 10,
      "title": "Sales Platform 관련 미팅",
      "scheduleDateTime": "2026-06-12T10:00:00",
      "status": "SCHEDULED"
    }
  ]
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

### PATCH /api/schedules/{scheduleId}/outcome

미팅이 끝난 뒤 결과를 기록합니다. 일정 상태는 `COMPLETED`가 됩니다. 시작 전이거나 취소된 일정에는 기록할 수 없습니다(400).

Authorization: 필요

Request:

```json
{
  "outcome": "SUCCESS"
}
```

- `outcome`: `SUCCESS`(잘됨) / `ON_HOLD`(보류) / `LOST`(무산)
- 일정 응답(`ScheduleResponse`)에 `outcome`, `outcomeRecordedAt`이 추가되었습니다.

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

## Customers

### GET /api/customers

로그인 사용자의 고객사/담당자 목록을 조회합니다.

Authorization: 필요

Response data 예시:

```json
[
  {
    "customerContactId": 1,
    "customerName": "Delta Systems",
    "contactName": "최유진",
    "email": "lani5700@naver.com",
    "domain": "naver.com",
    "lastSeenAt": "2026-06-25T14:00:00",
    "activityCount": 3
  }
]
```

### GET /api/customers/{customerContactId}/timeline

고객사별 활동 타임라인을 조회합니다.

Authorization: 필요

타임라인에는 AI 분석, 일정 생성/변경/취소, Salesmap 등록 이력이 포함됩니다. 업무 외 메일은 고객 타임라인에 등록하지 않습니다.

Response data 예시:

```json
{
  "customer": {
    "customerContactId": 1,
    "customerName": "Delta Systems",
    "contactName": "최유진",
    "email": "lani5700@naver.com",
    "domain": "naver.com",
    "lastSeenAt": "2026-06-25T14:00:00",
    "activityCount": 3
  },
  "activities": [
    {
      "activityId": 10,
      "activityType": "AI_ANALYZED",
      "title": "Sales Platform 관련 미팅 분석",
      "description": "일정 생성 요청 표현과 날짜/시간 정보가 포함되어 있습니다.",
      "sourceId": 2464,
      "analysisId": 362,
      "scheduleId": null,
      "salesmapRecordId": null,
      "occurredAt": "2026-06-25T14:00:00",
      "createdAt": "2026-06-05T12:00:00"
    }
  ]
}
```

### GET /api/customers/pipeline

고객사별 딜 진행 카드 목록입니다. 최근 분석의 actionType으로 단계를 정하고, 업무 외 메일은 제외합니다. Authorization: 필요

Response data 항목: `customerContactId`, `customerName`, `contactName`, `email`, `productName`, `amount`, `stage`, `stageLabel`, `stageOrder`, `lastActionType`, `nextAction`, `stale`, `dealCount`, `lastActivityAt`

| stage | 라벨 | 기준 |
| --- | --- | --- |
| `LEAD` | 리드 | 그 외 |
| `MEETING` | 미팅 예정 | 최근 `CREATE` |
| `ADJUSTING` | 일정 조율 | 최근 `UPDATE` |
| `CONFIRMED` | 확정 | 최근 `CONFIRM` |
| `CANCELED` | 보류·취소 | 최근 `CANCEL` |

`stale`: 리드·미팅 예정·일정 조율 단계에서 3영업일 이상 활동이 없으면 `true`

## Dev (개발자용 데모 모드)

켜면 로그인한 사용자의 요청이 숨은 데모 사용자의 데이터로 처리됩니다. 모두 Authorization: 필요.

### GET /api/dev/demo/status

### POST /api/dev/demo/enable

### POST /api/dev/demo/disable

### POST /api/dev/demo/reset

Response data: `enabled`, `demoAccountEmail`, `anchorDate`, `deliveredStage`, `lastStage`, `deliveredMailCount`, `pendingMailCount`

## 기타

### GET /api/health

서버 상태 확인. Authorization: 불필요

### POST /api/users · GET /api/users/{id}

1학기 초기 사용자 생성·조회 API (회원가입은 `/api/auth/signup` 사용). Authorization: 필요

## Status Codes

| Status | 의미 |
| --- | --- |
| 200 | 성공 |
| 400 | 요청값 오류 또는 validation 실패 |
| 401 | 로그인 필요 |
| 403 | 소유권 없음 |
| 404 | 데이터 없음 |
| 409 | 일정 중복 또는 근접 일정 충돌 |
| 500 | 서버 내부 오류 |
| 502 | AI Module 또는 Google Calendar 등 외부 연동 실패 |
