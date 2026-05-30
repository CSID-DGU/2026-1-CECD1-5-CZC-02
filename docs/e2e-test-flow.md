# E2E Test Flow

SALESMAP 활동 자동화 AI Agent의 프론트-백엔드 통합 흐름을 로컬에서 테스트하기 위한 문서입니다.

현재 테스트 범위는 로그인부터 Source 생성, Mock AI 분석, Mock SALESMAP 등록까지입니다. 실제 FastAPI/OpenAI/Salesmap 외부 API 호출은 아직 포함하지 않습니다.

## 1. 사전 준비

### MySQL 실행

로컬 MySQL이 실행 중인지 확인합니다.

필요한 DB:

```sql
CREATE DATABASE IF NOT EXISTS salesmap;
```

백엔드 실행 전 `backend/src/main/resources/application.properties`의 MySQL 계정 정보를 로컬 환경에 맞게 확인합니다.

### Backend 실행

```powershell
cd C:\salesmap-agent\backend
.\gradlew.bat bootRun
```

Backend 주소:

```text
http://localhost:8080
```

### Frontend 실행

```powershell
cd C:\salesmap-agent\frontend
npm run dev
```

Frontend 주소:

```text
http://localhost:5173
```

## 2. 테스트 계정 준비

프론트 화면에서 회원가입하거나 기존 계정으로 로그인합니다.

테스트 계정 예시:

```json
{
  "email": "test@test.com",
  "password": "12345678",
  "name": "test"
}
```

로그인 성공 후 브라우저 개발자 도구에서 token 저장 여부를 확인합니다.

확인 위치:

```text
DevTools -> Application -> Local Storage -> http://localhost:5173 -> accessToken
```

또는 Console:

```javascript
localStorage.getItem("accessToken")
```

값이 존재하면 보호 API 호출 시 axios interceptor가 자동으로 Authorization 헤더를 붙입니다.

## 3. E2E 테스트 순서

1. 프론트 접속

   ```text
   http://localhost:5173
   ```

2. 회원가입 또는 로그인

   로그인 성공 후 `/dashboard`로 이동하는지 확인합니다.

3. 대시보드 진입 확인

   accessToken이 없으면 `/login`으로 이동해야 합니다.

4. Source 생성 테스트 실행

   대시보드의 API 테스트 패널에서 Source 생성 버튼을 클릭합니다.

   기대 결과:

   - Source 생성 성공 메시지 표시
   - Source API 상태 카드의 개수 증가

5. Schedule 생성 테스트 실행

   대시보드의 API 테스트 패널에서 Schedule 생성 버튼을 클릭합니다.

   기대 결과:

   - Schedule 생성 성공 메시지 표시
   - Schedule API 상태 카드의 개수 증가

6. Jandi 또는 Gmail 메뉴 이동

   Source 목록을 확인할 수 있는 화면으로 이동합니다.

7. 백엔드 Source 목록에서 Source 클릭

   방금 생성한 Source 또는 기존 Source를 클릭합니다.

8. Source 상세 확인

   Source 제목, 타입, 내용이 표시되는지 확인합니다.

9. AI 분석 테스트 클릭

   Source 상세 화면에서 `AI 분석 테스트` 버튼을 클릭합니다.

   현재 백엔드는 `MockAiClient`를 사용하므로 FastAPI 서버 없이도 분석 결과가 생성됩니다.

10. Analysis 결과 확인

    분석 결과 목록에 `summary`, `followUpAction`, `scheduleText`, `status` 등이 표시되는지 확인합니다.

11. SALESMAP 등록 테스트 클릭

    Analysis 결과 카드에서 `SALESMAP 등록 테스트` 버튼을 클릭합니다.

    현재 백엔드는 `MockSalesmapClient`를 사용하므로 실제 외부 Salesmap API 호출 없이 등록 결과가 생성됩니다.

12. SalesmapRecord 결과 확인

    등록 결과에 `REGISTERED` 상태와 `mock-salesmap-{analysisId}` 형태의 `externalRecordId`가 표시되는지 확인합니다.

## 4. Network 확인 항목

브라우저 개발자 도구의 Network 탭을 열고 테스트합니다.

보호 API에서는 공통으로 다음을 확인합니다.

```http
Authorization: Bearer {accessToken}
```

### Auth

#### POST /api/auth/signup

확인 항목:

- Status Code: `200`
- Request Body: `email`, `password`, `name`
- Response Body:
  - `success: true`
  - `data.accessToken`
  - `data.user.email`

#### POST /api/auth/login

확인 항목:

- Status Code: `200`
- Request Body: `email`, `password`
- Response Body:
  - `success: true`
  - `data.accessToken`
  - `data.tokenType: "Bearer"`

### Dashboard 조회

#### GET /api/sources

확인 항목:

- Status Code: `200`
- Authorization 헤더 포함
- Response Body:
  - `success: true`
  - `data`가 배열

#### GET /api/schedules

확인 항목:

- Status Code: `200`
- Authorization 헤더 포함
- Response Body:
  - `success: true`
  - `data`가 배열

### Source 생성

#### POST /api/sources

확인 항목:

- Status Code: `200`
- Authorization 헤더 포함
- Request Body:

```json
{
  "integrationId": null,
  "sourceType": "EMAIL",
  "title": "테스트 이메일",
  "content": "고객사 미팅 일정과 후속 조치가 포함된 테스트 내용입니다."
}
```

- Response Body:
  - `data.sourceId`
  - `data.sourceType`
  - `data.status: "CREATED"`

### Schedule 생성

#### POST /api/schedules

확인 항목:

- Status Code: `200`
- Authorization 헤더 포함
- Request Body:

```json
{
  "analysisId": null,
  "title": "테스트 일정",
  "scheduleDateTime": "2026-05-29T14:00:00",
  "memo": "프론트-백 통합 테스트 일정"
}
```

- Response Body:
  - `data.scheduleId`
  - `data.status: "SCHEDULED"`

### Source 상세

#### GET /api/sources/{sourceId}

확인 항목:

- Status Code: `200`
- Authorization 헤더 포함
- Response Body:
  - `data.sourceId`
  - `data.title`
  - `data.content`

### Analysis

#### POST /api/analysis

확인 항목:

- Status Code: `200`
- Authorization 헤더 포함
- Request Body:

```json
{
  "sourceId": 1
}
```

- Response Body:
  - `data.analysisId`
  - `data.sourceId`
  - `data.summary`
  - `data.status: "ANALYZED"`

#### GET /api/analysis/source/{sourceId}

확인 항목:

- Status Code: `200`
- Authorization 헤더 포함
- Response Body:
  - `data`가 배열
  - 분석 결과가 있으면 `summary`, `followUpAction`, `scheduleText`, `status` 포함

### Salesmap 등록

#### POST /api/salesmap/register

확인 항목:

- Status Code: `200`
- Authorization 헤더 포함
- Request Body:

```json
{
  "analysisId": 1
}
```

- Response Body:
  - `data.salesmapRecordId`
  - `data.analysisId`
  - `data.externalRecordId`
  - `data.status: "REGISTERED"`
  - `data.registeredAt`

#### GET /api/salesmap/analysis/{analysisId}

확인 항목:

- Status Code: `200`
- Authorization 헤더 포함
- Response Body:
  - `data`가 배열
  - 등록 결과가 있으면 `status: "REGISTERED"` 포함

## 5. 자주 발생하는 오류

### 400 Bad Request

원인:

- request body 필드명이 백엔드 DTO와 다름
- 필수값 누락
- 날짜가 과거 시간
- `sourceId`, `analysisId`가 1 미만

확인:

- Schedule은 `description`이 아니라 `memo`를 사용합니다.
- Source 생성 request에는 현재 `externalSourceId`, `collectedAt`을 보내지 않습니다.

### 401 Unauthorized

원인:

- accessToken 없음
- accessToken 만료
- Authorization 헤더 누락
- 로그아웃 후 보호 API 호출

대응:

1. `localStorage.accessToken` 확인
2. 재로그인
3. Network Request Headers에서 Authorization 헤더 확인

### 403 Forbidden

원인:

- 다른 사용자의 Source, Analysis, Schedule, SalesmapRecord에 접근
- request body 또는 query parameter의 기존 호환용 `userId`가 로그인 사용자와 다름

대응:

- 같은 계정으로 생성한 데이터인지 확인합니다.

### 404 Not Found

원인:

- API 경로 오타
- 존재하지 않는 `sourceId` 또는 `analysisId`
- 부모 리소스가 없음

대응:

- Network URL 확인
- Source 생성 후 실제 `sourceId` 사용
- Analysis 생성 후 실제 `analysisId` 사용

### 500 Internal Server Error

원인:

- 서버 내부 오류
- DB 저장 오류

대응:

- backend 콘솔 로그 확인
- MySQL 실행 상태 확인
- DB 스키마가 최신인지 확인

### 502 Bad Gateway

원인:

- `ai.module.mode=http`에서 FastAPI 서버 호출 실패
- `salesmap.api.mode=http`에서 외부 Salesmap API 호출 실패

현재 기본 mock 모드에서는 일반적으로 발생하지 않습니다.

## 6. 현재 Mock 동작

현재 기본 설정:

```properties
ai.module.mode=mock
salesmap.api.mode=mock
```

의미:

- AI Module은 실제 FastAPI/OpenAI를 호출하지 않습니다.
- Analysis 생성 시 `MockAiClient`가 mock 분석 결과를 반환합니다.
- Salesmap 등록 시 실제 외부 Salesmap API를 호출하지 않습니다.
- Salesmap 등록 결과는 `MockSalesmapClient`가 반환합니다.

실제 연동 전환은 추후 다음 property로 진행합니다.

```properties
ai.module.mode=http
salesmap.api.mode=http
```

## 7. 테스트 완료 기준

아래 항목이 모두 확인되면 현재 프론트-백엔드 통합 테스트는 성공입니다.

- 로그인 또는 회원가입 성공
- `localStorage.accessToken` 저장 확인
- 보호 API 요청에 Authorization 헤더 포함
- Source 생성 후 Source 개수 증가
- Schedule 생성 후 Schedule 개수 증가
- Source 상세 화면에서 Source 내용 표시
- AI 분석 테스트 후 Analysis 결과 표시
- Salesmap 등록 테스트 후 SalesmapRecord 표시
- SalesmapRecord 상태가 `REGISTERED`
- Network에서 `POST /api/salesmap/register` 이후 `GET /api/salesmap/analysis/{analysisId}` 재조회 확인
