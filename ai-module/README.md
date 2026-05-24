# SALESMAP AI Module

메시지 분석 및 일정 추출 AI 모듈 (FastAPI + OpenAI)

## 개요

Backend의 `/api/analysis` 요청을 받아 아래 과정을 통해 분석 결과를 반환합니다:

1. **전처리** - 텍스트 정제
2. **Komoran** - 한국어 형태소 분석
3. **Regex** - 날짜/시간 추출
4. **GPT** - 요약 및 분류
5. **분류** - 활동 유형 판정 (MEETING/CALL/EMAIL/TASK)
6. **일정 JSON** - 구조화된 데이터 생성

## 설치

### 1. 환경 설정

```bash
cd ai-module
python -m venv venv
source venv/bin/activate  # macOS/Linux
# 또는
venv\Scripts\activate  # Windows
```

### 2. 의존성 설치

```bash
pip install -r requirements.txt
```

### 3. 환경변수 설정

`.env.example`을 `.env`로 복사 후 수정:

```bash
cp .env.example .env
```

`.env` 파일에서 아래 항목 설정:

```env
OPENAI_API_KEY=sk-your-api-key-here
OPENAI_MODEL=gpt-3.5-turbo  # 또는 gpt-4
SERVER_HOST=0.0.0.0
SERVER_PORT=8000
BACKEND_URL=http://localhost:8080
```

## 실행

### 개발 모드 (Hot reload)

```bash
python -m app.main
# 또는
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### 프로덕션 모드

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 4
```

## API 문서

앱 시작 후 아래 URL에서 Swagger UI 문서 확인:

- **Swagger UI**: http://localhost:8000/docs
- **ReDoc**: http://localhost:8000/redoc

## API 엔드포인트

### 1. 메시지 분석

**Endpoint**: `POST /api/analyze`

**Request**:

```json
{
  "request_id": "REQ_001",
  "user_id": 1,
  "message": "금요일 오후 3시 회의 가능하신가요?",
  "created_at": "2026-05-13T10:30:00"
}
```

**Response**:

```json
{
  "success": true,
  "analysis_result": {
    "summary": "회의 일정 조율 요청",
    "customer_name": null,
    "contact_person": "김대리",
    "activity_type": "MEETING",
    "todo_required": true,
    "todo_content": "회의 진행",
    "schedule": {
      "title": "AI 프로젝트 회의",
      "date": "2026-05-15",
      "time": "15:00",
      "participants": ["김대리"],
      "location": null
    },
    "confidence": 0.94
  }
}
```

### 2. 헬스 체크

**Endpoint**: `GET /api/health`

**Response**:

```json
{
  "status": "healthy"
}
```

## 폴더 구조

```
ai-module/
├── app/
│   ├── __init__.py
│   ├── main.py                  # FastAPI 앱 진입점
│   ├── config.py                # 환경변수 설정
│   ├── routes/
│   │   ├── __init__.py
│   │   └── analyze.py           # 분석 라우트
│   ├── services/
│   │   ├── __init__.py
│   │   ├── preprocessor.py      # 전처리
│   │   ├── extractor.py         # 형태소 분석 & 정보 추출
│   │   ├── classifier.py        # 활동 유형 분류
│   │   ├── summarizer.py        # GPT 요약
│   │   └── analyzer.py          # 통합 분석 (Orchestrator)
│   ├── schemas/
│   │   ├── __init__.py
│   │   └── models.py            # Pydantic 모델
│   └── utils/
│       ├── __init__.py
│       └── date_parser.py       # 날짜/시간 파싱
├── requirements.txt             # 의존성
├── .env.example                 # 환경변수 템플릿
└── README.md                    # 이 파일
```

## 기술 스택

- **Framework**: FastAPI
- **Server**: Uvicorn
- **AI**: OpenAI API (GPT-3.5-turbo / GPT-4)
- **NLP**: KoNLPy (Komoran)
- **Validation**: Pydantic
- **DateTime**: python-dateutil

## 주요 기능

### 1. 전처리 (Preprocessor)

- 특수문자 제거
- 공백 정규화
- UTF-8 인코딩 보장

### 2. 형태소 분석 & 정보 추출 (Extractor)

- **Komoran** 기반 한국어 형태소 분석
- 인물명/참여자 추출
- 장소 추출
- 폴백 기능 (Komoran 실패 시)

### 3. 활동 유형 분류 (Classifier)

분류 유형:
- **MEETING** (회의, 미팅)
- **CALL** (통화, 전화)
- **EMAIL** (이메일, 메시지)
- **TASK** (업무, 작업)

키워드 기반 점수 계산

### 4. 날짜/시간 파싱 (DateParser)

지원 형식:
- 절대 날짜: `2026-05-15`, `5월 15일`
- 상대 날짜: `내일`, `모레`, `금요일`
- 시간: `15:30`, `오후 3시`, `3시`

### 5. GPT 기반 분석 (Summarizer)

- 텍스트 요약 (1-2줄)
- TODO 필요 여부 판정
- 자연스러운 한국어 처리

### 6. 통합 분석 (Analyzer)

전체 파이프라인 조율:
1. 입력 메시지 받음
2. 전처리
3. 정보 추출 (날짜, 시간, 참여자, 장소)
4. 활동 유형 분류
5. GPT 요약
6. TODO 판정
7. 최종 JSON 생성

## 에러 처리

### OpenAI API 없음

`OPENAI_API_KEY`가 설정되지 않으면 규칙 기반 폴백 동작:
- 요약: 단순 텍스트 잘라내기
- TODO 판정: 키워드 기반

### Komoran 설치 실패

Komoran이 없으면 정규식 기반 폴백:
- 참여자 추출: 정규식 패턴 매칭

## 테스트

### cURL로 테스트

```bash
curl -X POST "http://localhost:8000/api/analyze" \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "TEST_001",
    "user_id": 1,
    "message": "내일 오후 2시에 김대리와 회의 있어?",
    "created_at": "2026-05-13T10:30:00"
  }'
```

### Postman으로 테스트

1. `POST http://localhost:8000/api/analyze`
2. Body → raw JSON
3. 위의 cURL 예시 JSON 입력
4. Send

## 성능 최적화

- 병렬 처리: 비동기 함수 사용
- 캐싱: (추후 구현)
- 배치 처리: (추후 구현)

## 주의사항

### Komoran 설치 (선택)

```bash
# Komoran 사용하려면 아래 설치
pip install konlpy
```

한국어 형태소 분석이 필요하면 설치. 없으면 정규식 폴백.

### OpenAI API 비용

GPT 호출 시 비용 발생. 테스트/개발 시 주의.

- gpt-3.5-turbo: 저비용 (추천)
- gpt-4: 고비용, 고정확도

### Timezone 설정

기본값: `Asia/Seoul`

필요시 `config.py`에서 변경.

## 주요 개선 항목

- [ ] 동시성 최적화 (Task Queue)
- [ ] 응답 캐싱
- [ ] 배치 API 지원
- [ ] 다국어 지원 확대
- [ ] 모니터링/로깅 강화
- [ ] 단위 테스트 추가

## 문제 해결

### "ModuleNotFoundError: No module named 'app'"

```bash
# 프로젝트 루트에서 실행
python -m app.main
```

### "OPENAI_API_KEY not found"

`.env` 파일 생성 후 API 키 설정:

```bash
cp .env.example .env
# .env 파일 수정 (OPENAI_API_KEY 입력)
```

### Komoran 설치 오류

```bash
pip install konlpy
# Mac에서 Java/JDK 필요
brew install openjdk
```

## 라이선스

내부 프로젝트

## 연락처

AI 담당: hagyeong

## 참고 문서

- [FastAPI 공식 문서](https://fastapi.tiangolo.com/)
- [OpenAI API 가이드](https://platform.openai.com/docs)
- [KoNLPy 공식 문서](https://konlpy.org/)
