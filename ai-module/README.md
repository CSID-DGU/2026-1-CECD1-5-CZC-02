# SALESMAP AI Module

메일·메모 문장을 분석해 고객사·담당자·제품·일정·할 일 정보를 뽑는 FastAPI 모듈입니다. 기본은 **규칙(정규식·키워드) 분석**이고, 설정을 켜면 LLM(Ollama/OpenAI)·임베딩(BGE-M3)·개체명 인식(GLiNER)이 결과를 보정합니다.

> 2026-10 갱신 (1학기 최종 + 9월 정비 기준). 2학기에 추가되는 회의록·할 일 형식은 `contracts/`를 따릅니다.

## 실행

설치·실행·`.env` 설정은 [루트 README의 "실행"](../README.md#실행)을 보세요. 실행 후 `http://localhost:8000/docs`에서 API를 바로 시험할 수 있습니다.

## 구조

```text
app/
  main.py            FastAPI 앱, 시작 시 설정 출력
  config.py          환경 변수 (아래 표)
  routes/analyze.py  API 엔드포인트
  schemas/           요청·응답 형식 (API 형식의 기준)
  services/
    analyzer.py               분석 전체 흐름 (규칙 → 보조 모델 → LLM 보정)
    classifier.py             활동 종류, CREATE·UPDATE·CANCEL·CONFIRM 판정 (규칙)
    regex_parser.py · utils/date_parser.py   날짜·시간 추출 (규칙)
    embedding_classifier.py   BGE-M3 보조 분류
    entity_extractor.py       GLiNER 보조 추출
    llm_analysis.py           LLM 의미 필드 보정
    reply_draft.py            답장 초안
  llm/               LLM 공급자 추상화 (공용 — 수정은 팀 합의)
    router.py        작업별 공급자 선택, 폴백(OpenAI → Ollama → 규칙), 월 예산 상한
eval/                정답 라벨 평가셋(dataset.jsonl)과 채점 스크립트
tests/               단위 테스트 + contracts 형식 검사 (네트워크 없음)
```

## API

| 메서드·경로 | 호출하는 곳 | 하는 일 |
| --- | --- | --- |
| `POST /analyze` | 백엔드 `HttpAiClient` | 메일 묶음(스레드)과 기존 일정을 받아 분석 결과 반환 |
| `POST /analyze/schedule` | 단독 확인용 | 문장 하나에서 일정만 추출 |
| `POST /reply-draft` | 백엔드 | 분석 결과로 답장 초안 생성 |
| `POST /minutes` | 백엔드 회의록(B) | 메모·메일 → `meeting_note` 초안 (`app/minutes/`) |
| `POST /identity/resolve` | 백엔드 식별(A) | 인물 언급 + 인물 대장 → `identity_resolution` 배열 (`app/identity/API.md`, D-022) |
| `POST /tasks/extract` | 백엔드 할 일(C) | `meeting_note` → `task` 배열 (`app/tasks/README.md`) |
| `GET /`, `GET /health` | - | 상태 확인 |

요청·응답 필드는 `app/schemas/request.py`, `app/schemas/response.py`가 기준입니다.

## 분석 흐름

```text
메일 묶음 입력
-> 전처리 (줄바꿈 정리)
-> 규칙: 날짜/시간 정규식, 키워드로 영업 여부·actionType 판정
-> (켜면) BGE-M3 보조 분류, GLiNER 보조 추출
-> (AI_ENGINE=ollama) LLM이 의미 필드 6개만 보정
     고객사·담당자·제품·참석자·일정 제목·요약
     영업 여부·actionType·날짜·금액은 규칙 결과를 덮어쓰지 못함
-> LLM 실패 시 규칙 결과로 폴백, 사용한 엔진은 llmProvider/llmModel로 기록
```

## 설정 (환경 변수)

| 부품 | 변수 | 기본값 |
| --- | --- | --- |
| 분석 엔진 | `AI_ENGINE` (`rule` / `ollama`) | `rule` |
| LLM 공급자 | `LLM_PROVIDER` (`ollama` / `openai`) | `ollama` |
| Ollama | `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, `OLLAMA_TIMEOUT_SECONDS`, `OLLAMA_TEMPERATURE` | `qwen2.5:7b`, 300초 (D-019) |
| OpenAI | `OPENAI_API_KEY`, `OPENAI_MODEL_ANALYSIS`, `OPENAI_MODEL_REPLY`, `OPENAI_MONTHLY_BUDGET_USD` | 키 없음 → 호출 안 함 |
| 임베딩 | `EMBEDDING_CLASSIFIER_ENABLED`, `EMBEDDING_MODEL_NAME` | 꺼짐, `BAAI/bge-m3` |
| 개체명 인식 | `GLINER_ENABLED`, `GLINER_MODEL_NAME`, `GLINER_THRESHOLD` | 꺼짐, `urchade/gliner_multi-v2.1` |

전체 목록은 `.env.example`. OpenAI 전환은 [docs/openai-setup.md](../docs/openai-setup.md) — 기본은 꺼져 있고 키가 없으면 과금되지 않습니다.

## 테스트·평가

```bash
python -m pytest tests app ml           # 네트워크·유료 API 호출 없음 (CI와 같음)
python -m eval.run_eval                  # 규칙 엔진 정확도 (21통)
python -m eval.run_eval --engine ollama  # 규칙 + LLM
```
