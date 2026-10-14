# AGENTS.md

Salesmap 영업 활동 자동화 에이전트 — 2026 종합설계2 팀 컴쫀쿠(4명). 이 파일은 Codex·Claude Code 등 AI 도구와 팀원이 **작업 전에 반드시 읽는** 공통 규칙이다. 짧게 유지하고, 자세한 내용은 아래 문서로 보낸다.

## 목표 (한 줄)

영업 담당자가 사람을 만난 뒤, AI가 **상대를 식별하고 → 회의록을 쓰고 → 할 일을 뽑고 → 때맞춰 준비 안내**를 보내는 두 에이전트를 만든다. 자세히: `docs/project/overview.md`

## 먼저 읽을 것

| 상황 | 읽을 파일 |
| --- | --- |
| 무엇을 왜 만드는지 | `docs/project/overview.md` |
| 내 역할·담당 폴더 | `docs/project/roles.md` |
| 모듈 사이 데이터 형식 | `contracts/README.md`, `contracts/*.schema.json`, `contracts/examples/` |
| 회사 요청 기능 5개·주간 일정 | `docs/project/overview.md` |
| 화면 시안·화면별 담당 | `docs/project/screens.md` (이미지 `docs/project/screens/`) |
| 메일플러그 연동 | `docs/project/mailplug.md` |
| 할 일 11부류 | `docs/project/task-types.md` |
| 최근 결정 (다른 문서와 충돌하면 이것이 우선) | `docs/project/decisions.md` |
| 용어 | `docs/project/glossary.md` |
| 기존 API·DB | `docs/api-spec.md`, `docs/db-design.md`, `docs/domain-enums.md` |

사용자가 자기 역할(A/B/C/D)을 말하면 `roles.md`에서 담당 폴더를 확인하고 그 범위 안에서 작업한다.

## 레포 구조

- `backend/` Spring Boot · JPA · MySQL · JWT — 패키지는 `com.salesmap.backend.<도메인>`
- `ai-module/` FastAPI — 분석 엔진, LLM 추상화(`app/llm/`), 평가(`eval/`), 테스트(`tests/`)
- `frontend/` React · Vite · Tailwind
- `contracts/` 모듈 사이 데이터 형식 (JSON Schema + 가짜 샘플)
- `docs/project/` 2학기 목표·역할·결정 기록

## 반드시 지킬 규칙

1. **실제 고객·인물 데이터를 쓰지 않는다.** 코드·테스트·샘플·프롬프트 모두 가짜 데이터만. 실제 사람·회사처럼 보이는 이름도 만들지 않는다. 키·비밀번호·`.env`는 커밋하지 않는다.
2. **AI 결과를 자동 확정하지 않는다.** 인물 병합, 회의록·할 일 확정은 사람 확인을 거친다. 외부로 나가는 메일·메시지는 **발송 대기함까지만** 만들고 자동 발송 코드를 추가하지 않는다.
3. **모르는 값은 추측하지 않는다.** `null` 또는 빈 배열로 두고 표시한다. 뽑아낸 내용에는 원문 근거(`evidence`)를 붙인다.
4. **규칙으로 되는 것에 LLM을 쓰지 않는다.** 날짜 계산, 상태 변경, 기한 판정은 규칙 코드로.
5. **사람이 값을 고치는 화면을 만들면 수정 기록(revision)을 남긴다.** 형식은 `contracts/revision.schema.json`.
6. **알림을 늘리는 기능은 하루 1회 묶음을 기본값으로** 만든다. 개별 즉시 알림은 꼭 필요할 때만.
7. **유료 API(OpenAI 등)를 실제 호출하는 코드·테스트를 기본값으로 켜지 않는다.** 테스트는 네트워크 없이 돌아야 한다.

## 데이터 형식(contracts) 다루기

- 모듈 사이에 주고받는 데이터는 **반드시 `contracts/`의 스키마를 따른다.** 필드를 임의로 추가·이름 변경·삭제하지 않는다.
- 스키마 변경이 필요해 보이면 **코드를 고치기 전에 사용자에게 알리고 멈춘다.** 변경은 팀 합의 사항이다 (`contracts/README.md`).
- 앞 단계 코드가 없으면 `contracts/examples/`의 샘플을 입력으로 쓴다.
- 새 출력 코드를 만들면 결과가 스키마에 맞는지 확인하는 테스트를 함께 만든다.

## 작업 범위

- `docs/project/roles.md`의 **자기 담당 폴더 밖은 고치지 않는다.** 꼭 필요하면 무엇을 왜 바꾸는지 먼저 사용자에게 말한다.
- 공용 파일(`contracts/`, `docs/project/`, `AGENTS.md`, `ai-module/app/llm/`, `backend/.../global/`)은 사용자가 명시적으로 요청할 때만 수정한다.
- 결정이 생기면 `docs/project/decisions.md`에 한 줄 추가를 제안한다.

## 코드 스타일

- 주변 코드의 이름·주석 밀도·구조를 따른다. 백엔드 JSON은 camelCase, 파이썬 내부는 snake_case.
- 커밋 메시지: `feat(ai): …`, `fix(backend): …`, `docs: …` (한국어 설명 가능)
- 브랜치: **`s2/<담당>-<주제>`** (2학기 작업 표시). 담당은 `a`·`b`·`c`·`d`·`common`, 주제는 영어 소문자 kebab-case. 예) `s2/a-identity-baseline`, `s2/common-ci-setup`. 규칙에 안 맞으면 PR 검사가 실패한다. `feature/…` 등 1학기 브랜치에서 새 작업을 시작하지 않는다.

## 테스트

```bash
cd ai-module && python -m pytest tests          # AI 모듈 + 데이터 형식 검사
cd backend && ./gradlew test --tests "*Test"                 # 단위 테스트 전부 (MySQL 불필요)
```

`BackendApplicationTests`는 실제 MySQL이 필요해서 위 명령에서 빠진다. **백엔드 단위 테스트 클래스 이름은 `...Test`로 끝나게** 하고 MySQL 없이 돌게 만든다 (CI가 이 이름으로 고른다). 작업을 끝냈다고 말하기 전에 관련 테스트를 실행하고 결과를 그대로 보고한다.

PR을 열면 GitHub Actions(`.github/workflows/ci.yml`)가 브랜치 이름, AI 모듈 테스트, 백엔드 단위 테스트, 프론트 빌드를 자동으로 검사한다. AI 모듈 테스트에 새 라이브러리 import가 필요하면 `ai-module/requirements-test.txt`에도 추가한다.
