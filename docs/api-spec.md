# API Specification

SALESMAP 활동 자동화 AI Agent 백엔드 API 명세입니다.

모든 응답은 `ApiResponse` 형태로 감싸서 반환합니다.

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

## Current Notes

- User, Source, Analysis, Schedule, Salesmap API는 MySQL DB 기반으로 동작합니다.
- 주요 보호 API는 JWT 인증이 필요합니다.
- 보호 API 요청에는 `Authorization: Bearer {accessToken}` 헤더가 필요합니다.
- Source, Schedule, Analysis, Salesmap API는 로그인 사용자 기준 소유권 검증을 수행합니다.
- AI 분석은 현재 기본값 `ai.module.mode=mock` 기준으로 `MockAiClient`가 결과를 생성합니다.
- Salesmap 등록은 현재 기본값 `salesmap.api.mode=mock` 기준으로 `MockSalesmapClient`가 결과를 생성합니다.
- 실제 Gmail, JANDI, FastAPI AI Module, 외부 Salesmap API 호출은 아직 연결하지 않았습니다.

## Common Status Codes

| Status | Meaning |
| --- | --- |
| 200 | 요청 성공 |
| 400 | Validation 실패 또는 잘못된 요청 |
| 401 | 인증 토큰 없음 또는 유효하지 않음 |
| 403 | 인증은 되었지만 해당 리소스 접근 권한 없음 |
| 404 | 요청한 리소스 없음 |
| 500 | 서버 내부 오류 |
| 502 | 외부 AI Module 또는 Salesmap API 호출 실패 |

Validation 실패 예시:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "title": "제목은 필수입니다."
  }
}
```

## Auth API

### POST /api/auth/signup

회원가입 후 JWT accessToken을 발급합니다.

Authorization: 불필요

Request Body:

```json
{
  "email": "test@example.com",
  "password": "12345678",
  "name": "test"
}
```

Response:

```json
{
  "success": true,
  "message": "회원가입이 완료되었습니다.",
  "data": {
    "tokenType": "Bearer",
    "accessToken": "jwt-access-token",
    "expiresIn": 3600000,
    "user": {
      "id": 1,
      "email": "test@example.com",
      "name": "test",
      "role": "USER",
      "status": "ACTIVE",
      "createdAt": "2026-05-27T14:00:00",
      "updatedAt": "2026-05-27T14:00:00"
    }
  }
}
```

### POST /api/auth/login

로그인 후 JWT accessToken을 발급합니다.

Authorization: 불필요

Request Body:

```json
{
  "email": "test@example.com",
  "password": "12345678"
}
```

Response는 signup과 동일한 `AuthResponse` 구조입니다.

### GET /api/auth/me

현재 로그인 사용자 정보를 조회합니다.

Authorization: 필요

Response:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "id": 1,
    "email": "test@example.com",
    "name": "test",
    "role": "USER",
    "status": "ACTIVE",
    "createdAt": "2026-05-27T14:00:00",
    "updatedAt": "2026-05-27T14:00:00"
  }
}
```

## Health API

### GET /api/health

서버 상태를 확인합니다.

Authorization: 불필요

Response:

```json
{
  "success": true,
  "message": "Backend server is running",
  "data": "OK"
}
```

## Source API

### POST /api/sources

로그인 사용자의 원본 데이터를 생성합니다.

Authorization: 필요

Request Body:

```json
{
  "integrationId": null,
  "sourceType": "EMAIL",
  "title": "테스트 이메일",
  "content": "고객사 미팅 일정과 후속 조치가 포함된 테스트 내용입니다."
}
```

Request Fields:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| userId | Long | No | 기존 호환용. 있으면 로그인 사용자 ID와 같아야 함 |
| integrationId | Long | No | 연동 계정 ID. null 가능 |
| sourceType | String | Yes | `EMAIL`, `JANDI_MESSAGE`, `MEETING_NOTE`, `MANUAL_INPUT` |
| title | String | Yes | 원본 데이터 제목 |
| content | String | Yes | 원본 내용 |

Response:

```json
{
  "success": true,
  "message": "원본 데이터가 생성되었습니다.",
  "data": {
    "sourceId": 1,
    "userId": 1,
    "integrationId": null,
    "sourceType": "EMAIL",
    "externalSourceId": null,
    "title": "테스트 이메일",
    "content": "고객사 미팅 일정과 후속 조치가 포함된 테스트 내용입니다.",
    "status": "CREATED",
    "collectedAt": null,
    "createdAt": "2026-05-27T14:00:00",
    "updatedAt": "2026-05-27T14:00:00"
  }
}
```

Status:

- `400`: Validation 실패 또는 잘못된 `sourceType`
- `401`: 인증 필요
- `403`: 다른 사용자의 `userId` 또는 `integrationId`
- `404`: user 또는 integration 없음

### GET /api/sources

로그인 사용자의 Source 목록을 조회합니다.

Authorization: 필요

Query Parameters:

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| userId | Long | No | 기존 호환용. 있으면 로그인 사용자 ID와 같아야 함 |

Response:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": [
    {
      "sourceId": 1,
      "userId": 1,
      "integrationId": null,
      "sourceType": "EMAIL",
      "externalSourceId": null,
      "title": "테스트 이메일",
      "content": "고객사 미팅 일정과 후속 조치가 포함된 테스트 내용입니다.",
      "status": "CREATED",
      "collectedAt": null,
      "createdAt": "2026-05-27T14:00:00",
      "updatedAt": "2026-05-27T14:00:00"
    }
  ]
}
```

빈 목록:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": []
}
```

Status:

- `401`: 인증 필요
- `403`: 다른 사용자의 `userId`
- `404`: userId에 해당하는 사용자 없음

### GET /api/sources/{sourceId}

Source 상세 정보를 조회합니다.

Authorization: 필요

Path Variables:

| Name | Type | Description |
| --- | --- | --- |
| sourceId | Long | Source ID |

Response:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "sourceId": 1,
    "userId": 1,
    "integrationId": null,
    "sourceType": "EMAIL",
    "externalSourceId": null,
    "title": "테스트 이메일",
    "content": "고객사 미팅 일정과 후속 조치가 포함된 테스트 내용입니다.",
    "status": "CREATED",
    "collectedAt": null,
    "createdAt": "2026-05-27T14:00:00",
    "updatedAt": "2026-05-27T14:00:00"
  }
}
```

Status:

- `401`: 인증 필요
- `403`: 다른 사용자의 Source
- `404`: Source 없음

## Schedule API

### POST /api/schedules

로그인 사용자의 일정을 생성합니다.

Authorization: 필요

Request Body:

```json
{
  "analysisId": null,
  "title": "테스트 일정",
  "scheduleDateTime": "2026-05-29T14:00:00",
  "memo": "프론트-백 통합 테스트 일정"
}
```

Request Fields:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| userId | Long | No | 기존 호환용. 있으면 로그인 사용자 ID와 같아야 함 |
| analysisId | Long | No | Analysis ID. null이면 일반 일정 |
| title | String | Yes | 일정 제목 |
| scheduleDateTime | LocalDateTime | Yes | 현재 또는 미래 시간 |
| memo | String | Yes | 일정 메모 |

Response:

```json
{
  "success": true,
  "message": "일정이 등록되었습니다.",
  "data": {
    "scheduleId": 1,
    "userId": 1,
    "analysisId": null,
    "title": "테스트 일정",
    "scheduleDateTime": "2026-05-29T14:00:00",
    "memo": "프론트-백 통합 테스트 일정",
    "reminderDateTime": null,
    "status": "SCHEDULED",
    "createdAt": "2026-05-27T14:00:00",
    "updatedAt": "2026-05-27T14:00:00"
  }
}
```

Status:

- `400`: Validation 실패
- `401`: 인증 필요
- `403`: 다른 사용자의 `userId` 또는 `analysisId`
- `404`: user 또는 analysis 없음

### GET /api/schedules

로그인 사용자의 일정 목록을 조회합니다.

Authorization: 필요

Query Parameters:

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| userId | Long | No | 기존 호환용. 있으면 로그인 사용자 ID와 같아야 함 |

Response:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": [
    {
      "scheduleId": 1,
      "userId": 1,
      "analysisId": null,
      "title": "테스트 일정",
      "scheduleDateTime": "2026-05-29T14:00:00",
      "memo": "프론트-백 통합 테스트 일정",
      "reminderDateTime": null,
      "status": "SCHEDULED",
      "createdAt": "2026-05-27T14:00:00",
      "updatedAt": "2026-05-27T14:00:00"
    }
  ]
}
```

빈 목록은 `data: []`로 반환합니다.

Status:

- `401`: 인증 필요
- `403`: 다른 사용자의 `userId`
- `404`: userId에 해당하는 사용자 없음

## Analysis API

### POST /api/analysis

Source를 기반으로 Analysis를 생성합니다. 현재는 실제 FastAPI를 호출하지 않고 `MockAiClient` 결과를 DB에 저장합니다.

Authorization: 필요

Request Body:

```json
{
  "sourceId": 1
}
```

Request Fields:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| sourceId | Long | Yes | 분석할 Source ID |

Response:

```json
{
  "success": true,
  "message": "AI 분석이 완료되었습니다.",
  "data": {
    "analysisId": 1,
    "sourceId": 1,
    "customerName": "ABC Corp",
    "contactName": "홍길동",
    "productName": "Sales Solution",
    "amount": 1000000,
    "scheduleText": "다음 주 수요일 미팅",
    "followUpAction": "견적서 발송",
    "summary": "고객이 제품 도입을 검토 중이며 다음 미팅 예정",
    "status": "ANALYZED",
    "analyzedAt": "2026-05-27T14:00:00",
    "approvedAt": null,
    "createdAt": "2026-05-27T14:00:00",
    "updatedAt": "2026-05-27T14:00:00"
  }
}
```

Status:

- `400`: Validation 실패
- `401`: 인증 필요
- `403`: 다른 사용자의 Source
- `404`: Source 없음
- `502`: `ai.module.mode=http`에서 AI Module 호출 실패

### GET /api/analysis/source/{sourceId}

Source 기준 Analysis 목록을 조회합니다.

Authorization: 필요

Path Variables:

| Name | Type | Description |
| --- | --- | --- |
| sourceId | Long | Source ID |

Response:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": [
    {
      "analysisId": 1,
      "sourceId": 1,
      "customerName": "ABC Corp",
      "contactName": "홍길동",
      "productName": "Sales Solution",
      "amount": 1000000,
      "scheduleText": "다음 주 수요일 미팅",
      "followUpAction": "견적서 발송",
      "summary": "고객이 제품 도입을 검토 중이며 다음 미팅 예정",
      "status": "ANALYZED",
      "analyzedAt": "2026-05-27T14:00:00",
      "approvedAt": null,
      "createdAt": "2026-05-27T14:00:00",
      "updatedAt": "2026-05-27T14:00:00"
    }
  ]
}
```

빈 목록은 `data: []`로 반환합니다.

Status:

- `401`: 인증 필요
- `403`: 다른 사용자의 Source
- `404`: Source 없음

## Salesmap API

### POST /api/salesmap/register

Analysis를 기반으로 Salesmap 등록 이력을 생성합니다. 현재는 실제 외부 Salesmap API를 호출하지 않고 `MockSalesmapClient` 결과를 DB에 저장합니다. 등록 성공 시 Analysis 상태는 `APPROVED`로 변경됩니다.

Authorization: 필요

Request Body:

```json
{
  "analysisId": 1
}
```

Request Fields:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| analysisId | Long | Yes | Salesmap 등록 대상 Analysis ID |

Response:

```json
{
  "success": true,
  "message": "Salesmap 등록 요청이 완료되었습니다.",
  "data": {
    "salesmapRecordId": 1,
    "analysisId": 1,
    "externalRecordId": "mock-salesmap-1",
    "requestPayload": "{\"analysisId\":1,\"sourceId\":1,\"customerName\":\"ABC Corp\"}",
    "responsePayload": "{\"externalRecordId\":\"mock-salesmap-1\",\"status\":\"REGISTERED\"}",
    "status": "REGISTERED",
    "registeredAt": "2026-05-27T14:00:00",
    "createdAt": "2026-05-27T14:00:00",
    "updatedAt": "2026-05-27T14:00:00"
  }
}
```

Status:

- `400`: Validation 실패
- `401`: 인증 필요
- `403`: 다른 사용자의 Analysis
- `404`: Analysis 없음
- `500`: DB 저장 오류
- `502`: `salesmap.api.mode=http`에서 외부 Salesmap API 호출 실패

### GET /api/salesmap/analysis/{analysisId}

Analysis 기준 Salesmap 등록 이력 목록을 조회합니다.

Authorization: 필요

Path Variables:

| Name | Type | Description |
| --- | --- | --- |
| analysisId | Long | Analysis ID |

Response:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": [
    {
      "salesmapRecordId": 1,
      "analysisId": 1,
      "externalRecordId": "mock-salesmap-1",
      "requestPayload": "{\"analysisId\":1,\"sourceId\":1,\"customerName\":\"ABC Corp\"}",
      "responsePayload": "{\"externalRecordId\":\"mock-salesmap-1\",\"status\":\"REGISTERED\"}",
      "status": "REGISTERED",
      "registeredAt": "2026-05-27T14:00:00",
      "createdAt": "2026-05-27T14:00:00",
      "updatedAt": "2026-05-27T14:00:00"
    }
  ]
}
```

빈 목록은 `data: []`로 반환합니다.

Status:

- `401`: 인증 필요
- `403`: 다른 사용자의 Analysis
- `404`: Analysis 없음

## Frontend E2E Flow

현재 프론트에서 확인 가능한 흐름:

1. `POST /api/auth/signup` 또는 `POST /api/auth/login`
2. `localStorage.accessToken` 저장
3. `GET /api/sources`, `GET /api/schedules` 보호 API 호출
4. `POST /api/sources`
5. `GET /api/sources/{sourceId}`
6. `POST /api/analysis`
7. `GET /api/analysis/source/{sourceId}`
8. `POST /api/salesmap/register`
9. `GET /api/salesmap/analysis/{analysisId}`

## Notes

- `SourceCreateRequest`는 현재 `externalSourceId`, `collectedAt`을 request body로 받지 않습니다.
- `ScheduleCreateRequest`는 `description`이 아니라 `memo`를 사용합니다.
- 날짜/시간은 ISO-8601 문자열을 사용합니다. 예: `2026-05-29T14:00:00`
- 목록 조회 결과가 없으면 빈 배열 `[]`을 반환합니다.
