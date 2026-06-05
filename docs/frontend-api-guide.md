# Frontend API Guide

이 문서는 React + Vite 프론트엔드가 현재 백엔드 API를 어떻게 사용하고 있는지 설명합니다.

## 기본 구조

Frontend:

```text
http://localhost:5173
```

Backend:

```text
http://localhost:8080
```

공통 axios client:

```text
frontend/src/api/client.js
```

API 함수 파일:

```text
frontend/src/api/auth.js
frontend/src/api/integrations.js
frontend/src/api/sources.js
frontend/src/api/analyses.js
frontend/src/api/schedules.js
frontend/src/api/salesmapRecords.js
frontend/src/api/customers.js
frontend/src/api/errors.js
```

## 인증 흐름

```text
회원가입 또는 로그인
  -> 백엔드가 accessToken 반환
  -> localStorage.accessToken 저장
  -> ProtectedRoute가 로그인 상태 확인
  -> axios interceptor가 Authorization 헤더 자동 첨부
```

Authorization 헤더:

```http
Authorization: Bearer {accessToken}
```

로그아웃 시:

```text
localStorage.accessToken 삭제
-> /login 이동
```

## 주요 화면별 API 흐름

### LoginPage

- `POST /api/auth/signup`
- `POST /api/auth/login`

성공 시:

- `accessToken` 저장
- `/dashboard` 이동

### Dashboard

역할:

- 대시보드 캘린더 표시
- 오늘/다가오는 일정 표시
- 오늘의 영업 브리핑 표시
- 일정 직접 수정/삭제
- 로그인 사용자 이름과 프로필 표시
- Google Calendar/Salesmap 연동 상태 표시

사용 API:

- `GET /api/auth/me`
- `GET /api/schedules`
- `PATCH /api/schedules/{scheduleId}`
- `DELETE /api/schedules/{scheduleId}`
- `GET /api/integrations`

중요 동작:

- 일정 삭제는 내부 Schedule 삭제와 Google Calendar 이벤트 삭제를 같이 시도합니다.
- Google Calendar에서 삭제되면 Salesmap 양방향 캘린더 연동으로 TODO도 사라질 수 있습니다.
- 일정 수정은 내부 Schedule 수정과 Google Calendar 이벤트 수정을 같이 시도합니다.
- 캘린더 등록 시 같은 시간대/근접 시간대 일정 충돌이 있으면 사용자에게 경고합니다.
- 사용자 선택 드롭다운에는 시연용 부서/담당자 예시 일정도 함께 표시됩니다.

### SettingsPage

역할:

- 사용자 프로필 표시
- Gmail 연결/해제
- Gmail 연결 상태 표시
- Salesmap 연동 상태 안내

사용 API:

- `GET /api/auth/me`
- `GET /api/integrations`
- `GET /api/integrations/gmail/authorize`
- `DELETE /api/integrations/gmail`

Gmail 연결 흐름:

```text
Gmail 연결 버튼
  -> GET /api/integrations/gmail/authorize
  -> authorizationUrl 수신
  -> Google OAuth 화면 이동
  -> /settings/gmail/callback 복귀
```

### GmailOAuthCallbackPage

사용 API:

- `GET /api/integrations/gmail/callback`

성공 시:

- Integration이 `GMAIL`, `CONNECTED` 상태로 저장됩니다.
- Settings 화면으로 이동합니다.

### MessageView

역할:

- 수집된 Gmail 메일 목록 표시. 현재 프론트는 30건 요청
- 메일 상세 표시
- 같은 발신자의 최근 메일 표시
- AI 분석 실행
- 수집된 메일 일괄 AI 분석
- 분석 결과 수동 수정
- 답장 초안 생성 및 복사
- Salesmap 등록/변경/삭제 승인
- 수집 메일 삭제

사용 API:

- `GET /api/sources`
- `GET /api/sources/{sourceId}`
- `DELETE /api/sources/{sourceId}`
- `POST /api/integrations/gmail/collect`
- `POST /api/analysis`
- `POST /api/analysis/group`
- `GET /api/analysis/source/{sourceId}`
- `POST /api/analysis/{analysisId}/reply-draft`
- `PATCH /api/analysis/{analysisId}`
- `POST /api/salesmap/register`
- `GET /api/salesmap/analysis/{analysisId}`

분석 API 선택 기준:

```text
선택한 메일에 sourceGroupId가 있음
  -> POST /api/analysis/group

sourceGroupId가 없음
  -> POST /api/analysis
```

등록 버튼 표시:

| actionType | 버튼 문구 | 의미 |
| --- | --- | --- |
| `CREATE` | 일정 등록 | 새 일정 생성 승인 |
| `UPDATE` | 등록된 일정 변경 | 기존 일정 변경 승인 |
| `CANCEL` | 등록된 일정 삭제 | 기존 일정 삭제 승인 |
| `CONFIRM` | Salesmap 등록 | 확인성 분석 등록 |
| `UNKNOWN` | Salesmap 등록 | 일반 메일 이력 등록 |

업무 외 메일:

- `businessType=NON_BUSINESS`인 경우 등록 대상이 아닌 메일로 표시합니다.
- 분석 결과 주요 필드는 `해당 없음`으로 표시합니다.
- Salesmap 등록 대신 삭제/제외 흐름으로 처리합니다.

## Gmail 새로고침

MessageView에서 Gmail 새로고침 버튼을 누르면:

```text
POST /api/integrations/gmail/collect?mode=manual&recentDays=30&debug=true
```

의미:

- 최근 30일 Gmail 메일 재조회
- 이미 저장된 messageId는 중복 제외
- 새 메일만 Source로 저장
- 수집 후 Source 목록 reload

## AI 분석 결과 수정

분석 결과 카드에서 수정 버튼을 누르면 사용자가 다음 내용을 직접 고칠 수 있습니다.

- 분석 요약
- 다음 행동
- 일정 정보
- 참석자
- 고객사
- 제품
- 금액
- 처리 유형
- 판단 근거
- 대상 일정 ID
- 대상 일정명

저장 API:

```http
PATCH /api/analysis/{analysisId}
```

수정된 결과로 등록하면 Dashboard 캘린더와 Google Calendar에 반영됩니다.

## 답장 초안 생성

분석 결과 카드에서 `답장 초안 생성` 버튼을 누르면 다음 API를 호출합니다.

```http
POST /api/analysis/{analysisId}/reply-draft
```

동작:

- 원본 메일 제목/본문, 분석 결과, 고객사/제품/일정 정보를 FastAPI AI 모듈로 전달합니다.
- 시연 핵심 케이스는 안정적인 템플릿 기반 답장을 반환합니다.
- 일반 케이스는 Ollama LLM 생성 결과를 시도하고, 실패하거나 품질이 낮으면 템플릿으로 fallback합니다.
- 프론트는 생성된 제목/본문을 표시하고 복사 버튼을 제공합니다.

## 처리 이력 화면

Route:

```text
/history
```

역할:

- 수집 메일이 승인 대기/등록됨/삭제됨 중 어떤 상태인지 확인
- AI 분석 결과와 Salesmap 반영 상태를 한 화면에서 확인
- 우선 확인할 메일에서 상세 화면으로 이동

사용 API:

- `GET /api/sources`
- `GET /api/analysis/source/{sourceId}`
- `GET /api/salesmap/analysis/{analysisId}`

## 고객 타임라인 화면

Route:

```text
/customers
```

역할:

- 고객사/담당자별 메일, AI 분석, 일정 반영, Salesmap 등록 이력을 시간순으로 표시
- 같은 발신자 또는 같은 고객사 맥락을 빠르게 확인
- 업무 외 메일은 고객 타임라인에 포함하지 않음

사용 API:

- `GET /api/customers`
- `GET /api/customers/{customerContactId}/timeline`

## Salesmap 등록의 실제 의미

현재 Salesmap TODO 직접 생성 API가 없기 때문에 프론트에서 누르는 `Salesmap 등록`은 다음 흐름을 실행합니다.

```text
POST /api/salesmap/register
  -> Analysis actionType 확인
  -> 내부 Schedule 생성/수정/삭제
  -> Google Calendar 이벤트 생성/수정/삭제
  -> Salesmap 캘린더 양방향 연동으로 TODO 반영
```

프론트는 Salesmap API를 직접 호출하지 않습니다.

## 오류 코드 의미

| Status | 프론트에서 안내할 의미 |
| --- | --- |
| 400 | 요청값이 백엔드 DTO와 맞지 않음 |
| 401 | 로그인 만료 또는 토큰 없음 |
| 403 | 다른 사용자의 리소스 접근 |
| 404 | 데이터 또는 API 경로 없음 |
| 500 | 서버 내부 오류 |
| 502 | FastAPI AI 모듈 또는 Google/Salesmap 외부 연동 실패 |

## 발표 시 프론트에서 보여줄 화면

1. 로그인 화면
2. 설정 화면에서 Gmail 연결 상태
3. Gmail 메일 목록
4. 메일 상세 및 AI 분석
5. AI 분석 결과 수정
6. 답장 초안 생성
7. 일정 등록/변경/삭제 버튼
8. Dashboard 캘린더 반영
9. 처리 이력
10. 고객 타임라인
11. Salesmap TODO 반영 화면
