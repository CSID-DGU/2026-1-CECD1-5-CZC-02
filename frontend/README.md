# SALESMAP Frontend

React · Vite · Tailwind. 패키지 관리자는 **npm** (`package-lock.json`).

```bash
npm install
npm run dev     # http://localhost:5173
npm run build   # PR 자동 검사에서도 실행
```

백엔드 주소는 `src/api/client.js`의 `baseURL`(`http://localhost:8080`)이고, 로그인 토큰은 `localStorage.accessToken`으로 붙습니다. API 형식은 [docs/api-spec.md](../docs/api-spec.md), 2학기 화면 시안은 [docs/project/screens.md](../docs/project/screens.md).

> 2026-10 정리. 1학기 `docs/frontend-api-guide.md`는 이 파일과 api-spec으로 대체했습니다.

## 화면과 API 파일 (현재 코드 기준)

| 경로 | 화면 (`src/components/`) | 쓰는 API (`src/api/`) |
| --- | --- | --- |
| `/login` | `LoginPage` | `client` |
| `/dashboard` | `Dashboard` — 오늘의 브리핑, 일정, 승인 대기 | `analyses`, `schedules`, `sources` |
| `/messages/:source` | `MessageView` — 메일 목록·상세, AI 분석 확인·수정·승인, 답장 | `analyses`, `analysisJobs`, `autoPipeline`, `integrations`, `salesmapRecords`, `sources` |
| `/messages/jandi` | `JandiComingSoon` | - |
| `/history` | `ProcessingHistoryPage` — 처리 이력 | `analyses`, `salesmapRecords`, `sources` |
| `/customers` | `CustomerTimelinePage` — 고객 타임라인 | `customers` |
| `/pipeline` | `PipelinePage` — 고객별 진행 현황 | `customers` |
| `/settings` | `SettingsPage` — Gmail 연결, 자동 수집·분석, 데모 모드 | `integrations`, `autoPipeline`, `demo` |
| `/settings/gmail/callback` | `GmailOAuthCallbackPage` | `integrations` |

공통 틀은 `DashboardLayout`(사이드바, 알림), 오류 메시지 변환은 `api/errors.js`.
