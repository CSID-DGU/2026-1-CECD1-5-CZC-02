# SalesMap Agent

영업 담당자가 주고받는 이메일을 AI로 분석해 영업 활동을 자동으로 정리·추적하는 에이전트입니다.
받은 메일에서 고객사·제품·일정·금액·처리 유형을 추출하고, 고객별 딜 파이프라인과 다음 액션을
관리하며, Gmail·Google Calendar와 연동됩니다.

> 2026-1 종합설계1 05분반 CZC(컴쫀쿠) 팀 프로젝트

## 구성

세 개 모듈로 이루어진 모노레포입니다.

| 모듈 | 스택 | 역할 |
| --- | --- | --- |
| `backend` | Spring Boot · JPA · MySQL · JWT | API 서버, 인증, 분석/일정/파이프라인 관리, Gmail·Calendar 연동 |
| `ai-module` | FastAPI · 로컬 LLM(Ollama) | 메일 분석 엔진(규칙 + LLM 하이브리드), 답장 초안 생성 |
| `frontend` | React · Vite · Tailwind CSS | 대시보드, 메일·분석 뷰, 딜 파이프라인, 설정 |

## 주요 기능

- **하이브리드 메일 분석** — 규칙(정규식)이 날짜·시간·금액·처리 유형을, LLM이 고객사·제품·참석자·요약을 담당하고 결과를 병합합니다.
- **발신자 문맥 기억** — 같은 발신자의 이전 분석을 참조해 고객사·제품 등 문맥을 이어붙입니다.
- **딜 파이프라인** — 고객별로 분석을 묶어 단계(리드·미팅·조율·확정·보류)를 분류하고 다음 액션을 제안합니다.
- **메일 방향 구분** — 받은/보낸 메일을 태깅해 정보 추출은 받은 메일만 대상으로 하고, 보낸 답장은 고객 타임라인에 소통 기록으로 남깁니다.
- **자동 수집·분석** — 활성화하면 백그라운드로 새 메일을 증분 수집하고 미분석 메일을 자동 분석합니다(처리량 상한 적용).
- **Gmail·Calendar 연동** — 메일 수집, 답장 초안 생성 및 전송, 분석 결과 기반 일정 등록·변경·취소.

## 실행

세 모듈을 각각 기동합니다.

### backend

```bash
cd backend
./gradlew bootRun
```

다음 값을 환경변수 또는 `backend/.env`로 주입합니다(값은 저장소에 포함하지 않습니다).

```
SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD
JWT_SECRET
GMAIL_CLIENT_ID, GMAIL_CLIENT_SECRET
```

### ai-module

```bash
cd ai-module
pip install -r requirements.txt
uvicorn app.main:app --port 8000
```

로컬 LLM은 [Ollama](https://ollama.com)(`qwen2.5`)를 사용합니다. 사전에 `ollama pull qwen2.5` 후 Ollama가 실행 중이어야 합니다.

OpenAI API로 전환하는 구조도 들어 있지만 기본값은 꺼져 있습니다(`LLM_PROVIDER=ollama`). 설정 방법은 [docs/openai-setup.md](docs/openai-setup.md)를 참고하세요.

### frontend

```bash
cd frontend
npm install
npm run dev
```

## 테스트와 평가

```bash
# AI 모듈 단위 테스트 (네트워크·유료 API 호출 없음)
cd ai-module
python -m pytest tests

# 정답 라벨 평가셋(eval/dataset.jsonl, 21통)으로 분석 정확도 측정
python -m eval.run_eval                  # 규칙 엔진
python -m eval.run_eval --engine ollama  # 규칙 + LLM

# 백엔드 단위 테스트 (JDK 17)
cd backend
./gradlew test --tests "*BusinessDayCalculatorTest" --tests "*AnalysisRevisionRecordingTest"
```

`BackendApplicationTests`는 실제 MySQL 연결이 필요합니다.

## 디렉터리

```
backend/     Spring Boot API 서버
ai-module/   FastAPI 분석 엔진
frontend/    React 웹 클라이언트
docs/        API 명세 등 문서
```
