# API Specification

SALESMAP 활동 자동화 AI Agent 백엔드 API 명세 초안입니다.

모든 응답은 공통 응답 형식인 `ApiResponse`로 감싸서 반환합니다.

현재 `User API`, `Source API`, `Analysis API`, `Schedule API`, `Salesmap API`는 MySQL DB 기반으로 동작합니다.

## Common Response

### Success

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

### Fail

```json
{
  "success": false,
  "message": "오류 메시지",
  "data": null
}
```

### Validation Error

요청 DTO 검증에 실패하면 HTTP `400 Bad Request`와 함께 아래 형식으로 응답합니다.

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "title": "제목은 필수입니다.",
    "content": "내용은 필수입니다."
  }
}
```

## Health API

### GET /api/health

설명:
백엔드 서버가 정상 실행 중인지 확인합니다.

요청:
없음

응답:

```json
{
  "success": true,
  "message": "Backend server is running",
  "data": "OK"
}
```

## User API

사용자 생성 및 조회 API입니다. 현재 `users` Entity와 `UserRepository`를 통해 MySQL DB에 저장하고 조회합니다.

### POST /api/users

설명:
사용자를 생성합니다. 생성 시 `role`은 `USER`, `status`는 `ACTIVE`로 저장됩니다. 비밀번호 암호화와 로그인 기능은 아직 구현하지 않았습니다.

요청:

```json
{
  "email": "test@example.com",
  "name": "홍길동",
  "password": "1234"
}
```

요청 필드:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| email | String | Yes | 빈 문자열 불가, 이메일 형식 |
| name | String | Yes | 빈 문자열 불가 |
| password | String | Yes | 빈 문자열 불가 |

성공 응답:

```json
{
  "success": true,
  "message": "사용자가 생성되었습니다.",
  "data": {
    "id": 1,
    "email": "test@example.com",
    "name": "홍길동",
    "role": "USER",
    "status": "ACTIVE",
    "createdAt": "2026-05-24T14:00:00",
    "updatedAt": "2026-05-24T14:00:00"
  }
}
```

400 Bad Request - Validation Error:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "email": "이메일 형식이 올바르지 않습니다.",
    "name": "이름은 필수입니다.",
    "password": "비밀번호는 필수입니다."
  }
}
```

400 Bad Request - Duplicate Email:

```json
{
  "success": false,
  "message": "이미 사용 중인 이메일입니다.",
  "data": null
}
```

### GET /api/users/{id}

설명:
사용자 ID로 사용자를 조회합니다.

Path Variable:

| Name | Type | Description |
| --- | --- | --- |
| id | Long | 사용자 ID |

요청:
없음

성공 응답:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "id": 1,
    "email": "test@example.com",
    "name": "홍길동",
    "role": "USER",
    "status": "ACTIVE",
    "createdAt": "2026-05-24T14:00:00",
    "updatedAt": "2026-05-24T14:00:00"
  }
}
```

404 Not Found:

```json
{
  "success": false,
  "message": "사용자를 찾을 수 없습니다.",
  "data": null
}
```

## Source API

원본 데이터 관리 API입니다. 이메일, 메시지, 회의록 등 AI 분석 전 원본 데이터를 DB에 저장하고 조회합니다.

### POST /api/sources

설명:
원본 데이터를 생성합니다.

요청:

```json
{
  "userId": 1,
  "integrationId": null,
  "sourceType": "EMAIL",
  "title": "고객 미팅 관련 이메일",
  "content": "원본 이메일 또는 메시지 내용"
}
```

요청 필드:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| userId | Long | Yes | `null` 불가, 1 이상 |
| integrationId | Long | No | 값이 있으면 1 이상 |
| sourceType | String | Yes | 빈 문자열 불가, `EMAIL`, `JANDI_MESSAGE`, `MEETING_NOTE`, `MANUAL_INPUT` 중 하나 |
| title | String | Yes | 빈 문자열 불가 |
| content | String | Yes | 빈 문자열 불가 |

성공 응답:

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
    "title": "고객 미팅 관련 이메일",
    "content": "원본 이메일 또는 메시지 내용",
    "status": "CREATED",
    "collectedAt": null,
    "createdAt": "2026-05-24T14:00:00",
    "updatedAt": "2026-05-24T14:00:00"
  }
}
```

400 Bad Request - Validation Error:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "userId": "userId는 필수입니다.",
    "integrationId": "integrationId는 1 이상이어야 합니다.",
    "sourceType": "원본 데이터 타입은 필수입니다.",
    "title": "제목은 필수입니다.",
    "content": "내용은 필수입니다."
  }
}
```

400 Bad Request - Invalid Source Type:

```json
{
  "success": false,
  "message": "지원하지 않는 원본 데이터 타입입니다.",
  "data": null
}
```

400 Bad Request - Integration Owner Mismatch:

```json
{
  "success": false,
  "message": "해당 사용자의 연동 정보가 아닙니다.",
  "data": null
}
```

404 Not Found - User Not Found:

```json
{
  "success": false,
  "message": "사용자를 찾을 수 없습니다.",
  "data": null
}
```

404 Not Found - Integration Not Found:

```json
{
  "success": false,
  "message": "연동 정보를 찾을 수 없습니다.",
  "data": null
}
```

### GET /api/sources/{sourceId}

설명:
원본 데이터 ID로 원본 데이터를 조회합니다.

Path Variable:

| Name | Type | Description |
| --- | --- | --- |
| sourceId | Long | 원본 데이터 ID |

요청:
없음

성공 응답:

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
    "title": "고객 미팅 관련 이메일",
    "content": "원본 이메일 또는 메시지 내용",
    "status": "CREATED",
    "collectedAt": null,
    "createdAt": "2026-05-24T14:00:00",
    "updatedAt": "2026-05-24T14:00:00"
  }
}
```

404 Not Found:

```json
{
  "success": false,
  "message": "원본 데이터를 찾을 수 없습니다.",
  "data": null
}
```

### GET /api/sources?userId=1

설명:
사용자 ID 기준으로 원본 데이터 목록을 조회합니다.

Query Parameter:

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| userId | Long | Yes | 사용자 ID |

성공 응답:

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
      "title": "고객 미팅 관련 이메일",
      "content": "원본 이메일 또는 메시지 내용",
      "status": "CREATED",
      "collectedAt": null,
      "createdAt": "2026-05-24T14:00:00",
      "updatedAt": "2026-05-24T14:00:00"
    }
  ]
}
```

빈 목록 응답:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": []
}
```

400 Bad Request - Missing Query Parameter:

```json
{
  "success": false,
  "message": "필수 요청 파라미터가 누락되었습니다: userId",
  "data": null
}
```

404 Not Found - User Not Found:

```json
{
  "success": false,
  "message": "사용자를 찾을 수 없습니다.",
  "data": null
}
```

## Analysis API

AI 분석 요청 및 분석 결과 확인 API입니다. 현재 실제 AI module은 호출하지 않고, mock 분석 결과를 `analyses` 테이블에 저장합니다.

### POST /api/analysis

설명:
`sourceId`를 기반으로 Source를 조회한 뒤 mock 분석 결과를 DB에 저장합니다. 분석 생성 시 Source 상태는 `ANALYZED`로 변경됩니다.

요청:

```json
{
  "sourceId": 1
}
```

요청 필드:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| sourceId | Long | Yes | `null` 불가, 1 이상 |

성공 응답:

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
    "analyzedAt": "2026-05-24T14:00:00",
    "approvedAt": null,
    "createdAt": "2026-05-24T14:00:00",
    "updatedAt": "2026-05-24T14:00:00"
  }
}
```

400 Bad Request - Validation Error:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "sourceId": "sourceId는 1 이상이어야 합니다."
  }
}
```

404 Not Found - Source Not Found:

```json
{
  "success": false,
  "message": "원본 데이터를 찾을 수 없습니다.",
  "data": null
}
```

### GET /api/analysis/{analysisId}

설명:
분석 ID로 DB에 저장된 AI 분석 결과를 조회합니다.

Path Variable:

| Name | Type | Description |
| --- | --- | --- |
| analysisId | Long | 분석 결과 ID |

요청:
없음

성공 응답:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
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
    "analyzedAt": "2026-05-24T14:00:00",
    "approvedAt": null,
    "createdAt": "2026-05-24T14:00:00",
    "updatedAt": "2026-05-24T14:00:00"
  }
}
```

404 Not Found:

```json
{
  "success": false,
  "message": "분석 결과를 찾을 수 없습니다.",
  "data": null
}
```

### GET /api/analysis/source/{sourceId}

설명:
원본 데이터 ID 기준으로 AI 분석 결과 목록을 조회합니다.

Path Variable:

| Name | Type | Description |
| --- | --- | --- |
| sourceId | Long | 원본 데이터 ID |

성공 응답:

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
      "analyzedAt": "2026-05-24T14:00:00",
      "approvedAt": null,
      "createdAt": "2026-05-24T14:00:00",
      "updatedAt": "2026-05-24T14:00:00"
    }
  ]
}
```

빈 목록 응답:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": []
}
```

404 Not Found - Source Not Found:

```json
{
  "success": false,
  "message": "원본 데이터를 찾을 수 없습니다.",
  "data": null
}
```

## Salesmap API

Salesmap 등록 요청 API입니다. 사용자가 분석 결과를 승인한 뒤 Salesmap 등록 이력을 DB에 저장하는 흐름입니다.

### POST /api/salesmap/register

설명:
`analysisId`를 기반으로 Analysis를 조회한 뒤 Salesmap 등록 이력을 `salesmap_records` 테이블에 저장합니다. 실제 Salesmap 외부 API는 아직 호출하지 않고, mock `externalRecordId`, `requestPayload`, `responsePayload`를 저장합니다. 등록 성공 시 Analysis 상태는 `APPROVED`로 변경됩니다.

요청:

```json
{
  "analysisId": 1
}
```

요청 필드:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| analysisId | Long | Yes | `null` 불가, 1 이상 |

성공 응답:

```json
{
  "success": true,
  "message": "Salesmap 등록 요청이 완료되었습니다.",
  "data": {
    "salesmapRecordId": 1,
    "analysisId": 1,
    "externalRecordId": "mock-salesmap-1",
    "requestPayload": "{\"analysisId\":1}",
    "responsePayload": "{\"externalRecordId\":\"mock-salesmap-1\",\"status\":\"REGISTERED\"}",
    "status": "REGISTERED",
    "registeredAt": "2026-05-24T14:00:00",
    "createdAt": "2026-05-24T14:00:00",
    "updatedAt": "2026-05-24T14:00:00"
  }
}
```

400 Bad Request - Validation Error:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "analysisId": "analysisId는 1 이상이어야 합니다."
  }
}
```

404 Not Found - Analysis Not Found:

```json
{
  "success": false,
  "message": "분석 결과를 찾을 수 없습니다.",
  "data": null
}
```

### GET /api/salesmap/analysis/{analysisId}

설명:
분석 결과 ID 기준으로 Salesmap 등록 이력 목록을 조회합니다.

Path Variable:

| Name | Type | Description |
| --- | --- | --- |
| analysisId | Long | 분석 결과 ID |

성공 응답:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": [
    {
      "salesmapRecordId": 1,
      "analysisId": 1,
      "externalRecordId": "mock-salesmap-1",
      "requestPayload": "{\"analysisId\":1}",
      "responsePayload": "{\"externalRecordId\":\"mock-salesmap-1\",\"status\":\"REGISTERED\"}",
      "status": "REGISTERED",
      "registeredAt": "2026-05-24T14:00:00",
      "createdAt": "2026-05-24T14:00:00",
      "updatedAt": "2026-05-24T14:00:00"
    }
  ]
}
```

빈 목록 응답:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": []
}
```

404 Not Found - Analysis Not Found:

```json
{
  "success": false,
  "message": "분석 결과를 찾을 수 없습니다.",
  "data": null
}
```

## Schedule API

분석 결과에서 추출된 일정 또는 후속 조치를 등록하는 API입니다.

### POST /api/schedules

설명:
사용자 ID와 선택적 분석 결과 ID를 기반으로 일정을 생성합니다. `analysisId`가 없으면 일반 사용자 일정으로 저장합니다.

요청:

```json
{
  "userId": 1,
  "analysisId": null,
  "title": "ABC Corp 미팅",
  "scheduleDateTime": "2026-05-29T14:00:00",
  "memo": "견적서 준비 후 미팅"
}
```

요청 필드:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| userId | Long | Yes | `null` 불가, 1 이상 |
| analysisId | Long | No | 값이 있으면 1 이상 |
| title | String | Yes | 빈 문자열 불가 |
| scheduleDateTime | LocalDateTime | Yes | `null` 불가, 현재 또는 미래 시간 |
| memo | String | Yes | 빈 문자열 불가 |

성공 응답:

```json
{
  "success": true,
  "message": "일정이 등록되었습니다.",
  "data": {
    "scheduleId": 1,
    "userId": 1,
    "analysisId": null,
    "title": "ABC Corp 미팅",
    "scheduleDateTime": "2026-05-29T14:00:00",
    "memo": "견적서 준비 후 미팅",
    "reminderDateTime": null,
    "status": "SCHEDULED",
    "createdAt": "2026-05-24T14:00:00",
    "updatedAt": "2026-05-24T14:00:00"
  }
}
```

400 Bad Request - Validation Error:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "userId": "userId는 필수입니다.",
    "analysisId": "analysisId는 1 이상이어야 합니다.",
    "title": "일정 제목은 필수입니다.",
    "scheduleDateTime": "일정 날짜와 시간은 현재 이후여야 합니다.",
    "memo": "메모는 필수입니다."
  }
}
```

404 Not Found - User Not Found:

```json
{
  "success": false,
  "message": "사용자를 찾을 수 없습니다.",
  "data": null
}
```

404 Not Found - Analysis Not Found:

```json
{
  "success": false,
  "message": "분석 결과를 찾을 수 없습니다.",
  "data": null
}
```

### GET /api/schedules?userId=1

설명:
사용자 ID 기준으로 일정 목록을 조회합니다.

Query Parameter:

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| userId | Long | Yes | 사용자 ID |

성공 응답:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": [
    {
      "scheduleId": 1,
      "userId": 1,
      "analysisId": null,
      "title": "ABC Corp 미팅",
      "scheduleDateTime": "2026-05-29T14:00:00",
      "memo": "견적서 준비 후 미팅",
      "reminderDateTime": null,
      "status": "SCHEDULED",
      "createdAt": "2026-05-24T14:00:00",
      "updatedAt": "2026-05-24T14:00:00"
    }
  ]
}
```

빈 목록 응답:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": []
}
```

400 Bad Request - Missing Query Parameter:

```json
{
  "success": false,
  "message": "필수 요청 파라미터가 누락되었습니다: userId",
  "data": null
}
```

404 Not Found - User Not Found:

```json
{
  "success": false,
  "message": "사용자를 찾을 수 없습니다.",
  "data": null
}
```

## Current Notes

- User API는 MySQL DB 기반으로 저장 및 조회합니다.
- Source API는 MySQL DB 기반으로 저장 및 조회합니다.
- Analysis API는 MySQL DB 기반으로 저장 및 조회합니다. 단, 실제 AI module은 아직 호출하지 않고 mock 분석 결과를 저장합니다.
- Schedule API는 MySQL DB 기반으로 저장합니다.
- Salesmap API는 MySQL DB 기반으로 등록 이력을 저장합니다. 단, 실제 Salesmap 외부 API는 아직 호출하지 않고 mock payload를 저장합니다.
- Entity와 Repository는 users, integrations, sources, analyses, schedules, salesmap_records 도메인에 적용되어 있습니다.
- 아직 인증/인가가 적용되어 있지 않습니다.
- 아직 Gmail, Jandi, AI module, Salesmap 외부 API를 실제 호출하지 않습니다.
- 날짜와 시간은 ISO-8601 형식 문자열을 사용합니다. 예: `2026-05-29T14:00:00`
