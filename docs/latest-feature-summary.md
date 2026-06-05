# Latest Feature Summary

최종 시연 버전 이후 추가된 주요 기능 요약입니다. 팀원이 변경 범위를 빠르게 파악할 때 먼저 읽으면 됩니다.

## 추가된 화면

### 오늘의 영업 브리핑

- Dashboard 왼쪽 상단에 표시
- 오늘 일정, 승인 대기 분석, 우선 확인 메일을 요약
- 우선 확인 메일에서 상세 화면으로 이동 가능

### 처리 이력

- Route: `/history`
- 승인 대기, 등록됨, 삭제됨 상태를 요약
- AI 분석 결과와 Salesmap 반영 상태를 함께 확인
- 우선 확인할 메일을 클릭하면 메일 상세로 이동

### 고객 타임라인

- Route: `/customers`
- 고객사/담당자별 활동 이력 표시
- AI 분석, 일정 생성/변경/취소, Salesmap 등록 이력을 시간순으로 확인
- 업무 외 메일은 타임라인에 포함하지 않음

## 추가된 AI 기능

### 영업 메일/업무 외 메일 분류

- 업무 외 메일은 `NON_BUSINESS`로 판단
- 업무 외 메일은 고객 타임라인과 Salesmap 등록 대상에서 제외
- 화면에서는 주요 분석 필드를 `해당 없음`으로 표시

### BGE-M3 보조 분류

- 오픈소스 임베딩 모델
- 메일이 영업 활동인지, 일정 생성/변경/취소에 가까운지 보조 판단
- 규칙 기반 분석의 애매한 케이스를 보완하기 위한 구조

### GLiNER 보조 추출

- 고객사, 제품명, 참석자 후보 추출 보조
- 규칙 기반 추출이 놓치는 엔티티를 보완

### Ollama 기반 답장 초안

- `POST /api/analysis/{analysisId}/reply-draft`
- 분석 결과와 원본 메일을 바탕으로 답장 제목/본문 생성
- 시연 핵심 케이스는 안정적인 템플릿 우선
- 그 외 케이스는 Ollama 생성 시도 후 템플릿 fallback

## 추가된 일정 안정화 기능

### 일정 충돌 경고

- 같은 시간대에 일정이 있으면 충돌로 안내
- 같은 제목/시간이면 중복 일정으로 판단
- 등록 예정 일정 전후 약 3시간 안에 일정이 있으면 근접 일정 경고
- 중복은 등록 차단, 근접 경고는 사용자 확인 후 진행 가능

### 등록/변경/삭제 흐름

- `CREATE`: 내부 Schedule 생성, Google Calendar 이벤트 생성
- `UPDATE`: 내부 Schedule 수정, Google Calendar 이벤트 수정
- `CANCEL`: 내부 Schedule 삭제, Google Calendar 이벤트 삭제
- Salesmap은 Google Calendar 양방향 연동으로 TODO 자동 반영

## 추가된 API

| Method | URL | 설명 |
| --- | --- | --- |
| `DELETE` | `/api/sources/{sourceId}` | 수집 메일 삭제 |
| `POST` | `/api/analysis/{analysisId}/reply-draft` | 답장 초안 생성 |
| `GET` | `/api/customers` | 고객사/담당자 목록 |
| `GET` | `/api/customers/{customerContactId}/timeline` | 고객사별 활동 타임라인 |

## 추가된 DB 테이블

- `customer_contacts`
- `customer_activities`

## 발표 멘트

```text
이번 버전에서는 단순히 메일에서 일정을 뽑는 것에서 끝나지 않고,
영업 메일 여부 판단, 답장 초안 생성, 처리 이력, 고객사별 활동 타임라인까지 확장했습니다.

AI 모듈은 규칙 기반 분석에 BGE-M3, GLiNER, Ollama를 조합한 MVP 구조입니다.
시연 안정성을 위해 핵심 케이스는 템플릿 fallback을 두었고,
추후 OpenAI API나 더 큰 모델로 교체해도 FastAPI AI 모듈만 확장하면 됩니다.
```

## 실행 시 주의

- FastAPI 변경사항 반영을 위해 AI 모듈 재시작 필요
- BGE-M3/GLiNER는 최초 실행 시 모델 다운로드 시간이 오래 걸릴 수 있음
- 기존 DB에 저장된 분석 결과는 자동으로 바뀌지 않으므로, 바뀐 분석 결과를 보려면 다시 AI 분석 실행 필요
- Salesmap 반영은 Google Calendar와 Salesmap 양방향 동기화 지연 영향을 받을 수 있음
