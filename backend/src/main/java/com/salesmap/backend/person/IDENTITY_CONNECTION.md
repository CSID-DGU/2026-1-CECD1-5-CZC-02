# A 인물 식별 실제 호출 연결

## 구현한 흐름

B의 기존 초안 서비스 → `IdentityResolutionClient` → A `PersonIdentityResolutionClient`
→ 본인 인물 대장 전체 DTO → `POST /identity/resolve` → 계약 형태·요청 대응·대장 참조 검사
→ 기존 B 회의록 초안 흐름으로 결과 전달.

2번 당시에는 A의 `person` 패키지와 같은 패키지의 테스트만 변경했다.
3번의 후보 화면·제안/선택 저장 추가분은 IDENTITY_SELECTION.md를 우선한다. B/C/D·공용 설정·contracts는 수정하지 않았다.

## 실행 모드

- `AI_MODULE_MODE=http`이면 A 구현을 `@Primary`로 우선 주입한다.
  기존 `AI_MODULE_BASE_URL`(기본 http://localhost:8000)과 연결/읽기 제한 시간을 그대로 사용한다.
  새 환경변수나 공용 설정 파일 변경은 없다. 실제 동작에는 백엔드 DB와 AI 모듈이 필요하다.
- 기본 mock 모드에서는 새 HTTP 구현을 등록하지 않는다. 기존 B의 가짜 식별 동작이 유지된다.
- **B의 FakeIdentityResolutionClient는 삭제하거나 수정하지 않았다.** HTTP 모드에서도 빈은 남지만
  인터페이스 주입은 A 실제 구현을 선택하는 것을 컨텍스트 테스트로 확인했다.
  따라서 기존 가짜 구현의 시작 경고 문구가 여전히 출력될 수 있다.
  가짜 구현 정리와 기존 B 테스트 fixture 교체는 담당자 검토 후 별도 변경해야 한다.

## 요청과 사용자 분리

- B의 AttendeeMention에서 name·email·channel을 그대로, sourceId를 mention.sourceRef로 전달한다.
- 이름 없이 이메일만 있으면 name=null을 유지한다. 이메일 주소를 이름으로 꾸며 채우지 않는다.
- 회사·전화·직함처럼 B가 전달하지 않은 단서를 임의로 추가하지 않는다.
- owner는 B가 전달한 인증 사용자 ID다. 인물 대장은 `findByUserIdOrderByIdAsc`로 본인 것만 조회한다.
- 화면의 50명/100명 페이지를 재사용하지 않는다. 공유 연락처·동명이인을 놓치지 않게 본인 대장
  전체를 읽는다. 120명 회귀 검사로 첫 페이지 누락을 방지한다.
- 대장을 읽는 읽기 전용 트랜잭션은 DTO 반환 시 끝난다. 클라이언트에 트랜잭션을 붙이지 않아
  HTTP 호출 동안 새 DB 트랜잭션을 유지하지 않는다.
- 빈 참석자는 대장 조회·HTTP 호출을 생략한다. 빈 대장도 AI에 명시적으로 []를 보내며 샘플을 로드하지 않는다.

## 응답·실패 처리

- 입력 언급과 같은 수·순서·원문 mention이어야 한다. resolutionId 형식·중복과 계약 필드를 검사한다.
- 지금 연결은 RULE API 전용이다. score는 null, 후보 최대 3명, 중복 후보 없음, 각 후보 이유 필수다.
- 후보와 확정 personId가 요청에 보낸 본인 대장 안에 있어야 한다. 모르는 인물 ID는 거절한다.
- NEW는 personId=null·후보 없음, CANDIDATES는 personId=null·후보 있음이어야 한다.
- HTTP 오류·접속/읽기 실패·잘못된 응답은 예외로 넘긴다. NEW/CONFIRMED나 가짜 후보로 대체하지 않는다.
  B의 기존 실패 처리에 따라 회의록은 연결 정보 없는 DRAFT로 남는다.
- upstream 오류 본문·개인 단서를 예외 메시지나 로그에 복사하지 않는다.
- 클라이언트는 인물 등록·병합이나 회의록 확정을 하지 않는다. 3번에서 검증한 제안의 A 저장을 추가했다.
  사람의 선택·등록·수정 기록은 별도의 A 선택 API가 사용자 요청을 받아 처리한다.

## B 담당 검토가 필요한 경계

현재 B 백엔드의 `linkParticipant`와 AI `minutes/engine.py`는 **이름으로만** A 결과를 연결한다.

1. **이름이 같은 참석자 여러 명:** 서로 다른 이메일로 식별돼도 이름 키 하나로 연결돼 잘못된 인물로
   붙을 수 있다. 공백 차이까지 제거한 이름이 중복되고 CONFIRMED가 포함되면 A 클라이언트가
   현재는 전체 식별 응답을 거절한다. B의 기존 실패 처리로 personId=null을 유지해 오연결을 막는다.
   나머지 정상 참석자도 이번 초안에서는 식별 결과가 빠지는 보수적인 제한이 있다.
   이를 CANDIDATES/NEW로 꾸며 반환하지 않는다.
2. **이메일만 있는 참석자:** A 응답은 정상이고 name=null을 보존하지만, B가 표시용 이메일과
   mention.name을 맞추지 못하므로 회의록 participant에 resolutionId/personId가 연결되지 않을 수 있다.
3. 위 경우를 정상 연결하려면 B가 순서·이메일 등으로 참석자와 결과를 정확히 대응시켜야 한다.
   B의 두 연결 함수를 이번에 수정하지 않았다. 계약 변경이 필요하면 먼저 팀 합의해야 한다.
4. 후보 선택 화면·HUMAN 결정·Revision은 3번의 A 선택 API에 구현했다. B 회의록 반영과
   인물 대장의 사람별 이력 조회는 남아 있다.

따라서 **A HTTP 호출·일반 이름 참석자 연결은 구현했으며, 모든 참석자 유형의 앱 연결 완료를 뜻하지 않는다.**

## 검사

기본 테스트는 네트워크·MySQL·Ollama 없이 실행한다.

```bash
cd backend
bash ./gradlew test --tests 'com.salesmap.backend.person.*Test'
bash ./gradlew test --tests '*Test'
```

- MockRestServiceServer로 실제 HTTP 클라이언트의 요청·응답과 장애를 검사한다.
- HTTP/mock 모드에서 인터페이스가 어떤 빈을 고르는지 컨텍스트로 검사한다.
- B의 실제 MeetingNoteService에 A 실제 클라이언트를 넣어 초안 호출·결과 전달·실패 후 DRAFT 보존을 검사한다.
  DB 저장소와 HTTP 응답은 mock이므로 실제 DB/소켓 통합 테스트는 아니다.
- 실제 A FastAPI 엔드포인트도 인프로세스 TestClient로 확인했다. 백엔드와 같은 Person 예제로
  이름+이메일, 이름만, 이메일만, 미일치, 정보 부족의 5가지 응답이 JSON Schema를 통과했다.

전체 백엔드 단위 테스트: **158개 통과, 실패·오류·건너뜀 0개** (1번 127개에서 31개 추가).
