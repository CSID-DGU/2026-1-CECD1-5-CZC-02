# Backend Status

팀 공유용 백엔드 진행 상태 요약입니다.

## Current Status

현재 백엔드는 Spring Boot + MySQL + JPA 기반으로 주요 도메인 API가 동작합니다.

완료된 핵심 작업:

- JWT 기반 회원가입, 로그인, 내 정보 조회
- BCrypt 비밀번호 암호화
- 보호 API에 `Authorization: Bearer {accessToken}` 적용
- Source, Schedule, Analysis, Salesmap API 소유권 검증
- 다른 사용자의 `sourceId`, `analysisId`, `userId` 접근 시 `403` 반환
- User, Integration, Source, Analysis, Schedule, SalesmapRecord Entity/Repository 구성
- Source, Analysis, Schedule, Salesmap 주요 API DB 기반 동작
- AI Module client 구조 분리
- Salesmap API client 구조 분리
- mock/http 모드 전환 구조 적용
- 프론트 연동 문서 작성: [frontend-api-guide.md](./frontend-api-guide.md)

## Frontend Team Notes

프론트엔드는 아래 문서를 우선 참고하면 됩니다.

- API 사용 흐름: [frontend-api-guide.md](./frontend-api-guide.md)
- 전체 API 명세: [api-spec.md](./api-spec.md)

중요 연동 규칙:

- 로그인 또는 회원가입 후 `accessToken`을 저장합니다.
- 보호 API 호출 시 항상 아래 헤더를 보냅니다.

```http
Authorization: Bearer {accessToken}
```

- 신규 화면에서는 `userId`를 직접 보내지 않습니다.
- Source/Schedule 목록은 로그인 사용자 기준입니다.

```http
GET /api/sources
GET /api/schedules
```

- 다른 사용자의 리소스 접근 시 `403`이 반환됩니다.
- 외부 AI Module 또는 Salesmap API 장애 시 백엔드는 `502`를 반환합니다.

## AI Module Team Notes

백엔드와 AI Module 연동 기준:

```http
POST /analyze
```

백엔드가 AI Module로 보내는 주요 필드:

- `sourceId`
- `sourceType`
- `externalSourceId`
- `title`
- `content`
- `collectedAt`

AI Module이 반환해야 하는 주요 필드:

- `customerName`
- `contactName`
- `productName`
- `amount`
- `scheduleTitle`
- `scheduleDateTime`
- `todoContent`
- `keyIssues`
- `summary`
- `confidenceScore`

규칙:

- `scheduleDateTime`은 ISO-8601 형식입니다.
- `confidenceScore`는 `0.0` 이상 `1.0` 이하입니다.
- 실패 응답은 `errorCode`, `message`, `details` 구조를 사용합니다.

상세 계약은 [backend-guide.md](./backend-guide.md)의 AI Module 섹션을 참고합니다.

## Salesmap Integration Notes

Salesmap 외부 API 연동은 client 구조만 준비되어 있습니다.

현재 백엔드는 아래 구조를 사용합니다.

```text
SalesmapService
  -> SalesmapClient
      -> MockSalesmapClient
      -> HttpSalesmapClient
```

Salesmap API 요청에 포함되는 주요 필드:

- `analysisId`
- `sourceId`
- `customerName`
- `contactName`
- `productName`
- `amount`
- `scheduleText`
- `followUpAction`
- `summary`

실제 Salesmap API endpoint와 인증 방식이 확정되면 `HttpSalesmapClient`에 인증 헤더와 실제 path를 반영하면 됩니다.

## Mock / HTTP Mode

현재 기본값은 모두 mock입니다.

```properties
ai.module.mode=mock
salesmap.api.mode=mock
```

mock 모드에서는 외부 서버가 없어도 백엔드 API 테스트가 가능합니다.

실제 AI Module 호출로 전환:

```properties
ai.module.mode=http
ai.module.base-url=http://localhost:8000
```

실제 Salesmap API 호출로 전환:

```properties
salesmap.api.mode=http
salesmap.api.base-url=http://localhost:9000
salesmap.api.register-path=/records
```

## Next Work

앞으로 필요한 작업:

- 실제 FastAPI AI Module 구현 및 `/analyze` 연동 테스트
- 실제 Salesmap API endpoint, 인증 방식, 요청/응답 필드 확정
- Gmail/Jandi 연동 API 구현
- Integration 생성/조회 API 구현
- 프론트 화면과 API 연결 테스트
- 주요 API 통합 테스트 작성
- 운영용 환경 변수 정리
- refresh token 또는 token 만료 처리 정책 확정

## Meeting Summary

현재 백엔드는 인증, DB 기반 주요 도메인 API, 소유권 검증, AI/Salesmap 외부 연동 client 구조까지 완료되었습니다.

프론트는 `frontend-api-guide.md` 기준으로 JWT 로그인 후 API를 붙이면 됩니다.

AI Module과 Salesmap은 현재 mock 기본값으로 동작하며, 실제 서버가 준비되면 property만 `http`로 바꿔 연동 테스트를 시작할 수 있습니다.
