# A 인물 식별 API

2026-10-20. D-022를 반영한 읽기 전용 규칙 API다. 인물 등록·병합·DB 저장은 하지 않는다.
구현은 `engine.py`, HTTP 요청 처리는 `api.py`에 있다.

## 요청·응답

`POST /identity/resolve`에 JSON을 보낸다. `mentions`와 `persons`는 모두 필수 배열이다.
각 언급은 `contracts/identity_resolution.schema.json`의 mention, 각 인물은
`contracts/person.schema.json`을 따른다. 추가 필드나 잘못된 형식·중복 personId는 422로 거절한다.
빈 mentions는 빈 결과를 반환한다. 빈 persons는 모든 언급에 NEW를 반환한다.
실험 정답(label·expected·출처 인물 ID)은 요청에 넣지 않는다.

가짜 입력 예:

```json
{
  "mentions": [{
    "name": "가상인물_0001", "company": "가상회사_001",
    "email": "synthetic@people.example", "channel": "MEETING", "sourceRef": "src_demo"
  }],
  "persons": [{
    "personId": "p_demo", "name": "가상인물_0001", "company": "가상회사_001",
    "emails": ["synthetic@people.example"], "isSynthetic": true
  }]
}
```

응답은 입력 언급과 같은 순서의 `identity_resolution` **배열**이다.
각 결과를 실제 계약 스키마로 검사한 뒤 반환한다. 위 예는 CONFIRMED, personId=p_demo이며
후보 score=null과 이름·이메일·소속 일치 이유를 포함한다.
원문 mention은 그대로 보존하고 resolutionId는 호출마다 새 UUID 기반 ID를 만든다.
같은 요청을 다시 보내도 ID가 같다고 가정하지 않는다. sourceRef는 원문 추적용이다.

## 판정

| 결과 | 조건·표시 |
| --- | --- |
| CONFIRMED | 이메일 또는 전화가 정확히 일치하는 인물이 유일하고 이름·현재/이전 소속 충돌, 공유 연락처 경고가 없음. 함께 제공된 다른 연락처의 불일치·미지원 표기도 없어야 함 |
| CANDIDATES | 이름/별칭·연락처 또는 기존 유사 이름 검색에서 후보가 있으나 확정 조건을 만족하지 않음. 충돌·동명이인·이전 소속 일치 등 관측 근거를 reasons에 표시 |
| NEW | 기존 검색 규칙으로 찾은 후보가 없음. 신규 등록을 자동 수행한다는 뜻이 아님 |

연락처만 있고 이름·소속이 없으면 없는 정보를 추측하지 않는다. 대장의 유일한 연락처와
정확히 일치하고 다른 충돌이 없을 때는 D-022에 따라 CONFIRMED가 가능하다.
공유 연락처나 이메일·전화의 대상 충돌은 CANDIDATES로 남긴다.
이름만 정확히 일치하거나 이름이 비슷한 것은 확정 근거가 아니다.
기존 학습에서 정한 유사도 기준 0.88을 유지한다. 제목/직함만으로 인물을 확정하지 않는다.

모든 후보 score는 null이고 decidedBy는 RULE이다. 내부 순위를 확률로 내보내지 않는다.
후보는 기존 순위·동점의 personId 순으로 최대 3명이다. 더 있으면 reasons에 전체 후보 수와
인물 대장 직접 검색 안내를 넣는다. 신규 등록·후보 선택은 사람이 한다.
CONFIRMED도 회의록 확정 화면에서 한 번 더 확인해야 한다.

## B 연결과 화면의 다음 작업

호출자가 사용자 범위의 인물 대장을 조회해 persons로 전달한다. AI가 예제/학습 대장을 자동으로
불러오거나 사용자 권한을 검증하지 않는다. 기존 서버의 내부 호출 경로로 사용한다.
현재 B의 IdentityResolutionClient는 sourceId·attendees·userId를 받으므로, 실제 연결 때
참석자 이름을 mention으로 만들고 채널·sourceRef·해당 사용자 대장을 전달하는 어댑터가 필요하다.
이 요청 봉투는 이번 AI API의 인터페이스이며 백엔드 연결이 완료됐다는 뜻은 아니다.

- 이름·이메일·전화가 모두 없어서 NEW이면 화면은 “정보 부족 — 확인 필요”로 보여야 한다.
  내부 `has_identity_clue(mention)`과 같은 정규화 규칙으로 판별하며 계약 필드는 추가하지 않는다.
- 후보 목록·이유·직접 검색·새 사람 등록 버튼은 참석자 후보 화면 작업에서 연결한다.
- 사람이 고르면 HUMAN 결과와 수정 기록을 B-3 RevisionClient → D API로 전달해야 한다.
  이번 API는 사람 선택/Revision 저장을 구현하지 않는다.
- 백엔드 실제 클라이언트 구현·Fake 삭제는 10/28 연결 범위다. 이번에는 변경하지 않았다.

## 변경 범위·검사

**공용 파일 수정:** `ai-module/app/main.py`의 identity router import와 include_router 등록, 두 줄만 추가했다.
contracts·다른 담당 폴더·백엔드·프론트·의존성 파일은 수정하지 않았다.

ai-module에서 실행:

```bash
python -m pytest app/identity/test_engine.py app/identity/test_api.py -q
python -m pytest tests app ml -q
```

TestClient로 실제 main 라우트의 세 판정·배열 순서·오류 응답과 JSON Schema를 검사한다.
기존 학습의 AMBIGUOUS 64건은 정답 필드를 입력하지 않고 CANDIDATES·가능한 두 후보·이유를
반환하는지 회귀 검사한다. 이는 해당 사례의 회귀 검사이며 실사용 정확도 측정은 아니다.
새 holdout/v1은 성능 평가에 사용하지 않았다. 네트워크·LLM·BGE 모델 없이 검사한다.

전체 검사 결과: **406 passed, 5 warnings**. 경고는 기존 FastAPI/Starlette 사용 중단 예고다.
