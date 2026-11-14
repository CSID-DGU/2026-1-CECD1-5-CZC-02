# A 인물 식별 API

2026-11-12. D-022 보호 규칙과 로지스틱 후보 보완을 사용하는 읽기 전용 API다. 인물 등록·병합·DB 저장은 하지 않는다.
현재 연결은 `hybrid.py`, 기존 규칙은 `engine.py`, HTTP 처리는 `api.py`에 있다. 모델 정보와 전환 방법은 [HYBRID.md](HYBRID.md)를 참고한다.

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
| CANDIDATES | 이름/별칭·연락처 또는 로지스틱 후보 검색에서 후보가 있으나 확정 조건을 만족하지 않음. 충돌·동명이인·이전 소속 일치 등 관측 근거를 reasons에 표시 |
| NEW | 현재 검색 방식으로 찾은 후보가 없음. 신규 등록을 자동 수행한다는 뜻이 아님 |

연락처만 있고 이름·소속이 없으면 없는 정보를 추측하지 않는다. 대장의 유일한 연락처와
정확히 일치하고 다른 충돌이 없을 때는 D-022에 따라 CONFIRMED가 가능하다.
공유 연락처나 이메일·전화의 대상 충돌은 CANDIDATES로 남긴다.
이름만 정확히 일치하거나 이름이 비슷한 것은 확정 근거가 아니다.
규칙 모드는 기존 유사도 기준 0.88을 유지한다. 혼합 모드는 이름 유사도를 포함한 14개 단서와 고정 로지스틱 기준 .02로 후보를 검색한다. 제목/직함만으로 인물을 확정하지 않는다.

규칙 확정·규칙 폴백은 score=null, decidedBy=RULE이다. 혼합 검색은 decidedBy=MODEL이며 후보 score는 별도 합성 보정 자료로 sigmoid 보정한 값이다(실제 업무 확률의 보장은 아님).
혼합 후보는 연락처 일치 → 정확 이름 일치 → 모델 점수 순이며, 동점은 personId 순으로 최대 3명이다. 더 있으면 reasons에 전체 후보 수와
인물 대장 직접 검색 안내를 넣는다. 신규 등록·후보 선택은 사람이 한다.
CONFIRMED도 회의록 확정 화면에서 한 번 더 확인해야 한다.

## 백엔드·화면 연결과 남은 경계

호출자가 사용자 범위의 인물 대장을 조회해 persons로 전달한다. AI가 예제/학습 대장을 자동으로
불러오거나 사용자 권한을 검증하지 않는다. 기존 서버의 내부 호출 경로로 사용한다.
A의 `PersonIdentityResolutionClient`는 `AI_MODULE_MODE=http`일 때 본인 대장 전체와
B가 전달한 name/email/channel/sourceRef를 보낸다. 응답의 원문·순서·대장 참조·후보 수를 검사한다.
2026-11-13에는 백엔드의 RULE 전용 수신 제한을 고쳐 MODEL 후보도 검증·저장하도록 했다.
MODEL 자동 확정·범위 밖 점수·모델 버전 누락·HUMAN 가장은 거절한다.

- 단서 없는 NEW는 화면에서 정보 부족으로 표시하며 임의 등록하지 않는다.
- A 후보 화면의 직접 검색·명시적 신규 등록·선택 저장은 구현했다.
- HUMAN 선택은 A 선택 저장소와 B-3 RevisionClient → D RevisionService를 같은 트랜잭션으로 저장한다.
  이 FastAPI 자체는 DB/사람 선택/Revision을 저장하지 않는다.
- B의 회의록 participants/perPerson 반영, 동명이인 다중 참석자·이메일만 있는 참석자 대응,
  Fake 클라이언트 정리는 B 검토 경계다. A에서 B 데이터를 직접 고치지 않는다.

현재 범위·검증은 [HANDOFF](../../ml/identity/HANDOFF.md)를 우선한다.

## 초기 API 변경 범위와 현재 검사

초기 API 등록 당시 **공용 파일 수정:** `ai-module/app/main.py`의 identity router import와 include_router 등록, 두 줄만 추가했다.
당시 contracts·다른 담당 폴더·백엔드·프론트·의존성 파일은 수정하지 않았다. 이후 A 백엔드·화면 구현은 위 현재 상태를 따른다.

ai-module에서 실행:

```bash
python -m pytest app/identity/test_engine.py app/identity/test_api.py -q
python -m pytest tests app ml -q
```

TestClient로 실제 main 라우트의 세 판정·배열 순서·오류 응답과 JSON Schema를 검사한다.
기존 학습의 AMBIGUOUS 64건은 정답 필드를 입력하지 않고 CANDIDATES·가능한 두 후보·이유를
반환하는지 회귀 검사한다. 이는 해당 사례의 회귀 검사이며 실사용 정확도 측정은 아니다.
새 holdout/v1은 성능 평가에 사용하지 않았다. 네트워크·LLM·BGE 모델 없이 검사한다.

2026-11-13 전체 검사 결과: **532 passed, 4 warnings**. 경고는 기존 FastAPI/Starlette 사용 중단 예고다.
