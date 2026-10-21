# A — 인물 비교 쌍 교차 검수 반영

2026-10-20. 총괄이 지적한 두 유형을 기존 seed 42 자료에서 재현하고 정답을 수정했다.
이번 범위는 데이터·생성기·실험 채점·회귀 검사다. 식별 API나 화면은 아직 수정하지 않았다.
contracts·공용 파일·다른 담당 폴더는 변경하지 않았다.

## 수정 내용

| 생성 유형 | 수정 전 | 수정 후 | 학습 / 평가 |
| --- | --- | --- | ---: |
| same_name의 소속/직함 이력 중복 | 다른 사람 0 | label=null, AMBIGUOUS | 53 / 0 |
| missing_company의 동명이인 | 같은 사람 1 | label=null, AMBIGUOUS | 11 / 0 |

53건은 모두 같은 출처 인물 쌍 p_016 / p_015의 반복이다.
관측 이름이 같고 B의 회사·직함이 A의 이전 이력과 같아, 단일 정답을 강제할 수 없다.
11건은 대장에 동명이인이 있고 B 언급의 회사가 없어 누군지 특정할 수 없다.
주어진 직함만으로 회사가 없는 동명이인을 제외하지 않는다.

인물 300명·분할·평가 600쌍 JSON은 바이트 그대로 유지했다.
학습 2,400쌍도 순서·ID·이름·소속·연락처·원문 근거를 유지하고 64쌍의 label과 기대 정답만 바꿨다.
생성기를 다시 실행하거나 다른 seed로 만들 때도 두 검수 유형의 조건을 다시 검사한다.
연락처가 실제로 특정 인물을 구분하면 애매한 이름만으로 AMBIGUOUS를 붙이지 않는다.
이 규칙은 이번 검수 대상 두 유형에 적용한 것으로 모든 잠재적 모호함을 검수했다는 뜻은 아니다.

현재 label 분포:

| 분할 | 같은 사람 1 | 다른 사람 0 | 애매함 null | 합계 |
| --- | ---: | ---: | ---: | ---: |
| train | 1,189 | 1,147 | 64 | 2,400 |
| eval | 300 | 300 | 0 | 600 |

## 실험 내부 정답 형식

기존 모듈 간 계약을 바꾼 것이 아니다. pair/split은 ML-1 실험 내부 형식이다.
AMBIGUOUS는 holdout의 정답 종류를 따르며 외부 API의 새로운 decision이 아니다.

```json
{
  "label": null,
  "expected": {
    "kind": "AMBIGUOUS",
    "targetPersonIds": ["p_015", "p_016"],
    "relatedPersonIds": ["p_015", "p_016"],
    "reason": "HOMONYM_WITH_HISTORY_OVERLAP"
  }
}
```

missing_company의 reason은 HOMONYM_WITHOUT_COMPANY다.
targetPersonIds는 주어진 단서로 가능한 인물 집합, relatedPersonIds와 기존 personIdA/B는 생성 출처다.
missing_company는 출처 ID가 하나여도 가능한 인물은 둘일 수 있다.
**생성 출처를 안다는 사실과 모델이 주어진 정보로 식별할 수 있다는 사실은 다르다.**
label=null을 0으로 변환하지 않는다. 이진 분류 학습·정밀도·재현율 계산에서는 제외하고
사용자 확인이 필요한 사례로 별도 집계한다. 이진 학습 입력은 `pair["label"] is not None`으로 거른다.
label·expected·출처 ID·caseType·evidence·sourceRef는 모델 비교 특성으로 넣지 않는다.

## 평가 처리

- 일반 검색 지표는 단일 정답이 가능한 학습 2,336건과 기존 평가 600건을 대상으로 한다.
- 학습의 64건은 ambiguity 항목에 별도 보고한다. 가능한 두 인물 모두 후보에 남는지 확인한다.
- check_ambiguous_response(pair, response)로 향후 API 응답을 채점할 수 있다.
  CANDIDATES + personId=null + 가능한 두 후보 + 각 후보의 비어 있지 않은 reasons면 정답이다.
  CONFIRMED/NEW, 단일 후보만 반환, 엉뚱한 후보, 중복 후보, 이유 누락은 오답이다.
- 가능한 정답이 3명을 넘으면 가능한 3명을 제시하고 이유에 추가 검색 안내가 있어야 한다.
- 전체 계약 준수 여부는 API 응답 형식 테스트에서 별도로 확인해야 한다.
- 현재 평가 실행은 후보 검색만 수행했다. 실제 API가 없으므로 responsesEvaluated=0,
  responseAccuracy=null이다. 실제 CANDIDATES 응답 정확도를 측정했다고 보고하지 않는다.

새 결과의 학습 후보 집합 검사에서는 64건 모두 가능한 두 인물이 전체 후보와 첫 3명 안에 남았다.
유사도 기준은 다시 선택해도 0.88로 유지됐으며, 실서비스 판정 기준을 바꾼 것은 아니다.
단일 정답 학습 건수와 정밀도 분모가 바뀌었으므로 이전 지표와 직접 비교해 모델 개선이라고 하지 않는다.
평가 쌍은 변경하지 않았고 기존 평가의 검색 결과도 같다.

## 재현·검사

ai-module/에서 실행한다. 기존 data/ 재생성은 JSON 4개를 덮어쓰므로 필요할 때만 한다.

```bash
python -m ml.identity.generate_dataset
python -m ml.identity.evaluate_candidates
python -m ml.identity.analyze_failures
python -m pytest tests app ml -q
```

candidate_evaluation.json과 failure_analysis.json은 수정된 정답으로 재생성했다.
embedding_comparison.json과 candidate_benchmark.json은 이전 입력 파일 해시를 가진 과거 기록이다.
이번에 임베딩이나 속도를 재측정하지 않았고 과거 보고서의 해시를 새 값으로 바꾸지 않았다.
기존 수치와 실행 환경은 각 보고서의 입력 해시 기준으로 해석한다.

검사: **371 passed, 5 warnings**. 경고는 기존 라이브러리 사용 중단 예고다.
64건 재현·분할 유지·거짓 0/1 정답 거절·정답 집합 참조·재생성·다른 seed·연락처 구분·
API 채점 조건·애매함의 이진 지표 제외를 검사했다.
새 holdout/v1은 그대로 보존했으며 성능 평가에 사용하지 않았다.
파일 입출력은 UTF-8, 생성 JSON 줄바꿈은 LF로 유지했다.
커밋·push는 하지 않았다.

## 후속 API 작업 반영

같은 날 후속 단계에서 [식별 API](../../app/identity/API.md)를 구현했다. 아래는 데이터 수정 단계의
요구 사항이다. test_engine.py가 관측 단서만으로 64건 모두 CANDIDATES·가능한 두 후보·이유를
반환하는지 검사한다. 저장된 candidate_evaluation.json은 앞선 검색 평가 기록으로 유지했으므로
responsesEvaluated=0/responseAccuracy=null은 그대로다. 회귀 검사와 성능 지표를 섞지 않는다.

## API 요구 사항

D-022에 따라 위 사례를 CANDIDATES와 후보별 reasons로 반환해야 한다.
원문 관측 단서로 판단해야 하며 실험의 expected나 출처 ID를 읽어 판정해서는 안 된다.
화면에서는 이 중 누구인지 확인하고 직접 검색·새 사람 등록으로 이어진다.
이 문서는 데이터 수정 결과다. API 구현은 후속 절과 API.md를 참고하며 화면은 아직 미구현이다.
