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
- `NoSuchElementException`
- `MissingServletRequestParameterException`

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
- `GET /api/sources?userId=1`

기능:

- 원본 데이터 생성
- 원본 데이터 단건 조회
- 사용자 기준 원본 데이터 목록 조회
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
- 실제 AI module 호출 없이 mock 분석 결과 저장
- 분석 결과 단건 조회
- Source 기준 분석 결과 목록 조회
- 기본 status: `ANALYZED`
- 분석 생성 시 Source status를 `ANALYZED`로 변경

### Schedule API

DB 기반으로 동작합니다.

- `POST /api/schedules`
- `GET /api/schedules?userId=1`

기능:

- 일정 생성
- 사용자 기준 일정 목록 조회
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
POST /api/users
```

```json
{
  "email": "test@example.com",
  "name": "홍길동",
  "password": "1234"
}
```

### 2. Source 생성

```http
POST /api/sources
```

```json
{
  "userId": 1,
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
  "userId": 1,
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
GET /api/sources?userId=1
GET /api/schedules?userId=1
GET /api/analysis/source/1
GET /api/salesmap/analysis/1
```

## Not Implemented Yet

아직 구현하지 않은 항목:

- JWT/auth
- 로그인 API
- password 암호화
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
- Source API: 생성, 단건 조회, userId 기준 목록 조회
- Analysis API: 생성, 단건 조회, sourceId 기준 목록 조회
- Schedule API: 생성, userId 기준 목록 조회
- Salesmap API: 등록 이력 생성, analysisId 기준 목록 조회

현재 User, Source, Analysis, Schedule, Salesmap API는 DB 기반으로 동작한다.
다만 실제 Gmail/Jandi/AI module/Salesmap 외부 API 호출은 아직 하지 않고 mock 데이터 또는 mock payload를 저장한다.

아직 구현하지 않은 것:
- JWT/auth
- 로그인
- password 암호화
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
