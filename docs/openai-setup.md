# OpenAI API 연결 가이드

AI 모듈은 기본적으로 **로컬 Ollama(무료)** 를 사용합니다. 아래 순서대로 설정하면 OpenAI로 전환할 수 있습니다.
설정 전까지는 코드가 OpenAI를 절대 호출하지 않으므로 과금이 발생하지 않습니다.

## 동작 방식

| 설정 | 실제 동작 |
| --- | --- |
| `LLM_PROVIDER=ollama` (기본값) | Ollama만 사용 |
| `LLM_PROVIDER=openai` + `OPENAI_API_KEY` 없음 | Ollama만 사용 (로그에 경고) |
| `LLM_PROVIDER=openai` + `OPENAI_API_KEY` 있음 | OpenAI → 실패·예산 초과 시 Ollama → 그래도 실패 시 규칙/템플릿 |

- 작업별 모델: 메일 분석 `gpt-5.6-luna`, 답장 초안 `gpt-5.6-terra` (`.env`에서 변경 가능)
- 응답 형식은 Structured Outputs(JSON 스키마 강제)를 사용하므로 JSON 파싱 실패가 나지 않습니다.
- 연속 2번 실패하면 60초 동안 해당 제공자를 건너뜁니다.
- 호출마다 AI 모듈 콘솔에 `[LLM] provider=... in=... out=... cost=$...` 로그가 남습니다.

## 1. 계정 · 조직 · 프로젝트 만들기

1. [platform.openai.com](https://platform.openai.com)에 가입합니다.
   - 개인 계정으로 가입하면 **기본 조직(Organization)이 자동으로 만들어집니다.** 팀원을 초대하려면 Settings → Organization → Members에서 초대합니다.
2. 왼쪽 위 프로젝트 선택 메뉴에서 **새 프로젝트**를 만듭니다. 예: `salesmap-agent`
   - 키·예산·사용량을 프로젝트 단위로 나눠 관리할 수 있어 팀 프로젝트에 적합합니다.
3. (확인 필요) 일부 최신 모델이나 기능은 **조직 인증(Organization Verification)** 을 요구할 수 있습니다. Settings → Organization → General에서 인증 요구 여부를 확인하세요.

## 2. 결제 · 비용 상한 (가장 중요)

1. Settings → Billing에서 결제 수단을 등록하고 **선불 크레딧을 충전**합니다. 최소 $5, 권장 $10.
2. **자동 충전(Auto recharge)은 끕니다.** 크레딧이 떨어지면 호출이 멈추므로 **가장 확실한 상한**입니다.
3. 프로젝트 설정에서 **월 예산 한도(Budget / Limit)** 를 설정합니다. 권장 $10.
   - 한도가 알림용인지 실제 차단인지는 대시보드 화면에서 확인하세요. 차단이 아니라면 2번 선불 크레딧이 실제 안전장치입니다.
4. AI 모듈 자체 상한도 있습니다. `OPENAI_MONTHLY_BUDGET_USD`(기본 $10)를 넘으면 OpenAI를 건너뛰고 Ollama를 씁니다.
   - 단, **서버를 재시작하면 0부터 다시 셉니다.** 그래서 1~3번 설정이 반드시 필요합니다.

예상 비용(2026-09 공식 가격 기준): 하루 메일 50통 분석 + 답장 10건이면 **월 약 $2 내외**입니다.
자세한 계산은 `docs/phase2-feature-proposal.md` 2-1절을 참고하세요.

## 3. API 키 발급 · 설정

1. 프로젝트 안에서 API keys → **Create new secret key** 를 누릅니다. 키 이름 예: `ai-module-local`
2. 키는 **한 번만 보입니다.** 바로 복사합니다.
3. `ai-module/.env` 에 추가합니다. 이 파일은 `.gitignore`에 포함되어 있어 커밋되지 않습니다.

```env
LLM_PROVIDER=openai
OPENAI_API_KEY=sk-...
OPENAI_MONTHLY_BUDGET_USD=10
```

> ⚠️ 키를 코드, 커밋, 채팅, 스크린샷에 절대 넣지 마세요. 노출되면 즉시 대시보드에서 키를 삭제(Revoke)합니다.

4. OpenAI SDK가 설치돼 있는지 확인합니다.

```bash
cd ai-module
pip install -r requirements.txt
```

## 4. 첫 테스트는 5건만

유료 호출이므로 **평가셋 5건으로 먼저** 확인합니다. 비용은 1센트 미만입니다.

```bash
cd ai-module
python -m eval.run_eval --engine ollama --limit 5 --verbose
```

- `--engine ollama` 는 "LLM 사용" 모드라는 뜻입니다. 실제로 쓰는 제공자는 `LLM_PROVIDER` 설정을 따릅니다.
- 콘솔에 `[LLM] task=analysis provider=openai model=gpt-5.6-luna ...` 가 찍히면 성공입니다.
- `provider=ollama ... fallback=openai: ...` 가 찍히면 OpenAI 호출이 실패한 것입니다. 사유를 확인하세요. 보통 키 오류, 크레딧 부족, 모델 이름 오류입니다.

문제가 없으면 전체 평가셋(21건)을 돌려 Ollama 결과와 비교합니다.

```bash
python -m eval.run_eval --engine ollama
```

## 5. 되돌리기

`.env`에서 `LLM_PROVIDER=ollama` 로 바꾸고 AI 모듈을 재시작하면 즉시 로컬 모델로 돌아갑니다.

## 개인정보 주의

OpenAI로 전환하면 **고객 메일 본문이 외부 서버로 전송됩니다.**
- 실제 고객 메일로 테스트하기 전에 팀과 멘토에게 확인합니다.
- 시연과 평가는 가상 고객사 메일로 진행합니다. `eval/dataset.jsonl`이 그렇게 되어 있습니다.
- 대응 방안(마스킹, 보존 정책)은 `docs/phase2-feature-proposal.md` 2-6절을 참고하세요.
