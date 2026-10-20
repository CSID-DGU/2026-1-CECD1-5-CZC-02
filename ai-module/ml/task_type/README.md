# C ML-2 실험·평가 — #25 리뷰 후속

운영 추출기는 RULE을 사용한다. 모델 학습/다운로드/로컬 Ollama 생성은 명령을 명시적으로
실행할 때만 동작한다. CI 테스트는 네트워크나 무거운 ML import 없이 실행된다.

## 데이터와 검수 상태

- `train.json`: 후보 720개. 기존 660개의 ID를 유지하고 인력 소싱 v2 60개를 추가했다.
  기존 공급업체/공급망 탐색 60개는 NONE 음성 예제로 바꿨다.
- 대상·의도가 불분명한 후보 64개는 `trainingEligible=false`이며 학습에서 제외한다.
  실제 학습은 656개이고 더 이상 부류별 개수가 같지 않다. 부류별 수는 report.json에 있다.
- `evaluation.json`: 기존 55개를 파일 내용 그대로 사용한다. 사용자 전달 피드백에 따르면
  교차 검수에서 라벨 모두 일치했다. 파일의 이전 PENDING 표기는 수정하지 않고
  training-review.json과 report.json에 평가 라벨 검수 사실을 구분해 기록했다.
- 학습 후보의 C 수정/재생성은 여전히 독립 교차 검수 전이다. 평가 라벨 검수와 혼동하지 않는다.
- `extraction.json`: 입력 회의록과 기대 할 일 33개를 포함한 개발 회귀셋 31건.
  정보 부족 미팅의 종류 1개만 NONE으로 바꿨다. 평가 55개와 별개다.
- `training-review.json`: 정정 ID·전후 라벨·학습 포함 여부·이유·사용자 피드백/C 예비 검토 출처.
  제거한 회사명처럼 보이는 표현은 원문을 다시 복사해 보관하지 않았다.
- `BOUNDARIES.md`: 총괄에게 공용 문서 반영을 요청할 경계 기준 제안.

학습/평가의 동일 문장·출처·그룹 중복을 검사한다. 평가 55개는 이번 개발 피드백에도 사용했으므로
독립 최종 평가셋으로 주장하지 않는다. 이름을 가린 회사 문장을 받으면 별도 최종 평가 전용으로 추가한다.

## 실행 (ai-module에서)

```bash
# 네트워크·모델 없이 규칙/추출 평가
python -m ml.task_type.experiment --output /tmp/task-rule-report.json
# 선택적 실험 의존성
python -m pip install -r ml/task_type/requirements.txt
# 캐시된 모델만 사용. 원래 조건 비교 + 부류 불균형 보정 진단을 구분해서 기록
python -m ml.task_type.experiment --model sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2 --compare-balanced
# 최초 공개 모델 다운로드가 필요할 때만 --allow-download 추가
```

기존 비교 조건은 seed=42, max_iter=2000, 정규화 문장 임베딩, class_weight=None로 유지한다.
`--compare-balanced`는 같은 임베딩에서 class_weight=balanced를 추가 진단한다. 평가 점수로
정규화 강도나 임계값을 검색하지 않는다. `report-before-review.json`은 최신 main(5fcb228)의
데이터/규칙을 수정하기 전에 같은 평가셋으로 다시 측정한 비교 기준이다.

결과는 report.json, 로컬 가중치는 local_model.npz / balanced_model.npz(git 제외)에 남긴다.
부류별 precision/recall/F1, macro F1, NONE 재현율, 오분류 인덱스, 학습 정확도도 보고한다.
저장 후 예측 재현을 검사하며 pickle을 허용하지 않는다. model.py로 로컬 추론할 수 있다.
학습 모델은 API에 자동 적용하지 않는다.

## 후보 생성·교차 검수

```bash
# 로컬 Ollama가 실행 중일 때만. S-10 정의를 명시하고 기존 train 문장을 프롬프트 힌트로 쓰지 않음
python -m ml.task_type.generate --label SOURCING_START --output ml/task_type/generated-train.json
# 새로 생성하려면 새 --checkpoint 경로 지정. 이전 버전 캐시는 재사용하지 않음
python -m ml.task_type.generate --label SOURCING_START --checkpoint /tmp/new-sourcing-progress.json --output /tmp/new-sourcing.json
python -m ml.task_type.review --data train.json --export /tmp/task-review.csv
# A/B/D가 reviewerRole과 label을 채운 뒤
python -m ml.task_type.review --data train.json --votes /tmp/task-review.csv
```

생성 결과는 후보 묶음이다. 단일 부류 60개 파일로 전체 11부류 train.json을 덮어쓰지 않는다.
정의·인력 탐색 여부·고유명사·중복을 확인한 뒤 새 ID로 합치고 정정 이력을 남긴다.
단순 영어/연락처 필터는 한글 고유명사를 모두 탐지하지 못하므로 수동 검토를 생략하지 않는다.
CSV의 규칙 답은 검토 참고일 뿐 정답 강제가 아니다. 학습 제외 여부도 함께 보여준다.
불일치율 20% 이상인 부류는 정의를 먼저 검토한다. 검수 도구는 원본 라벨을 자동 수정하지 않는다.

## 검증

`python -m pytest tests app ml`. 자세한 점수·제약은 RESULTS.md, 팀 연결은 ../../app/tasks/HANDOFF.md.
