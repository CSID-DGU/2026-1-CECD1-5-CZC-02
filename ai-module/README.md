# SALESMAP AI Module

메일/메신저 문장을 분석해 일정 정보를 추출하는 규칙 기반 FastAPI 모듈입니다.

## Stack

- Python
- FastAPI
- Uvicorn
- Komoran
- Regex
- Pydantic

## Run

```bash
cd ai-module
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## Health

```http
GET /
```

Response:

```json
{
  "message": "AI module running",
  "docs": "/docs",
  "health": "/health"
}
```

## Backend Endpoint

Spring Backend의 `HttpAiClient`는 아래 API를 호출합니다.

```http
POST /analyze
Content-Type: application/json
```

Request:

```json
{
  "sourceId": 1,
  "sourceType": "EMAIL",
  "externalSourceId": "external-1",
  "title": "고객 미팅",
  "content": "김대리님 금요일 오후 3시에 AI 프로젝트 회의 가능하실까요?",
  "collectedAt": "2026-05-01T10:00:00"
}
```

Response:

```json
{
  "customerName": null,
  "contactName": "김대리",
  "productName": null,
  "amount": null,
  "scheduleTitle": "AI 프로젝트 회의",
  "scheduleDateTime": "2026-06-05T15:00:00",
  "todoContent": "AI 프로젝트 회의 준비",
  "keyIssues": null,
  "summary": "AI 프로젝트 회의 / 금요일 / 오후 3시 / 김대리",
  "confidenceScore": 1.0
}
```

## Standalone Schedule Test

AI Module 단독 확인용 API입니다.

```http
POST /analyze/schedule
Content-Type: application/json
```

Request:

```json
{
  "user_id": 1,
  "message": "김대리님 금요일 오후 3시에 AI 프로젝트 회의 가능하실까요?"
}
```

Response:

```json
{
  "success": true,
  "schedule": {
    "title": "AI 프로젝트 회의",
    "date": "금요일",
    "time": "오후 3시",
    "participants": ["김대리"]
  }
}
```

## Pipeline

```text
문장 입력
-> 전처리
-> Komoran 명사 추출
-> regex 날짜/시간 추출
-> 키워드 분류
-> 일정 JSON 생성
-> Backend 반환
```

## Optional Ollama LLM Enhancement

The default AI engine is still rule-based for stable demos.
Set `AI_ENGINE=ollama` to enhance the rule-based result with a local Ollama LLM.
If Ollama is not running or the LLM returns invalid JSON, the module falls back to the rule-based result.

```powershell
# 1. Install Ollama, then pull a model
ollama pull qwen2.5:3b

# 2. Start Ollama if it is not already running
ollama serve

# 3. Run the AI module with LLM enhancement
$env:AI_ENGINE="ollama"
$env:OLLAMA_BASE_URL="http://localhost:11434"
$env:OLLAMA_MODEL="qwen2.5:3b"
python -m uvicorn app.main:app --reload --port 8000
```

Environment options:

```text
AI_ENGINE=rule | ollama
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5:3b
OLLAMA_TIMEOUT_SECONDS=12
OLLAMA_TEMPERATURE=0.1
```
