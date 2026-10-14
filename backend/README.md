# SALESMAP Backend

Spring Boot · JPA · MySQL · JWT. 실행 방법과 기본 환경 변수는 [루트 README의 "실행"](../README.md#실행), API는 [docs/api-spec.md](../docs/api-spec.md), 테이블은 [docs/db-design.md](../docs/db-design.md)를 보세요.

> 2026-10 정리. 1학기 `docs/backend-guide.md`·`e2e-test-flow.md`에서 지금도 유효한 설정만 옮겨 왔습니다.

## 처음 한 번: DB 만들기

```sql
CREATE DATABASE IF NOT EXISTS salesmap
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

테이블은 `ddl-auto=update`로 자동 생성됩니다. 팀원마다 DB 스키마를 따로 쓰려면 `SPRING_DATASOURCE_URL`의 DB 이름만 바꾸세요.

## 추가 환경 변수

루트 README 표 외에 자주 쓰는 것만. 전체는 `src/main/resources/application.properties`.

| 변수 | 설명 | 기본값 |
| --- | --- | --- |
| `AI_MODULE_BASE_URL` | AI 모듈 주소 (`AI_MODULE_MODE=http`일 때) | `http://localhost:8000` |
| `AI_MODULE_READ_TIMEOUT_MS` | AI 모듈 응답 대기. AI 모듈의 LLM 대기(300초)보다 길게 | `330000` (5분 30초) |
| `GMAIL_REDIRECT_URI` | Google OAuth 콜백 | `http://localhost:5173/settings/gmail/callback` |
| `GMAIL_OAUTH_SCOPE` | Google 권한 범위 | 아래 참고 |
| `GOOGLE_CALENDAR_ENABLED` · `GOOGLE_CALENDAR_ID` | 캘린더 등록 사용 여부·대상 | `true` · `primary` |
| `SALESMAP_API_MODE` | Salesmap API 직접 호출 (`mock`이면 호출 안 함) | `mock` |

## Google 연결 (Gmail·캘린더)

Google Cloud Console의 OAuth 클라이언트에 **승인된 리디렉션 URI**를 등록합니다.

```text
http://localhost:5173/settings/gmail/callback
```

기본 권한 범위:

```text
openid email profile
https://www.googleapis.com/auth/gmail.readonly
https://www.googleapis.com/auth/gmail.send
https://www.googleapis.com/auth/calendar.events
```

클라이언트 ID·시크릿은 `GMAIL_CLIENT_ID`·`GMAIL_CLIENT_SECRET` 환경 변수로만 넣고 커밋하지 않습니다.

## Salesmap 반영 방식

지금은 Salesmap API를 직접 부르지 않고 **Google 캘린더 양방향 연동**으로 반영됩니다. 승인된 일정이 Google 캘린더에 등록되면 Salesmap이 가져갑니다.

Salesmap 쪽 설정: 개인 설정 → 연동 → 캘린더 → Google 계정 연결 → 양방향 연동 → 가져오기 유형 "미팅" → 저장

## 패키지 구조

```text
com.salesmap.backend
  auth          JWT 회원가입·로그인
  user          사용자
  integration   외부 연동 계정 (Gmail 등)
  gmail         Gmail OAuth·수집·답장 발송
  source        수집 원본 (메일·직접 입력)
  ai            FastAPI AI 모듈 클라이언트
  analysis      AI 분석 결과, 수정 기록(analysis_revisions), 답장 초안
  analysisjob   분석 일괄 실행·진행률, 활동 피드
  autopipeline  자동 수집·분석 모드 (켜면 주기적으로 수집 → 분석)
  schedule      내부 일정, 미팅 결과 기록
  calendar      Google Calendar API
  salesmap      Salesmap 반영 이력·승인 흐름
  customer      고객 연락처·활동 타임라인
  demo          개발자용 데모 모드
  global        공통 응답·예외·보안·설정 (공용 — 수정은 팀 합의)
```

2학기 새 도메인도 `com.salesmap.backend.<도메인>`으로 추가합니다.

## 테스트

```bash
./gradlew test --tests "*Test"
```

이름이 `...Test`로 끝나는 단위 테스트를 모두 돌립니다 (CI와 같음). `BackendApplicationTests`는 실제 MySQL이 필요해서 빠집니다. 새 단위 테스트도 이름을 `...Test`로 끝내고 MySQL 없이 돌게 만드세요.
