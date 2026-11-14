# SalesMap Agent

영업 담당자가 고객과 주고받은 메일·미팅 내용을 AI가 정리해서 **회의록, 할 일, 일정**으로 바꾸고, 사람이 확인하면 Google Calendar와 Salesmap에 등록해 주는 영업 활동 자동화 에이전트입니다.

> 동국대학교 2026 종합설계1·2 **컴쫀쿠(CZC)** 팀 · 기업 멘토 연계 프로젝트

## 2학기 목표

1학기에 만든 "메일 → 분석 → 승인 → 캘린더 등록" 흐름을 두 개의 에이전트로 확장합니다.

```
메일(Gmail·메일플러그)·미팅 메모
   │
   ▼
① 일정관리 에이전트 (기록 담당)
   상대 식별 → 표준 회의록 → 할 일 추출 → [사람 확인] → 캘린더·Salesmap 등록
   │ 할 일 목록
   ▼
② 고객지원 에이전트 (준비 담당)
   할 일 종류 판정 → 준비 시점 계산 → 자료 수집 → 준비 안내 → 아침 묶음 브리핑
```

- 머신러닝 2종: **같은 사람 판별(ML-1)**, **할 일 11부류 분류(ML-2)**. 사람이 고친 값은 모두 기록해 재학습 정답으로 씁니다(ML-4).
- 원칙: 실제 고객 데이터 미사용 · AI 단독 확정 금지(사람 확인 관문) · 외부 발송은 대기함까지만 · 알림은 하루 1회 묶음

자세한 목표·역할·일정은 [docs/project/overview.md](docs/project/overview.md)와 [docs/project/roles.md](docs/project/roles.md)를 보세요.

## 2학기 진행 상황 (11/12 기준)

**기록 흐름(① 일정관리 에이전트) 연결 상태**

```
메모·메일 입력 ──▶ 상대 식별 ──▶ 표준 회의록 ──▶ 할 일 추출 ──▶ 사람 확인 ──▶ 수정 기록 ──▶ 캘린더 등록
     ✅              🔄              ✅              ✅             ✅            ✅             ⬜
```

✅ main에서 실제로 이어짐 · 🔄 기능은 있고 연결 중 · ⬜ 아직 없음

- 지금 되는 것: 미팅 메모나 메일(스레드)로 회의록 초안을 만들고, 확인 화면(원문 · 회의록 · 할 일 3단)에서 근거를 보며 고친 뒤, 할 일을 하나씩 확정하고 회의록을 확정한다. 사람이 고친 값과 그대로 승인한 값은 모두 수정 기록으로 쌓인다.
- 연결 중: 상대 식별은 판정 API, 인물 대장 저장, 확인 화면에서 후보 고르기·새 사람 등록·수정 기록까지 완성(#45). 고른 사람을 회의록 참석자 칸에 반영하는 연결이 남음. 캘린더 등록은 확정 이후 넘기는 자리까지 만들어 둠.

**담당별**

| 담당 | 완료 (PR) | 진행 중 · 남은 것 |
| --- | --- | --- |
| A 상대 식별 | 가짜 인물 300명·비교 쌍 3,000개, 이름·연락처·소속 비교와 후보 검색 (#21) · 판정 규칙 확정 (D-022, #23) · 식별 API: 확실/후보 3명/신규 판정, 애매한 정답 처리 (#28) · 인물 대장 저장·검색, 확인 화면의 후보 고르기·새 사람 등록, 선택을 수정 기록으로 (#45) | 식별 결과 ID 규칙(D-008) 맞추기 · 인물 병합·되돌리기 (D-025) · ML-1 같은 사람 판별 학습 |
| B 회의록 | 회의록 평가셋·채점 (#13) · 메모 → 표준 회의록 엔진, 항목마다 원문 근거 (#17) · 회의록 저장·수정·확정 API (#19) · 미팅 기록 화면, 3단 확인 화면, 확인 대기함 (#24) · 할 일·수정 기록 실제 연결 (#31) · 메일에서도 회의록 만들기 (#32) · 메일용 지시문으로 메일 회의록 재현율 개선 (#34) · 메일 평가셋 10건으로 확장 (#37) · 회의록 보관·해제 (#39, D-025·D-026) · 할 일 분류: 규칙 vs 학습 모델 vs LLM 직접 판정 비교 보고서 (#44) | 고른 상대를 회의록 참석자 칸에 반영 (A #45 연결) · 메모 약속의 주체(우리/고객) 구분 개선 · 메일 평가 e04~e10·새 평가 문장 36개 교차 검수 |
| C 할 일 | 회의록 → 할 일(무엇을·누가·언제까지) 추출, 기한은 규칙으로 계산, 11부류 규칙 분류, 할 일 저장·수정·확정 API, 할 일 보드 (#25) · 학습 데이터 교정, 부류 경계 정리 (#27, D-024) | 할 일 수정 기록을 D 저장소로 넘기기 · ML-2 분류기 (학습 모델이 아직 규칙 기준을 넘지 못해 규칙 유지) |
| D 일정·브리핑 | 수정 기록 저장·조회 API, 아침 묶음 브리핑 API (#26) | 확정한 할 일 → Google 캘린더 등록 · 상태 자동 변경 규칙 · 미팅 전날 준비 브리핑(S-01) |
| 공통 | PR 자동 검사(CI) 4종 (#11·#20·#22) · 모델 비교 도구 (#15) · 로컬 LLM 7b 교체 (#18, D-019) · C 할 일 API를 공용 AI 모듈에 연결, 할 일 메뉴 (#29) · 평가 재현성(seed 고정)·회의록을 모델 비교에 추가 (#35) · 데이터 결정 권한 (D-025·D-026) · 로컬 LLM 4종 비교, 기본값 유지 (#40, D-027) · 시스템별 관여 범위·필수 vs 확장·기존 도구 비교표 (#42·#43, `docs/project/scope.md`) | 메일플러그 수집 · 회의록만 다른 모델로 나눌지 결정(11/20) · 모델 비교(개체명·임베딩) · 설치 문서 |

**주간 일정 대비** ([overview.md](docs/project/overview.md) 주간표)

| 주 | 목표 | 상태 |
| --- | --- | --- |
| 1 · 10/13~10/17 | 설계 마무리 | ✅ |
| 2 · 10/20~10/24 | 각자 1차 버전 | ✅ (A 식별 API · B 회의록 · C 할 일 · D 수정 기록 API) |
| 3 · 10/27~10/31 | 기록 흐름 끝까지 | 🔄 상대 식별 연결 · 캘린더 등록 남음. 협력 기업 중간 시연은 일정 조율 중 |
| 4 · 11/3~11/7 | 관리 기능 | 🔄 메일 회의록·재현율 개선·회의록 보관·할 일 보드 ✅, 할 일 상태 자동 변경 남음 |
| 5 · 11/10~11/14 | 준비 기능 + 학습 | 🔄 할 일 분류 LLM 비교 ✅ · 인물 대장·후보 고르기 ✅ · 미팅 전날 브리핑·캘린더 등록 진행 중 |
| 6~9 | 11/20 새 기능 마감 → 11/30 실사용 버전 → 12/1~4 시범 운영 → 12/11 최종 발표 | 예정 |

**지금 수치** (모두 팀이 만든 가짜 평가셋 기준. 이름을 가린 실제 자료로 다시 잴 예정. LLM 수치는 seed를 고정해 다시 돌려도 같게 측정)

| 부품 | 지표 | 결과 |
| --- | --- | --- |
| 회의록 | 문항 F1, 가짜 메모 10건 (qwen2.5:7b, seed 고정) | 0.830 |
| 회의록 | 문항 F1, 가짜 메일 10건 (같은 조건) | 0.615 — 새로 넣은 7건(e04~e10)은 정답 교차 검수 전 |
| 메일 분석 | 무수정 통과율, 가짜 메일 21통 | 규칙만 42.9% → 7b 81.0% |
| 상대 식별 | 후보 3명 안에 정답이 있는 비율, 평가 600건 | 100% (정확 일치만 91.7%) |
| 할 일 분류 | macro F1, 규칙 / 학습 모델(임베딩+LR) / LLM 직접 판정 | 기존 평가 55개 1.00 / 0.31 / 0.91~0.98 · 처음 보는 문장 36개(검수 전) 0.32 / 0.26 / 0.84~0.93 → 규칙 유지, "규칙이 모를 때만 LLM" 보조는 11/20에 결정 |
| 로컬 LLM 비교 | 설정만 바꿔 4종을 같은 코드·평가셋으로 실행 (메일 분석 무수정 통과율 / 회의록 F1 20건) | 모두 동작(성공률 100%). qwen2.5:7b 81.0% / 0.745 · gemma4:e4b 66.7% / 0.838 · qwen3.5:4b 71.4% / 0.609 · kanana 8B 71.4% / 0.606 → 과제마다 1등이 달라 기본값 7b 유지 (D-027) |

결정 기록은 [decisions.md](docs/project/decisions.md) (D-001~D-027), 모델 비교는 [ai-module/eval/MODELS.md](ai-module/eval/MODELS.md).

## 팀원이라면 먼저

1. **[AGENTS.md](AGENTS.md)** 를 읽습니다. 팀 공통 규칙이며 Codex·Claude Code가 자동으로 읽습니다 (`CLAUDE.md`는 이 파일을 불러옵니다).
2. [docs/project/roles.md](docs/project/roles.md)에서 내 역할의 **담당 폴더**와 **읽을 문서**("담당별 읽을 문서")를 확인합니다.
3. 모듈 사이 데이터는 **[contracts/](contracts/README.md)** 의 형식을 따릅니다. 앞 단계가 아직 없으면 `contracts/examples/`의 가짜 샘플을 입력으로 씁니다.
4. 결정은 [docs/project/decisions.md](docs/project/decisions.md)에 기록합니다. AI 도구 사용법·브랜치·PR 규칙은 [docs/project/workflow.md](docs/project/workflow.md).

## 2학기에 추가된 기능

- **미팅 기록 → 회의록**: 메모를 입력하면 표준 회의록(논의·고객 요청·우려·약속·다음 접촉) 초안. 모든 항목에 원문 근거 문장, 모르는 칸은 비워 두고 "확인 필요" 표시
- **메일 → 회의록**: 메일 상세에서 "회의록 만들기". 스레드 전체를 원문으로, 서명·인용은 빼고, 같은 스레드는 회의록 하나
- **확인 화면**: 원문 · 회의록 · 할 일 3단. 항목을 누르면 근거 문장 강조, 새 항목의 근거는 원문 문장에서 고름. 할 일은 하나씩 확정해야 회의록 확정 가능
- **할 일**: 회의록에서 무엇을·누가·언제까지 추출, 11부류 종류 분류, 할 일 보드
- **수정 기록**: 사람이 승인·수정한 값을 모두 기록 (나중에 재학습 정답)
- **상대 식별 API**: 이메일·전화가 정확히 맞으면 확실, 애매하면 후보 3명과 이유, 없으면 신규 (연결 중)
- **아침 묶음 브리핑 API**: 확정된 할 일 중 오늘·내일 기한을 한 번에

## 1학기 기능 (+ 9월 정비)

- **하이브리드 메일 분석**: 규칙(정규식)이 날짜·시간·금액·처리 유형을, LLM이 고객사·제품·참석자·요약을 맡고 결과를 병합합니다.
- **영업 / 비영업 분류**: 업무 외 메일은 타임라인·등록 대상에서 제외합니다. 분류는 사람이 고칠 수 있습니다.
- **사람 승인 후 등록**: 분석 결과를 사람이 확인·수정한 뒤 Google Calendar 일정 생성·변경·취소. 수정 내역은 `analysis_revisions`에 남습니다.
- **일정 충돌 경고**: 같은 시간대·중복·근접 일정을 알려 줍니다.
- **고객 타임라인 · 딜 파이프라인**: 고객사·담당자별 활동 이력과 영업 단계(리드·미팅·조율·확정·보류).
- **자동 수집·분석**: 새 메일을 백그라운드로 증분 수집하고 일괄 분석합니다(진행률·알림 표시).
- **답장 초안**: 분석 결과 기반 답장 초안 생성·저장. 미답장 메일 찾기.
- **데모 데이터 모드**: 실제 계정 없이 가짜 데이터로 화면을 확인할 수 있습니다.

## 구성

| 모듈 | 스택 | 역할 |
| --- | --- | --- |
| `backend` | Spring Boot · JPA · MySQL · JWT | API 서버, 인증, 회의록·할 일·수정 기록·브리핑, 분석·일정·파이프라인 관리, Gmail·Calendar 연동 |
| `ai-module` | FastAPI · Ollama(로컬 LLM) · BGE-M3 · GLiNER | 메일 분석, 회의록 엔진(`/minutes`), 상대 식별(`/identity/resolve`), 할 일 추출(`/tasks/extract`), 답장 초안. OpenAI 전환 구조 포함(기본 꺼짐) |
| `frontend` | React · Vite · Tailwind CSS | 미팅 기록·확인 화면·확인 대기함, 할 일, 대시보드, 메일·분석 뷰, 딜 파이프라인, 설정 |

```
frontend (5173) ──▶ backend (8080) ──▶ ai-module (8000) ──▶ Ollama (11434)
                        │
                        ├──▶ Gmail API / Google Calendar API
                        └──▶ MySQL
```

## 실행

세 모듈을 각각 실행합니다.

### backend (JDK 17, MySQL 필요)

```bash
cd backend
./gradlew bootRun
```

설정은 **환경변수**로 넣습니다. 값은 저장소에 커밋하지 않습니다.

| 변수 | 설명 | 기본값 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | MySQL 주소 | `jdbc:mysql://localhost:3306/salesmap…` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | DB 계정 | `root` / 빈 값 |
| `JWT_SECRET` | JWT 서명 키 | 개발용 값 (운영에서는 반드시 변경) |
| `AI_MODULE_MODE` | `mock` 또는 `http` (실제 AI 모듈 호출) | `mock` |
| `GMAIL_CLIENT_ID` / `GMAIL_CLIENT_SECRET` | Google OAuth | 빈 값 |
| `CORS_ALLOWED_ORIGINS` | 프론트 주소 | `localhost:5173, 5174` |

### ai-module (Python 3, 개발 환경 3.13)

```bash
cd ai-module
python -m venv .venv
.venv\Scripts\activate          # macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env             # 필요한 값만 수정
uvicorn app.main:app --port 8000
```

LLM 분석을 쓰려면 [Ollama](https://ollama.com)를 설치하고 `ollama pull qwen2.5:7b` 후 `.env`에서 `AI_ENGINE=ollama`로 설정합니다 (모델 메모리 약 4.7GB. 부족하면 `qwen2.5:3b`를 받고 `OLLAMA_MODEL=qwen2.5:3b`). OpenAI 전환은 [docs/openai-setup.md](docs/openai-setup.md)를 참고하세요 (기본은 꺼져 있고, 키가 없으면 과금되지 않습니다).

### frontend

```bash
cd frontend
npm install
npm run dev
```

## 테스트와 평가

```bash
# AI 모듈 단위 테스트 + 데이터 형식(contracts) 검사 (네트워크·유료 API 호출 없음)
cd ai-module
python -m pytest tests app ml   # 담당 폴더 안의 test_*.py 포함 (CI와 같음)

# 정답 라벨 평가셋(eval/dataset.jsonl, 21통)으로 분석 정확도 측정
python -m eval.run_eval                  # 규칙 엔진
python -m eval.run_eval --engine ollama  # 규칙 + LLM

# 모델 비교: 설정만 바꿔 같은 평가셋을 돌리고 eval/MODELS.md 표로 모으기 (유료는 --allow-paid --limit N 필요)
python -m eval.bench --list
python -m eval.bench --write-md

# 회의록 평가셋(가짜 메모 10개 + 메일 3개): 정답끼리 채점하면 만점이어야 정상, 엔진 결과는 --pred <폴더>
python -m eval.minutes.score --self

# 백엔드 단위 테스트
cd backend
./gradlew test --tests "*Test"   # 단위 테스트 전부
```

`BackendApplicationTests`는 실제 MySQL 연결이 필요해서 위 명령에서 빠집니다. 백엔드 단위 테스트 이름은 `...Test`로 끝나게 합니다.

PR을 열면 GitHub Actions가 브랜치 이름 규칙(`s2/<담당>-<주제>`), AI 모듈 테스트, 백엔드 단위 테스트, 프론트엔드 빌드를 자동으로 검사합니다 ([ci.yml](.github/workflows/ci.yml)). 브랜치·PR 규칙은 [docs/project/workflow.md](docs/project/workflow.md)를 참고하세요.

## 디렉터리

```
AGENTS.md      팀·AI 도구 공통 규칙 (CLAUDE.md는 이 파일을 불러옴)
backend/       Spring Boot API 서버
ai-module/     FastAPI 분석 엔진 (app/, eval/, tests/)
frontend/      React 웹 클라이언트
contracts/     모듈 사이 데이터 형식 (JSON Schema + 가짜 샘플)
docs/          API 명세, DB 설계, 상태값, OpenAI 설정
docs/project/  2학기 목표·역할·결정 기록·작업 방법
docs/archive/  지난 분석 자료 (참고용, 최신 아님)
```

## 문서

| 문서 | 내용 |
| --- | --- |
| [docs/project/](docs/project/overview.md) | **2학기 목표·역할·일정·결정·화면 시안** (팀원은 여기부터) |
| [docs/api-spec.md](docs/api-spec.md) | REST API 명세 |
| [docs/db-design.md](docs/db-design.md) · [docs/domain-enums.md](docs/domain-enums.md) | DB 설계, 상태값 |
| [backend/README.md](backend/README.md) | DB 생성, Google·Salesmap 연결 설정, 패키지 구조 |
| [ai-module/README.md](ai-module/README.md) | AI 모듈 구조·API·분석 흐름·모델 설정 |
| [frontend/README.md](frontend/README.md) | 화면별 컴포넌트와 API 파일 |
| [docs/openai-setup.md](docs/openai-setup.md) | OpenAI 전환 방법 (기본은 꺼짐) |
| [docs/archive/](docs/archive/phase2-feature-proposal.md) | 9월 확장 제안서 (비용 추정 등 참고용. 2학기 계획은 `docs/project/`가 기준) |
