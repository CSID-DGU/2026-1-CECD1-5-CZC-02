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

- 수집된 Gmail 메일 목록 표시
- 메일 상세 표시
- 같은 발신자의 최근 메일 표시
- AI 분석 실행
- 분석 결과 수동 수정
- Salesmap 등록/변경/삭제 승인

사용 API:

- `GET /api/sources`
- `GET /api/sources/{sourceId}`
- `POST /api/integrations/gmail/collect`
- `POST /api/analysis`
- `POST /api/analysis/group`
- `GET /api/analysis/source/{sourceId}`
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
6. 일정 등록/변경/삭제 버튼
7. Dashboard 캘린더 반영
8. Salesmap TODO 반영 화면
