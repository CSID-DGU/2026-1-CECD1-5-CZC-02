# 역할과 담당 영역

> 확정: 2026-10-11 (`decisions.md` D-001 참고).

## 한눈에

| | 담당자 | 맡는 일 | ML·실험 | 넘겨주는 것 |
| --- | --- | --- | --- | --- |
| **A** | 조현우 | 인물 식별 3단 판정, 인물 대장, **후보 고르는 화면** | ML-1 같은 사람 판별 | `identity_resolution`, `person` |
| **B** | 이민재 (개발 총괄 겸임) | 메모·메일 → 표준 회의록, **회의록·할 일 확인 화면** (STT는 추가 검토, D-015) | ML-2 비교용 LLM 직접 판정 + 비교 보고서 | `meeting_note` |
| **C** | 송하경 | 회의록 → 할 일 추출 (무엇을·언제까지·누가) + 종류 분류 | ML-2 할 일 11부류 분류기 | `task` |
| **D** | 유혜림 | 캘린더 등록, 상태 자동 변경, 준비 브리핑 (S-01 먼저, D-016), 아침 묶음 | ML-4 수정 기록 API | `revision` 저장소, 브리핑 |

데이터는 A → B → C → D로 흐르지만 **개발은 동시에** 한다. 앞사람 결과 대신 `contracts/examples/`의 샘플을 입력으로 쓴다.

## 담당별 읽을 문서

작업 시작 전에 **모두 읽기**를 읽고, 자기 담당 줄을 읽는다. AI 도구에 "나는 ○ 담당이야"라고 말하면 이 표를 보고 같은 문서를 읽는다.

**모두 읽기** (순서대로): `AGENTS.md` → [`overview.md`](overview.md) (목표·주간 일정) → 이 파일 → [`decisions.md`](decisions.md) (최근 결정, 다른 문서보다 우선) → [`workflow.md`](workflow.md) (브랜치·PR·AI 도구) → [`screens.md`](screens.md) (화면 시안) → [`contracts/README.md`](../../contracts/README.md)

| 담당 | 추가로 읽을 것 | 관련 결정 |
| --- | --- | --- |
| **A** | `contracts/person.schema.json`, `identity_resolution.schema.json`, `contracts/examples/` · [`glossary.md`](glossary.md) · 화면 ⑤, ③의 참석자 후보 영역 · `ai-module/README.md`의 임베딩·개체명 설정 | D-002, D-008 |
| **B** | `contracts/meeting_note.schema.json` · `ai-module/eval/minutes/README.md` (회의록 정답 규칙) · 화면 ②, ③ 틀 · `ai-module/eval/MODELS.md` (모델 비교) | D-009, D-015, D-017, D-018 |
| **C** | [`task-types.md`](task-types.md) (11부류) · `contracts/task.schema.json`, `meeting_note.schema.json` · `ai-module/eval/minutes/README.md`의 `commitments`·`nextContact` 규칙 · 화면 ④, ③의 할 일 영역 | D-002, D-007, D-018 |
| **D** | `contracts/revision.schema.json` · `docs/db-design.md`의 `analysis_revisions` · `docs/domain-enums.md` · `backend/README.md` · 화면 ①, ⑥ | D-002, D-007, D-016 |
| **총괄** | [`mailplug.md`](mailplug.md) · `ai-module/eval/MODELS.md` · `.github/workflows/ci.yml` | D-010 ~ D-014 |

**이 표는 문서가 생기거나 바뀔 때 같은 PR에서 고친다.** 총괄이 PR 리뷰 때 확인한다.

### 개발 총괄 (이민재)

- 모든 PR 리뷰·승인 (`CODEOWNERS`), 주간 이어 붙이기 진행, `decisions.md` 정리
- 공용 파일(`contracts/`, `docs/project/`, `AGENTS.md`, `ai-module/app/llm/`, `backend/.../global/`) 관리
- **메일플러그 수집** (4주차, 브랜치 `s2/common-mailplug-imap`, [`mailplug.md`](mailplug.md))
- **모델 비교·교체**: LLM(로컬 vs OpenAI)·개체명 인식·임베딩을 같은 평가셋으로 비교하고 이길 때만 교체
- 회사·멘토 소통 (잔디)

## 담당 폴더 (제안 · 첫 PR에서 확정)

남의 폴더는 고치지 않는다. 고쳐야 하면 담당자에게 먼저 말하거나 PR 리뷰를 요청한다.

| 담당 | AI 모듈 (`ai-module/`) | 학습·실험 | 백엔드 (`com.salesmap.backend.`) | 프론트 화면 (`screens.md` 번호) |
| --- | --- | --- | --- | --- |
| A | `app/identity/` | `ml/identity/` | `person` | ⑤ 사람, ③의 참석자 후보 영역 |
| B | `app/minutes/` (`app/stt/`는 보류) | `ml/llm_baseline/` | `minutes` | ② 미팅 기록, ③ 확인 화면 틀 |
| C | `app/tasks/` | `ml/task_type/` | `task` | ④ 할 일, ③의 할 일 영역 |
| D | — | — | `revision`, `briefing`, 기존 `calendar`·`schedule` | ① 오늘, ⑥ 미팅 준비 브리핑, 설정(알림 시간) |
| 총괄 | — | — | 메일플러그 수집 (`mailplug`, 기존 `gmail`·`autopipeline` 연결) | 설정(메일 계정) |

화면은 각자 자기 기능 것을 만든다 (D-017). AI 모듈의 라우트 등록(`app/main.py`)처럼 여러 사람이 한 줄씩 건드리는 파일은 PR에 "공용 파일 수정"이라고 적는다.

공용(수정 전 팀 공유): `contracts/`, `docs/project/`, `AGENTS.md`, `ai-module/app/llm/`, `backend/.../global/`, 공통 설정 파일.

## 주의할 연결 지점

- **ML-4 수정 기록**: D가 형식·저장 API를 **10/23까지** 만든다. A·B·C는 자기 화면에서 그 API를 **호출만** 한다. 사람이 값을 고치는 화면을 만들면 기록 호출을 빠뜨리지 않는다 (목표: 기록률 100%).
- **초안 만들기 순서 (제안)**: 미팅 기록에서 "AI로 정리하기"를 누르면 B의 백엔드(`minutes`)가 A 식별 → B 회의록 → C 할 일 순서로 각자의 API를 불러 초안을 묶고, 확인 화면에서 확정하면 D로 넘긴다. 각 단계 API는 담당자가 만들고, 아직 없으면 `contracts/examples/` 샘플을 돌려주는 가짜 구현을 끼운다.
- **DB 테이블**: 각자 자기 테이블은 직접 만든다. A는 인물 테이블만. 다른 사람 테이블에 컬럼을 추가할 땐 담당자 확인.
- **A의 기준 모델**: 학습 모델 전에 규칙(연락처 일치) + 임베딩 유사도 버전을 **10/23까지** 먼저. 파이프라인은 학습 모델 없이도 돌아야 한다.

## 공통 과업 (넷이 같이)

| 과업 | 주기 |
| --- | --- |
| 데이터 형식 유지 (`contracts/`) | 변경 시마다 합의 |
| 주 1회 이어 붙이기 — 넷의 결과를 실제로 연결해 끝까지 돌려 보기 | 매주 |
| 제약·한계 기록 — 못 한 것과 그 이유 | 매주 |
| 정답 교차 검수 — ML-1 쌍·ML-2 예문은 만든 사람이 아닌 사람이 검수 | 데이터 만들 때 |
| 하루 알림 건수 측정 | 11월부터 |
