# A 인물 대장 백엔드 — 저장·조회·검색

`person` 패키지만 추가했다. `contracts/person.schema.json`과 D-008·D-022·D-025·D-026을 따른다.
기존 `customer`는 1학기 타임라인 기능으로 그대로 두며 데이터를 자동으로 복사하지 않는다.

## 후속 단계 현재 상태

2번의 A HTTP 식별 호출과 일반 이름 참석자의 기존 B 초안 흐름 연결까지 구현했다.
HTTP/mock 선택·검증 결과·B와 검토할 남은 제약은 [IDENTITY_CONNECTION.md](IDENTITY_CONNECTION.md)를 우선한다.
아래 직접 등록·조회·검색 API는 그대로다. B의 가짜 구현 삭제·이메일만 있는 참석자/동명이인 다중 참석자의
정상 연결·후보 선택 화면은 완료한 것으로 표시하지 않는다.

3번의 A 후보 화면·HUMAN 선택 저장·D 수정 기록 연결도 구현했다.
[IDENTITY_SELECTION.md](IDENTITY_SELECTION.md)의 현재 상태와 B 반영 한계를 함께 확인한다.

## API

기존 JWT 인증을 사용한다. 응답은 기존 `ApiResponse` 봉투이고 `data` 안의 인물은 Person 계약을 따른다.
조회·검색은 원본 입력을 바꾸거나 새 인물을 만들지 않는다.

| 요청 | 기능 |
| --- | --- |
| `POST /api/persons` | 사용자가 직접 등록 요청한 인물을 저장하고 `data`에 Person 반환 |
| `GET /api/persons?q=가상&page=0&size=50` | 본인 대장 목록·검색. `data`는 Person 배열 |
| `GET /api/persons/p_17` | 본인 인물 한 명 조회 |

- 등록 성공·조회 성공은 기존 백엔드와 같은 200이다. 형식 오류는 400, 미인증은 401이다.
- 다른 사용자의 인물과 존재하지 않는 인물은 모두 404로 처리하여 존재 여부를 노출하지 않는다.
- 검색은 대표 이름·별칭·현재/이전 회사·이메일·전화의 부분 문자열이다. 유사 인물 판정은 하지 않는다.
- 검색용 사본만 NFKC·소문자·공백 정리하며 저장·응답의 원문은 보존한다.
- `%`·`_`·`!`는 검색 패턴 제어 문자가 아니라 문자 자체로 검색한다. SQL에는 바인딩 파라미터를 쓴다.
- 검색어 최대 200자, page 0~1,000,000, size 1~100(기본 50), 최신 ID 순이다.
  q가 없거나 공백이면 본인 목록이다. 다음 페이지는 page를 하나씩 올려 조회한다.
  총 개수·페이지 메타데이터는 반환하지 않는다. 전체 대장을 한 페이지로 가정하지 않는다.

등록 요청 예(가짜 데이터):

```json
{
  "name": "가상인물_0001",
  "nameVariants": ["Synthetic Person 0001"],
  "company": "가상회사_001",
  "department": null,
  "title": "부장",
  "emails": ["synthetic@people.example"],
  "phones": ["000-0000-0001"],
  "channels": ["EMAIL", "MEETING"],
  "companyHistory": [{"company": "가상회사_이전", "title": "과장", "until": "2025-12-31"}],
  "photoAvailable": false,
  "isSynthetic": true
}
```

이름·isSynthetic은 필수다. personId는 DB 저장 뒤 `p_` + 숫자 PK로 서버가 생성한다.
클라이언트의 personId·소유자·추가 필드는 거절한다. 문자열/boolean을 다른 타입에서 자동 변환하지 않는다.
회사·부서·직함 등 모르는 문자열은 null, 빠진 배열은 []다. 배열을 명시적으로 null로 보내면 거절한다.
사진 보유 여부를 모르면 photoAvailable을 생략한다. false로 추측하지 않고 출력에서도 생략한다.
과거 이력의 until은 null 또는 유효한 YYYY-MM-DD다. 요청은 JSON 표현 기준 64,000자 이하다.
대표 이름은 호칭을 제외해서 입력해야 한다. 서버가 이름이나 영문 별칭을 추측해 보충하지 않는다.

## 저장·접근 제어

- A 전용 `person_entries` 테이블을 사용한다. 사용자 FK와 `(user_id, id)` 인덱스를 둔다.
  인물 필드는 JSON 문자열, 검색용 필드는 별도 내부 사본으로 저장한다.
- 기존 JPA 설정 `ddl-auto=update`에 따라 백엔드를 실행할 때 새 테이블이 생긴다.
  이번 개발·단위 테스트에서는 실제 DB를 변경하거나 가짜 대장을 자동 등록하지 않았다.
- 같은 이름·이메일·전화라도 다른 사람이거나 공유 연락처일 수 있으므로 고유 제약·자동 병합을 두지 않는다.
- 소유자는 요청 본문이나 query에서 받지 않고 `CustomUserPrincipal.getUserId()`만 사용한다.
  데모 모드도 기존과 같이 데모 사용자 범위로 분리된다.
- 직접 등록한 실제 사용자(`getOwnerUserId()`)와 KST 시각을 createdBy/createdAt에 감사 정보로 저장한다.
  이 필드는 외부 Person 계약에 넣지 않는다. 직접 등록은 AI 제안에 대한 선택이 아니므로
  RULE 등의 가짜 제안자를 만들어 revision을 기록하지 않는다(D-026).

## 인물 대장 API 범위와 이후 연결

이 문서의 인물 대장 API는 직접 등록·조회·검색 범위다. 별도의 식별·선택 기능은 위 연결 문서를 따른다.
Person 수정·삭제·병합·AI 자동 등록 API는 없다.
새 사람이 자동으로 생성되거나 동명이인이 자동으로 합쳐지지 않는다.

- 후속 단계에서 사용자 범위 대장을 읽어 `/identity/resolve`에 전달하는 실제 백엔드 클라이언트를 구현했다. 위 연결 문서 참고.
- B의 `IdentityResolutionClient`·Fake 구현과 확인 화면은 수정하지 않았다.
- AI 제안을 보고 신규 등록/후보 선택하는 흐름은 사람의 결정과 D의 Revision 저장을 같은 흐름으로
  연결해야 한다. 이번 직접 등록 API만 호출하고 후보 선택이 완료됐다고 처리해서는 안 된다.
- 향후 병합·되돌리기는 사용자 결정, 병합 전 상태 보존, 감사 기록이 필요하다(D-025·D-026).
- 기존 회의록·할 일 데이터에서 사람별 이력을 조회하는 기능은 별도 연결 작업이다.
- 공용 설정·의존성·계약·다른 담당 폴더는 수정하지 않았다. API 명세 공용 문서는 이 내용을 팀 리뷰 뒤 반영하도록 제안한다.

## 검사

backend에서 Java 17로 실행한다.

```bash
bash ./gradlew test --tests 'com.salesmap.backend.person.*Test'
bash ./gradlew test --tests '*Test'
```

1번 초기 검사 38개: 계약 출력·엄격한 입력·소유자 제한·인증 없음·검색 패턴·페이지 제한·중복 인물 보존·
감사 정보·Hibernate 엔티티/검색 HQL 매핑을 검사한다. 기존 B의 테스트용 계약 검사기는 import해서 읽기만 했다.
위 단위 검사는 MySQL 없이 실행한다. 별도 실제 MySQL 저장·조회·선택·롤백 검사 8개도 통과했다.
재현·한계는 [MYSQL_VERIFICATION.md](MYSQL_VERIFICATION.md)를 참고한다. HTTP JWT 통합은 아직 검증하지 않았다.
보안 필터는 기존 SecurityConfig의 anyRequest().authenticated()를 그대로 사용한다.

## 2026-11-12 사람별 이력 조회 추가

GET `/api/persons/{personId}/meetings?page=0&size=20`은 본인 인물·본인 원본·미보관·미삭제·확정 회의록 중 저장된 personId가 정확히 연결된 기록만 반환한다. data는 기존 meeting_note 계약 배열이다. 이름 또는 A의 미반영 선택으로 이력을 추정하지 않는다. 미팅 시각 최신순(미기록은 마지막), ID 역순으로 정렬한 뒤 페이지를 적용한다. size는 1~100이다. B 저장소/조회 서비스는 읽기만 하며 B 데이터는 갱신하지 않는다.

사람 화면 접속·정확한 한계는 [PEOPLE_SCREEN.md](../../../../../../../../frontend/src/components/identity/PEOPLE_SCREEN.md)를 참고한다. 전체 본인 회의록을 먼저 읽는 초기 방식이라 대량 데이터에서는 쿼리 최적화가 필요하다.
