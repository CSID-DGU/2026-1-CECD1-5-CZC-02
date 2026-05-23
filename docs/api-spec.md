# API Specification

SALESMAP 활동 자동화 AI Agent 백엔드 API 명세 초안입니다.

현재 문서는 DB 연결 전 mock API 기준입니다. 모든 응답은 공통 응답 형식인 `ApiResponse`로 감싸서 반환합니다.

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

## Source API

원본 데이터 관리 API입니다. 이메일, 메시지, 회의록 등 AI 분석 전 원본 데이터를 다룹니다.

### POST /api/sources

설명:
원본 데이터를 생성합니다.

요청:

```json
{
  "sourceType": "EMAIL",
  "title": "고객 미팅 관련 이메일",
  "content": "원본 이메일 또는 메시지 내용"
}
```

응답:

```json
{
  "success": true,
  "message": "원본 데이터가 생성되었습니다.",
  "data": {
    "sourceId": 1,
    "sourceType": "EMAIL",
    "title": "고객 미팅 관련 이메일",
    "content": "원본 이메일 또는 메시지 내용",
    "status": "CREATED"
  }
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

응답:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "sourceId": 1,
    "sourceType": "EMAIL",
    "title": "고객 미팅 관련 이메일",
    "content": "원본 이메일 또는 메시지 내용",
    "status": "CREATED"
  }
}
```

## Analysis API

AI 분석 요청 및 분석 결과 확인 API입니다.

### POST /api/analysis

설명:
원본 데이터 ID를 기반으로 AI 분석을 요청합니다. 현재는 AI module을 실제 호출하지 않고 mock 분석 결과를 반환합니다.

요청:

```json
{
  "sourceId": 1
}
```

응답:

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
    "status": "ANALYZED"
  }
}
```

### GET /api/analysis/{analysisId}

설명:
분석 ID로 AI 분석 결과를 조회합니다.

Path Variable:

| Name | Type | Description |
| --- | --- | --- |
| analysisId | Long | 분석 결과 ID |

요청:
없음

응답:

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
    "status": "ANALYZED"
  }
}
```

## Salesmap API

Salesmap 등록 요청 API입니다. 사용자가 분석 결과를 승인한 뒤 Salesmap 등록을 요청하는 흐름입니다.

### POST /api/salesmap/register

설명:
분석 결과 ID를 기반으로 Salesmap 등록 요청을 처리합니다. 현재는 실제 Salesmap API를 호출하지 않고 mock 등록 결과를 반환합니다.

요청:

```json
{
  "analysisId": 1
}
```

응답:

```json
{
  "success": true,
  "message": "Salesmap 등록 요청이 완료되었습니다.",
  "data": {
    "salesmapRecordId": 1,
    "analysisId": 1,
    "externalRecordId": "mock-salesmap-001",
    "status": "REGISTERED"
  }
}
```

## Schedule API

분석 결과에서 추출된 일정 또는 후속 조치를 등록하는 API입니다.

### POST /api/schedules

설명:
분석 결과 ID를 기반으로 일정을 생성합니다.

요청:

```json
{
  "analysisId": 1,
  "title": "ABC Corp 미팅",
  "scheduleDateTime": "2026-05-29T14:00:00",
  "memo": "견적서 준비 후 미팅"
}
```

응답:

```json
{
  "success": true,
  "message": "일정이 등록되었습니다.",
  "data": {
    "scheduleId": 1,
    "analysisId": 1,
    "title": "ABC Corp 미팅",
    "scheduleDateTime": "2026-05-29T14:00:00",
    "memo": "견적서 준비 후 미팅",
    "status": "SCHEDULED"
  }
}
```

## Current Notes

- 현재 API는 mock Service 기반입니다.
- 아직 DB 저장은 하지 않습니다.
- 아직 Entity와 Repository는 없습니다.
- 아직 인증/인가가 적용되어 있지 않습니다.
- 아직 Gmail, Jandi, AI module, Salesmap 외부 API를 실제 호출하지 않습니다.
- 날짜와 시간은 ISO-8601 형식 문자열을 사용합니다. 예: `2026-05-29T14:00:00`
