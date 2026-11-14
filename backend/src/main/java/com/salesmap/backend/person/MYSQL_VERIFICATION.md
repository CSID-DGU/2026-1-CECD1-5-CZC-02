# A 실제 MySQL 검증

2026-11-13 갱신. 로컬 MySQL **9.6.0**, Java 17에서 실행했다. 인물 등록·선택 서비스와 기존 D RevisionService를 실제 Spring/JPA 트랜잭션으로 연결한 검사 **9개 통과, 실패·오류·건너뜀 0개**다. 외부 AI·메일·메시지 호출은 없다.

## 확인한 내용

- 등록·한글/별칭/이전 소속 검색·특수문자 검색·페이지 조회·사용자별 접근 제한과 Person 형식.
- HUMAN 선택과 D Revision의 실제 저장·재조회, 계약 형식, 한국 시간 기록.
- 새 인물 등록, 명시적인 선택 변경, 동일 선택 재요청과 오래된 버전의 중복 생성 방지.
- AI 재실행 시 원본 제안과 사람 선택 보존.
- 타 사용자 접근, 확정·보관한 노트의 변경 거절.
- D 기록 저장을 DB 제약으로 실패시키면 신규 Person과 선택도 롤백.
- A 선택 저장을 DB 제약으로 실패시키면 앞서 저장한 D 기록과 신규 Person도 롤백.
- 같은 버전으로 두 요청을 동시에 보내면 하나만 저장되고 나머지는 충돌. 수정 기록은 1개.

## JWT 요청 검증 추가 (2026-11-13)

MockMvc에서 기존 SecurityConfig·JwtAuthenticationFilter·CustomUserDetailsService를 실제로 사용한다.
테스트 전용 서명 키로 생성한 토큰만 사용하며 실제 사용자 토큰·비밀번호를 읽지 않는다.

- 미인증·잘못된 토큰·만료 토큰의 401.
- 인증된 인물 등록·검색·타 사용자 인물 조회의 404.
- MODEL 후보의 저장·조회 → If-Match 선택 → HUMAN 결과 재조회.
- MODEL 원본과 수정 기록 보존, 타 사용자 선택 거절, 오래된 버전의 신규 등록 거절.
- 새 인물 중복 생성·수정 기록 중복 생성이 없는지 실제 DB 검사.

## 재현

backend에서 로컬 MySQL 서버·mysql CLI·Java 17과 Gradle 의존성 캐시가 준비된 상태로 실행한다.

```bash
python3 src/test/java/com/salesmap/backend/person/run_mysql_verification.py
```

기본 접속은 로컬 root와 `/private/tmp/mysql.sock`이며 소켓이 없으면 127.0.0.1:3306을 사용한다.
다르면 `A_PERSON_MYSQL_ADMIN_USER`, `A_PERSON_MYSQL_SOCKET`, `A_PERSON_MYSQL_PORT`를 환경변수로 지정한다.
관리자 비밀번호가 필요하면 `A_PERSON_MYSQL_ADMIN_PASSWORD` 환경변수로 전달한다. 파일·커밋·로그에 넣지 않는다.

실행기는 무작위 이름의 **새 DB와 그 DB에만 권한을 가진 임시 계정**을 만든다. 암호는 매번 생성하며 저장·출력하지 않는다.
테스트는 로컬 전용 URL·전용 계정과 빈 DB를 확인한 뒤 스키마를 생성한다. 종료 시 실행기가 자신이 만든 DB·계정만 제거한다.
임시 DB 안에서만 실패 유도용 CHECK 제약을 만들고 제거한다. 기존 앱 DB나 그 테이블은 조회·수정하지 않는다.
정리 실패 시 임시 자원 이름을 표시하고 실패로 종료한다.

테스트 클래스는 `PersonMysqlIntegrationTests`로 일반 CI의 `*Test` 패턴에서 제외되며,
`A_PERSON_MYSQL_TEST=true`와 검증 전용 환경변수가 있을 때만 실행된다. 일반 단위 검사에는 MySQL이 필요 없다.

## 남은 범위

- 새 빈 DB에 현재 엔티티로 스키마를 생성한 결과다. 기존 앱 DB의 스키마 이행·기존 데이터 호환성은 검증하지 않았다.
- HTTP 인증 필터/JWT는 MockMvc로 검사했다. 실제 TCP 서버·브라우저 클릭·실제 AI HTTP 호출은 이번 검사 범위 밖이다.
- A 선택은 B 회의록 participants/perPerson와 C 할 일 상대방을 변경하지 않는다. B 연결과 확정 조건은 담당자 검토가 필요하다.
- B·D 소스, 공용 계약·CI·설정은 변경하지 않았다. 검증에서 기존 B/D 서비스를 호출하고 전용 DB에만 가짜 fixture를 저장했다.
