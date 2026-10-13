# B 회의록 — 세션별 작업 계획

> 담당: 이민재 (개발 총괄 겸임). 한 세션 = 한 브랜치 = 한 PR. 세션을 시작할 때 아래 "세션 시작 문장"을 첫 줄에 붙이면 AI 도구가 이 문서와 필요한 파일을 읽고 시작한다.
> 순서·범위가 바뀌면 이 파일을 고치고, 큰 결정은 `decisions.md`에 한 줄 남긴다.

## B가 만드는 것

| 결과물 | 내용 | 받는 사람 |
| --- | --- | --- |
| 회의록 엔진 | 메모·메일 텍스트 → `meeting_note` (5문항, 모든 항목에 원문 근거, 모르는 칸은 비우고 `missingFields`) | C |
| 회의록 백엔드 | 회의록 저장·조회·수정·확정 API, 초안 만들기 순서 묶기 (A → B → C) | 화면, D |
| ② 미팅 기록 화면 | 메모 입력 / 메일에서 가져오기 → "AI로 정리하기" | 사용자 |
| ③ 확인 화면 틀 | 원문 · 회의록 · 할 일 3단, 근거 강조, 확정. A·C 영역을 끼움 | 사용자 |
| ML-2 비교 실험 | LLM 직접 판정(제로샷·퓨샷) vs C의 학습 모델 비교 보고서 | 발표 |

기준 문서: `contracts/meeting_note.schema.json`, `contracts/examples/meeting_note.example.json`, `docs/project/screens.md`(②③), `decisions.md` D-009(회의록 문항)·D-015(STT 보류).

## 공통 규칙 (모든 세션)

- **모르면 비운다.** LLM이 낸 항목 중 근거(`evidence`, 원문 문장 그대로)가 원문에 없으면 버린다 — 이 검사는 규칙 코드로.
- **확정 전엔 아무것도 등록하지 않는다.** 회의록 `status`는 확인 화면에서 확정하기 전까지 `DRAFT`.
- 사람이 고친 값은 D의 수정 기록 API로 남긴다 (없으면 호출 자리만 만들고 TODO).
- LLM은 `ai-module/app/llm`의 `generate(LLMRequest(...))`만 쓴다. 기본값은 로컬(Ollama). **테스트는 가짜 provider로 네트워크 없이.**
- 데이터는 가짜만 (`contracts/examples/`의 홍길동·가상전자 시나리오 스타일).
- 끝내기 전에 `cd ai-module && python -m pytest tests` (백엔드를 건드렸으면 백엔드 단위 테스트도) 돌리고 결과를 PR에 그대로 적는다.

## 세션 목록

| # | 세션 | 브랜치 | 주차 | 선행 |
| --- | --- | --- | --- | --- |
| B-1 | 평가용 가짜 메모 10개 + 채점 스크립트 | `s2/b-minutes-eval` | 1주 (~10/17) | — |
| B-2 | 회의록 엔진 1차 (ai-module) | `s2/b-minutes-engine` | 2주 (~10/24) | B-1 |
| B-3 | 회의록 백엔드 (`minutes` 도메인) | `s2/b-minutes-api` | 2~3주 | B-2의 응답 형식 |
| B-4 | ② 미팅 기록 + ③ 확인 화면 | `s2/b-review-screen` | 3주 (~10/31) | B-3 |
| B-5 | 메일에서 회의록 만들기 | `s2/b-minutes-from-mail` | 4주 | B-3 |
| B-6 | ML-2 LLM 직접 판정 + 비교 보고서 | `s2/b-llm-baseline` | 5~6주 | C의 평가 데이터 |

10/31 중간 시연에 필요한 것은 **B-1~B-4**. 매주 금요일 이어 붙이기 전에 그 주 세션을 PR까지 올린다.

---

### B-1 평가용 가짜 메모 + 채점

**목표**: 엔진을 만들기 전에 "잘 만들었다"의 기준부터 만든다.

- `ai-module/eval/minutes/` 아래
  - 가짜 미팅 메모 10개 + 정답 `meeting_note` (사람이 직접 작성). 섞을 것: 약속이 여러 개, 우려 없음(빈 칸), 기한 표현("이번 주 금요일", "다음 달 초"), 상대 여러 명, 다인 방문(LIGHT 모드) 1~2개, 회의와 무관한 잡담
  - 채점 스크립트: 문항별 항목 재현율·정밀도(텍스트 유사도 기준), **근거 일치율**(근거가 원문에 그대로 있는 비율), `missingFields` 정확도
- 정답 파일은 스키마 검사 테스트에 넣는다.

**완료 기준**: 정답 10개가 스키마를 통과하고, 정답끼리 채점하면 만점이 나온다.

**세션 시작 문장**
```
나는 B(회의록) 담당이야. docs/project/plans/b-minutes.md의 B-1을 진행해 줘.
contracts/meeting_note.schema.json과 examples를 읽고, ai-module/eval/minutes/에 가짜 메모 10개·정답·채점 스크립트를 만들어 줘.
```

### B-2 회의록 엔진 1차

**목표**: 메모 텍스트 → `meeting_note` 초안.

- `ai-module/app/minutes/`
  1. 전처리: 문장 나누기, 메일이면 서명·인용문 제거(기존 `app/services/preprocess*.py` 참고)
  2. LLM 호출: 5문항(`discussion`·`customerRequests`·`concerns`·`commitments`·`nextContact`)을 JSON으로, 항목마다 원문 인용 근거
  3. **검증(규칙)**: 근거가 원문에 없는 항목 제거 → 빈 문항은 `missingFields`에 → 스키마 검사
  4. 참석자: 입력으로 받은 A의 식별 결과(`identity_resolution`)를 그대로 연결. 없으면 이름만 두고 `personId: null`
  5. LLM이 실패하면 빈 회의록 + 전 문항 `missingFields` (지어내지 않음)
- 라우트: `POST /minutes` (`app/routes/`에 파일 추가, `app/main.py`에 한 줄 등록 → PR에 "공용 파일 수정" 표시)
- 테스트: 가짜 provider로 (a) 정상 (b) 근거 없는 항목 제거 (c) LLM 실패 시 빈 회의록 (d) 결과 스키마 통과
- B-1 채점 스크립트로 점수를 내서 PR에 적는다 (로컬 Ollama로 돌린 결과, CI에서는 돌리지 않음).

**완료 기준**: 테스트 통과, 평가 점수 기록, 근거 없는 항목이 결과에 0개.

**세션 시작 문장**
```
나는 B(회의록) 담당이야. docs/project/plans/b-minutes.md의 B-2를 진행해 줘.
ai-module/app/llm 사용법과 기존 app/services의 LLM 분석 코드를 먼저 읽고, ai-module/app/minutes/에 회의록 엔진과 POST /minutes를 만들어 줘.
```

### B-3 회의록 백엔드

**목표**: 회의록을 저장하고, 화면이 쓸 API를 만든다.

- `com.salesmap.backend.minutes`
  - 테이블: 회의록(원본 연결, 시각·장소, 모드, 문항 JSON, 상태 `DRAFT`/`CONFIRMED`, 생성 방식·모델)
  - 원본 저장: 메모는 기존 `source`에 `sourceType=MEETING_NOTE`(또는 `MANUAL_INPUT`)로 — 원본은 지우지 않는다
  - API (camelCase JSON, ID는 `mn_` + PK)
    - `POST /api/meeting-notes` 메모로 초안 만들기 → A 식별 → ai-module `/minutes` → C 할 일 순서로 묶음 (A·C API가 없으면 샘플을 돌려주는 가짜 구현)
    - `GET /api/meeting-notes`, `GET /api/meeting-notes/{id}`
    - `PATCH /api/meeting-notes/{id}` 문항 수정 → 수정 기록 API 호출
    - `POST /api/meeting-notes/{id}/confirm` 확정 → 할 일 확정은 C, 캘린더는 D로 넘김
  - ai-module 호출은 기존 `ai/client`(`AiClient`·`HttpAiClient`·`MockAiClient`) 방식을 따른다. `AI_MODULE_MODE=mock`이면 샘플 회의록을 돌려준다. (`AiClient`는 1학기 공용 코드라 메서드 추가 시 PR에 표시)
- 단위 테스트는 MySQL 없이 (서비스 계층 + mock).

**완료 기준**: mock 모드로 메모 → 초안 → 수정 → 확정이 API로 돈다.

**세션 시작 문장**
```
나는 B(회의록) 담당이야. docs/project/plans/b-minutes.md의 B-3을 진행해 줘.
backend의 ai/client, analysis(AnalysisRevision 포함), source 패키지 구조를 먼저 읽고 com.salesmap.backend.minutes를 같은 스타일로 만들어 줘.
```

### B-4 ② 미팅 기록 + ③ 확인 화면

**목표**: 10/31 시연에서 사람이 쓰는 화면.

- 시안: `docs/project/screens.md` ②③. 기존 `DashboardLayout.jsx` 메뉴에 "미팅 기록"·"확인 대기함" 연결, 3단 배치는 `MessageView.jsx` 참고
- ② 미팅 기록: 언제·어디서·만난 사람·메모 → "AI로 정리하기" → 확인 화면으로 이동. 오른쪽 최근 회의록 목록
- ③ 확인 화면: 원문 | 회의록 | 할 일
  - 항목을 누르면 원문의 근거 문장 강조
  - 빈 문항은 "확인 필요" 표시, 항목 추가·수정·삭제
  - 참석자 후보 영역(A)·할 일 영역(C)은 **컴포넌트 자리**로 두고, 아직 없으면 샘플 데이터로 그린 임시 컴포넌트
  - 확정 버튼 → `confirm` API. 확정 전엔 아무것도 등록 안 됨
- `npm run build` 통과.

**완료 기준**: 가짜 메모 하나로 입력 → 확인 → 확정까지 화면에서 된다.

**세션 시작 문장**
```
나는 B(회의록) 담당이야. docs/project/plans/b-minutes.md의 B-4를 진행해 줘.
docs/project/screens.md의 ②③ 시안과 frontend의 DashboardLayout.jsx, MessageView.jsx를 먼저 읽고 같은 스타일로 만들어 줘.
```

### B-5 메일에서 회의록

- 메일함(기존 메일 상세)에서 "회의록 만들기" → 그 메일(스레드)을 원문으로 B-3의 초안 만들기 (`sourceType=EMAIL`)
- 메일 본문 전처리(서명·인용 제거)를 엔진에 연결, B-1 평가셋에 메일 2~3개 추가
- 메일플러그로 들어온 메일도 같은 `source`라 따로 할 일 없음

### B-6 ML-2 LLM 직접 판정 + 비교 보고서

- `ai-module/ml/llm_baseline/`: 할 일 문장 → 11부류(`docs/project/task-types.md`)를 LLM이 바로 판정 (제로샷, 퓨샷 2가지)
- **C와 같은 평가 데이터**로 부류별 F1·평균 F1·'해당 없음' 재현율·건당 시간 측정
- 보고서: C의 학습 모델과 비교 → 어느 쪽을 쓸지 근거 (`ml/llm_baseline/REPORT.md`)
- 평가 데이터는 C가 만든다. 학습용과 평가용 출처를 섞지 않는다.

## 하지 않는 것

- 녹음 → 텍스트 (D-015, 추가 검토). `app/stt/` 폴더는 만들지 않는다
- 회의록 문항 교차 검증 ML-3 (D-006)
- 회사 회의록 양식을 받기 전에 문항을 임의로 늘리기 — 받으면 `[contract]` PR로 맞춘다
