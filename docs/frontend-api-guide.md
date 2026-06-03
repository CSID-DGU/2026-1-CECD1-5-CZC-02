# Frontend API Guide

React + Vite 프론트엔드에서 SALESMAP 백엔드 API를 연동하는 방법을 정리한 문서입니다. 전체 API 필드 명세는 [api-spec.md](./api-spec.md)를 기준으로 확인합니다.

## Base URL

로컬 백엔드 기본 주소:

```text
http://localhost:8080
```

권장 Vite 환경 변수:

```env
VITE_API_BASE_URL=http://localhost:8080
```

현재 프론트 API client는 기본적으로 `http://localhost:8080`을 사용합니다.

## Authentication Flow

프론트 인증 흐름:

```text
회원가입 또는 로그인
  -> 백엔드가 accessToken 반환
  -> localStorage.accessToken 저장
  -> ProtectedRoute로 보호 화면 접근 제어
  -> axios interceptor가 보호 API 요청마다 Authorization 헤더 자동 첨부
  -> 로그아웃 시 localStorage.accessToken 삭제
```

Authorization 헤더:

```http
Authorization: Bearer {accessToken}
```

공개 API:

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `GET /api/health`

그 외 주요 API는 JWT가 필요합니다.

## Axios Client Pattern

현재 프론트 구조는 `frontend/src/api/client.js`의 공통 axios client를 사용합니다.

예시:

```javascript
import axios from "axios";

export const api = axios.create({
  baseURL: "http://localhost:8080",
  headers: {
    "Content-Type": "application/json",
  },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("accessToken");

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});
```

개별 API 파일에서는 `axios`를 직접 import하지 말고 반드시 공통 `api` 인스턴스를 사용합니다.

```javascript
import { api } from "./client";

export async function getSources() {
  const response = await api.get("/api/sources");
  return response.data.data;
}
```

## Current API Files

현재 프론트에서 분리된 API 함수 파일:

- `frontend/src/api/sources.js`
- `frontend/src/api/schedules.js`
- `frontend/src/api/analyses.js`
- `frontend/src/api/integrations.js`
- `frontend/src/api/salesmapRecords.js`
- `frontend/src/api/client.js`

역할:

- `client.js`: base URL, JSON header, Authorization interceptor
- `sources.js`: Source 생성, 목록 조회, 상세 조회
- `schedules.js`: Schedule 생성, 목록 조회
- `analyses.js`: Analysis 생성, 상세 조회, Source 기준 목록 조회
- `salesmapRecords.js`: Salesmap 등록, Analysis 기준 등록 이력 조회

## Gmail Auto Sync

로그인 성공 후 프론트는 `localStorage.accessToken`을 저장한 다음 Gmail 자동 동기화를 시도합니다.

흐름:

```text
POST /api/auth/login
  -> localStorage.accessToken 저장
  -> GET /api/integrations
  -> GMAIL provider가 CONNECTED이면 POST /api/integrations/gmail/collect 호출
  -> 대시보드로 이동
```

주의:

- Gmail 연동 정보가 없으면 자동 수집을 건너뜁니다.
- Gmail 수집 실패가 로그인 자체를 막지는 않습니다.
- 자동 수집 API는 기존 JWT Authorization header를 사용합니다.

## Protected Routing

`ProtectedRoute`는 `localStorage.accessToken`이 없으면 로그인 화면으로 이동시킵니다.

로그아웃 시 처리:

```javascript
localStorage.removeItem("accessToken");
navigate("/login");
```

## Dashboard Integration

현재 Dashboard에서는 기존 mock UI를 유지하면서 보호 API 연결 상태를 확인합니다.

연결된 API:

- `GET /api/sources`
- `GET /api/schedules`
- `GET /api/analysis/source/{sourceId}` 또는 관련 조회 함수
- `GET /api/salesmap/analysis/{analysisId}` 또는 관련 조회 함수

통합 테스트 패널에서 가능한 작업:

- Source 생성 테스트
- Schedule 생성 테스트
- 생성 후 목록 재조회
- 성공/실패 상태 표시

주의:

- Source 생성 payload는 백엔드 DTO 기준으로 `sourceType`, `title`, `content`, 선택 `integrationId`만 사용합니다.
- Schedule 생성 payload는 `title`, `scheduleDateTime`, `memo`, 선택 `analysisId`를 사용합니다.
- `description` 필드는 현재 백엔드 Schedule DTO에 없습니다.

## MessageView Flow

현재 `MessageView`에서는 Source -> Analysis -> Salesmap 흐름을 테스트할 수 있습니다.

흐름:

```text
Source 목록 조회
  -> Source 클릭
  -> GET /api/sources/{sourceId}
  -> GET /api/analysis/source/{sourceId}
  -> AI 분석 테스트 버튼
  -> if sourceGroupId exists, call POST /api/analysis/group
  -> otherwise, call POST /api/analysis
  -> Analysis 목록 재조회
  -> SALESMAP 등록 테스트 버튼
  -> POST /api/salesmap/register
  -> GET /api/salesmap/analysis/{analysisId}
```

현재 AI는 실제 FastAPI가 아니라 백엔드 `MockAiClient` 결과를 사용합니다. Salesmap 등록도 실제 외부 API가 아니라 `MockSalesmapClient` 결과를 사용합니다.

Gmail collected Sources have `sourceGroupId` based on the Gmail thread ID, so the UI calls the SourceGroup analysis API first.
For manually created Sources without `sourceGroupId`, the UI keeps the existing single Source analysis API.

## Main Request Examples

### Signup

```http
POST /api/auth/signup
Content-Type: application/json
```

```json
{
  "email": "test@example.com",
  "password": "12345678",
  "name": "test"
}
```

성공 시 `data.accessToken`을 저장합니다.

### Login

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "email": "test@example.com",
  "password": "12345678"
}
```

### Create Source

```http
POST /api/sources
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "integrationId": null,
  "sourceType": "EMAIL",
  "title": "테스트 이메일",
  "content": "고객사 미팅 일정과 후속 조치가 포함된 테스트 내용입니다."
}
```

### Get Sources

```http
GET /api/sources
Authorization: Bearer {accessToken}
```

### Get Source Detail

```http
GET /api/sources/{sourceId}
Authorization: Bearer {accessToken}
```

### Create Analysis

```http
POST /api/analysis
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "sourceId": 1
}
```

### Create Group Analysis

Use this when the selected Source has `sourceGroupId`.

```http
POST /api/analysis/group
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "sourceGroupId": 1
}
```

### Get Analyses By Source

```http
GET /api/analysis/source/{sourceId}
Authorization: Bearer {accessToken}
```

### Create Schedule

```http
POST /api/schedules
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "analysisId": null,
  "title": "테스트 일정",
  "scheduleDateTime": "2026-05-29T14:00:00",
  "memo": "프론트-백 통합 테스트 일정"
}
```

### Get Schedules

```http
GET /api/schedules
Authorization: Bearer {accessToken}
```

### Register Salesmap

```http
POST /api/salesmap/register
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "analysisId": 1
}
```

### Get Salesmap Records By Analysis

```http
GET /api/salesmap/analysis/{analysisId}
Authorization: Bearer {accessToken}
```

## Error Handling

### 400 Bad Request

요청 DTO 검증 실패입니다. request body 필드명 또는 필수값을 확인합니다.

### 401 Unauthorized

토큰이 없거나 유효하지 않은 경우입니다.

프론트 처리:

- `localStorage.accessToken` 삭제
- 로그인 화면으로 이동

### 403 Forbidden

로그인은 되었지만 다른 사용자의 리소스에 접근한 경우입니다.

예:

- 다른 사용자의 `sourceId`로 Analysis 생성
- 다른 사용자의 `analysisId`로 Salesmap 등록

### 404 Not Found

요청한 부모 리소스가 없거나 API 경로가 맞지 않는 경우입니다.

### 500 Internal Server Error

백엔드 저장 오류 등 서버 내부 오류입니다. 현재 백엔드는 `DataIntegrityViolationException`도 JSON 형태로 응답합니다.

### 502 Bad Gateway

백엔드가 외부 AI Module 또는 Salesmap API 호출에 실패한 경우입니다. 현재 기본 mock 모드에서는 일반적으로 발생하지 않습니다.

## Mock Mode vs HTTP Mode

프론트 요청 방식은 mock/http 모드와 관계없이 동일합니다.

| Target | Mock Mode | HTTP Mode |
| --- | --- | --- |
| AI Module | `MockAiClient`가 mock 분석 결과 반환 | FastAPI `/analyze` 호출 |
| Salesmap API | `MockSalesmapClient`가 mock 등록 결과 반환 | 실제 Salesmap API 호출 |

프론트는 백엔드 API만 호출하고, 외부 서비스 호출 여부는 백엔드 property로 제어합니다.

## CORS

백엔드는 `cors.allowed-origins` 설정으로 허용 origin을 관리합니다.

현재 기본값:

```properties
cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://127.0.0.1:5173}
```

React + Vite 기본 개발 서버는 `http://localhost:5173`입니다.

## Frontend Notes

- 보호 API는 항상 공통 `api` 인스턴스를 사용합니다.
- `userId`를 일반 화면에서 직접 보내지 않는 구조를 우선 사용합니다.
- 날짜/시간은 ISO-8601 문자열을 사용합니다.
- 목록 API는 데이터가 없으면 빈 배열 `[]`을 반환합니다.
- 실제 AI/FastAPI/OpenAI 호출은 아직 프론트에서 직접 다루지 않습니다.
