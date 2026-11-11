# ML-2 비교 — LLM 직접 판정 (B)

할 일 문장 하나를 LLM이 직접 11부류(10부류 + NONE) 중 하나로 고르게 하고, C의 규칙·임베딩+LR과 같은 평가셋·같은 지표로 비교한다. **운영 분류기는 계속 RULE**이고 이 폴더는 비교 실험 전용이다. 결과와 판단은 [REPORT.md](REPORT.md).

## 파일

| 파일 | 내용 |
| --- | --- |
| `holdout.json` | 새 평가 문장 36개 (부류마다 3개 + NONE 경계 6개). **모델을 돌리기 전에 정답을 정해 먼저 커밋**했다. 검수 전 |
| `data.py` | 평가셋 읽기, C 데이터·지시문 예시와의 겹침 검사 (글자 2-gram 유사도) |
| `classifier.py` | LLM 판정. 지시문은 `docs/project/task-types.md` 정의표·경계표에서 만든다. 출력 JSON `{"taskType", "reason"}`, 형식이 틀리면 NONE + 실패로 셈 |
| `compare.py` | 규칙 vs 임베딩+LR vs LLM vs (규칙 → NONE일 때만 LLM) 비교 실행 |
| `results-*.json` | 실행 결과 (지표, 혼동표, 문장별 예측·LLM 근거·시간) |

C 폴더(`ml/task_type/`, `app/tasks/`)는 읽기만 한다 — 데이터·규칙·지표 함수를 import해서 쓴다.

## 실행 (ai-module에서, 로컬 Ollama가 켜져 있을 때만)

```bash
OLLAMA_SEED=42 python -m ml.llm_baseline.compare --llm qwen2.5:7b \
  --embedding-model sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2 \
  --output ml/llm_baseline/results-qwen2.5-7b.json
OLLAMA_SEED=42 OLLAMA_THINK=false python -m ml.llm_baseline.compare --llm gemma4:e4b \
  --output ml/llm_baseline/results-gemma4-e4b.json
```

- 임베딩 모델은 HF 캐시에 있는 것만 쓴다 (`local_files_only`). 처음이면 한 번 내려받아야 한다 (약 460MB). 학습 조건은 C `experiment.py`와 같다 (정규화 임베딩, seed=42, max_iter=2000, class_weight=None).
- `--embedding-model`을 빼면 55개 셋은 C `report.json`에 남은 예측을 복원해 쓰고, holdout은 임베딩 비교를 생략한다.
- 유료 API는 쓰지 않는다. `OLLAMA_SEED`가 없으면 실행을 거부한다 (재현용).

## 테스트

`python -m pytest tests app ml` — 가짜 LLM 제공자로 돌고 Ollama·네트워크·임베딩 모델을 부르지 않는다.
