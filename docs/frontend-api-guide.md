# Frontend API Guide

React + Vite 프론트엔드에서 SALESMAP 백엔드 API를 연동하기 위한 사용 흐름 중심 가이드입니다.

전체 필드 명세는 [api-spec.md](./api-spec.md)를 참고하고, 이 문서는 실제 화면 연동 순서와 인증 처리에 집중합니다.

## Base URL

로컬 기본 주소:

```text
http://localhost:8080
```

Vite 환경 변수 예시:

```env
VITE_API_BASE_URL=http://localhost:8080
```

## Auth Flow

프론트엔드 기본 흐름:

```text
회원가입 또는 로그인
  -> accessToken 수신
  -> localStorage 또는 상태 관리 도구에 저장
  -> 보호 API 호출 시 Authorization 헤더에 포함
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

## Common Response

성공:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

실패:

```json
{
  "success": false,
  "message": "오류 메시지",
  "data": null
}
```

프론트에서는 `response.data.success`를 기준으로 성공 여부를 판단하고, 실제 데이터는 `response.data.data`에서 사용합니다.

## Axios Example

설치:

```powershell
npm install axios
```

API client 예시:

```javascript
import axios from "axios";

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080",
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("accessToken");

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;

    if (status === 401) {
      localStorage.removeItem("accessToken");
      // 로그인 화면으로 이동 처리
    }

    return Promise.reject(error);
  }
);
```

## Fetch Example

```javascript
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export async function request(path, options = {}) {
  const token = localStorage.getItem("accessToken");

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  const body = await response.json();

  if (!response.ok || !body.success) {
    throw {
      status: response.status,
      message: body.message,
      data: body.data,
    };
  }

  return body.data;
}
```

## Main API Flow

권장 연결 순서:

```text
1. 회원가입 또는 로그인
2. 토큰 저장
3. 내 정보 조회
4. Source 생성
5. Analysis 생성
6. Schedule 생성 또는 조회
7. Salesmap 등록
8. 목록 조회
```

## 1. Signup

```http
POST /api/auth/signup
Content-Type: application/json
```

```json
{
  "email": "test@example.com",
  "name": "홍길동",
  "password": "1234"
}
```

응답:

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
      "name": "홍길동",
      "role": "USER",
      "status": "ACTIVE"
    }
  }
}
```

프론트 처리:

```javascript
const response = await api.post("/api/auth/signup", {
  email,
  name,
  password,
});

localStorage.setItem("accessToken", response.data.data.accessToken);
```

## 2. Login

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "email": "test@example.com",
  "password": "1234"
}
```

응답 구조는 회원가입과 같습니다.

```javascript
const response = await api.post("/api/auth/login", {
  email,
  password,
});

localStorage.setItem("accessToken", response.data.data.accessToken);
```

## 3. Me

```http
GET /api/auth/me
Authorization: Bearer {accessToken}
```

응답:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "id": 1,
    "email": "test@example.com",
    "name": "홍길동",
    "role": "USER",
    "status": "ACTIVE"
  }
}
```

## 4. Create Source

로그인 사용자 기준으로 원본 데이터를 생성합니다. `userId`는 보내지 않는 것을 권장합니다.

```http
POST /api/sources
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "integrationId": null,
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

프론트에서는 `sourceId`를 저장해 다음 분석 요청에 사용합니다.

## 5. Create Analysis

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
    "status": "ANALYZED",
    "analyzedAt": "2026-05-24T14:00:00",
    "approvedAt": null
  }
}
```

## 6. Schedule

### Create Schedule

`analysisId`가 있으면 분석 결과 기반 일정, `null`이면 일반 사용자 일정입니다.

```http
POST /api/schedules
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "analysisId": 1,
  "title": "ABC Corp 미팅",
  "scheduleDateTime": "2026-05-29T14:00:00",
  "memo": "견적서 준비 후 미팅"
}
```

### Get My Schedules

```http
GET /api/schedules
Authorization: Bearer {accessToken}
```

응답:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": []
}
```

## 7. Register Salesmap

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

응답:

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
    "registeredAt": "2026-05-24T14:00:00"
  }
}
```

## 8. Lists

로그인 사용자 기준 목록:

```http
GET /api/sources
GET /api/schedules
```

부모 리소스 기준 목록:

```http
GET /api/analysis/source/{sourceId}
GET /api/salesmap/analysis/{analysisId}
```

다른 사용자의 `sourceId`, `analysisId`를 조회하면 `403`이 반환됩니다.

## Error Handling

### 400 Bad Request

요청값 검증 실패입니다.

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "title": "제목은 필수입니다."
  }
}
```

### 401 Unauthorized

토큰이 없거나 잘못된 경우입니다.

프론트 처리 권장:

- 저장된 token 제거
- 로그인 화면으로 이동

### 403 Forbidden

인증은 되었지만 접근 권한이 없는 리소스입니다.

대표 케이스:

- 다른 사용자의 `sourceId`로 Analysis 생성
- 다른 사용자의 `analysisId`로 Salesmap 등록
- 다른 사용자의 일정/원본 데이터 조회

### 502 Bad Gateway

백엔드가 외부 모듈 호출에 실패한 경우입니다.

대표 케이스:

- `ai.module.mode=http`에서 FastAPI 서버 다운 또는 timeout
- `salesmap.api.mode=http`에서 Salesmap API 서버 다운 또는 timeout

프론트 처리 권장:

- "외부 서비스 연동에 실패했습니다. 잠시 후 다시 시도해 주세요." 형태의 안내
- 필요하면 재시도 버튼 제공

## Mock Mode vs HTTP Mode

프론트 요청 방식은 동일합니다. 차이는 백엔드 내부에서 외부 서비스를 실제 호출하는지 여부입니다.

| Target | Mock Mode | HTTP Mode |
| --- | --- | --- |
| AI Module | `MockAiClient`가 고정 분석 결과 반환 | FastAPI `/analyze` 호출 |
| Salesmap API | `MockSalesmapClient`가 mock 등록 결과 반환 | 실제 Salesmap 등록 API 호출 |

프론트는 mode를 신경 쓰지 않고 동일한 백엔드 API를 호출하면 됩니다.

## CORS

백엔드는 `cors.allowed-origins` 설정으로 허용 origin을 관리합니다.

현재 기본값:

```properties
cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://127.0.0.1:5173}
```

React + Vite 기본 개발 서버는 보통 `http://localhost:5173`입니다.

프론트 개발 서버 주소가 다르면 백엔드 `application.properties` 또는 환경 변수에 origin을 추가해야 합니다.

예:

```properties
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
```

## Minimal Screen Mapping

프론트 화면별 권장 API:

| Screen | APIs |
| --- | --- |
| Login | `POST /api/auth/login` |
| Signup | `POST /api/auth/signup` |
| App bootstrap | `GET /api/auth/me` |
| Source input | `POST /api/sources`, `GET /api/sources` |
| Analysis result | `POST /api/analysis`, `GET /api/analysis/{analysisId}` |
| Calendar | `GET /api/schedules`, `POST /api/schedules` |
| Salesmap register | `POST /api/salesmap/register`, `GET /api/salesmap/analysis/{analysisId}` |

## Notes

- 보호 API에는 항상 `Authorization` 헤더를 넣습니다.
- 신규 화면에서는 `userId`를 직접 보내지 않습니다.
- 날짜/시간은 ISO-8601 문자열을 사용합니다. 예: `2026-05-29T14:00:00`
- `data`가 배열인 목록 API는 결과가 없으면 빈 배열 `[]`을 반환합니다.
- 상세 필드와 추가 오류 예시는 [api-spec.md](./api-spec.md)를 참고합니다.
