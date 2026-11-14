# A 사람의 인물 선택·수정 기록

## 저장 경계

`PersonIdentityResolutionClient`가 검사한 RULE/MODEL 제안을 A의 `person_identity_resolutions`에 저장한다.
이 테이블은 원본 제안·현재 결과·원본 ID·언급 순서·소유자·버전·선택자/시각을 가진다.
B의 원본/회의록 테이블은 소유자와 상태 확인을 위해 읽기만 하며 수정하지 않는다.
기존 JPA `ddl-auto=update`로 백엔드 실행 시 A 테이블이 생성된다. 별도 검증 DB의 결과는 [MYSQL_VERIFICATION.md](MYSQL_VERIFICATION.md)를 참고한다. 기존 앱 DB는 변경하지 않았다.

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

## 남은 B 협의

**A 저장 성공은 B 회의록 참석자 반영 성공을 의미하지 않는다.**
B가 소유한 participants/perPerson 변경, 대응 방식, C 할 일에 보이는 상대방, 저장 후 확인 화면 갱신과
회의록 확정 전에 확인할 조건은 B 담당과 연결해야 한다. 이번에는 다른 담당의 DB 필드를 A에서 수정하지 않았다.
B의 이름 기반 연결 제한과 가짜 구현 정리는 IDENTITY_CONNECTION.md를 참고한다.

선택 화면은 D-017의 A 자리인 `frontend/src/components/minutes/ParticipantCandidatesPanel.jsx`에서
`frontend/src/components/identity/`를 사용하도록 바꿨다. B의 화면 틀·확정 로직·C 패널은 그대로다.

## 검사와 한계

backend의 `bash ./gradlew test --tests '*Test'`: **181개 통과**, 실패·오류·건너뜀 0개.
계약 출력·권한·버전 충돌·같은 원본 확인·재조회·명시적 선택 변경·기록 실패 시 트랜잭션 롤백을 검사했다.
JPA 엔티티/잠금 HQL 매핑도 DB 연결 없이 검사한다. 추가로 격리된 실제 MySQL에서 커밋·롤백·동시 선택 등 8개를 통과했다.
상세 범위와 재현 방법은 [MYSQL_VERIFICATION.md](MYSQL_VERIFICATION.md)를 참고한다. 2026-11-13에는 실제 JWT 필터→A 컨트롤러→격리 MySQL까지 검증했다. 브라우저 클릭은 미검증이다.
기존 DB의 D 수정 기록 조건은 [D README](../revision/README.md)를 확인해야 한다.
프론트는 12개 검사·A 영역 ESLint·전체 빌드를 통과했다. 유료 API·Ollama·실제 데이터는 사용하지 않았다.
공용 계약·설정·의존성·CI 파일은 수정하지 않았다.
