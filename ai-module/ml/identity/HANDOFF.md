# A 인물 식별 — 검토·인수인계

작성: 2026-10-15. 상태: **독립 작업 1~4 완료 / 팀 검토 전 / API·화면·백엔드 미연결**.
독립 작업 완료가 A 기능 전체나 10/23 통합 기준 모델의 완료를 뜻하지는 않는다.
아래는 팀 검토용 정리이며 팀 합의나 다른 담당자의 승인을 대신하지 않는다.

## 2026-10-20 후속 작업 — 현재 상태

위 10/15 상태와 아래 실험·검토 기록은 당시 결과다. 현재는 PR #22/#23과 D-021/D-022를 반영했다.

- 학습의 애매한 정답 64건을 수정했다. [DATA_REVIEW.md](DATA_REVIEW.md) 참고.
- `POST /identity/resolve`를 구현했다. 요청·판정·연결 제약은 [API 설명](../../app/identity/API.md) 참고.
- D-022의 CONFIRMED/CANDIDATES/NEW, RULE score=null, 후보 최대 3명·reasons를 반환한다.
- 64건 모두 정답 정보를 입력하지 않고 CANDIDATES·가능한 두 후보·이유를 반환하는 회귀 검사를 통과했다.
- **공용 파일 수정:** app/main.py의 라우터 import·등록 두 줄만 추가했다.
  contracts·다른 담당 폴더·백엔드·프론트·공용 CI는 수정하지 않았다.
- D-021에 따라 기존 공용 CI는 이미 `python -m pytest tests app ml`로 담당 폴더까지 검사한다.
- 남은 작업: 사용자 범위 대장 조회를 포함한 B 백엔드 실제 클라이언트 연결(10/28),
  참석자 후보 화면·직접 검색·신규 등록·사람 선택 후 HUMAN/Revision 연결.
- 전체 검사: **406 passed, 5 warnings** (`python -m pytest tests app ml -q`).
- 새 holdout은 미사용이며 BGE-M3는 기본값에 적용하지 않았다. 이번 작업은 커밋·push하지 않았다.

## 먼저 볼 내용

- 내부 비교·후보 검색·순위·검사는 구현했다. 확률 점수·최종 판정은 아직 없다.
- 기본 규칙을 유지한다. BGE-M3는 실제 비교했으나 기존 eval에서 개선이 없어 선택 실험으로 둔다.
- 기존 평가 600건에서 정답 누락 0건이지만 150건에 정답 외 후보가 남았다. 자동 확정 근거가 아니다.
- 새 검증 자료는 생성·형식 검사만 했다. 검색·임베딩 평가에는 아직 사용하지 않았다.
- 현재 로컬 검증은 **256 passed, 5 warnings**. 경고는 기존 FastAPI/Starlette 사용 중단 예고다.
- 이번 작업 범위는 app/identity/·ml/identity/다. contracts·공용 라우트·B/C/D·백엔드·프론트는 수정하지 않았다.

권장 읽기: 이 문서 → [규칙/구현 설명](../../app/identity/RULES.md) →
[실패·속도 분석](ANALYSIS.md) → [임베딩 실험](EMBEDDINGS.md) → [새 검증 자료](holdout/README.md).
데이터 생성·분할의 상세 설명은 [README](README.md)에 있다.

## 완료 범위와 파일

| 부분 | 완료한 내용 | 주요 파일 |
| --- | --- | --- |
| 기존 합성 데이터 | 인물 300명, 학습 2,400쌍/평가 600쌍, 인물·동일 이름 그룹 분리 | generate_dataset.py, data/ |
| 표기 정리 | 이름·호칭·공백·이메일 도메인·전화 구분자, 원문 보존 | app/identity/normalization.py |
| 단서 비교 | 이름/별칭·연락처·현재/이전 회사, 공유 연락처·충돌 | app/identity/features.py |
| 문자열 유사도 | 문자 삽입/삭제/교체 거리, 별칭별 원문과 비교 경로 | app/identity/similarity.py |
| 내부 후보 | 정확 일치 선별, 회사 조건을 둔 유사 이름 검색, 순위·동점 보존 | app/identity/candidates.py, ranking.py |
| 기존 자료 평가 | 학습에서만 기준 선택, 고정된 기준으로 기존 eval 측정 | evaluate_candidates.py |
| 독립 작업 1 | 별도 대장 120명·대장 밖 신규 20명·15유형 언급 300건 | generate_holdout.py, holdout/v1/ |
| 독립 작업 2 | 로컬 BGE-M3 비교, 모델 부재 시 규칙 평가, 기본 적용 안 함 | app/identity/embedding.py, compare_embeddings.py |
| 독립 작업 3 | 누락·추가 후보 원인 예시, 60/120/300/1,000명 규칙 검색 시간 | analyze_failures.py, benchmark_candidates.py |
| 독립 작업 4 | 실행·검토 요청·한계·인수인계 정리 | 이 문서 |

app/identity/로 시작하는 표의 경로는 ai-module/ 기준, 나머지는 ml/identity/ 기준이다.
핵심 코드의 테스트는 app/identity/test_*.py, 데이터·실험의 테스트는 ml/identity/test_*.py에 있다.

## 재현 방법

레포 루트의 ai-module/에서 설치된 Python 환경을 활성화한다.
최소 테스트 의존성은 기존 requirements-test.txt다. 새 환경이 필요할 때만 다음을 실행한다.

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements-test.txt
```

기본 테스트는 실제 모델·유료 API·네트워크 없이 실행된다. 초기 패키지 설치는 별도다.
검증 당시 새 저장소에 .venv를 만들지 않고 이미 설치된 테스트용 Python을 사용했다.

```bash
python -m pytest tests ml/identity app/identity -q
```

10/15 당시 CI에는 A 폴더 테스트가 빠져 있었다. 현재는 D-021/PR #22로
`python -m pytest tests app ml`에 포함된다. 이번 작업에서 CI 파일은 수정하지 않았다.

기존 결과를 보존하려면 다른 위치에 보고서를 쓴다.

```bash
python -m ml.identity.evaluate_candidates --output /tmp/a-candidates.json
python -m ml.identity.analyze_failures --output /tmp/a-failures.json
python -m ml.identity.benchmark_candidates --output /tmp/a-benchmark.json
```

선택 임베딩 실험은 별도로 sentence-transformers와 로컬 BGE-M3가 있어야 한다.
실행 환경·모델 파일 해시는 results/embedding_comparison.json에 있다.

```bash
HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 python -m ml.identity.compare_embeddings \
  --model "$HOME/Models/bge-m3" --output /tmp/a-embeddings.json
```

기존 data/는 재생성할 필요 없다. generate_dataset의 기본 실행은 JSON 4개를 덮어쓴다.
새 holdout/v1도 다시 만들지 않는다. 생성기는 기존 출력 폴더가 있으면 거절한다.
newcomers.json은 정답 검수용이며 검색 대장에 넣지 않는다.

## 연결 담당자가 알아야 할 내부 함수

```python
from app.identity.features import compare_person
from app.identity.ranking import rank_candidates

person = {
    "personId": "p_demo_001", "name": "가상인물_0001",
    "company": "가상회사_001", "isSynthetic": True,
}
mention = {
    "name": "가상인믈_0001", "company": "가상회사_001", "channel": "MEETING",
}
comparison = compare_person(mention, person)
print(comparison.name.status, comparison.name_similarity)
# DIFFERENT, 약 0.889: 문자열이 비슷하다는 뜻이며 같은 사람으로 판정한 것이 아니다.

candidates = rank_candidates(mention, [person], fuzzy_threshold=0.88)
print([item.person_id for item in candidates])
# ['p_demo_001']: 사람이 검토할 내부 후보
```

호출자는 계약에 맞는 mention과 Person 목록을 전달한다. 원문·대장은 변경하지 않는다.
`fuzzy_threshold` 기본값 None은 유사도 후보 추가 비활성이다. 위 0.88은 학습에서 선택한 실험값이다.
반환값은 파이썬 내부 자료다. `priority`는 우선순위 튜플이고 동일 인물 확률이 아니다.
전체 후보와 경고를 보존하며 최종 3명 선택·CANDIDATES/NEW/CONFIRMED 응답은 생성하지 않는다.
**이 값을 그대로 B에 보내거나 priority/문자열 유사도를 API score에 넣으면 안 된다.**
B의 선행 개발은 [공식 샘플](../../../contracts/examples/identity_resolution.example.json)을 사용할 수 있다.

## 결과와 한계

| 항목 | 현재 결과 | 해석 범위 |
| --- | --- | --- |
| 기존 eval top-3 보장 재현율 | 정확 일치 91.7% → 유사도 규칙 100% | 가짜 평가 60명·600건, 동점 보수 처리 |
| 추가 후보 | 600건 중 150건에 총 157명 | 자동 확정 오판 건수가 아니라 검토할 후보 |
| BGE-M3 | 학습 top-1 97.083% → 97.250%, eval 개선 없음 | 기본 규칙 유지, 모델 선택 기능만 보관 |
| 규칙 검색 중앙값 | 300명 13.93ms / 1,000명 47.73ms | 해당 컴퓨터, 거리 캐시 없음, HTTP·DB·모델 제외 |
| 새 holdout | 15유형 300건 준비 | 생성/계약 검사 완료, 검색 성능 미측정 |

기존 쌍은 반복 관측과 번호형 가짜 이름에 의존한다. 신규·공유·충돌의 실제 성능을 증명하지 않는다.
새 holdout도 긴 가상 접두어·합성 토큰·인위적 비율이 있어 실제 인명 분포를 대표하지 않는다.
기존 eval은 이미 확인했으므로 미사용 검증이라고 부르지 않는다.
새 holdout을 쓰려면 모호한 정답 집합·충돌·신규·정보 부족을 구분하는 평가기가 먼저 필요하다.
결과를 보고 기준을 바꾸면 그 자료도 더 이상 미사용 검증 자료가 아니다.

## 팀 검토 요청

아래 항목은 아직 결정·승인·연결되지 않았다. AI가 대신 공용 파일을 바꾸지 않는다.

| 검토 항목 | 필요한 확인 | 검토 대상 |
| --- | --- | --- |
| 합성 정답 교차 검수 | 생성자가 아닌 사람이 기존 쌍·새 자료·원문을 확인. 특히 모호함/충돌을 단일 정답으로 만들지 않았는지 | A + 다른 팀원 |
| score 의미 | 계약은 동일 인물 확률을 요구함. 보정 방식·검증 기준을 합의. 임의 순위 점수로 대체하지 않음 | 총괄·A·소비 담당 |
| 정보 부족·연락처 충돌 | 계약 상태 안에서 어떻게 표현·확인할지, NEW와 구별할 방법 | 총괄·A·B |
| CONFIRMED·사람 확인 | 계약의 자동 연결 설명과 사람 확인 원칙의 적용 범위 | 총괄·A·B |
| 최종 후보 최대 3명 | 동점이 3명을 넘을 때 누락·추가 검색·정보 요청 처리 | A·B·총괄 |
| API·화면 연결 | 경로/요청/오류/호출 순서와 공용 라우트 수정 범위를 먼저 공유 | A·B·총괄 |
| Revision 연결 | 사람이 선택/수정한 결과의 기록 호출 규약. 저장 API는 D가 담당 | A·D |
| A 테스트의 CI 포함 | app/identity/·ml/identity/ 검사 경로 추가 방법 | 총괄 |
| 독립 작업 이후 일정 | API·후보 선택 화면·인물 대장 저장과 10/23·10/31 연결 범위 | A·총괄·연결 담당 |

스키마 변경이 필요하면 [contracts 규칙](../../../contracts/README.md)에 따라 팀 공유 후 진행한다.
공용 코드나 다른 담당 파일 변경도 사전 공유한다. B의 회의록/호출 코드와 D의 기록 저장을 A가 대신 만들지 않는다.
API를 만들 때는 기존 계약에 맞는 응답 검사도 필요하다. 현재 내부 테스트 통과가 API 완성을 뜻하지 않는다.
합의 후 decisions.md 반영은 총괄에게 제안하며 이 문서로 결정 기록을 대체하지 않는다.

## 팀에 공유할 요약 초안

> A의 합성 인물·비교 쌍, 표기 정리, 이름/연락처/소속 비교, 내부 후보 검색·순위와 검사를 구현했습니다.
> 로컬 BGE-M3는 기존 평가에서 규칙 대비 개선이 없어 기본 규칙을 유지합니다.
> 새 검증 자료는 준비했으며 모델 평가는 하지 않았습니다. 전체 검사 256개가 통과했습니다.
> 내부 priority는 확률이 아니고 API·화면·백엔드는 미연결입니다.
> 데이터 교차 검수와 score/정보 부족/CONFIRMED/최종 3명 처리, API·Revision·CI 연결 범위를 검토 부탁드립니다.

이 문구는 초안으로만 저장했다. 팀 채팅·이메일·GitHub에 자동 발송하지 않았다.

## 로컬 변경·PR 준비 상태

확인한 작업 저장소는 salesbreeze-restart, 브랜치는 s2/a-identity-restart, 기준 HEAD는 01f7dd5다.
이것은 로컬 상태 기록이며 현재 GitHub 최신 main과 같다고 재확인한 결과는 아니다.
기존 HEAD 추적 파일의 변경은 없고 A의 app/identity/·ml/identity/가 미추적 상태로 추가돼 있다.
이번 문서 정리 후에도 커밋·push·PR 생성은 하지 않았다.

레포 루트에서 범위를 다시 확인할 수 있다.

```bash
git status --short
git diff --name-only
git ls-files --others --exclude-standard ai-module/app/identity ai-module/ml/identity
```

git diff만으로는 미추적 파일을 볼 수 없으므로 세 번째 명령도 확인한다.
공용 CI·다른 담당 코드·contracts 변경을 이 PR에 몰래 포함하지 않는다.
PR이 필요해지면 팀 workflow에 따라 작은 범위로 나누고, 검토 결과와 검사 명령을 본문에 기록한다.
