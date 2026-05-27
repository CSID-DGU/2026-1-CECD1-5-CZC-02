# Backend Status

Dongguk University 종합설계 프로젝트 `SALESMAP 활동 자동화 AI Agent`의 백엔드 진행 상태입니다.

## Current Summary

현재 백엔드는 Spring Boot + MySQL + JPA 기반으로 핵심 도메인 API가 동작하며, React + Vite 프론트엔드와 JWT 인증 흐름 및 주요 업무 흐름이 연결되어 있습니다.

완료된 핵심 흐름:

```text
회원가입/로그인
  -> JWT accessToken 저장
  -> Source 생성/조회
  -> MockAiClient 기반 Analysis 생성/조회
  -> MockSalesmapClient 기반 Salesmap 등록/조회
```

## Completed Backend Features

- JWT 기반 회원가입, 로그인, 내 정보 조회
- BCrypt 비밀번호 암호화
- `Authorization: Bearer {accessToken}` 기반 보호 API
- `ApiResponse` 공통 응답 구조
- DTO Validation 및 `GlobalExceptionHandler`
- User, Integration, Source, Analysis, Schedule, SalesmapRecord Entity/Repository
- User, Source, Analysis, Schedule, Salesmap API DB 기반 동작
- Source/Schedule/Analysis/Salesmap 소유권 검증
- AI Module client 구조 분리
  - `AiClient`
  - `MockAiClient`
  - `HttpAiClient`
- Salesmap API client 구조 분리
  - `SalesmapClient`
  - `MockSalesmapClient`
  - `HttpSalesmapClient`
- `mock` / `http` property 기반 전환 구조

## Frontend Integration Status

현재 `feature/frontend-backend-integration` 브랜치에서 다음 프론트-백엔드 연동이 완료되었습니다.

- JWT 로그인/회원가입 프론트 연동
- `localStorage.accessToken` 저장
- axios interceptor 기반 Authorization 자동 첨부
- `ProtectedRoute` 적용
- 로그아웃 시 accessToken 삭제
- `GET /api/sources` 보호 API 연결
- `GET /api/schedules` 보호 API 연결
- Source 생성 테스트 연결
- Schedule 생성 테스트 연결
- Source 상세 조회 연결
- `GET /api/analysis/source/{sourceId}` 연결
- `POST /api/analysis` MockAiClient 기반 분석 생성 연결
- Analysis 결과 표시
- `POST /api/salesmap/register` 연결
- `GET /api/salesmap/analysis/{analysisId}` Mock Salesmap 등록 결과 조회
- `MessageView` 기반 Source -> Analysis -> Salesmap 흐름 연결

## Current E2E Test Flow

현재 로컬에서 가능한 통합 테스트 흐름:

1. 프론트에서 회원가입 또는 로그인
2. accessToken이 `localStorage`에 저장되는지 확인
3. `/dashboard` 진입
4. Source 생성 테스트 실행
5. Source 목록에서 생성된 Source 선택
6. Source 상세 조회
7. AI 분석 테스트 실행
8. Analysis 결과 표시 확인
9. SALESMAP 등록 테스트 실행
10. SalesmapRecord 등록 결과 표시 확인

## Mock Mode

현재 기본값은 외부 서버 없이 테스트 가능한 mock 모드입니다.

```properties
ai.module.mode=mock
salesmap.api.mode=mock
```

Mock 동작:

- `MockAiClient`: FastAPI 서버 없이 고정 분석 결과를 반환하고 `analyses` 테이블에 저장
- `MockSalesmapClient`: 실제 Salesmap 외부 API 호출 없이 mock `externalRecordId`, `requestPayload`, `responsePayload`를 반환하고 `salesmap_records` 테이블에 저장

실제 연동 전환 예시:

```properties
ai.module.mode=http
ai.module.base-url=http://localhost:8000

salesmap.api.mode=http
salesmap.api.base-url=http://localhost:9000
salesmap.api.register-path=/records
```

## Recent Backend Fixes

Salesmap 등록 테스트 중 401처럼 보이던 문제가 있었으나, 실제 원인은 DB 저장 오류였습니다.

반영된 수정:

- `salesmap_records.request_payload` 컬럼을 `LONGTEXT`로 변경
- `salesmap_records.response_payload` 컬럼을 `LONGTEXT`로 변경
- `/error`를 Security `permitAll`에 추가
- `DataIntegrityViolationException` JSON 응답 처리 추가
- `SalesmapController`에서 `Authentication` 기반 사용자 확인으로 수정
- Salesmap register 진단용 로그를 `debug` 수준으로 추가

## Database Tables

현재 구현된 주요 테이블:

- `users`
- `integrations`
- `sources`
- `analyses`
- `schedules`
- `salesmap_records`

주의:

- `salesmap_records.request_payload`, `salesmap_records.response_payload`는 JSON payload 저장을 위해 `LONGTEXT`입니다.
- JPA `ddl-auto=update` 기준으로 로컬 DB 스키마가 갱신됩니다.

## Frontend Team Notes

프론트 팀은 아래 문서를 우선 참고하면 됩니다.

- [frontend-api-guide.md](./frontend-api-guide.md)
- [api-spec.md](./api-spec.md)

핵심 규칙:

- 로그인/회원가입 성공 시 `accessToken`을 저장합니다.
- 보호 API는 axios interceptor가 자동으로 Authorization 헤더를 붙입니다.
- 일반 사용자 화면에서는 `userId`를 직접 보내지 않는 흐름을 우선 사용합니다.
- 다른 사용자의 `sourceId`, `analysisId`, `userId` 접근은 `403`입니다.

## AI Module Team Notes

FastAPI AI Module 실제 연동은 아직 미구현입니다.

현재 백엔드는 다음 구조를 준비했습니다.

```text
AnalysisService
  -> AiClient
      -> MockAiClient
      -> HttpAiClient
```

실제 FastAPI endpoint는 `/analyze`를 기준으로 준비되어 있습니다. 계약 상세는 [backend-guide.md](./backend-guide.md)의 AI Module 섹션을 참고합니다.

## Remaining Work

- 실제 Gmail OAuth 및 Gmail 데이터 수집
- 실제 JANDI 연동
- 실제 FastAPI AI Module `/analyze` 구현 및 연동 테스트
- 실제 Salesmap 외부 API endpoint, 인증 방식, request/response 확정
- Integration 생성/조회 API 구현
- Swagger/OpenAPI 문서화
- Docker 및 배포 환경 구성
- 운영 로그 정책 정리
- refresh token 또는 token 만료 UX 정책
- 백엔드 통합 테스트 및 프론트 E2E 테스트 보강
