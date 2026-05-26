# Backend Guide

SALESMAP 활동 자동화 AI Agent 백엔드 구조와 실행 방법을 정리한 문서입니다.

이 문서는 팀원 또는 Codex가 현재 백엔드 상태를 빠르게 이해하고 이어서 작업할 수 있도록 작성되었습니다.

## Overview

백엔드는 Spring Boot 기반 REST API 서버입니다.

현재 목표는 Gmail, Jandi, 회의록 등 원본 데이터를 수집하고, AI 분석 결과를 저장한 뒤, 사용자가 승인한 내용을 Salesmap 등록 이력으로 관리하는 기본 흐름을 만드는 것입니다.

현재 구현된 API는 실제 MySQL DB를 사용합니다. 단, 외부 서비스 호출은 아직 mock 처리입니다.

## Tech Stack

- Spring Boot 3.5.14
- Java 17
- Gradle
- MySQL
- Spring Data JPA
- Bean Validation
- Spring Security
- JWT

## Local Setup

### 1. MySQL 실행 확인

로컬 MySQL 서버가 실행 중이어야 합니다.

기본 연결 기준:

```text
host: localhost
port: 3306
database: salesmap
username: root
```

### 2. Database 생성

MySQL에서 `salesmap` DB를 생성합니다.

```sql
CREATE DATABASE salesmap
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

### 3. application.properties 설정

파일:

```text
backend/src/main/resources/application.properties
```

로컬 MySQL 비밀번호를 설정해야 합니다.

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/salesmap?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
spring.datasource.username=root
spring.datasource.password=YOUR_LOCAL_PASSWORD
ai.module.base-url=${AI_MODULE_BASE_URL:http://localhost:8000}
```

현재 JPA 설정:

```properties
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
```

### 4. 서버 실행

```powershell
cd C:\salesmap-agent\backend
.\gradlew bootRun
```

Health Check:

```text
GET http://localhost:8080/api/health
```

## Implemented Features

### Common API Response

모든 API 응답은 `ApiResponse` 형식으로 감쌉니다.

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

### Validation

요청 DTO에 Bean Validation을 적용했습니다.

예:

- `@NotBlank`
- `@NotNull`
- `@Positive`
- `@Email`
- `@FutureOrPresent`

Validation 실패 시 `400 Bad Request`와 함께 필드별 에러를 반환합니다.

### GlobalExceptionHandler

공통 예외 처리를 담당합니다.

현재 처리하는 주요 예외:

- `MethodArgumentNotValidException`
- `IllegalArgumentException`
- `AuthenticationException`
- `AccessDeniedException`
- `NoSuchElementException`
- `MissingServletRequestParameterException`

### Auth / JWT

JWT 기반 인증이 적용되어 있습니다.

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `GET /api/auth/me`

`/api/auth/signup`, `/api/auth/login`, `/api/health`를 제외한 보호 API는 아래 헤더가 필요합니다.

```http
Authorization: Bearer {accessToken}
```

### User API

DB 기반으로 동작합니다.

- `POST /api/users`
- `GET /api/users/{id}`

기능:

- 사용자 생성
- 사용자 조회
- 이메일 중복 검사
- 기본 role: `USER`
- 기본 status: `ACTIVE`

### Source API

DB 기반으로 동작합니다.

- `POST /api/sources`
- `GET /api/sources/{sourceId}`
- `GET /api/sources`

기능:

- 원본 데이터 생성
- 원본 데이터 단건 조회
- 로그인 사용자 기준 원본 데이터 목록 조회
- `User` 필수 참조
- `Integration` 선택 참조
- 기본 status: `CREATED`

### Analysis API

DB 기반으로 동작합니다.

- `POST /api/analysis`
- `GET /api/analysis/{analysisId}`
- `GET /api/analysis/source/{sourceId}`

기능:

- Source 기준 분석 결과 생성
- `AiClient`를 통해 AI 분석 결과 수신
- 현재는 실제 FastAPI 호출 없이 `MockAiClient`가 mock 분석 결과 반환
- 분석 결과 단건 조회
- Source 기준 분석 결과 목록 조회
- 기본 status: `ANALYZED`
- 분석 생성 시 Source status를 `ANALYZED`로 변경

### AI Module Integration Structure

AI Module 연동을 위한 client 계층이 준비되어 있습니다.

현재 패키지:

```text
ai
  client
    AiClient
    MockAiClient
  config
    AiModuleProperties
  dto
    AiAnalysisRequest
    AiAnalysisResponse
    AiErrorResponse
```

현재 동작:

- `AnalysisService`는 mock 값을 직접 만들지 않습니다.
- `AnalysisService`는 `AiClient.analyze(...)`를 호출합니다.
- 기본 Bean은 `MockAiClient`이며 실제 HTTP 호출은 하지 않습니다.
- `ai.module.mode=http`로 설정하면 `HttpAiClient`가 FastAPI AI Module을 호출합니다.
- `HttpAiClient`는 `POST {ai.module.base-url}/analyze`를 호출합니다.

AI Module 설정:

```properties
ai.module.mode=${AI_MODULE_MODE:mock}
ai.module.base-url=${AI_MODULE_BASE_URL:http://localhost:8000}
ai.module.connect-timeout-ms=${AI_MODULE_CONNECT_TIMEOUT_MS:3000}
ai.module.read-timeout-ms=${AI_MODULE_READ_TIMEOUT_MS:10000}
```

모드별 동작:

| Mode | Bean | Description |
| --- | --- | --- |
| `mock` | `MockAiClient` | 기본값. FastAPI 서버 없이 mock 분석 결과 반환 |
| `http` | `HttpAiClient` | 실제 FastAPI AI Module에 HTTP 요청 |

FastAPI endpoint 계약:

```http
POST /analyze
Content-Type: application/json
```

백엔드에서 AI Module로 보내는 요청 필드:

| Field | Type | Required | Nullable | Description |
| --- | --- | --- | --- | --- |
| sourceId | Long | Yes | No | 백엔드 `sources.id` |
| sourceType | String | Yes | No | `EMAIL`, `JANDI_MESSAGE`, `MEETING_NOTE`, `MANUAL_INPUT` |
| externalSourceId | String | No | Yes | Gmail/Jandi 등 외부 원본 ID |
| title | String | Yes | No | 원본 제목 |
| content | String | Yes | No | 분석 대상 원문 |
| collectedAt | String | No | Yes | 원본 수집 시각, ISO-8601 형식 |

백엔드 -> AI Module 요청 JSON 예시:

```json
{
  "sourceId": 1,
  "sourceType": "EMAIL",
  "externalSourceId": null,
  "title": "고객 미팅 관련 이메일",
  "content": "원본 이메일 또는 메시지 내용",
  "collectedAt": null
}
```

AI Module에서 백엔드로 반환하는 성공 응답 필드:

| Field | Type | Required | Nullable | Description |
| --- | --- | --- | --- | --- |
| customerName | String | No | Yes | 추출된 고객사명 |
| contactName | String | No | Yes | 추출된 담당자명 |
| productName | String | No | Yes | 추출된 제품명 |
| amount | Long | No | Yes | 추출된 금액 |
| scheduleTitle | String | No | Yes | 추출된 일정 제목 또는 일정 설명 |
| scheduleDateTime | String | No | Yes | 추출된 일정 시각, ISO-8601 형식 |
| todoContent | String | No | Yes | 후속 조치 내용 |
| keyIssues | String | No | Yes | 주요 이슈 또는 리스크 |
| summary | String | Yes | No | 원본 커뮤니케이션 요약 |
| confidenceScore | Number | Yes | No | 분석 신뢰도, `0.0` 이상 `1.0` 이하 |

AI Module -> 백엔드 성공 응답 JSON 예시:

```json
{
  "customerName": "ABC Corp",
  "contactName": "홍길동",
  "productName": "Sales Solution",
  "amount": 1000000,
  "scheduleTitle": "다음 주 수요일 미팅",
  "scheduleDateTime": null,
  "todoContent": "견적서 발송",
  "keyIssues": "예산 검토 및 도입 일정 확인 필요",
  "summary": "고객이 제품 도입을 검토 중이며 다음 미팅 예정",
  "confidenceScore": 0.95
}
```

시간 형식은 ISO-8601 문자열로 통일합니다.

```json
{
  "scheduleDateTime": "2026-05-29T14:00:00"
}
```

AI Module 실패 응답 필드:

| Field | Type | Required | Nullable | Description |
| --- | --- | --- | --- | --- |
| errorCode | String | Yes | No | 오류 코드. 예: `INVALID_REQUEST`, `ANALYSIS_FAILED` |
| message | String | Yes | No | 사람이 읽을 수 있는 오류 메시지 |
| details | Object | No | Yes | 필드별 오류 또는 디버깅용 부가 정보 |

AI Module 실패 응답 JSON 예시:

```json
{
  "errorCode": "ANALYSIS_FAILED",
  "message": "AI analysis failed",
  "details": {
    "sourceId": 1,
    "reason": "content is empty or unsupported"
  }
}
```

`HttpAiClient` 실패 처리:

- FastAPI가 non-2xx 응답을 반환하면 `AiErrorResponse`로 파싱합니다.
- FastAPI 서버가 내려가 있거나 timeout이 발생하면 `AiClientException`을 발생시킵니다.
- 백엔드는 `AiClientException`을 `502 Bad Gateway`와 `ApiResponse` 형식으로 반환합니다.

백엔드가 반환하는 AI 호출 실패 응답 예시:

```json
{
  "success": false,
  "message": "AI Module 오류: AI analysis failed",
  "data": {
    "errorCode": "ANALYSIS_FAILED",
    "message": "AI analysis failed",
    "details": {
      "sourceId": 1,
      "reason": "content is empty or unsupported"
    }
  }
}
```

FastAPI 서버 다운 또는 timeout 예시:

```json
{
  "success": false,
  "message": "AI Module 호출에 실패했습니다.",
  "data": null
}
```

로컬 수동 테스트:

1. 기본 mock 모드는 FastAPI 서버 없이 실행합니다.

```properties
ai.module.mode=mock
```

2. 실제 FastAPI 연동 모드는 AI Module 서버를 `localhost:8000`에서 실행한 뒤 설정합니다.

```properties
ai.module.mode=http
ai.module.base-url=http://localhost:8000
```

3. 백엔드에서 분석 생성 API를 호출합니다.

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

### Schedule API

DB 기반으로 동작합니다.

- `POST /api/schedules`
- `GET /api/schedules`

기능:

- 일정 생성
- 로그인 사용자 기준 일정 목록 조회
- `User` 필수 참조
- `Analysis` 선택 참조
- 기본 status: `SCHEDULED`

### Salesmap API

DB 기반으로 동작합니다.

- `POST /api/salesmap/register`
- `GET /api/salesmap/analysis/{analysisId}`

기능:

- Analysis 기준 Salesmap 등록 이력 생성
- 실제 Salesmap 외부 API 호출 없이 mock payload 저장
- Analysis 기준 Salesmap 등록 이력 목록 조회
- 기본 status: `REGISTERED`
- 등록 성공 시 Analysis status를 `APPROVED`로 변경

## Package Structure

기준 패키지:

```text
com.salesmap.backend
```

주요 구조:

```text
global
  config
  entity
  exception
  health
  response
  security

auth
  controller
  dto
  service

ai
  client
  config
  dto

user
  controller
  dto
  entity
  repository
  service

integration
  controller
  dto
  entity
  repository
  service

source
  controller
  dto
  entity
  repository
  service

analysis
  controller
  dto
  entity
  repository
  service

schedule
  controller
  dto
  entity
  repository
  service

salesmap
  controller
  dto
  entity
  repository
  service
```

역할:

- `controller`: REST API endpoint
- `service`: 비즈니스 로직, 트랜잭션 처리
- `dto`: 요청/응답 객체
- `entity`: JPA Entity
- `repository`: Spring Data JPA Repository
- `global`: 공통 응답, 예외, 설정, 공통 Entity

## DB Summary

현재 구현된 테이블:

| Table | Entity | Purpose |
| --- | --- | --- |
| `users` | `User` | 사용자 정보 |
| `integrations` | `Integration` | Gmail, Jandi, Salesmap 연동 정보 |
| `sources` | `Source` | 이메일, 메시지, 회의록 등 원본 데이터 |
| `analyses` | `Analysis` | AI 분석 결과 |
| `schedules` | `Schedule` | 일정, 후속 조치, 리마인드 |
| `salesmap_records` | `SalesmapRecord` | Salesmap 등록 이력 |

주요 관계:

```text
users 1 ── N integrations
users 1 ── N sources
users 1 ── N schedules

integrations 1 ── N sources

sources 1 ── N analyses

analyses 1 ── N schedules
analyses 1 ── N salesmap_records
```

주요 unique 제약:

- `users.email`
- `integrations.user_id + integrations.provider`

상세 DB 설계는 [db-design.md](./db-design.md)를 참고합니다.

## API Test Flow

### 1. User 생성

```http
POST /api/auth/signup
```

```json
{
  "email": "test@example.com",
  "name": "홍길동",
  "password": "1234"
}
```

로그인 후 `accessToken`을 발급받습니다.

```http
POST /api/auth/login
```

```json
{
  "email": "test@example.com",
  "password": "1234"
}
```

이후 보호 API에는 아래 헤더를 포함합니다.

```http
Authorization: Bearer {accessToken}
```

### 2. Source 생성

```http
POST /api/sources
```

```json
{
  "integrationId": null,
  "sourceType": "EMAIL",
  "title": "고객 미팅 관련 이메일",
  "content": "원본 이메일 또는 메시지 내용"
}
```

### 3. Analysis 생성

```http
POST /api/analysis
```

```json
{
  "sourceId": 1
}
```

### 4. Schedule 생성

```http
POST /api/schedules
```

```json
{
  "analysisId": 1,
  "title": "ABC Corp 미팅",
  "scheduleDateTime": "2026-05-29T14:00:00",
  "memo": "견적서 준비 후 미팅"
}
```

일반 사용자 일정은 `analysisId`를 `null`로 보냅니다.

### 5. Salesmap 등록

```http
POST /api/salesmap/register
```

```json
{
  "analysisId": 1
}
```

### 6. 목록 조회

```http
GET /api/sources
GET /api/schedules
GET /api/analysis/source/1
GET /api/salesmap/analysis/1
```

## Not Implemented Yet

아직 구현하지 않은 항목:

- 실제 Gmail API 연동
- 실제 Jandi API 연동
- 실제 AI Module 호출
- 실제 Salesmap 외부 API 호출
- Integration 생성/조회 API
- Source 수집 자동화
- Schedule 수정/완료/취소 API
- Salesmap 등록 재시도/실패 처리 고도화

## Codex Context Summary

다른 팀원이 Codex에 붙여넣기 좋은 현재 백엔드 컨텍스트입니다.

```text
현재 SALESMAP 활동 자동화 AI Agent 프로젝트의 backend는 Spring Boot 3.5.14, Java 17, Gradle, MySQL, JPA 기반이다.

패키지 기준은 com.salesmap.backend 이다.

구현 완료:
- ApiResponse 공통 응답
- Bean Validation
- GlobalExceptionHandler
- BaseEntity(createdAt, updatedAt)
- JPA Auditing
- MySQL 연결
- users, integrations, sources, analyses, schedules, salesmap_records Entity/Repository
- User API: 생성, 단건 조회
- Source API: 생성, 단건 조회, 로그인 사용자 기준 목록 조회
- Analysis API: 생성, 단건 조회, sourceId 기준 목록 조회
- Schedule API: 생성, 로그인 사용자 기준 목록 조회
- Salesmap API: 등록 이력 생성, analysisId 기준 목록 조회
- Auth API: signup, login, me
- AI client 구조: AiClient, MockAiClient, AI 요청/응답 DTO

현재 User, Source, Analysis, Schedule, Salesmap API는 DB 기반으로 동작한다.
다만 실제 Gmail/Jandi/AI module/Salesmap 외부 API 호출은 아직 하지 않고 mock 데이터 또는 mock payload를 저장한다.

아직 구현하지 않은 것:
- 실제 Gmail/Jandi 연동
- 실제 AI module 호출
- 실제 Salesmap 외부 API 호출
- Integration API

중요 정책:
- 모든 API 응답은 ApiResponse로 감싼다.
- Entity/Repository/Service/Controller/DTO 계층을 분리한다.
- 상태값은 Java enum을 사용하고 DB에는 EnumType.STRING으로 저장한다.
- frontend 코드는 backend 작업 중 건드리지 않는다.
```
