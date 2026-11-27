# 인증 정보의 로그 출력 방지

로그인·회원가입 record의 자동 `toString()`을 Spring MVC가 DEBUG/TRACE에서 출력하면서
비밀번호가 로그에 남을 수 있었다. 로그인 응답에는 같은 경로로 accessToken이 포함될 수 있었다.

- `LoginRequest`, `SignupRequest`, `AuthResponse`의 문자열 출력은 고정된 `REDACTED` 문장이다.
  JSON 직렬화·필드 접근·유효성 검사와 실제 응답 토큰은 바꾸지 않는다.
- `logback-spring.xml`의 `SensitiveAuthLogFilter`는 `/api/auth`와 그 하위 요청을 처리하는 동안
  `org.springframework.web.*`의 진단 로그를 차단한다. 검증 실패의 rejectedValue·깨진 JSON·
  상세 헤더 로그도 포함한다. 다른 API의 진단과 앱 자체 로그는 유지한다.
- 로그용 마스킹은 비밀번호의 암호화·저장 방식을 바꾸는 기능이 아니다.
  직접 `password()`나 실제 JSON을 로그에 넣는 코드는 추가하면 안 된다.
- 기존에 생성된 로그는 이 수정으로 지워지지 않는다. 인증 정보가 담긴 과거 로그를 공유하지 않는다.

2026-11-26 검사:

```bash
cd backend
./gradlew test --tests "*Test"
```

전체 단위 검사 305개 통과, 실패·오류·건너뜀 0개. 인증 보호 검사 8개 포함.
가짜 이메일·비밀번호·토큰만 사용했고 실제 계정·MySQL·외부 서비스는 필요 없다.
MVC TRACE와 상세 요청 출력이 켜진 상태에서 성공·실패 로그인, 회원가입, 검증 오류,
깨진 JSON의 로그를 확인하고 JSON 요청·토큰 응답 호환성 및 기본 필터 설정 로딩을 검사했다.

## DB 무결성 오류 로그

`GlobalExceptionHandler`는 DB 무결성 오류의 예외 전문과 스택 추적을 출력하지 않는다.
예외 종류, 5자리 영숫자 SQL 상태 코드, 숫자 DB 오류 코드만 기록한다.
사용자 응답은 기존 500 상태와 일반 오류 문구를 유지한다.
가짜 입력값이 포함된 SQL 예외, SQL 원인이 없는 오류, 비정상 SQL 상태 코드 검사 3개를 추가했다.
이 보호 범위는 공용 예외 처리기의 출력이며, DB 드라이버·ORM의 별도 진단 로그 전체를 보장하지 않는다.
