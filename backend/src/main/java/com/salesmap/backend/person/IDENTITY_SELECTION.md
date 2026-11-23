# A 사람의 인물 선택·수정 기록

## 저장 경계

`PersonIdentityResolutionClient`가 검사한 RULE/MODEL 제안을 A의 `person_identity_resolutions`에 저장한다.
이 테이블은 원본 제안·현재 결과·원본 ID·언급 순서·소유자·버전·선택자/시각을 가진다.
B의 원본/회의록 테이블은 소유자와 상태 확인을 위해 읽기만 하며 수정하지 않는다.
기존 JPA `ddl-auto=update`로 백엔드 실행 시 A 테이블이 생성된다. MySQL 검증은 앱 데이터와 분리된 새 임시 DB에서 수행한다. 준비·정리 방식은 [MYSQL_VERIFICATION.md](MYSQL_VERIFICATION.md)를 참고한다.

- 새 저장 결과 ID는 D-008의 `ir_<DB 숫자 PK>`다. DB 컬럼·제안 JSON·현재 JSON·B로 반환하는 응답을 같은 ID로 맞춘다. AI의 저장 전 임시 ID는 회의록으로 넘기지 않는다.
- PK 발급과 ID 교체는 한 트랜잭션이다. 이전 UUID ID의 기존 결과는 회의록·수정 기록 참조 보존을 위해 바꾸지 않으며, 재실행·조회·선택도 기존 ID를 유지한다.
- ETag는 저장 버전이므로 시작 번호를 가정하지 않고 조회·선택 응답의 값을 그대로 사용한다.
- 동일 사용자·원본·언급 순서 중복을 DB 제약으로 막는다.
- 같은 단서로 다시 호출해도 원본과 HUMAN 결정을 덮어쓰지 않는다. 단서가 다르면 조용히 덮어쓰지 않고 실패한다.
- UI는 원본 제안을 업로드하거나 제안자를 지정할 수 없다. 실제 A API에서 검사된 제안만 출발점으로 쓴다.

## 선택 API

- GET `/api/person-identities/for-note/{noteId}` → 기존 ApiResponse 봉투의 data는
  `[{ "resolution": {기존 identity_resolution 계약}, "etag": "\"irv-0\"" }]`다.
  etag는 HTTP 편집 메타데이터이며 계약 객체에 필드를 추가한 것이 아니다.
- POST `/api/person-identities/for-note/{noteId}/{resolutionId}/selection`
  - `If-Match` 필수. `{personId}` 또는 `{newPerson}` 중 하나만 받는다.
  - 사용자·원본·인물 소유자를 서버가 검사한다. 다른 사용자의 노트/결과/인물과 다른 원본의 결과는 거절한다.
  - 확정·보관한 노트는 선택을 변경할 수 없다. 목록은 읽을 수 있다.
  - 저장 결과는 기존 계약대로 CONFIRMED·personId·decidedBy=HUMAN, 후보 score=null이다.
  - 원본 AI 제안은 그대로 둔다. 최초 후보 선택은 SELECTED_CANDIDATE, 직접 검색 선택은 EDITED,
    기존 확정값 승인은 ACCEPTED, 신규 등록은 CREATED_NEW다. 사람이 선택을 변경하면 EDITED다.
  - 동일 인물을 다시 저장하면 상태를 바꾸거나 중복 기록을 만들지 않는다.

선택은 본인 결과에 대한 비관적 잠금 + 버전 검사로 처리한다. 오래된 요청/중복 클릭은 사람/기록을
추가 생성하기 전에 409로 거절한다. 다른 화면의 값을 자동으로 덮어쓰지 않는다.

선택·새 Person 생성·D 수정 기록은 하나의 Spring 트랜잭션이다. D 호출은 기존 B-3 RevisionClient
→ RevisionServiceClient → D RevisionService를 사용한다. B·D 파일은 수정하지 않았다.
수정 기록이 실패하면 트랜잭션이 롤백되며 성공 응답을 보내지 않는다.
`screen=IDENTITY_CANDIDATE`, `targetType=IDENTITY_RESOLUTION`, `fieldName=personId`,
proposedBy/modelVersion은 저장된 원본 제안에서 가져온다. 제안자를 HUMAN으로 꾸미지 않는다.
선택자의 실제 로그인 ID는 A의 감사 필드에도 저장한다. D의 actorId는 기존 인터페이스처럼 데이터 소유 사용자로 기록된다.

## B 회의록에 반영되는 시점

현재 B의 `MeetingNoteService.confirmMeetingNote`는 **확정 직전에** `applyIdentityDecisions`를 실행한다. B 연결 자체를 미구현 상태로 보지 않는다.

1. 초안 생성에서 A의 저장된 식별 결과를 B로 전달하고 참석자의 `resolutionId`를 연결한다. 새 저장 결과는 DB 기반 ID를 사용한다.
2. 후보 선택 저장은 A의 현재 결과를 HUMAN/CONFIRMED로 바꾸고 수정 기록을 남긴다. **이 시점에 B 초안의 participants/perPerson을 즉시 다시 쓰지는 않는다.** 후보 영역은 저장한 선택을 표시한다.
3. 회의록을 확정하면 B의 `PersonIdentityDecisionReader`가 같은 사용자·원본에 저장된 CONFIRMED 결과를 읽는다. `resolutionId → personId`로 참석자를 갱신하고, LIGHT의 사람별 칸에도 연결한 ID를 반영한다. 선택하지 않았거나 대응하는 resolutionId가 없으면 새 ID를 추측하지 않는다.
4. 확정 회의록은 A 사람 화면의 이력 조회 대상이 된다. A 선택 저장만 성공한 DRAFT가 사람의 확정 미팅 이력에 나타나는 것은 아니다.

관련 코드: B의 `MeetingNoteService.applyIdentityDecisions`, `PersonIdentityDecisionReader`. FULL/LIGHT 확정 반영과 무관한 resolutionId 제외는 `MeetingNoteServiceTest`에서 검사한다. 이번 문서 정리에서 B 코드는 변경하지 않았다.

선택 화면은 D-017의 A 영역인 `frontend/src/components/minutes/ParticipantCandidatesPanel.jsx`가 `frontend/src/components/identity/`를 사용한다.

## 실제로 남은 연결·제한

- 초안에서 참석자와 A 결과를 처음 대응시키는 과정은 여전히 이름 기반이다. 동명이인 여러 명·이메일만 있는 참석자는 모든 경우의 정확한 연결을 보장하지 않는다. 자세히 [IDENTITY_CONNECTION.md](IDENTITY_CONNECTION.md).
- LIGHT 사람별 칸 반영에는 표시 이름 대응이 남아 있다. 같은 이름의 여러 참석자 구분을 해결했다고 보지 않는다.
- A 선택 저장으로 이미 만들어진 C 할 일의 상대방을 즉시 바꾸는 흐름은 이 선택 API의 범위에 없다. A 사람 화면의 인물별 할 일 탭·D 미팅 준비 화면 연결도 별도 작업이다.
- 실제 브라우저에서 메모 입력 → 후보 선택 → 회의록 확정 → 사람 이력까지 전 과정을 한 번에 확인하는 검증은 남아 있다. 단위 테스트, 격리 DB 검사, 가짜 API를 이용한 병합 화면 검사를 이 전체 흐름의 검증으로 대신 주장하지 않는다.

## 인물 병합과 감사 기록

사람 화면의 수동 병합·되돌리기는 구현했다. D-026에 따라 AI 학습용 revision 대신 병합 전 정보와 실제 사용자·시각을 A 감사 테이블에 남긴다. 기존 회의록의 personId를 덮어쓰지 않고, A 이력 조회에서 명시적으로 병합한 ID 집합을 사용한다. API·보존·되돌리기 제한은 [PERSON_MERGE.md](PERSON_MERGE.md)를 참고한다.

## 검사와 한계

2026-11-22 로컬 1~3번 작업 완료 시 확인한 결과:

- 백엔드 전체 단위 테스트 `bash ./gradlew test --tests '*Test'`: **225개 통과**, 실패·오류·건너뜀 0개.
- 별도 임시 MySQL: **16개 통과**. JWT/소유자, 저장 ID와 DB PK 일치, 선택·재조회, 동시 요청, 병합·되돌리기, 감사 저장 실패 롤백, 회의록 원래 ID 보존을 검사했다.
- A 프론트 `node --test src/components/identity/*.test.mjs`: **28개 통과**. 변경한 A 코드 ESLint와 전체 프론트 빌드도 통과했다.
- 브라우저에서는 실제 PeoplePage에 가짜 API를 연결해 병합 전 확인·병합·되돌리기·감사 표시를 검사했다. 실제 백엔드까지 연결한 전체 회의록 흐름의 브라우저 검증은 아니다.

원본·권한·계약 검사와 이전 UUID 결과의 조회/선택 호환은 유지한다. 유료 API·실제 고객 데이터는 사용하지 않았다. D 수정 기록의 기존 DB 조건은 [D README](../revision/README.md)를 참고한다. 공용 계약·설정·의존성·CI 파일은 변경하지 않았다.
