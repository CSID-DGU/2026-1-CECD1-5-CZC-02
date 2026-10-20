# C ML-2 실험·평가

운영 추출기는 규칙 모델을 사용한다. 이 폴더의 학습/생성 명령은 명시적으로 실행할 때만
모델/로컬 Ollama를 사용하며 테스트는 네트워크나 무거운 ML import 없이 실행된다.

## 데이터

- `train.json`: 부류별 60개 학습 후보. `source`, `group`, `reviewStatus`를 함께 보관한다.
- `evaluation.json`: 별도 작성한 부류별 5개, 총 55개 평가 문장. 학습 프롬프트에는 넣지 않는다.
- `extraction.json`: 입력 회의록과 사람이 검토할 기대 결과 31건. 요청만 있는 음성 사례,
  고객 측 약속, 조건/범위 날짜, 분류 충돌, LIGHT의 미식별 다인 후속, 중복, 윤년/연말 등을 포함한다.
- `BOUNDARIES.md`: B·D가 검토할 부류 경계 후보.

학습·평가의 출처/그룹/동일 문장 중복을 검사한다. 다만 모두 합성이고 PENDING이며,
다른 팀원의 교차 검수·회사 발화 평가셋은 아직 없다. 점수는 작은 내부 평가의 결과이며
운영 성능·목표 달성의 증거로 확대하지 않는다. 테스트 통과와 추출 정확도는 별개다.

## 재현 (ai-module에서)

```bash
# 네트워크·모델 없이 규칙/추출 평가
python -m ml.task_type.experiment --output ml/task_type/rule-report.json
# 선택적 실험 의존성
python -m pip install -r ml/task_type/requirements.txt
# 모델이 캐시에 있을 때: 네트워크 없이 임베딩+로지스틱 회귀 학습 및 규칙과 비교
python -m ml.task_type.experiment --model sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
# 최초 공개 모델 다운로드를 원할 때만 위 명령에 --allow-download 추가
```

학습 결과는 `report.json`과 로컬 `local_model.npz`(git 제외)다. seed=42, max_iter=2000,
정규화 문장 임베딩과 로지스틱 회귀를 사용한다. 저장한 가중치의 예측 재현도 검사한다.
매크로 F1, 부류별 precision/recall/F1, NONE 재현율, 오분류 인덱스를 보고한다.
추출 평가는 content+evidence로 항목을 대조해 precision/recall/명시적 약속 recall과 기한·종류·약속 구분 정확도를 보고한다.
기대 결과는 엔진을 호출해 만들지 않았다. 아직 예문 일부는 단일 약속 위주라 실제 복합 발화 평가는 별도로 필요하다.

규칙을 이겨도 교차 검수 전에는 운영으로 자동 교체하지 않는다. `model.py`는 실험용 로딩/추론이며
API 기본값을 바꾸지 않는다. `.npz`는 pickle을 허용하지 않고 읽는다.

```python
from pathlib import Path
from ml.task_type.model import EmbeddingClassifier
classifier = EmbeddingClassifier('sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2', Path('ml/task_type/local_model.npz'))
print(classifier.predict(['계약서 초안 송부']))
```

## 학습 후보 생성·교차 검수

```bash
# 로컬 Ollama가 실행 중일 때만. 유료 API를 사용하지 않는다.
python -m ml.task_type.generate --output ml/task_type/generated-train.json
# generated-train.json을 실험하려면 --train generated-train.json 지정
python -m ml.task_type.review --data train.json --export /tmp/task-review.csv
# A/B/D가 reviewerRole과 label을 채운 뒤
python -m ml.task_type.review --data train.json --votes /tmp/task-review.csv
```

생성 중 체크포인트는 git에서 제외한다. 재개 시 완료한 부류는 유지한다.
검수자는 C 외 역할이어야 한다. 부류별 불일치율 20% 이상이면 정의를 먼저 검토하며,
검수 도구는 원본 라벨을 자동 수정하거나 검수를 완료했다고 기록하지 않는다.

## 테스트

`python -m pytest app/tasks ml/task_type` 또는 전체 `python -m pytest tests app ml`.
