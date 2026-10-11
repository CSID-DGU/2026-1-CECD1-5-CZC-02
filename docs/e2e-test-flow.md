# E2E Test Flow

최종 시연 전 팀원이 직접 확인할 수 있는 로컬 통합 테스트 순서입니다.

## 1. 사전 준비

### MySQL

MySQL을 실행하고 DB가 없으면 생성합니다.

```sql
CREATE DATABASE IF NOT EXISTS salesmap
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

### Google Cloud Console

OAuth Client의 Authorized redirect URI에 아래 값을 등록합니다.

```text
http://localhost:5173/settings/gmail/callback
```

Gmail/Calendar에 필요한 scope:

```text
openid
email
profile
https://www.googleapis.com/auth/gmail.readonly
https://www.googleapis.com/auth/calendar.events
```

### Salesmap 설정

Salesmap 개인 설정에서 Google Calendar 양방향 연동을 완료합니다.

```text
개인 설정
  -> 연동
  -> 캘린더
  -> Google 계정 연결
  -> 양방향 연동 선택
  -> 가져오기 유형: 미팅
  -> 저장
```

## 2. 서버 실행

### Backend

필요 환경변수:

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

실행:

```powershell
cd C:\salesmap-agent\backend
.\gradlew.bat bootRun
```

### FastAPI AI Module

```powershell
cd C:\salesmap-agent\ai-module
uvicorn app.main:app --reload --port 8000
```

### Frontend

```powershell
cd C:\salesmap-agent\frontend
npm run dev
```

접속:

```text
http://localhost:5173
```

## 3. 기본 로그인 테스트

1. 회원가입 또는 로그인
2. `/dashboard` 이동 확인
3. 개발자 도구 Application 탭에서 `localStorage.accessToken` 확인
4. Network 탭에서 보호 API에 `Authorization: Bearer {token}` 포함 여부 확인

## 4. Gmail 연결 테스트

1. 설정 화면으로 이동
2. Gmail 연결 버튼 클릭
3. Google OAuth 동의
4. 설정 화면으로 복귀
5. Gmail 연결 상태가 `연결됨`인지 확인

확인 API:

- `GET /api/integrations/gmail/authorize`
- `GET /api/integrations/gmail/callback`
- `GET /api/integrations`

## 5. Gmail 수집 테스트

1. Gmail 또는 JANDI/Gmail 메뉴 화면으로 이동
2. `Gmail 새로고침` 클릭
3. 수집된 메일 목록이 갱신되는지 확인

확인 API:

- `POST /api/integrations/gmail/collect`
- `GET /api/sources`

성공 기준:

- 새 메일이 Source 목록에 표시
- DB `sources`에 새 row 생성
- Gmail thread가 있으면 `source_groups`에 그룹 저장

## 6. CREATE 시나리오

### 테스트 메일

제목:

```text
2026-06-12 Nimbus Tech 도입 상담 미팅 요청
```

본문:

```text
안녕하세요.

Nimbus Tech 김도윤입니다.

2026년 6월 12일 오전 10시에 CRM Automation 서비스 도입 상담 미팅을 요청드립니다.
참석자는 Nimbus Tech 김도윤, 영업팀 이민재입니다.

가능하시면 일정 등록 부탁드립니다.

감사합니다.
```

### 웹 테스트

1. Gmail 새로고침
2. 해당 메일 선택
3. AI 분석 실행
4. 분석 결과 확인
5. 필요하면 분석 결과 수정
6. `일정 등록` 클릭
7. Dashboard 캘린더 확인
8. Google Calendar 확인
9. Salesmap TODO 확인

성공 기준:

- actionType: `CREATE`
- 고객사: `Nimbus Tech`
- 제품: `CRM Automation`
- 일정: `2026-06-12 10:00`
- Dashboard에 일정 생성
- Google Calendar에 이벤트 생성
- Salesmap TODO에 미팅으로 반영

## 7. UPDATE 시나리오

### 테스트 메일

제목:

```text
Nimbus Tech 도입 상담 미팅 일정 변경 요청
```

본문:

```text
안녕하세요.

기존 2026년 6월 12일 오전 10시 Nimbus Tech CRM Automation 도입 상담 미팅을
2026년 6월 12일 오후 2시로 변경 부탁드립니다.

참석자는 동일합니다.

감사합니다.
```

### 웹 테스트

1. Gmail 새로고침
2. 변경 요청 메일 선택
3. AI 분석 실행
4. targetScheduleId가 기존 일정으로 잡혔는지 확인
5. 필요하면 수동으로 대상 일정 ID 또는 일정 정보를 수정
6. `등록된 일정 변경` 클릭
7. Dashboard 캘린더 시간이 바뀌었는지 확인
8. Google Calendar와 Salesmap TODO 변경 확인

성공 기준:

- actionType: `UPDATE`
- 기존 일정 시간이 변경됨
- 새 일정이 중복 생성되지 않음

## 8. CANCEL 시나리오

### 테스트 메일

제목:

```text
Nimbus Tech 도입 상담 미팅 취소 요청
```

본문:

```text
안녕하세요.

2026년 6월 12일 오전 10시에 예정되어 있던 Nimbus Tech CRM Automation 서비스 도입 상담 미팅은 취소 부탁드립니다.

내부 일정 조정으로 이번 미팅은 진행하지 않겠습니다.
추후 다시 일정 요청드리겠습니다.

감사합니다.
```

### 웹 테스트

1. Gmail 새로고침
2. 취소 요청 메일 선택
3. AI 분석 실행
4. targetScheduleId가 기존 일정으로 잡혔는지 확인
5. `등록된 일정 삭제` 클릭
6. Dashboard 캘린더에서 일정이 사라졌는지 확인
7. Google Calendar에서 이벤트가 삭제되었는지 확인
8. Salesmap TODO가 삭제 또는 동기화 해제되었는지 확인

성공 기준:

- actionType: `CANCEL`
- 내부 Schedule 삭제
- Google Calendar 이벤트 삭제
- Salesmap TODO도 사라짐

## 9. 일반 메일 UNKNOWN 시나리오

제목:

```text
견적서 전달드립니다
```

본문:

```text
안녕하세요.

요청하신 견적서를 첨부드립니다.
검토 후 문의사항 있으시면 연락 부탁드립니다.

감사합니다.
```

성공 기준:

- actionType: `UNKNOWN`
- 일정 정보: 해당 없음
- Dashboard에 일정이 생성되지 않음

## 10. 추가 확인 항목

### 답장 초안

1. 영업 메일 선택
2. AI 분석 실행
3. `답장 초안 생성` 클릭
4. 약 3초 후 제목/본문이 표시되는지 확인
5. `복사` 버튼으로 본문 복사가 가능한지 확인

성공 기준:

- 고객사/담당자 호칭이 자연스럽게 표시됨
- 일정 생성/변경/취소/자료 요청에 맞는 답변 문구가 생성됨
- 본문 앞 공백이나 깨진 한글이 없음

### 처리 이력과 고객 타임라인

처리 이력:

1. `/history` 또는 사이드 메뉴 `처리 이력` 진입
2. 승인 대기/등록됨/삭제됨 수치 확인
3. 우선 확인 메일을 클릭해 메일 상세 화면으로 이동

고객 타임라인:

1. `/customers` 또는 사이드 메뉴 `고객 타임라인` 진입
2. 고객사 선택
3. AI 분석, 일정 반영, Salesmap 등록 이력이 시간순으로 표시되는지 확인

성공 기준:

- 업무 외 메일은 고객 타임라인에 포함되지 않음
- GreenSoft/Delta Systems 등 시연 고객사의 활동 이력이 표시됨

### 일정 충돌 경고

1. 이미 등록된 일정과 같은 시간 또는 가까운 시간대의 미팅 메일을 분석
2. `일정 등록` 클릭
3. 중복 또는 근접 일정 경고가 표시되는지 확인
4. 중복 일정은 등록을 막고, 근접 일정은 사용자 확인 후 계속 진행 가능

성공 기준:

- 같은 시간대 일정은 `409 Conflict` 기반 경고가 표시됨
- 전후 약 3시간 안의 일정은 충돌 후보로 안내됨

## 11. DB 확인 SQL

```sql
select * from users order by id desc;
select * from integrations order by id desc;
select * from source_groups order by id desc;
select * from sources order by id desc;
select * from analyses order by id desc;
select * from schedules order by id desc;
select * from salesmap_records order by id desc;
select * from customer_contacts order by id desc;
select * from customer_activities order by id desc;
```

특정 일정 확인:

```sql
select id, title, schedule_date_time, status, google_calendar_event_id
from schedules
order by id desc;
```

## 12. 실패 시 확인 위치

- Browser Console: 프론트 에러 메시지
- Network: status code, request body, response body
- Backend terminal: Spring Boot 로그
- FastAPI terminal: `/analyze` 요청/응답 로그
- MySQL: 저장 여부
- Google Calendar: 이벤트 생성/수정/삭제 여부
- Salesmap: TODO 동기화 여부

## 13. 테스트 결과 전달 템플릿

```text
테스트 종류: CREATE / UPDATE / CANCEL / UNKNOWN
사용한 메일 제목:
사용한 메일 본문:
Browser Console 에러:
Network 요청/응답:
Backend 로그:
FastAPI 로그:
DB analyses 결과:
DB schedules 결과:
Google Calendar 결과:
Salesmap TODO 결과:
프론트 화면 결과:
성공/실패 판단:
```
