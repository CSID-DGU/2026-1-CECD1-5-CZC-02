# Backend Status

이 문서는 SALESMAP 활동 자동화 AI Agent의 최종 시연 기준 백엔드 상태를 팀원이 빠르게 이해하기 위한 요약입니다.

## 현재 한 줄 요약

Gmail에서 실제 메일을 수집하고, FastAPI AI 모듈로 분석한 뒤, 사용자가 승인하면 Google Calendar 이벤트를 생성/수정/삭제합니다. Salesmap은 Google Calendar 양방향 연동을 통해 TODO가 자동 반영되는 구조입니다.

## 완료된 핵심 흐름

```text
회원가입/로그인
  -> JWT 인증
  -> Gmail OAuth 연결
  -> Gmail 메일 수집
  -> Source / SourceGroup 저장
  -> FastAPI AI 분석
  -> Analysis 저장
  -> 사용자 확인/수정
  -> 일정 등록/변경/삭제 승인
  -> 내부 Schedule 반영
  -> Google Calendar 이벤트 생성/수정/삭제
  -> Salesmap 캘린더 양방향 연동으로 TODO 반영
```

## 완료된 기능

- Spring Boot + JPA + MySQL 기반 백엔드
- JWT 회원가입, 로그인, 내 정보 조회
- BCrypt 비밀번호 암호화
- `ApiResponse` 공통 응답
- DTO Validation 및 `GlobalExceptionHandler`
- Swagger/OpenAPI UI
- 사용자 소유권 검증
- Gmail OAuth 연결/해제
- Gmail 메일 수집
- Gmail messageId 중복 제거
- Gmail threadId 기준 `SourceGroup` 그룹핑
- Source 목록/상세 조회
- 수집 메일 수동 삭제
- SourceGroup 기반 AI 분석 API
- FastAPI AI 모듈 HTTP 연동
- BGE-M3 임베딩 기반 영업 메일/의도 보조 분류
- GLiNER 기반 고객사/제품/참석자 추출 보조
- Ollama 기반 답장 초안 생성과 템플릿 fallback
- 시연 안정성을 위한 주요 메일 패턴 보정
- AI 분석 결과 저장 및 수정
- 메일별 답장 초안 생성
- 수집 메일 일괄 AI 분석
- 영업/업무 외 메일 구분
- Analysis actionType 기반 Schedule 처리
- 일정 등록 전 중복/근접 일정 충돌 경고
- Google Calendar API 이벤트 생성/수정/삭제
- Salesmap 직접 TODO API 대신 Google Calendar 경유 연동
- 프론트 대시보드 캘린더와 일정 목록 연동
- 오늘의 영업 브리핑
- 처리 이력 화면
- 고객사별 활동 타임라인

## 주요 도메인

| 도메인 | 역할 |
| --- | --- |
| `user` | 사용자 계정과 인증 기준 데이터 |
| `auth` | JWT 회원가입/로그인/내 정보 |
| `integration` | 외부 연동 계정 정보 |
| `gmail` | Gmail OAuth와 메일 수집 |
| `source` | 수집된 원본 메일/메시지 |
| `analysis` | AI 분석 요청/결과 |
| `schedule` | 내부 캘린더 일정 |
| `calendar` | Google Calendar 이벤트 연동 |
| `salesmap` | Salesmap 등록 이력 및 승인 흐름 |
| `ai` | FastAPI AI 모듈 client |
| `customer` | 고객사/담당자별 활동 타임라인 |

## DB 테이블

- `users`
- `integrations`
- `source_groups`
- `sources`
- `analyses`
- `schedules`
- `salesmap_records`

중요 컬럼:

- `integrations.provider`: `GMAIL` 등 외부 서비스 구분
- `sources.external_source_id`: Gmail messageId
- `source_groups.external_group_id`: Gmail threadId
- `analyses.action_type`: `CREATE`, `UPDATE`, `CANCEL`, `CONFIRM`, `UNKNOWN`
- `schedules.google_calendar_event_id`: Google Calendar 이벤트 ID
- `salesmap_records.status`: 등록/변경/삭제 이력 상태
- `customer_contacts`: 고객사/담당자 식별 정보
- `customer_activities`: AI 분석, 일정 반영, Salesmap 등록 활동 이력

## AI 모듈 현재 구조

최종 시연용 AI 모듈은 완전한 상용 LLM 의존 구조가 아니라, 무료/로컬 모델과 규칙 보정을 조합한 MVP 구조입니다.

```text
Gmail thread/source
  -> FastAPI /analyze
  -> 규칙 기반 일정/날짜/금액/의도 추출
  -> BGE-M3 임베딩 유사도 기반 영업 메일/의도 보조 분류
  -> GLiNER 기반 고객사/제품/참석자 추출 보조
  -> 필요 시 Ollama LLM 보정
  -> 시연 핵심 메일 패턴 보정
  -> Analysis 저장
```

추가 AI 기능:

- 업무 외 메일은 `NON_BUSINESS`로 분류하고 등록 대상에서 제외
- `CREATE`, `UPDATE`, `CANCEL`, `CONFIRM`, `UNKNOWN` 판단
- 고객사/제품/금액/참석자/일정 정보 추출
- `POST /api/analysis/{analysisId}/reply-draft`로 답장 초안 생성
- 답장 초안은 Ollama를 우선 사용할 수 있고, 실패하거나 품질이 낮으면 안정적인 템플릿으로 fallback

## 현재 Salesmap 연동 방식

Salesmap 담당자 확인 결과, 외부에서 TODO를 직접 생성/수정하는 API는 제공되지 않습니다.

따라서 현재 최종 시연 구조는 다음과 같습니다.

```text
우리 웹에서 AI 분석 결과 승인
  -> 백엔드가 Google Calendar API로 이벤트 생성/수정/삭제
  -> Salesmap의 Google Calendar 양방향 연동이 이벤트를 감지
  -> Salesmap TODO에 자동 반영
```

Salesmap에서 필요한 사용자 설정:

1. Salesmap 개인 설정
2. 연동
3. 캘린더
4. Google 계정 연결
5. 양방향 연동 선택
6. 가져오기 유형을 미팅 등으로 지정
7. 저장

## 실행에 필요한 환경변수

```properties
SPRING_DATASOURCE_PASSWORD=로컬 MySQL 비밀번호
AI_MODULE_MODE=http
AI_MODULE_BASE_URL=http://localhost:8000
GMAIL_CLIENT_ID=Google OAuth Client ID
GMAIL_CLIENT_SECRET=Google OAuth Client Secret
GMAIL_REDIRECT_URI=http://localhost:5173/settings/gmail/callback
GMAIL_OAUTH_SCOPE=openid email profile https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/calendar.events
GOOGLE_CALENDAR_ENABLED=true
GOOGLE_CALENDAR_ID=primary
```

기본값상 AI 모듈은 `mock`으로도 실행 가능하지만, 최종 시연은 `AI_MODULE_MODE=http`로 FastAPI를 연결하는 것을 기준으로 합니다.

## 주요 API

| Method | URL | 설명 |
| --- | --- | --- |
| `POST` | `/api/auth/signup` | 회원가입 |
| `POST` | `/api/auth/login` | 로그인 |
| `GET` | `/api/auth/me` | 내 정보 조회 |
| `GET` | `/api/integrations/gmail/authorize` | Gmail OAuth URL 발급 |
| `GET` | `/api/integrations/gmail/callback` | Gmail OAuth callback 처리 |
| `DELETE` | `/api/integrations/gmail` | Gmail 연결 해제 |
| `POST` | `/api/integrations/gmail/collect` | Gmail 메일 수집 |
| `GET` | `/api/sources` | 수집 메일 목록 |
| `GET` | `/api/sources/{sourceId}` | 메일 상세 |
| `DELETE` | `/api/sources/{sourceId}` | 수집 메일 삭제 |
| `POST` | `/api/analysis` | 단일 Source 분석 |
| `POST` | `/api/analysis/group` | Gmail thread 그룹 분석 |
| `POST` | `/api/analysis/{analysisId}/reply-draft` | 답장 초안 생성 |
| `PATCH` | `/api/analysis/{analysisId}` | 분석 결과 수동 수정 |
| `GET` | `/api/analysis/source/{sourceId}` | Source 기준 분석 목록 |
| `GET` | `/api/schedules` | 일정 목록 |
| `POST` | `/api/schedules` | 일정 직접 생성 |
| `PATCH` | `/api/schedules/{scheduleId}` | 일정 수정 |
| `DELETE` | `/api/schedules/{scheduleId}` | 일정 삭제 |
| `POST` | `/api/salesmap/register` | 분석 결과 승인 및 Salesmap 경유 등록 |
| `GET` | `/api/salesmap/analysis/{analysisId}` | 등록 이력 조회 |
| `GET` | `/api/customers` | 고객사/담당자 목록 |
| `GET` | `/api/customers/{customerContactId}/timeline` | 고객사별 활동 타임라인 |

## 검증 완료 기준

- 로그인 후 JWT 발급
- Gmail OAuth 연결 완료
- Gmail 메일 수집 후 웹 목록 표시
- Gmail thread 기반 AI 분석 성공
- 수집 메일 일괄 분석 가능
- AI 분석 결과 화면 표시
- 답장 초안 생성 및 복사 가능
- 분석 결과 수동 수정 가능
- 일정 중복/근접 충돌 시 경고 표시
- `CREATE` 분석 결과 승인 시 Google Calendar와 Dashboard에 일정 생성
- `UPDATE` 분석 결과 승인 시 기존 일정 수정
- `CANCEL` 분석 결과 승인 시 기존 일정 삭제
- Google Calendar 이벤트가 Salesmap TODO에 반영
- 처리 이력 화면에서 승인 대기/등록됨/삭제됨 상태 확인
- 고객 타임라인 화면에서 고객사별 메일/분석/일정/Salesmap 이력 확인

## 남은 작업

- JANDI 실제 연동
- 운영 환경 배포
- Docker 구성
- refresh token 장기 운영 정책
- Gmail/Calendar 토큰 암호화 저장
- Salesmap 동기화 지연 상태에 대한 UX 보강
- AI 모델 정확도 개선 및 더 큰 LLM/OpenAI API 연결 옵션 검토
- 고객사/제품 사전 구축
- 정식 발표 자료 제작 및 화면 캡처 정리
