# Backend Guide

SALESMAP 활동 자동화 AI Agent 백엔드 구조와 실행 방법입니다.

## 기술 스택

- Java 17
- Spring Boot
- Gradle
- Spring Security + JWT
- Spring Data JPA
- MySQL
- Swagger/OpenAPI
- Gmail API
- Google Calendar API
- FastAPI AI Module 연동

## 로컬 실행

### 1. MySQL

```sql
CREATE DATABASE IF NOT EXISTS salesmap
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

### 2. Backend 환경변수

PowerShell 예시:

```powershell
$env:SPRING_DATASOURCE_PASSWORD="MySQL 비밀번호"
$env:AI_MODULE_MODE="http"
$env:AI_MODULE_BASE_URL="http://localhost:8000"
$env:GMAIL_CLIENT_ID="Google OAuth Client ID"
$env:GMAIL_CLIENT_SECRET="Google OAuth Client Secret"
$env:GMAIL_REDIRECT_URI="http://localhost:5173/settings/gmail/callback"
$env:GMAIL_OAUTH_SCOPE="openid email profile https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/calendar.events"
$env:GOOGLE_CALENDAR_ENABLED="true"
$env:GOOGLE_CALENDAR_ID="primary"
```

### 3. Backend 실행

```powershell
cd C:\salesmap-agent\backend
.\gradlew.bat bootRun
```

### 4. Swagger

```text
http://localhost:8080/swagger-ui/index.html
```

Swagger에서 보호 API를 테스트하려면 로그인 후 받은 JWT를 Authorize에 입력합니다.

```text
Bearer {accessToken}
```

## 주요 설정

파일:

```text
backend/src/main/resources/application.properties
```

주요 property:

```properties
ai.module.mode=${AI_MODULE_MODE:mock}
ai.module.base-url=${AI_MODULE_BASE_URL:http://localhost:8000}

gmail.oauth.redirect-uri=${GMAIL_REDIRECT_URI:http://localhost:5173/settings/gmail/callback}
gmail.oauth.scope=${GMAIL_OAUTH_SCOPE:openid email profile https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/calendar.events}

google.calendar.enabled=${GOOGLE_CALENDAR_ENABLED:true}
google.calendar.calendar-id=${GOOGLE_CALENDAR_ID:primary}
google.calendar.time-zone=${GOOGLE_CALENDAR_TIME_ZONE:Asia/Seoul}
```

## 패키지 구조

```text
com.salesmap.backend
  auth          JWT 회원가입/로그인
  user          사용자
  integration   외부 연동 계정
  gmail         Gmail OAuth/수집
  source        수집 원본 데이터
  analysis      AI 분석
  schedule      내부 일정
  calendar      Google Calendar API
  salesmap      Salesmap 등록 이력/승인 흐름
  ai            FastAPI AI 모듈 client
  global        공통 응답/예외/보안/config
```

## 핵심 도메인 흐름

### Gmail 수집

```text
Gmail OAuth 연결
  -> Integration 저장
  -> Gmail collect
  -> Gmail messageId 중복 제거
  -> Gmail threadId 기준 SourceGroup 저장
  -> 각 메일을 Source로 저장
```

### AI 분석

```text
Source 선택
  -> sourceGroupId가 있으면 POST /api/analysis/group
  -> FastAPI /analyze 호출
  -> actionType, 일정 정보, 고객사, 제품, 참석자 추출
  -> Analysis 저장
```

### 사용자 승인

```text
Analysis 결과 확인/수정
  -> POST /api/salesmap/register
  -> actionType 기준 Schedule 처리
  -> Google Calendar 이벤트 처리
  -> SalesmapRecord 저장
```

### Salesmap 반영

Salesmap TODO 직접 생성 API가 없어 Google Calendar를 경유합니다.

```text
Google Calendar 이벤트 생성/수정/삭제
  -> Salesmap 캘린더 양방향 연동
  -> Salesmap TODO 자동 생성/수정/삭제
```

## actionType 처리

| actionType | 백엔드 처리 |
| --- | --- |
| `CREATE` | Schedule 생성, Google Calendar 이벤트 생성 |
| `UPDATE` | 기존 Schedule 수정, Google Calendar 이벤트 수정 |
| `CANCEL` | 기존 Schedule 삭제, Google Calendar 이벤트 삭제 |
| `CONFIRM` | 분석 이력 중심 저장 |
| `UNKNOWN` | 일정 자동 반영 없음 |

## 테스트 명령

```powershell
cd C:\salesmap-agent\backend
.\gradlew.bat compileJava
```

전체 시연 순서는 [e2e-test-flow.md](./e2e-test-flow.md)를 참고합니다.

## 발표 준비용 문서

팀원이 발표 자료를 준비할 때는 아래 문서를 우선 읽으면 됩니다.

1. [final-presentation-guide.md](./final-presentation-guide.md)
2. [backend-status.md](./backend-status.md)
3. [e2e-test-flow.md](./e2e-test-flow.md)
4. [frontend-api-guide.md](./frontend-api-guide.md)
5. [api-spec.md](./api-spec.md)
