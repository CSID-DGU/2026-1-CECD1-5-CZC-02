# SalesMap Agent

영업 담당자가 고객과 주고받은 메일·미팅 내용을 AI가 정리해서 **회의록, 할 일, 일정**으로 바꾸고, 사람이 확인하면 Google Calendar와 Salesmap에 등록해 주는 영업 활동 자동화 에이전트입니다.

> 동국대학교 2026 종합설계1·2 **컴쫀쿠(CZC)** 팀 · 기업 멘토 연계 프로젝트

## 2학기 목표

1학기에 만든 "메일 → 분석 → 승인 → 캘린더 등록" 흐름을 두 개의 에이전트로 확장합니다.

```
메일·메모·녹음
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

## 팀원이라면 먼저

1. **[AGENTS.md](AGENTS.md)** 를 읽습니다. 팀 공통 규칙이며 Codex·Claude Code가 자동으로 읽습니다 (`CLAUDE.md`는 이 파일을 불러옵니다).
2. 내 역할의 담당 폴더를 [docs/project/roles.md](docs/project/roles.md)에서 확인합니다.
3. 모듈 사이 데이터는 **[contracts/](contracts/README.md)** 의 형식을 따릅니다. 앞 단계가 아직 없으면 `contracts/examples/`의 가짜 샘플을 입력으로 씁니다.
4. 결정은 [docs/project/decisions.md](docs/project/decisions.md)에 기록합니다. AI 도구 사용법·브랜치·PR 규칙은 [docs/project/workflow.md](docs/project/workflow.md).

## 현재 기능 (1학기 + 9월 정비)

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
| `backend` | Spring Boot · JPA · MySQL · JWT | API 서버, 인증, 분석·일정·파이프라인 관리, Gmail·Calendar 연동 |
| `ai-module` | FastAPI · Ollama(로컬 LLM) · BGE-M3 · GLiNER | 메일 분석 엔진, 답장 초안. OpenAI 전환 구조 포함(기본 꺼짐) |
| `frontend` | React · Vite · Tailwind CSS | 대시보드, 메일·분석 뷰, 딜 파이프라인, 설정 |

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

LLM 분석을 쓰려면 [Ollama](https://ollama.com)를 설치하고 `ollama pull qwen2.5:3b` 후 `.env`에서 `AI_ENGINE=ollama`로 설정합니다. OpenAI 전환은 [docs/openai-setup.md](docs/openai-setup.md)를 참고하세요 (기본은 꺼져 있고, 키가 없으면 과금되지 않습니다).

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
python -m pytest tests

# 정답 라벨 평가셋(eval/dataset.jsonl, 21통)으로 분석 정확도 측정
python -m eval.run_eval                  # 규칙 엔진
python -m eval.run_eval --engine ollama  # 규칙 + LLM

# 백엔드 단위 테스트
cd backend
./gradlew test --tests "*BusinessDayCalculatorTest" --tests "*AnalysisRevisionRecordingTest"
```

`BackendApplicationTests`는 실제 MySQL 연결이 필요합니다.

PR을 열면 GitHub Actions가 브랜치 이름 규칙(`s2/<담당>-<주제>`), AI 모듈 테스트, 백엔드 단위 테스트, 프론트엔드 빌드를 자동으로 검사합니다 ([ci.yml](.github/workflows/ci.yml)). 브랜치·PR 규칙은 [docs/project/workflow.md](docs/project/workflow.md)를 참고하세요.

## 디렉터리

```
AGENTS.md      팀·AI 도구 공통 규칙 (CLAUDE.md는 이 파일을 불러옴)
backend/       Spring Boot API 서버
ai-module/     FastAPI 분석 엔진 (app/, eval/, tests/)
frontend/      React 웹 클라이언트
contracts/     모듈 사이 데이터 형식 (JSON Schema + 가짜 샘플)
docs/          API 명세, DB 설계, 연동 가이드
docs/project/  2학기 목표·역할·결정 기록·작업 방법
```

## 문서

| 문서 | 내용 |
| --- | --- |
| [docs/api-spec.md](docs/api-spec.md) | REST API 명세 |
| [docs/db-design.md](docs/db-design.md) · [docs/domain-enums.md](docs/domain-enums.md) | DB 설계, 상태값 |
| [docs/frontend-api-guide.md](docs/frontend-api-guide.md) | 프론트에서 API 쓰는 법 |
| [docs/e2e-test-flow.md](docs/e2e-test-flow.md) | 통합 테스트 흐름 |
| [docs/latest-feature-summary.md](docs/latest-feature-summary.md) | 1학기 최종 이후 추가 기능 |
| [docs/phase2-feature-proposal.md](docs/phase2-feature-proposal.md) | 2학기 확장 제안서 |
