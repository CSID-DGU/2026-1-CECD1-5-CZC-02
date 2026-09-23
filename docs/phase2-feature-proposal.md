# 종합설계2 확장 제안서 — Salesmap 영업 활동 자동화 서비스

> 작성 기준일: 2026-09-21 · 대상 레포: `salesmap-agent` (main, 697f76b)
> 범위: (1) 현재 기능 전수 파악 → (2) LLM 고도화 방안 → (3) 추가 기능 발굴·평가 → (4) 최종 추천 + 8주 일정
> 이 문서는 분석·제안 문서이며, 코드는 수정하지 않았습니다.

---

## 0. 세 줄 요약

1. **지금 시스템의 "AI"는 대부분 규칙(정규식·키워드)입니다.** LLM(Qwen2.5)은 고객사·담당자·제품·참석자·일정제목·요약 **6개 의미 필드만** 보정하고, 영업/비영업 판정·CREATE/UPDATE/CANCEL/CONFIRM 판정·날짜/시간·금액은 코드에서 **LLM이 덮어쓰지 못하게 차단**되어 있습니다 (`ai-module/app/services/analyzer.py` `merge_llm_patch`).
2. **사용자 승인/수정 기록은 "학습 데이터"로 쓸 수 없는 형태입니다.** 수정은 `analyses` 행을 **덮어쓰기**하며 AI 원본이 남지 않습니다 (`backend/src/main/java/com/salesmap/backend/analysis/entity/Analysis.java` `updateEditableFields`). 이걸 고치는 게 "쓸수록 좋아지는 기능" 전체의 **1번 선행 조건**입니다.
3. 추천: **① LLM Provider 추상화 + OpenAI 전환 + 평가 하네스(기반)** → **② 승인/수정 피드백 루프(학습의 실체)** → **③ 미팅 전 브리핑 + 회의 STT(시연 임팩트)**. 예상 API 비용은 **월 $2~$9 수준**으로, 비용은 의사결정 변수가 아닙니다.

---

# 단계 1. 현재 기능 전수 파악

## 1-0. 먼저, 배경 인식에서 바로잡을 점

| 팀이 알고 있던 내용 | 코드 기준 실제 | 근거 |
| --- | --- | --- |
| "AI 분석(영업/비영업 분류, 처리 유형 판단 등)" | **LLM이 판단하지 않음.** 분류·액션판정·날짜·금액은 전부 규칙. LLM은 의미 필드 6개만 보정 | [analyzer.py](../ai-module/app/services/analyzer.py) `merge_llm_patch` |
| "신뢰도(confidence)" | **계산된 신뢰도가 아니라 상수.** 규칙 0.85 → LLM 성공 시 `max(0.85, 0.9)`, 가드 발동 시 0.88/0.92/0.95로 고정 대입 | [analyzer.py](../ai-module/app/services/analyzer.py) `confidence=0.85`, `confidence=max(...)` |
| "Salesmap이 캘린더를 TODO로 동기화" | 맞음. 단 **백엔드 `SalesmapClient`는 기본 mock**이고 실제 반영 경로는 Google Calendar 양방향 연동. `salesmap_records`는 mock 페이로드 기록용 | [application.properties](../backend/src/main/resources/application.properties) `salesmap.api.mode=mock`, [MockSalesmapClient.java](../backend/src/main/java/com/salesmap/backend/salesmap/client/MockSalesmapClient.java) |
| (언급 없었음) | **로컬 ML 모듈이 2개 더 있음** — BGE-M3 임베딩 분류기, GLiNER NER. 둘 다 **기본 비활성(`false`)** | [config.py](../ai-module/app/config.py) |
| (언급 없었음) | **백엔드 기본값이 `ai.module.mode=mock`.** 환경변수 없이 켜면 AI 모듈을 호출하지 않고 "ABC Corp" 고정 응답을 반환 | [application.properties](../backend/src/main/resources/application.properties), [MockAiClient.java](../backend/src/main/java/com/salesmap/backend/ai/client/MockAiClient.java) |
| "JANDI는 API가 없어 무산" | 수집(조회)은 맞음. **Incoming Webhook(외부 → 잔디 발송)은 공식 제공** → 3-1에서 상세 | [JANDI 인커밍 웹훅 문서](https://support.jandi.com/ko/articles/6352697) |

## 1-1. 모듈 구성

| 모듈 | 스택 | 규모 | 비고 |
| --- | --- | --- | --- |
| `backend` | Spring Boot 3.5.14 / Java 17 / JPA / MySQL / JWT | Java 소스 약 120파일 | `ddl-auto=update` (마이그레이션 도구 없음) |
| `ai-module` | FastAPI / Pydantic / Ollama(qwen2.5:3b) | Python 2,985 LOC (`analyzer.py` 혼자 1,432) | 선택적으로 sentence-transformers, GLiNER |
| `frontend` | React 19 / Vite 8 / Tailwind 4 / axios | JSX 5,516 LOC (`Dashboard.jsx` 1,441, `MessageView.jsx` 1,435) | 테스트 0개 |

## 1-2. 기능별 구현 현황

범례: **완성** = 실제 데이터로 end-to-end 동작 / **부분** = 동작하나 제약·미완 존재 / **목업** = 하드코딩·가짜 응답

| # | 기능 | 상태 | 실제 구현 범위 | 주요 파일 · 엔드포인트 |
| --- | --- | --- | --- | --- |
| 1 | 회원가입/로그인(JWT) | 완성 | BCrypt + HS256 JWT, `@AuthenticationPrincipal`로 소유권 검증 | [AuthController.java](../backend/src/main/java/com/salesmap/backend/auth/controller/AuthController.java) · `POST /api/auth/signup`, `/login`, `GET /api/auth/me` |
| 2 | Gmail OAuth 연동 | 완성 | authorize URL 생성 → callback 토큰 교환·저장, refresh token 재발급, disconnect | [GmailIntegrationService.java](../backend/src/main/java/com/salesmap/backend/gmail/service/GmailIntegrationService.java) · `GET /api/integrations/gmail/authorize`, `/callback`, `DELETE /api/integrations/gmail` |
| 3 | Gmail 메일 수집 | 완성 | 증분 수집(`lastSyncedAt` 기준 `after:` 쿼리), 폴백 쿼리 체인, threadId → `source_groups`, 중복 `externalSourceId` 스킵, HTML 제거·엔티티 디코딩, 발신/수신 방향 판정 | [GmailIntegrationService.java](../backend/src/main/java/com/salesmap/backend/gmail/service/GmailIntegrationService.java) `collectMessages` · `POST /api/integrations/gmail/collect` |
| 4 | 메일 분석(단건/스레드) | 완성 | Source(또는 SourceGroup) → AI 모듈 `/analyze` → `analyses` 저장. **SENT 메일은 분석 거부** | [AnalysisService.java](../backend/src/main/java/com/salesmap/backend/analysis/service/AnalysisService.java) · `POST /api/analysis`, `/api/analysis/group` |
| 5 | 발신자 문맥 기억 | 완성 | 같은 `senderEmail`의 최근 분석 5건을 AI 요청에 동봉, 고객사·제품 빈칸을 과거값으로 채움 | `AnalysisService.getRecentSenderAnalyses` + `analyzer.py` `first_recent_value` |
| 6 | 영업/비영업 분류 | **부분** | 100% 규칙. 발신자 도메인 블랙리스트 + 키워드 카운트(2개 이상 → SALES_ACTIVITY). LLM 미관여 | `analyzer.py` `classify_business_email` |
| 7 | 처리 유형 판정(CREATE/UPDATE/CANCEL/CONFIRM/UNKNOWN) | **부분** | 100% 규칙 + 6단 후처리 가드: `classify_action_type` → `apply_rule_action_corrections` → `merge_embedding_action` → `suppress_non_schedule_action` → `apply_reschedule_request_guard` → `apply_final_safety_guards` → (LLM 후) `apply_precision_action_corrections` | `analyzer.py` `analyze_message` |
| 8 | 개체 추출(고객사·제품·금액·참석자) | **부분** | 금액·참석자·날짜·시간은 정규식, 고객사·제품은 정규식 + (선택)GLiNER + LLM 보정 | `analyzer.py` `extract_customer` / `extract_product` / `extract_amount` |
| 9 | 일정 날짜/시간 추출 | **부분** | 정규식만. **"다음 주 화요일"류 상대 표현을 `datetime.now()` 기준으로 해석** → 메일 발송 시각(`sentAt`)을 쓰지 않음 (부채 C1) | `analyzer.py` `extract_date` / `extract_time` / `next_weekday` |
| 10 | LLM 의미 보정 | 완성 | Ollama `/api/generate`, `format:"json"`, 실패 시 규칙 결과로 폴백 | [ollama_client.py](../ai-module/app/services/ollama_client.py) |
| 11 | 임베딩 분류기(BGE-M3) | **부분/비활성** | 라벨별 예문을 이어붙인 프로토타입과 코사인 유사도 비교. `EMBEDDING_CLASSIFIER_ENABLED=false`가 기본 | [embedding_classifier.py](../ai-module/app/services/embedding_classifier.py) |
| 12 | NER(GLiNER) | **부분/비활성** | `customer company / product or service / person` 라벨 추출. `GLINER_ENABLED=false`가 기본 | [entity_extractor.py](../ai-module/app/services/entity_extractor.py) |
| 13 | 분석 결과 수정 | **부분** | 11개 필드 PATCH. **`businessType`·`businessRelevanceScore`는 수정 불가** → 영업/비영업 오분류를 사용자가 고칠 수 없음 | [AnalysisUpdateRequest.java](../backend/src/main/java/com/salesmap/backend/analysis/dto/AnalysisUpdateRequest.java) · `PATCH /api/analysis/{id}` |
| 14 | 승인 → 일정 반영 → 캘린더 동기화 | 완성 | `markApproved()` → CREATE/UPDATE/CANCEL 분기 → Google Calendar insert/update/delete | [SalesmapService.java](../backend/src/main/java/com/salesmap/backend/salesmap/service/SalesmapService.java), [GoogleCalendarEventService.java](../backend/src/main/java/com/salesmap/backend/calendar/service/GoogleCalendarEventService.java) · `POST /api/salesmap/register` |
| 15 | 일정 충돌 검사 | 완성 | 동일시각+유사제목=중복(강제 불가), 동일시각/전후 3시간=경고(`force=true`로 통과) | [ScheduleConflictService.java](../backend/src/main/java/com/salesmap/backend/schedule/service/ScheduleConflictService.java) |
| 16 | Salesmap 레코드 등록 | **목업(기본값)** | `salesmap.api.mode=mock` → `mock-salesmap-{analysisId}` 반환. HTTP 구현체는 존재하나 실제 엔드포인트 미검증 | [MockSalesmapClient.java](../backend/src/main/java/com/salesmap/backend/salesmap/client/MockSalesmapClient.java) |
| 17 | 답장 초안 생성 | 완성 | 템플릿 먼저 생성 → Ollama가 개선 시 대체 → 저품질(한자/금칙어) 감지 시 템플릿 폴백. **DB에 저장하지 않음** | [reply_draft.py](../ai-module/app/services/reply_draft.py) · `POST /api/analysis/{id}/reply-draft` |
| 18 | 답장 전송(Gmail) | 완성 | RFC822 base64url, threadId로 스레드 유지, 타임라인에 `REPLY_SENT` 기록 | `GmailIntegrationService.sendReplyEmail` · `POST /api/analysis/{id}/send-reply` |
| 19 | 고객 타임라인 | 완성 | `customer_contacts` upsert + `customer_activities` 6종 이벤트. 조회 시 과거 분석 backfill | [CustomerTimelineService.java](../backend/src/main/java/com/salesmap/backend/customer/service/CustomerTimelineService.java) · `GET /api/customers`, `/{id}/timeline` |
| 20 | 딜 파이프라인 | 완성 | **저장하지 않고 조회 시 계산.** 발신 이메일로 묶고 최신 `actionType` → 5단계(리드/미팅예정/일정조율/확정/보류·취소), 3일 무응답 시 `stale` | [PipelineService.java](../backend/src/main/java/com/salesmap/backend/customer/service/PipelineService.java) · `GET /api/customers/pipeline` |
| 21 | 자동 수집·분석 모드 | **부분** | 45초 스케줄러. **on/off가 인메모리(`ConcurrentHashMap`)** → 서버 재시작 시 초기화. 한 틱 5건, 최근 14일·최신 25건만 대상 | [AutoPipelineService.java](../backend/src/main/java/com/salesmap/backend/autopipeline/service/AutoPipelineService.java) · `POST /api/auto-pipeline/enable` |
| 22 | 처리 이력 화면 | 완성 | 승인대기/등록됨/삭제됨/분석전 필터 + 우선 확인(변경·취소) 강조 | [ProcessingHistoryPage.jsx](../frontend/src/components/ProcessingHistoryPage.jsx) |
| 23 | 대시보드 캘린더 | **목업 혼합** | **데모용 가짜 일정 9건을 실제 일정과 항상 합쳐서 렌더링** (`[...getDemoEvents(), ...backendEvents]`) | [Dashboard.jsx](../frontend/src/components/Dashboard.jsx) |
| 24 | 일정 알림(리마인드) | **부분(프론트 전용)** | 로그인 시 "오늘·내일" 요약, 60초 폴링으로 "30분 전" 팝업. **서버 스케줄·메일·푸시 없음**, `schedules.reminder_date_time` 컬럼은 항상 null | [DashboardLayout.jsx](../frontend/src/components/DashboardLayout.jsx) + [ReminderModal.jsx](../frontend/src/components/ReminderModal.jsx) |
| 25 | Salesmap 로그인 | **목업** | API 호출 없이 `localStorage.salesmapConnected='true'` 설정 | [SalesmapLoginPage.jsx](../frontend/src/components/SalesmapLoginPage.jsx) |
| 26 | JANDI 연동 | **미구현(안내 화면)** | "준비 중" 정적 페이지 | [JandiComingSoon.jsx](../frontend/src/components/JandiComingSoon.jsx) |

## 1-3. 데이터 흐름 — 메일 수집부터 Salesmap 반영까지

```
Gmail API
  │ GmailIntegrationService.collectMessages()
  ▼
source_groups (threadId 단위)   ← 1 thread = 1 row
sources        (message 단위)   ← title, content, sender_email, direction(SENT/RECEIVED), sent_at, status=COLLECTED
  │ AnalysisService.createAnalysis / createGroupAnalysis
  │   요청 동봉: requester, messages[], existingSchedules[](유저의 모든 일정), recentSenderAnalyses[](같은 발신자 최근 5건)
  ▼
FastAPI /analyze
  ├ 규칙 분석 (action / business / date / time / amount / attendees)
  ├ (옵션) BGE-M3 분류 · GLiNER NER
  └ Ollama 의미 보정 (NON_BUSINESS면 스킵)
  ▼
analyses  ← customer_name, contact_name, product_name, amount, attendees, action_type,
            target_schedule_id/title, action_reason, business_type, business_relevance_score,
            business_reason, schedule_text("제목 (2026-10-07T14:00)" 문자열!), follow_up_action,
            summary, status=ANALYZED, analyzed_at
  │ (동시) CustomerTimelineService.recordAnalysis()
  ▼
customer_contacts (user+email unique) / customer_activities(AI_ANALYZED)
  │
  │ [사용자] PATCH /api/analysis/{id} → analyses 행을 덮어씀 (AI 원본 소실 ⚠)
  │ [사용자] POST /api/salesmap/register
  ▼
ScheduleConflictService.validateBeforeRegister()  → 충돌 시 409
SalesmapClient.register()                          → (기본 mock) salesmap_records 저장
analysis.markApproved()                            → status=APPROVED, approved_at
AnalysisService.applyApprovedScheduleAction()
  ├ CREATE → schedules INSERT (+ analysis.linkCreatedSchedule)
  ├ UPDATE → schedules UPDATE
  └ CANCEL → schedule.cancel() → Calendar 삭제 → schedules DELETE
  ▼
Google Calendar (events.insert/update/delete, google_calendar_event_id를 schedules에 저장)
  ▼
Salesmap (캘린더 양방향 연동으로 TODO 생성 — 우리 코드 밖, 설정으로 처리)
```

### 저장 테이블 요약

| 테이블 | 무엇이 저장되나 | 학습 데이터로서의 가치 |
| --- | --- | --- |
| `sources` | 메일 원문·제목·발신자·방향·발송시각 | ★★★ 입력 X |
| `analyses` | AI 결과 **와** 사용자 수정 결과가 **같은 컬럼에 덮어써짐** | ★☆☆ 라벨 y로 쓰려면 스키마 변경 필수 |
| `schedules` | 승인 후 확정된 일정 + `google_calendar_event_id` | ★★☆ 일정 판단의 사실상 정답 |
| `customer_contacts` / `customer_activities` | 고객별 이벤트 로그(분석·일정·등록·답장) | ★★★ 응답지연·빈도 피처의 원천 |
| `salesmap_records` | (mock) 요청/응답 페이로드 | ★☆☆ |
| `integrations` | OAuth 토큰(**평문 저장**) | — (부채 D2) |

## 1-4. LLM 호출 지점 — 전부 2곳

### (A) 분석 의미 보정 — `ai-module/app/services/ollama_client.py`

| 항목 | 내용 |
| --- | --- |
| 호출 위치 | `analyzer.py` `enhance_with_ollama()` → `ollama_client.request_llm_analysis_patch()` |
| 호출 조건 | `analysisMode`(또는 `AI_ENGINE`)가 `ollama/llm/hybrid` **그리고** 규칙 판정이 `NON_BUSINESS`가 아닐 때 |
| API | `POST {OLLAMA_BASE_URL}/api/generate`, `stream:false`, `format:"json"`, `temperature:0.1`, `num_predict:700`, timeout 45s |
| 프롬프트 | 한국어. 역할 지정 + 6개 필드 규칙 + **예시 1건(1-shot)** + 규칙 힌트 JSON + 최근 발신자 기억 JSON + 메일 본문(`text[:4000]`). 정적부 **1,229자** |
| 출력 형식 | `{customerName, contactName, productName, attendees[], scheduleTitle, summary}` |
| 파싱 | `json.loads` 실패 시 `re.search(r"\{.*\}", DOTALL)`로 재시도 → 실패 시 `RuntimeError` |
| 검증 | `normalize_patch()`가 enum·숫자·리스트 타입 정리. `_NULL_TOKENS`("null","없음","-" 등) 문자열은 None 처리 |
| 병합 | `merge_llm_patch()` — **LLM 값은 규칙값이 비어 있을 때만 채택.** 날짜·시간·금액·actionType·businessType은 규칙값 고정 |
| 실패 시 | 전체 규칙 결과 반환 |

### (B) 답장 초안 — `ai-module/app/services/reply_draft.py`

| 항목 | 내용 |
| --- | --- |
| 호출 위치 | `routes/analyze.py` `/reply-draft` → `generate_reply_draft()` |
| 순서 | **① 템플릿 먼저 생성**(`build_template_reply`) → ② Ollama 호출 → ③ 품질검사 통과 시 LLM 결과 채택, 아니면 템플릿 |
| 프롬프트 | 영어 지시문 14줄 + 컨텍스트 JSON(원문 4,000자 + 분석 필드 + fallback 초안 전체). 정적부 **1,428자** |
| 출력 형식 | `{subject, body}` |
| 품질 게이트 | `is_low_quality_draft()` — 한자(`[一-鿿]`) 포함, `"meeting"`, `"??"`, **`"조현우님"`**, `"컴퓨터·AI학부님"` 등 금칙어 |
| 응답 표시 | `generatedBy`: `"hybrid-template"` / `"ollama:qwen2.5:3b"` / `"mock"` |
| 저장 | **없음.** `@Transactional(readOnly = true)`로 생성만 하고 DB에 남기지 않음 |

### 규칙 vs LLM 경계선 (한눈에)

| 출력 필드 | 담당 |
| --- | --- |
| `actionType` (CREATE/UPDATE/CANCEL/CONFIRM/UNKNOWN) | **규칙 전담** — LLM 값은 `merge_llm_patch`에서 무시 |
| `businessType`, `businessRelevanceScore` | **규칙 전담** (옵션: 임베딩 분류기가 제한적으로 보정) |
| `scheduleDateTime` (날짜·시간) | **정규식 전담** |
| `amount` | **정규식 전담** |
| `attendees` | 정규식 → 메일 헤더 → GLiNER → LLM 보정 가능 |
| `customerName`, `productName`, `contactName` | 정규식 → GLiNER → 발신자 기억 → **LLM 보정** |
| `scheduleTitle` | 규칙 제목 → LLM 제목 (날짜가 섞이면 제품명 기반 재구성) |
| `summary` | 최종 필드로 **규칙이 재조립** (LLM summary는 사실상 버려짐) |
| `confidenceScore` | **상수 대입** |

> **핵심 결론: "LLM을 OpenAI로 바꾼다"만으로는 분류·일정 판정 정확도가 거의 변하지 않습니다.** 판정 로직 자체가 LLM 밖에 있기 때문입니다. 단계 2에서 이 경계선을 **의도적으로 재설계**하는 것이 진짜 개선 포인트입니다.

## 1-5. ⭐ 사용자 승인/수정 기록은 DB에 남는가 — 학습 기능의 전제

**결론: AI 원본과 사용자 수정 결과가 구분되어 저장되지 않습니다.**

| 확인 항목 | 결과 | 근거 |
| --- | --- | --- |
| AI 원본 결과 스냅샷 컬럼 | **없음** | `Analysis.java` 전체 필드 확인 |
| 수정 방식 | `updateEditableFields()`가 11개 필드를 **그대로 대입(덮어쓰기)** | `Analysis.java` |
| 수정 이력 테이블 | **없음** | 엔티티 전수 확인 |
| 수정 여부 플래그 | **없음** (`updated_at`만 갱신되나 승인·일정연결로도 갱신되어 구분 불가) | `BaseEntity.java` |
| 승인 시각 | `approved_at` 있음 (승인 여부/시점은 구분 가능) | `Analysis.java` `markApproved` |
| 승인 없이 거부 | `AnalysisStatus.REJECTED` enum이 있으나 **어디에서도 사용되지 않음** | `grep REJECTED` 결과 = 선언 1건뿐 |
| 영업/비영업 오분류 교정 | **불가능** — `businessType`이 수정 가능 필드에 없음 | `AnalysisUpdateRequest.java` |
| 답장 초안 AI 원본 vs 실제 발송본 | **비교 불가** — 초안은 저장 안 하고, 발송본만 `customer_activities.description`(REPLY_SENT)에 텍스트로 남음 | `AnalysisService.generateReplyDraft` / `CustomerTimelineService.recordReplySent` |

### 이것이 의미하는 것

- 지금 DB에서 **"AI가 틀렸고 사람이 이렇게 고쳤다"는 쌍(pair)을 단 한 건도 복원할 수 없습니다.**
- 따라서 few-shot 예시 자동 선택, 경량 분류기 학습, 파인튜닝, 오류 대시보드 — **모든 학습형 기능의 공통 전제 조건**이 "AI 원본 스냅샷 + 수정 델타 저장"입니다.
- 다행히 **변경 비용은 작습니다.** `analyses`에 원본 스냅샷 컬럼을 추가하거나 `analysis_revisions` 테이블 1개를 신설하고, `updateEditableFields` 호출부 1곳에서 이전 값을 기록하면 됩니다. 이 작업은 **1주차에 즉시 착수해야** 나머지 7주 동안 데이터가 쌓입니다.

## 1-6. 확장 시 걸림돌 (기술 부채)

### A. 구조

| ID | 항목 | 내용 | 확장 영향 |
| --- | --- | --- | --- |
| A1 | `analyzer.py` 단일 파일 1,432줄 | 규칙·병합·가드·유틸이 한 파일. **파일 끝에 `apply_final_safety_guards`, `apply_reschedule_request_guard`, `is_strong_non_business_email`, `looks_like_new_request_after_past_cancel`, `is_weak_embedding_only_action` 5개 함수가 중복 정의되어 앞 정의를 덮어씀** ("인코딩 안전용" 주석과 함께 유니코드 이스케이프로 재작성) | 수정 시 어느 정의가 실행되는지 혼동. Phase2 착수 전 정리 필요 |
| A2 | 액션 판정이 6단 파이프라인 | 5개 보정 함수가 순차로 결과를 뒤집는데, **어느 단계가 최종 판정을 냈는지 로그에 남지 않음** | 정확도 개선 시 원인 추적 불가 → 평가 하네스에 **단계별 추적(trace)** 필요 |
| A3 | `schedule_text`가 문자열 | `"제목 (2026-10-07T14:00)"` 포맷을 백엔드가 정규식으로 다시 파싱 | 일정 관련 신규 기능마다 파싱 코드 중복. 컬럼 분리 권장 |
| A4 | AI 모듈에 상태·큐 없음 | 동기 HTTP 1회. 백엔드 read timeout 180초 | STT(수 분 소요)·배치 처리 추가 시 비동기 잡 구조 필요 |
| A5 | `ddl-auto=update` | 마이그레이션 도구 없음 | 스키마를 4명이 동시에 바꾸면 충돌. **Flyway 도입 권장** |

### B. 하드코딩 · 시연용 분기

| ID | 위치 | 내용 |
| --- | --- | --- |
| B1 | `reply_draft.py` `extract_contact_label()` | 데모 고객사 → 담당자명 매핑 하드코딩 (`GreenSoft→박서준님`, `Delta Systems→최유진님`, `Nimbus Tech→김도윤님`, `ABC 고객사→김민수님`) |
| B2 | `reply_draft.py` `is_low_quality_draft()` | 품질 게이트 금칙어에 데모 특정 인물명 `"조현우님"`, `"컴퓨터·AI학부님"` 포함 |
| B3 | `analyzer.py` (구버전 `apply_final_safety_guards`) | 비영업 메일에 `contact_person="GreenSoft 박서준"` 대입 — **A1의 중복 정의 덕분에 실행되지 않지만 코드에 남아 있음** |
| B4 | `Dashboard.jsx` | 데모 일정 9건을 실제 일정과 **항상 합쳐서** 표시 |
| B5 | `SalesmapLoginPage.jsx` | Salesmap 로그인이 API 없이 localStorage 플래그만 설정 |
| B6 | `application.properties` | `ai.module.mode`·`salesmap.api.mode` 기본값이 `mock` — 환경변수 미설정 시 조용히 가짜 결과 반환 |
| B7 | `analyzer.py` 키워드 상수 | `NON_BUSINESS_WORDS` / `is_strong_non_business_email`의 `"동아리" "강의실" "학생" "택배"` 등이 **학생 메일함 기준**으로 튜닝됨 → 실제 영업 현장 일반화 미검증 |

### C. 정확도 관련

| ID | 내용 |
| --- | --- |
| C1 | **상대 날짜를 분석 시각 기준으로 해석** — "다음 주 화요일"을 `datetime.now()`로 계산. 메일 `sentAt`을 AI 모듈에 보내고 있는데도 쓰지 않음. 며칠 지난 메일을 재분석하면 날짜가 틀어짐 |
| C2 | 시간대 상수 `TIMEZONE="Asia/Seoul"`가 선언만 되고 사용되지 않음 |
| C3 | `select_target_schedule`에 유저의 **모든** 일정을 매번 전달 (필터·페이징 없음) |
| C4 | `findTargetSchedule`의 폴백이 "같은 발신자의 아무 일정이나 최신순 첫 번째" |
| C5 | 그룹 분석은 스레드 전체 본문을 `"\n".join()`으로 이어붙여 1건으로 분석 → **최신 메시지 의도가 과거 문맥에 희석됨** |

### D. 테스트·운영

| ID | 내용 |
| --- | --- |
| D1 | **테스트가 사실상 0개.** 백엔드 `BackendApplicationTests`(컨텍스트 로딩) 1개, AI 모듈·프론트 0개. 단, `demo/demo-playbook.md`에 **정답 라벨이 달린 예시 메일 21통**이 있음 → 2-5 평가셋의 씨앗 |
| D2 | OAuth access/refresh token이 **평문** 저장 (`Integration.java`) |
| D3 | 자동 파이프라인 on/off가 인메모리 → 재시작 시 소실 |
| D4 | `CustomerTimelineService.getCustomers()`가 호출될 때마다 유저의 **전체 분석을 backfill** → 건수 증가 시 선형 악화 |
| D5 | 레포 루트에 JVM 크래시 로그 4개(`hs_err_pid*.log`, `replay_pid*.log`, 약 2MB)가 방치 → `.gitignore` 추가 필요 |
| D6 | 로컬 LLM 1건당 수십 초 → 자동 분석이 틱당 5건으로 제한. **처리량이 곧 병목** |

---

# 단계 2. LLM 고도화 (로컬 Qwen2.5 → 유료 API)

## 2-0. 확인한 공식 가격 (2026-09-21, developers.openai.com 기준)

| 모델 | 입력 / 1M | 캐시 입력 / 1M | 출력 / 1M |
| --- | --- | --- | --- |
| gpt-5.6-sol | $4.00 | $0.40 | $20.00 |
| gpt-5.6-terra | $2.00 | $0.20 | $12.00 |
| **gpt-5.6-luna** | **$0.20** | **$0.02** | **$1.20** |
| gpt-5.4-mini | $0.75 | $0.075 | $4.50 |
| gpt-5.4-nano | $0.20 | $0.02 | $1.25 |
| gpt-5-nano | $0.05 | $0.005 | $0.40 |
| text-embedding-3-small | $0.02 | — | — |
| gpt-4o-transcribe | $2.50 | — | $10.00 |
| gpt-4o-mini-transcribe | $1.25 | — | $5.00 |
| Whisper | $0.006 / 오디오 1분 | — | — |

- **프롬프트 캐싱: 캐시된 입력 토큰 90% 할인** (표의 "캐시 입력" 열이 이미 반영된 값)
- **Batch API: 50% 할인** (24시간 내 비동기 처리)
- 팀이 조사한 Luna $0.20/$1.20, Terra $2/$12는 **정확**합니다. Sol만 출처마다 다른데($5/$30 vs $4/$20), 이는 2026-08-21부터 3개월간 적용 중인 한시 인하 때문으로 보입니다 — **Sol은 쓸 계획이 없으므로 영향 없음**.
- 참고: [OpenAI 공식 가격](https://developers.openai.com/api/docs/pricing) · [GPT-5.6 티어 설명](https://www.vellum.ai/blog/gpt-5-6-sol-terra-luna-explained)

## 2-1. 우리 사용량 기준 월 비용 추정

### 토큰 수 산정 근거 (단계 1에서 측정한 실제 프롬프트 기준)

| 호출 | 구성 | 문자 수 | 추정 토큰 |
| --- | --- | --- | --- |
| **분석(A)** 입력 | 정적 지시문+예시 1,229자 | | ~800 |
| | 규칙 힌트 + 발신자 기억 JSON | ~250자 | ~150 |
| | 메일 본문 (데모 메일 평균 ~450자, 실메일 여유 600자) | ~600자 | ~500 |
| | **합계** | | **~1,450** |
| **분석(A)** 출력 | 6필드 JSON | | **~120** |
| **답장(B)** 입력 | 영어 지시문 1,428자 | | ~370 |
| | 컨텍스트 JSON(원문+분석필드+fallback 초안) | ~900자 | ~730 |
| | **합계** | | **~1,100** |
| **답장(B)** 출력 | 한국어 4~7문장 | | **~300** |

> 한국어 토큰화는 o200k_base 기준 대략 1토큰 ≈ 한글 1.2자로 잡았습니다. ±20% 오차 예상.

### 1,000건당 단가 (캐싱 미적용)

| 작업 | Luna | 5.4-nano | Terra |
| --- | --- | --- | --- |
| 분석 1,000건 | **$0.43** | $0.44 | $4.34 |
| 답장 1,000건 | **$0.58** | $0.60 | $5.80 |

### 월 비용 시나리오 (영업일 22일 기준)

"하루 메일 50통"이 팀 합산인지 1인당인지 불분명해서 두 경우 모두 계산했습니다.

| 시나리오 | 월 분석 건수 | 월 답장 건수 | 라우팅 | **월 비용** | 원화(₩1,400/$) |
| --- | --- | --- | --- | --- | --- |
| **A. 팀 합산 50통/일** (현실적 베이스) | 1,100 | 220 | 분석=Luna, 답장=Luna | **$0.61** | ~850원 |
| A' | 1,100 | 220 | 분석=Luna, 답장=**Terra** | **$1.76** | ~2,500원 |
| **B. 1인당 50통/일 (5명 = 250통/일)** | 5,500 | 1,100 | 분석=Luna, 답장=**Terra** | **$8.77** | ~12,300원 |
| C. 최악(전부 Terra, B 물량) | 5,500 | 1,100 | 전부 Terra | **$30.25** | ~42,000원 |
| B+캐싱 적용 | 5,500 | 1,100 | 분석=Luna(정적부 캐시), 답장=Terra | **~$7.4** | ~10,400원 |

> **결론: 비용은 의사결정 변수가 아닙니다.** 최악의 시나리오에서도 월 $30(4만원). 4명이 커피 한 번 덜 마시면 되는 수준이므로, **품질이 필요한 곳에는 주저 없이 상위 모델을 쓰세요.** 단, 평가·재현 실험을 반복하면 토큰이 예상보다 빨리 소모되니 **하드 상한(2-3)**은 반드시 넣어야 합니다.

### 시연/발표용 참고 수치

- 현재 로컬 Qwen2.5:3b — **1건당 수십 초**(`AutoPipelineService`가 틱당 5건으로 제한한 이유). 50통 자동 분석에 최소 7~8분.
- Luna/nano급 API — **1건당 1~3초 예상**. 50통 병렬 처리 시 1분 이내. → **"처리 시간 N배 단축"이 비용보다 설득력 있는 발표 수치**입니다. (실측 필요)

## 2-2. 기능별 모델 라우팅 전략

| 작업 | 특성 | 권장 모델 | 이유 |
| --- | --- | --- | --- |
| 영업/비영업 분류 | 2~3지 분류, 짧은 출력 | **gpt-5.4-nano 또는 5.6-luna** | 분류·라우팅은 Luna 티어의 설계 목적. 캐싱 + Batch 조합 시 사실상 무료 |
| 처리 유형(CREATE/UPDATE/CANCEL/CONFIRM) | 5지 분류 + 근거 1줄 | **gpt-5.6-luna** | 근거 문장 생성이 있어 nano보다 luna 권장 |
| 개체 추출(고객사·제품·금액·참석자·일정) | 구조화 추출, Structured Outputs 필수 | **gpt-5.6-luna** (오답 많으면 terra로 승급) | 스키마 강제 시 저가 모델로도 충분. 2-5 평가로 결정 |
| 답장 초안 | 자연스러운 한국어 비즈니스 문체 | **gpt-5.6-terra** | 지금 로컬 모델의 최대 약점(한자 혼입·어색한 조사)이 정확히 이 구간. 품질 차이가 눈에 보이는 유일한 기능 |
| 미팅 전 브리핑(신규) | 긴 컨텍스트 요약·종합 | **gpt-5.6-terra** | 타임라인 + 과거 메일 + 회의록을 묶어 요약. 입력이 길어 캐싱 효과 큼 |
| 회의록 정리(STT 후처리, 신규) | 긴 텍스트 구조화 | **gpt-5.6-terra** | 화자 정리·액션아이템 추출 |
| 임베딩(few-shot 예시 검색, 신규) | 벡터화 | **text-embedding-3-small** | $0.02/1M. 사실상 무료 |
| 회의 음성 → 텍스트(신규) | 한국어 STT | **확인 필요** — Whisper($0.006/분) vs gpt-4o-mini-transcribe vs 국내 STT(CLOVA Speech, ReturnZero VITO) | 한국어 + 비즈니스 용어 + 화자분리 품질은 실측 비교 필요. 4-4 리스크 참조 |

### 라우팅 원칙 3줄

1. **출력이 enum·짧은 JSON이면 Luna/nano.** 출력이 사람이 읽는 문장이면 Terra.
2. **정적 프롬프트(지시문+few-shot)를 항상 맨 앞에 고정**해서 캐싱이 걸리게 한다.
3. **하나라도 승급이 필요하면 평가셋 숫자로 증명한 뒤 승급**한다 (감으로 올리지 않는다).

## 2-3. 구조 개선안

### (1) Structured Outputs — 파싱 실패 제거

현재 `ollama_client.parse_json_object()`는 JSON 파싱이 실패하면 정규식으로 `{...}`를 다시 긁고, 그것도 실패하면 `RuntimeError` → 전체 LLM 결과를 버립니다. OpenAI의 `response_format: {type:"json_schema", strict:true}`를 쓰면 **스키마 위반 자체가 발생하지 않아** 이 폴백 경로가 통째로 불필요해집니다. GPT-4o(2024-08-06) 이후 모델 전부 지원합니다 ([문서](https://developers.openai.com/api/docs/guides/structured-outputs)).

제안 스키마 (분석용):

```json
{
  "name": "sales_email_analysis",
  "strict": true,
  "schema": {
    "type": "object",
    "additionalProperties": false,
    "required": ["businessType","actionType","customerName","contactName",
                 "productName","attendees","amount","scheduleDate","scheduleTime",
                 "scheduleTitle","summary","reason"],
    "properties": {
      "businessType": {"enum": ["SALES_ACTIVITY","NON_BUSINESS","UNKNOWN"]},
      "actionType":   {"enum": ["CREATE","UPDATE","CANCEL","CONFIRM","UNKNOWN"]},
      "customerName": {"type": ["string","null"]},
      "contactName":  {"type": ["string","null"]},
      "productName":  {"type": ["string","null"]},
      "attendees":    {"type": "array", "items": {"type": "string"}},
      "amount":       {"type": ["integer","null"]},
      "scheduleDate": {"type": ["string","null"], "description": "YYYY-MM-DD"},
      "scheduleTime": {"type": ["string","null"], "description": "HH:MM"},
      "scheduleTitle":{"type": ["string","null"]},
      "summary":      {"type": "string"},
      "reason":       {"type": "string"}
    }
  }
}
```

**중요한 설계 결정**: 이 스키마는 지금 규칙이 독점하던 `businessType`·`actionType`·날짜·금액을 **LLM에게도 요구**합니다. 그래야 "LLM 교체로 좋아졌다"를 측정할 수 있습니다. 다만 한 번에 규칙을 버리지 말고 **3단계로**:

| 단계 | 구성 | 목적 |
| --- | --- | --- |
| Phase 0 (현재) | 규칙 100% + LLM 의미보정 | 기준선(baseline) |
| Phase 1 | 규칙과 LLM을 **둘 다 돌리고 결과를 나란히 저장**, 최종 채택은 규칙 | 무위험으로 비교 데이터 수집 |
| Phase 2 | 평가셋에서 LLM이 이긴 필드만 LLM 채택, 진 필드는 규칙 유지 | **근거 있는 하이브리드** (발표 포인트) |

> Phase 1은 위험이 0이면서 발표용 비교표를 자동 생성해 줍니다. **1~2주차에 반드시 깔아 두세요.**

### (2) 프롬프트 캐싱

- OpenAI 캐싱은 **프롬프트 접두사(prefix)가 일정 길이(약 1,024토큰) 이상일 때** 자동 적용됩니다.
- 현재 분석 프롬프트 정적부는 약 800토큰으로 **문턱 아래**입니다.
- → **few-shot 예시를 1개에서 4~6개로 늘려** 정적 접두사를 1,200~1,500토큰으로 키우세요. 정확도와 캐시 적중을 동시에 얻습니다 (예시 추가분의 입력 비용은 90% 할인으로 상쇄).
- 순서를 반드시 **[고정 system 지시문 + few-shot] → [가변 컨텍스트(기억/힌트)] → [메일 본문]** 으로 두세요. 가변 부분이 앞에 오면 캐시가 전부 깨집니다. **현재 코드는 힌트 JSON이 본문 앞에 있어 문제없지만, 예시가 본문보다 뒤로 가면 안 됩니다.**

### (3) Batch API 활용처

| 용도 | Batch 적합? |
| --- | --- |
| 자동 파이프라인의 **과거 메일 백필**(최근 14일 넘는 백로그) | ✅ 24시간 내 처리면 충분, 50% 절감 |
| **평가셋 반복 실행**(모델·프롬프트 비교 실험) | ✅ 실험 비용 반감. 8주간 수십 회 돌릴 예정이므로 효과 큼 |
| 야간 **딜 리스크 스코어 재계산** | ✅ |
| 사용자가 버튼 누른 실시간 분석 | ❌ |
| 답장 초안 | ❌ |

### (4) 로컬 LLM을 fallback으로 유지

현 구조(`reply_draft.py`)는 이미 "템플릿 → LLM → 품질 미달 시 템플릿" 3단 폴백입니다. 이걸 그대로 확장:

```
1순위: OpenAI (Structured Outputs)
   ↓ 실패(네트워크/429/5xx/타임아웃/월 상한 초과)
2순위: Ollama Qwen2.5 (현재 로직 그대로)
   ↓ 실패
3순위: 규칙 + 템플릿 (현재 fallback)
```

- **폴백 발동을 반드시 DB에 기록**하세요(`provider`, `model`, `fallback_reason`). 발표에서 "가용성 99.x%"를 말할 근거가 되고, 데모 중 인터넷이 끊겨도 시연이 안 죽습니다 — **이게 로컬 LLM을 남기는 진짜 이유**입니다.
- 폴백은 **2회 연속 실패 시 60초간 차단(circuit breaker)** 으로 감싸세요. 안 그러면 API 장애 시 타임아웃 45초 × 건수만큼 UI가 멈춥니다.

### (5) API 키 · 비용 상한 관리

| 항목 | 권장 |
| --- | --- |
| 키 저장 | `.env` 또는 환경변수. **레포에 절대 커밋 금지.** 현재 `ai-module/.env`가 git에 추적되는지 확인 필요 → `.gitignore` 점검 |
| 키 범위 | 프로젝트 전용 키 1개 + OpenAI 대시보드에서 **월 한도(budget limit)** 설정. 학생 프로젝트는 $20~$50 권장 |
| 애플리케이션 상한 | `llm_usage` 테이블에 호출마다 `input_tokens`/`output_tokens`/`estimated_cost` 기록 → 월 누적이 상한 초과 시 자동으로 로컬 폴백 전환 |
| 요청당 상한 | `max_output_tokens` 명시(분석 300, 답장 800), 입력 본문은 현행대로 4,000자 절단 유지 |
| 재시도 | 429/5xx에 지수 백오프 3회. **재시도 횟수도 비용에 포함해 기록** |
| 노출 경로 | 프론트에서 직접 OpenAI 호출 금지. 반드시 AI 모듈 경유 (현 구조 유지) |

## 2-4. LLM 교체를 쉽게 하는 추상화 계층 (Provider 인터페이스)

### 배치 위치

`ai-module/app/llm/` 를 신설하고, `analyzer.py`·`reply_draft.py`가 직접 `urllib`로 Ollama를 부르는 코드를 전부 이쪽으로 옮깁니다.

```
ai-module/app/
  llm/
    __init__.py
    base.py          # LLMProvider 프로토콜 + 공통 DTO
    openai_provider.py
    ollama_provider.py
    router.py        # 작업(task)별 모델 선택 + 폴백 체인 + 서킷브레이커
    usage.py         # 토큰/비용 집계, 월 상한 체크
  prompts/
    analysis_v1.py   # 프롬프트 + JSON 스키마를 버전으로 관리
    reply_v1.py
    briefing_v1.py
```

### 인터페이스 설계

```python
# app/llm/base.py
from dataclasses import dataclass
from typing import Any, Optional, Protocol

@dataclass(frozen=True)
class LLMRequest:
    task: str                      # "analysis" | "reply" | "briefing" | "meeting_notes"
    system: str                    # 캐시 접두사가 될 고정 지시문 + few-shot
    user: str                      # 가변 컨텍스트 + 본문
    json_schema: Optional[dict]    # Structured Outputs 스키마 (None이면 자유 텍스트)
    max_output_tokens: int = 500
    temperature: float = 0.1

@dataclass(frozen=True)
class LLMResponse:
    data: Any                      # 스키마가 있으면 dict, 없으면 str
    provider: str                  # "openai" | "ollama"
    model: str
    input_tokens: int
    cached_input_tokens: int
    output_tokens: int
    latency_ms: int
    estimated_cost_usd: float
    fallback_reason: Optional[str] = None

class LLMProvider(Protocol):
    name: str
    def supports_schema(self) -> bool: ...
    def generate(self, request: LLMRequest) -> LLMResponse: ...
```

### 라우터

```python
# app/llm/router.py  (개념 코드)
TASK_MODELS = {
    "analysis":      ("openai", "gpt-5.6-luna"),
    "reply":         ("openai", "gpt-5.6-terra"),
    "briefing":      ("openai", "gpt-5.6-terra"),
    "meeting_notes": ("openai", "gpt-5.6-terra"),
}

def generate(request: LLMRequest) -> LLMResponse:
    for provider in resolve_chain(request.task):   # [openai, ollama]
        if circuit_open(provider.name):
            continue
        if usage.month_budget_exceeded():
            continue                                # 상한 초과 시 유료 건너뛰고 로컬로
        try:
            response = provider.generate(request)
            usage.record(response)
            return response
        except LLMError as error:
            trip_circuit(provider.name, error)
    raise AllProvidersFailed()                      # 호출부가 규칙/템플릿으로 폴백
```

### 교체 비용이 낮아지는 이유

- `analyzer.py`는 `generate(LLMRequest(task="analysis", ...))` 한 줄만 호출. 모델·프로바이더를 바꿔도 분석 로직은 변경 0.
- `ollama_provider`는 스키마를 강제할 수 없으므로 `supports_schema()=False`를 반환하고, 라우터가 **기존 정규식 파싱 경로**로 자동 우회.
- `TASK_MODELS`를 환경변수로 덮어쓸 수 있게 하면 **평가 실험 시 코드 수정 없이** 모델만 바꿔 돌릴 수 있습니다 (2-5 필수).

## 2-5. 품질 비교 방법 — "바꿔서 얼마나 좋아졌는지"를 숫자로

### (1) 평가셋 만들기

**출발점이 이미 있습니다.** `demo/demo-playbook.md`에 **정답 라벨이 달린 메일 21통**(영업 12 + 비영업 9)이 4개 고객사 시나리오로 정리되어 있고, 각 메일에 "→ 기대: 일정 생성(CREATE) / 일정=다음 주 화 10:00 / 참석자=김현우, 최지훈" 형태의 기대값이 이미 적혀 있습니다.

| 단계 | 할 일 | 목표 |
| --- | --- | --- |
| 1 | 플레이북 21통을 `ai-module/eval/dataset.jsonl`로 기계 판독 가능하게 변환 | 21건 |
| 2 | 팀원 4명이 각자 실제 받은 메일(개인정보 마스킹 후) + 합성 메일로 확장 | **120~150건** |
| 3 | 라벨링 규칙 문서화 후 **2명 교차 라벨링**, 불일치는 토론으로 확정 | Cohen's κ ≥ 0.8 |
| 4 | 난이도 태그 부여: `easy`(명시적 표현) / `hard`(상대 날짜, 기억 의존, 부정문맥) | hard 30% 이상 |

**구성 권장(150건 기준)**

| 카테고리 | 건수 | 왜 필요한가 |
| --- | --- | --- |
| CREATE (일정 요청) | 30 | 주 시나리오 |
| UPDATE (변경/연기) | 25 | 현재 가드가 가장 복잡한 구간 |
| CANCEL | 15 | |
| CONFIRM | 15 | "확정" 부정문맥 오매칭 케이스 포함 |
| UNKNOWN (자료·견적 전달) | 25 | 과잉 일정생성 방지 검증 |
| NON_BUSINESS | 25 | 뉴스레터·알림·학교메일. **B7 부채 검증용** |
| 기억 의존 (본문에 회사·제품 없음) | 15 | 플레이북 ④번류 — 우리 차별점 |

데이터셋 스키마:

```jsonl
{"id":"eval-031","sent_at":"2026-09-15T09:12:00","sender_email":"kim@daesung.co.kr",
 "sender_name":"김현우","subject":"다음 주 미팅 시간 조정 가능할까요","body":"...",
 "recent_sender_analyses":[{"customerName":"대성물류","productName":"스마트 배차 관제 시스템"}],
 "gold":{"businessType":"SALES_ACTIVITY","actionType":"UPDATE","customerName":"대성물류",
         "productName":"스마트 배차 관제 시스템","contactName":"김현우",
         "attendees":["김현우","최지훈"],"amount":null,
         "scheduleDate":"2026-09-22","scheduleTime":"14:00"},
 "difficulty":"hard","tags":["memory_dependent","reschedule"]}
```

> ⚠ `sent_at`을 데이터셋에 넣고 **평가 시 "현재 시각"으로 주입**해야 합니다. 안 그러면 부채 C1(상대 날짜 해석) 때문에 평가 결과가 실행일마다 달라집니다.

### (2) 측정 지표

| 지표 | 정의 | 목표 |
| --- | --- | --- |
| **영업/비영업 분류 정확도** | accuracy, macro-F1, 혼동행렬 | F1 0.90+ |
| **비영업 오탐률 (FPR)** | 비영업을 영업으로 분류한 비율 | **가장 중요.** 여기가 높으면 노이즈가 파이프라인에 쌓임. 5% 이하 |
| **처리 유형 정확도** | 5-class accuracy + macro-F1 + 혼동행렬 | F1 0.85+ |
| **개체 추출 F1** | 고객사/제품/담당자/참석자 각각. exact match와 **정규화 후 match**(공백·"고객사" 접미사 제거) 둘 다 보고 | 필드별 F1 0.85+ |
| **금액 정확도** | exact match | 0.95+ |
| **일정 판단 정확도** | 날짜 exact, 시간 exact, **(날짜+시간) 동시 정확** | 동시 정확 0.85+ |
| **전체 무수정 통과율** | 모든 필드가 gold와 일치한 비율 = "사람이 손 안 대도 되는 비율" | **발표 핵심 지표** |
| 지연시간 | p50 / p95 (초) | Luna 기준 p95 < 5초 |
| 비용 | 건당 USD | |
| 파싱 실패율 | JSON 파싱 실패 비율 | Structured Outputs 적용 후 **0%** (현재 로컬 모델 실패율 측정해서 대비 제시) |

### (3) 비교 대상 (5개 arm)

| Arm | 구성 | 목적 |
| --- | --- | --- |
| R | 규칙만 (`AI_ENGINE=rule`) | 순수 규칙 기준선 |
| **Q** | **규칙 + Qwen2.5:3b (현재 프로덕션)** | **기준선 — 모든 개선을 이것 대비로 발표** |
| L | 규칙 + gpt-5.6-luna (의미보정만, 현 역할 그대로) | 같은 역할에서 모델만 교체했을 때의 순수 효과 |
| **L+** | **gpt-5.6-luna 단독 판단 (Structured Outputs로 분류·액션·일정까지)** | **경계선 재설계 효과** |
| T+ | gpt-5.6-terra 단독 판단 | 상위 모델 한계치 |
| H | **L+ 중 신뢰도 낮은 건만 terra로 재질의(에스컬레이션)** | 비용/품질 최적점 |

### (4) 실행 방법

```
ai-module/eval/
  dataset.jsonl
  run_eval.py        # ARM 환경변수로 arm 선택, 결과를 results/{arm}_{timestamp}.json 저장
  report.py          # arm 간 비교표 + 혼동행렬 + 케이스별 diff를 Markdown/CSV로 출력
  results/
```

- `run_eval.py`는 **AI 모듈 함수를 직접 호출**(HTTP 경유 X)해서 DB·백엔드 없이 돌 수 있게 합니다.
- OpenAI arm은 **Batch API로 제출**하면 비용 절반 + 레이트리밋 회피. 단 지연시간 지표는 소량 동기 호출로 따로 측정.
- **동일 데이터셋·동일 시드·동일 `sent_at` 주입**을 보장할 것. 이게 안 되면 숫자를 신뢰할 수 없습니다.
- 회귀 방지: GitHub Actions에서 PR마다 arm R/Q만 돌려 **정확도가 떨어지면 실패**시키기 (비용 0).

### (5) 발표용 산출물 (이 4개를 목표로)

1. **막대그래프 1장** — 6개 지표 × 3개 arm(Q / L+ / T+). "로컬 → API 전환 시 처리유형 F1 0.71 → 0.89" 같은 문장이 나오게.
2. **혼동행렬 2장** — Q vs L+ 나란히. 어떤 오류가 사라졌는지 시각적으로 보임.
3. **처리 시간·비용 표** — "건당 38초 → 2.1초, 건당 $0.0004".
4. **실패 케이스 3건 심층 분석** — 개선된 것 2건 + 여전히 틀리는 것 1건. 한계를 스스로 말하는 게 점수에 유리합니다.

## 2-6. 개인정보 — 고객 메일이 외부 API로 나갈 때

### 위험 정리

| 데이터 | 현재 어디까지 나가나 | Phase 2 이후 |
| --- | --- | --- |
| 메일 본문 전체(4,000자) | 로컬 Ollama (내 PC 밖으로 안 나감) | **OpenAI 서버** |
| 발신자 이름·이메일 | AI 모듈 요청에 포함 | OpenAI (프롬프트 컨텍스트) |
| 고객사명·제품·금액 | 로컬 | OpenAI |
| 참석자 실명 | 로컬 | OpenAI |
| OAuth 토큰 | DB **평문** | 변화 없음 (별도 개선 과제) |

**핵심**: 지금까지는 "전부 로컬"이라 개인정보 이슈를 피해 갈 수 있었지만, 외부 API 전환은 **실제 고객사 임직원의 개인정보(성명·이메일·소속)를 국외 제3자에게 제공**하는 행위입니다. 발표에서 이 점을 **먼저 언급하고 대응책을 제시**하는 것이 심사에서 유리합니다.

### 대응 설계

**(1) 마스킹 — 이름·이메일을 토큰으로 치환 후 복원**

```
원문:  "안녕하세요, 대성물류 김현우 부장입니다. 최지훈 대리와 함께 참석합니다.
        회신은 kim@daesung.co.kr로 부탁드립니다."
       ↓ mask()
LLM 입력: "안녕하세요, [ORG_1] [PERSON_1] 부장입니다. [PERSON_2] 대리와 함께 참석합니다.
           회신은 [EMAIL_1]로 부탁드립니다."
       ↓ LLM
결과:  {"customerName":"[ORG_1]","attendees":["[PERSON_1]","[PERSON_2]"]}
       ↓ unmask()
저장:  {"customerName":"대성물류","attendees":["김현우","최지훈"]}
```

| 항목 | 설계 |
| --- | --- |
| 치환 대상 | 이메일 주소, 전화번호, 사람 이름(정규식 `[가-힣]{2,4}\s*(팀장\|과장\|부장\|대리\|대표\|이사)` + 메일 헤더에서 얻은 실명), 조직명 |
| 매핑 저장 | **요청 메모리 내에서만.** DB·로그에 매핑 테이블을 남기지 않음 |
| 위험 | 마스킹이 **추출 정확도를 떨어뜨릴 수 있음** — LLM이 `[ORG_1]`을 고객사로 인식 못 할 가능성. → **2-5 평가셋으로 마스킹 on/off를 A/B 측정**하고, 정확도 손실이 3%p 이상이면 "금액·계약조건만 마스킹"으로 완화 |
| 절충안 | 최소한 **이메일 주소·전화번호만 마스킹**해도 위험의 상당 부분이 줄어들고 정확도 손실은 거의 없습니다. 여기서 시작하세요. |

**(2) 데이터 보존 정책**

| 항목 | 조치 |
| --- | --- |
| OpenAI 학습 이용 | API로 보낸 데이터는 기본적으로 모델 학습에 쓰이지 않음 — 단 **현행 정책을 팀이 직접 확인하고 문서에 인용**할 것 (확인 필요) |
| Zero Data Retention | 엔터프라이즈 옵션이며 학생 계정에는 적용 어려움 (확인 필요). 적용 불가 시 "우리는 마스킹으로 대응했다"가 답변 |
| 우리 로그 | 프롬프트 원문을 **로그에 남기지 않기.** 지금 `HttpAiClient`의 `log.debug("AI Module /analyze request JSON={}")`가 메일 전문을 로그로 찍습니다 — 운영 프로필에서는 꺼야 합니다 |
| 평가셋 | `eval/dataset.jsonl`이 실제 메일 기반이면 **반드시 마스킹 후 커밋**. 가명·가상 회사명 권장 |
| 보관 기간 | `sources.content` 보관 기간 정책(예: 180일 후 본문 삭제, 분석 결과만 유지) 문서화 |
| 사용자 고지 | 설정 화면에 "메일 내용이 분석을 위해 OpenAI API로 전송됩니다" 안내 + on/off 토글(끄면 로컬 LLM 사용). **이 토글 자체가 발표 어필 포인트** |

**(3) 시연 시**

- 데모는 **팀원 계정 + 가상 고객사**로만 진행 (이미 `demo/demo-playbook.md`가 그 구조라 그대로 쓰면 됩니다).
- 실제 멘토사(올포랜드/Salesmap) 메일은 절대 시연 데이터에 넣지 않기.

---

# 단계 3. 추가 기능 아이디어 발굴 및 평가

## 3-1. 기업 멘토 과제 6건 — 우리 코드 기준 평가

| # | 과제 | 우리 코드에 붙일 수 있나 | 필요한 데이터·API | 난이도 | 판정 |
| --- | --- | --- | --- | --- | --- |
| **1** | **영업일 기준 자동 리마인드** | **매우 잘 맞음.** `PipelineService`에 이미 `STALE_DAYS=3` 무응답 판정이 있고, `customer_activities`에 고객별 이벤트 시각이 전부 쌓여 있음. 지금 없는 건 (a) 영업일 계산 (b) **서버 스케줄러** (c) 발송 채널 | 기존 DB만. 발송은 Gmail API(이미 `sendReplyEmail` 구현됨) 또는 JANDI Webhook | **하** | ✅ **채택 권장** |
| 2 | 영수증 검증·인건비 정산 | **결이 다름.** 영수증 이미지 OCR + 회계 규칙 검증은 영업 메일 파이프라인과 공유하는 게 거의 없음. 새 도메인·새 데이터·새 UI | 영수증 이미지, 회계 시스템 API, 인사 데이터 — **우리에게 전혀 없음** | 상 | ❌ 팀 판단에 동의 |
| **3** | **미팅 전 AI 준비 자료 정리** | **가장 자연스러움.** 입력이 전부 이미 있음: `customer_activities`(타임라인) + `sources.content`(과거 메일 전문) + `analyses`(고객사·제품·금액·다음행동) + `schedules`(미팅 시각). LLM 호출 1번 추가하면 초안이 나옴 | 기존 DB만. LLM(terra) | **중하** | ✅ **채택 권장** |
| **4** | **전환 지표 자동 발송(잔디)** | **발송은 가능, 지표는 절반만 가능** — 아래 상세 | JANDI Incoming Webhook URL | 하 | ⚠️ **축소 채택** |
| 5 | 법인 계좌 잔액 대조 | 결이 다름. 은행 오픈뱅킹 API 필요, 영업 데이터와 무관 | 오픈뱅킹/ERP — 없음 | 상 | ❌ |
| 6 | OK 이벤트 개인정보 항목 검토 | 결이 다름. 특정 사내 양식 검토 업무 | 해당 양식·정책 문서 — 없음 | 중 | ❌ |

### 과제 4번 상세 — JANDI는 "발송만" 가능합니다 (팀의 추측이 맞습니다)

**확인 결과**: JANDI 커넥트의 **Incoming Webhook은 공식 기능**입니다. 외부 서비스가 JANDI가 정한 JSON 포맷으로 웹훅 URL에 HTTP POST를 보내면 지정한 대화방에 메시지가 게시됩니다 ([공식 문서](https://support.jandi.com/ko/articles/6352697)).

| 항목 | 내용 |
| --- | --- |
| 방향 | **외부 → JANDI (발송) 전용.** 메시지 읽기/수집은 여전히 불가 |
| 인증 | 웹훅 URL 자체가 토큰 (별도 OAuth 불필요) |
| 포맷 | `Accept: application/vnd.tosslab.jandi-v2+json`, `Content-Type: application/json`, 본문에 `body` + `connectColor` + `connectInfo[]` |
| 제한 | 메시지 최대 5,000자, 페이로드 최대 256KB, **분당 60회 / 10분당 500회** |
| 구현 비용 | **매우 낮음.** 백엔드에 `JandiWebhookClient` 1개 + `integrations` 테이블에 `JANDI` provider 행 추가(enum에 이미 `JANDI` 존재) |

**"전환 지표"를 우리가 실제로 가질 수 있나 — 절반만 가질 수 있습니다.**

| 지표 | 우리 DB로 산출 가능? | 근거 |
| --- | --- | --- |
| 수집 메일 건수 / 영업 메일 비율 | ✅ | `sources`, `analyses.business_type` |
| AI 분석 건수 / 승인 건수 / **승인률** | ✅ | `analyses.status`, `approved_at` |
| **사용자 수정률**(AI를 몇 %나 고쳤나) | ⚠️ 지금은 불가 → **4-2의 revision 테이블이 생기면 가능** | 1-5 참조 |
| 파이프라인 단계별 딜 수 | ✅ | `PipelineService` 그대로 |
| **무응답(stale) 딜 수** | ✅ | `PipelineService.isStale` |
| 등록된 일정 수 / 발송 답장 수 | ✅ | `schedules`, `customer_activities(REPLY_SENT)` |
| **리드 → 미팅 → 확정 전환율** | ✅ (단, **표본이 작음**) | 단계 이동을 `analyses` 시계열로 계산 |
| **수주/매출 전환율** | ❌ | 계약·수주 데이터가 우리 DB에 없음. `amount`는 "견적 문의 금액"이지 수주액이 아님 |

> **판정**: "매출 전환율"은 못 만들지만 **"AI 자동화 운영 지표"(수집·분석·승인률·수정률·무응답 딜)**는 전부 만들 수 있습니다. 멘토 과제의 의도(매일 10분 수동 확인 → 1분)는 충족됩니다. 다만 **이건 1주짜리 부가 기능**이지 종합설계2의 주제가 될 수는 없습니다. **채택하되 8주 계획의 곁가지로.**

### 과제 1번 상세 — 현재 데이터로 구현 가능한가

**가능합니다.** 다만 지금 리마인드는 **프론트엔드 전용**이라는 점을 알아야 합니다.

| 현재 | 필요한 추가 |
| --- | --- |
| `DashboardLayout.jsx`가 로그인 시 "오늘·내일 일정" 팝업, 60초 폴링으로 "30분 전" 팝업 | **브라우저를 안 켜면 아무 일도 안 일어남** |
| `schedules.reminder_date_time` 컬럼 존재하나 **항상 null** | 서버 `@Scheduled` 잡이 이 컬럼을 채우고 소비 |
| `PipelineService.STALE_DAYS = 3` (달력일) | **영업일 기준** 계산 + 공휴일 처리 |
| 발송 채널 없음 | Gmail(`sendReplyEmail` 재사용) 또는 JANDI Webhook |

필요 데이터는 전부 있습니다: 마지막 수신(`customer_activities.occurred_at`), 마지막 발신(`REPLY_SENT`), 예정 일정(`schedules`), 딜 단계(`PipelineService`).

> 여기에 **"고객별 응답 패턴 학습"**을 얹으면(3-2 아이디어 D) 단순 cron이 아니라 **학습형 기능**이 됩니다. 이게 팀이 원하는 방향과 정확히 맞습니다.

## 3-2. 아이디어 목록

### A. 승인·수정 피드백 루프 (Human-in-the-loop 학습)

- **무엇**: AI 원본 결과와 사용자 수정 결과를 쌍으로 저장 → (a) 유사 메일에 과거 수정 사례를 few-shot으로 자동 주입 (b) 누적 데이터로 경량 분류기 학습 (c) 오류 패턴 대시보드
- **학습 메커니즘 3단**:
  1. **즉시(데이터 0건부터 동작)**: 같은 발신자/도메인의 과거 "수정된 값"을 규칙 fallback보다 우선 적용. 예) 사용자가 `customerName`을 "대성물류(주)"로 2번 고쳤으면 다음부터 그 값을 쓴다
  2. **30건부터**: 메일을 `text-embedding-3-small`로 임베딩 → 수정 이력 중 가장 유사한 상위 3건을 프롬프트에 few-shot으로 주입 (**RAG 방식, 파인튜닝 불필요, 즉시 효과**)
  3. **100건부터**: 임베딩 + 메타 피처로 로지스틱 회귀/LightGBM을 학습해 `businessType` 이진 분류. LLM 판정과 앙상블
- **발표 수치**: "수정 이력 0건 → 30건 → 100건 시점의 무수정 통과율" 곡선. **"쓸수록 좋아진다"를 그래프로 증명하는 유일한 기능**
- **필수 선행**: 1-5의 스키마 변경 + `businessType`을 수정 가능 필드에 추가

### B. 회의 음성 STT → 회의록 분석 (팀 제안)

- **무엇**: 녹음 파일 업로드 → STT → 화자·발언 정리 → **기존 `/analyze` 파이프라인에 `sourceType=MEETING_NOTE`로 투입** → 고객사·일정·후속조치 추출 → 기존 승인 흐름 재사용
- **기존 재사용도가 높음**: `SourceType.MEETING_NOTE`가 **이미 enum에 존재**하고 AI 모듈 `request.py`도 받습니다. 승인→일정→캘린더 경로 전체를 그대로 탑니다
- **학습 요소는 약함**: STT 자체는 외부 모델. "학습"이라 부를 수 있는 건 (a) 자주 나오는 고객사·제품명 사전을 누적해 STT 후처리 교정 (b) A의 피드백 루프를 회의록에도 적용 — **둘 다 A에 의존**
- **구조 변경 필요**: 파일 업로드 + 비동기 잡(부채 A4). 5분 녹음 STT는 수십 초~수 분
- **시연 임팩트는 최상**: 실제 회의 녹음 → 화면에 정리된 후속조치가 뜨는 장면

### C. 미팅 전 브리핑 자동 생성 (멘토 과제 3)

- **무엇**: 예정된 미팅 N시간 전, 그 고객의 **전체 히스토리를 LLM이 1장으로 요약**. 과거 논의 주제, 미해결 질문, 금액 언급, 상대가 신경 쓰는 포인트, 이번 미팅에서 확인할 것 3가지
- **입력이 전부 이미 있음**: `customer_activities`(타임라인) + `sources.content`(과거 메일 원문) + `analyses` + (B가 있으면) 회의록
- **B와의 결합**: 회의록이 쌓이면 브리핑이 "지난 미팅에서 이렇게 말했다"까지 포함 — **팀이 말한 조합이 정확히 이것**
- **학습 요소**: 사용자가 브리핑에 "도움됨/안됨" 피드백 → 어떤 섹션을 볼모로 남기는지 학습. 약하지만 A의 인프라를 재사용 가능
- **난이도 낮고 효과 큼**: 백엔드 조회 + 프롬프트 + 화면 1개

### D. 응답 패턴 학습형 리마인드 (멘토 과제 1 + 학습)

- **무엇**: 단순 "3일 지나면 알림"이 아니라, **고객별 과거 응답 지연 분포를 학습**해 리마인드 시점을 개인화
  - 예: A고객 평균 응답 1.2일(σ 0.5) → 2.5일 무응답이면 이례적 → 즉시 알림
  - 예: B고객 평균 응답 6일 → 3일 무응답은 정상 → 알리지 않음
- **학습 메커니즘**: 고객별 응답 간격의 경험분포(생존분석/Kaplan-Meier 또는 간단히 평균+표준편차). **데이터 10건이면 유의미**, cold start는 전체 평균으로 시작
- **데이터**: `customer_activities`의 수신/발신 시각 쌍 — **이미 전부 있음**
- **발표 수치**: "고정 3일 규칙 대비 불필요 알림 N% 감소, 놓친 딜 M% 감소" (과거 데이터로 백테스트 가능)
- **영업일·공휴일 처리** 포함 → 멘토 과제 1번을 완전히 커버

### E. 딜 이탈 위험 예측 / 후속 조치 우선순위

- **무엇**: 딜마다 0~100 위험 점수. 피처: 마지막 응답 이후 경과(영업일), 응답 지연 추세, 메일 왕복 횟수, 단계 체류 기간, 취소 이력, 금액, 상대 답변 길이 변화, 감정/온도(LLM 1줄 판정)
- **학습**: 과거 딜의 결과 라벨(확정/취소/방치)로 지도학습. **문제는 라벨 수** — 데모 데이터로는 딜이 4개뿐. 8주 동안 팀원 4명이 실제로 써도 수십 건 수준
- **현실적 대안**: 규칙 기반 스코어카드로 시작 → 데이터 쌓이면 가중치를 로지스틱 회귀로 학습. "학습 자리를 만들어 두고 cold start는 규칙" — **정직하게 발표하면 오히려 설계 역량으로 평가받음**
- **주의**: "이름만 ML"이 되기 가장 쉬운 아이디어. 라벨 확보 계획 없이 시작하지 말 것

### F. 운영 지표 일일 다이제스트 → JANDI 발송 (멘토 과제 4)

- **무엇**: 매일 오전 9시, 전날 지표(수집 N통, 영업 M통, 승인 K건, 승인률 x%, 수정률 y%, 무응답 딜 Z건, 오늘 미팅 P건)를 JANDI 대화방에 카드로 발송
- **학습 요소 없음.** 순수 자동화
- **구현 1주 미만.** A가 있으면 "수정률"까지 포함되어 지표가 풍부해짐
- **가치**: 멘토 과제 직접 충족 + 우리 시스템의 성과를 매일 보여주는 창구

### G. LLM Provider 추상화 + 평가 하네스 (단계 2 전체)

- 기능이라기보다 **기반 공사**. 하지만 종합설계2 발표에서 **"우리는 감이 아니라 측정으로 개선했다"**를 증명하는 유일한 장치
- A/B/C/D/E 전부가 이 위에 올라감

## 3-3. 평가표

| 기준 | A. 피드백 루프 | B. 회의 STT | C. 미팅 브리핑 | D. 학습형 리마인드 | E. 딜 위험 예측 | F. JANDI 다이제스트 | G. Provider+평가 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **기존 코드/데이터 연결성** | ★★★ `analyses` 확장 + 기존 분석 경로 그대로 | ★★★ `MEETING_NOTE` enum·승인 흐름 재사용 (입력만 새로) | ★★★ 조회만 추가, 새 테이블 거의 불필요 | ★★★ `customer_activities`·`PipelineService` 그대로 | ★★☆ 데이터는 있으나 라벨이 없음 | ★★☆ 지표는 기존 DB, 발송만 신규 | ★★★ 두 LLM 호출부를 감쌈 |
| **학습 요소의 실체성** | ★★★ **진짜.** 쓸수록 예시가 늘고 무수정률이 올라감. 측정 가능 | ★☆☆ STT는 외부 모델. 학습은 A에 얹어야 생김 | ★★☆ 약함. A의 피드백을 재사용하면 중간 | ★★★ **진짜.** 고객별 분포가 데이터로 갱신됨 | ★★☆ **라벨 부족 위험.** 8주로는 규칙 스코어카드에 머물 가능성 큼 | ☆☆☆ 없음 | ★★☆ 학습은 아니지만 학습을 측정 가능하게 만듦 |
| **2개월·4명 구현 가능성** | ★★★ 스키마 1개 + 임베딩 검색. 2~3주 | ★★☆ 파일 업로드+비동기 잡+STT 품질 검증. 3~4주, **리스크 있음** | ★★★ LLM 호출 1개 + 화면 1개. 1.5~2주 | ★★★ 스케줄러 + 통계. 2주 | ★★☆ 피처 엔지니어링은 빠르나 검증이 어려움. 2~3주 | ★★★ 1주 미만 | ★★★ 2주(1~2주차 집중) |
| **필요한 외부 API·비용** | 임베딩 $0.02/1M = 사실상 0 | **STT 비용 + 품질 리스크.** Whisper $0.006/분(60분=$0.36), 한국어 품질 **확인 필요** | terra 호출 1건당 ~$0.01 | 0 | 0 | 0 | 2-1 참조, 월 $2~$9 |
| **시연·발표 수치화** | ★★★ **"수정 이력 N건 → 무수정률 x%→y%" 곡선.** 최고 | ★★★ 시각적 임팩트 최고, 수치는 WER·추출 F1 | ★★☆ "콜 준비 시간 50%→" 사용자 체감 측정 필요(팀원 N명 실측) | ★★★ 백테스트로 "불필요 알림 -N%" 제시 가능 | ★★☆ 표본이 작아 신뢰구간이 넓음 | ★☆☆ "10분→1분" 정도 | ★★★ **모든 비교표의 출처** |
| **실무(멘토) 관점 가치** | ★★★ CRM 제품의 실제 고충(AI 오류 반복). Salesmap이 바로 이해함 | ★★☆ 수요는 크나 이미 유사 제품 다수(클로바노트 등). 차별점은 "영업 파이프라인 연결" | ★★★ **멘토 과제 3번 직접 충족.** 가장 요청이 명확 | ★★★ **멘토 과제 1번 직접 충족** | ★★☆ 매력적이나 검증 어려움 | ★★☆ **멘토 과제 4번 충족**, 난이도 대비 가성비 | ★★☆ 내부 가치 (엔지니어링 성숙도) |
| **종합** | **1순위** | 3순위 | **2순위** | **2순위(공동)** | 보류/축소 | 곁가지 | **0순위(기반)** |

---

# 단계 4. 최종 추천

## 4-1. 최우선 추천 조합

### 추천안: `G(기반) + A(학습 코어) + C+D(기능) + F(곁가지)` — B(STT)는 조건부 확장

```
[0주차·기반]  G. LLM Provider 추상화 + OpenAI 전환 + 평가 하네스
                 └ 여기서 나온 숫자가 발표 전체의 근거가 됨
                      ↓
[학습 코어]   A. 승인·수정 피드백 루프
                 └ AI 원본/수정 쌍 저장 → few-shot 자동선택 → 경량 분류기
                 └ "쓸수록 좋아진다"의 실체. 나머지 기능이 전부 여기 얹힘
                      ↓
        ┌─────────────┴─────────────┐
[기능1] C. 미팅 전 브리핑        [기능2] D. 응답패턴 학습형 리마인드
        (멘토 과제 3 충족)              (멘토 과제 1 충족)
        └ A의 수정 이력으로 정확도↑     └ 브리핑을 리마인드에 첨부
        └ (여유 시) B의 회의록을 입력으로 흡수
                      ↓
[곁가지] F. JANDI 다이제스트 (멘토 과제 4, 1주 미만)
```

### 왜 이 조합인가

1. **G를 먼저 하지 않으면 나머지가 전부 "감"이 됩니다.** 단계 1에서 확인했듯 지금 LLM은 의미 보정만 하므로, 모델을 바꿔도 뭐가 좋아졌는지 말할 수 없습니다. 평가 하네스가 있어야 "LLM 고도화"가 발표 가능한 성과가 됩니다.
2. **A가 유일하게 진짜 학습입니다.** 팀이 "딸각 붙이는 기능 말고 쓸수록 좋아지는 기능"을 원했는데, 평가표에서 학습 실체성 ★★★를 받은 건 A와 D 둘뿐이고, **A는 D·C·E 전부의 데이터 기반**이 됩니다. 그리고 1-5에서 확인했듯 **지금 안 만들면 8주 내내 학습 데이터가 0건**입니다.
3. **C와 D는 멘토 과제 3·1을 직접 충족**하면서 기존 코드 재사용도가 가장 높습니다(새 테이블 최소, 기존 파이프라인 재사용).
4. **B(STT)를 주 과제에서 뺀 이유**: 한국어 STT 품질·화자분리·회의 녹음 확보가 전부 미검증 변수입니다. 4명 8주에서 **3~4주를 리스크가 큰 곳에 쓰는 것은 비효율**입니다. 대신 **C의 입력 소스로 6~7주차에 편입**하면, C가 완성된 상태에서 "회의록도 넣을 수 있다"로 확장 시연이 되고, 실패해도 C는 남습니다.
5. **E는 보류**: 라벨(딜 성사/실패)이 8주 안에 충분히 쌓이지 않습니다. D의 응답 지연 통계를 파이프라인 카드에 "위험 신호"로 노출하는 정도로 **축소 흡수**하세요.

## 4-2. 추천안별 설계

### G. LLM Provider 추상화 + OpenAI 전환 + 평가

| 항목 | 내용 |
| --- | --- |
| 추가 모듈 | `ai-module/app/llm/{base,openai_provider,ollama_provider,router,usage}.py`, `ai-module/app/prompts/`, `ai-module/eval/` |
| 변경 모듈 | `analyzer.py`(LLM 호출을 router로), `reply_draft.py`(동일), `config.py`(모델 라우팅·상한 설정) |
| 신규 테이블 | `llm_usage(id, task, provider, model, input_tokens, cached_input_tokens, output_tokens, latency_ms, estimated_cost_usd, fallback_reason, analysis_id, created_at)` |
| 신규 엔드포인트 | `GET /api/admin/llm-usage/summary` (월 누적 비용·폴백률) |
| 학습 데이터 수집 | 해당 없음(기반) |
| 평가 지표 | 2-5의 6개 지표 × 5개 arm, 파싱 실패율, p95 지연, 건당 비용 |

### A. 승인·수정 피드백 루프

| 항목 | 내용 |
| --- | --- |
| 변경 모듈 | `Analysis.java`(원본 스냅샷), `AnalysisService.updateAnalysis`(델타 기록), `AnalysisUpdateRequest`(**`businessType` 추가**), `MessageView.jsx`(영업/비영업 토글 + "AI 원본 보기") |
| 추가 모듈 | `ai-module/app/feedback/{store,retriever}.py` (임베딩 색인 + 유사 사례 검색) |
| 신규 테이블 | `analysis_revisions(id, analysis_id, field_name, ai_value, user_value, revised_by, revised_at)` — **필드 단위 행**으로 저장해야 "어느 필드를 몇 % 고치나"를 바로 집계할 수 있음 |
| | `analysis_ai_snapshot` — 또는 `analyses`에 `ai_*` 접두 컬럼 11개 추가. **테이블 분리 권장**(analyses가 이미 넓음) |
| | `feedback_examples(id, user_id, source_id, masked_text, embedding BLOB, gold_json, created_at)` — few-shot 검색용 |
| 신규 엔드포인트 | `GET /api/analysis/{id}/revisions`, `GET /api/admin/feedback/stats`(필드별 수정률·오류 패턴) |
| 학습 데이터 수집 | ① 사용자가 PATCH할 때마다 자동 (명시적 라벨) ② 수정 없이 승인 = **암묵적 긍정 라벨** — 이것도 반드시 기록할 것 ③ 삭제/거부 = 부정 라벨(`REJECTED` 상태를 실제로 쓰기) |
| 학습 방식 | (a) 발신자별 확정값 캐시 → (b) 임베딩 top-3 few-shot 주입 → (c) 100건 이상 시 `businessType` 로지스틱 회귀 |
| 평가 지표 | **무수정 승인율**(핵심), 필드별 수정률, few-shot 주입 on/off A/B, 수정 이력 0/30/100건 시점의 정확도 곡선 |

### C. 미팅 전 브리핑

| 항목 | 내용 |
| --- | --- |
| 추가 모듈 | `backend/.../briefing/{controller,service,entity,repository}`, `ai-module/app/prompts/briefing_v1.py` |
| 변경 모듈 | `DashboardLayout.jsx`(브리핑 알림), `Dashboard.jsx`(일정 클릭 → 브리핑 패널) |
| 신규 테이블 | `meeting_briefings(id, schedule_id, customer_contact_id, content_json, generated_at, model, feedback_score)` |
| 신규 엔드포인트 | `POST /api/schedules/{id}/briefing`(생성), `GET /api/schedules/{id}/briefing`, `POST /api/briefings/{id}/feedback` |
| 자동 생성 | `@Scheduled` 잡이 미팅 24시간 전에 미리 생성(Batch API 활용 가능) → 사용자가 열 때 즉시 표시 |
| 브리핑 구성(프롬프트 출력 스키마) | `{relationshipSummary, discussedTopics[], openQuestions[], amountContext, lastInteraction, watchOuts[], suggestedAgenda[3]}` |
| 학습 데이터 수집 | 브리핑별 "도움됨/안됨" + 실제로 미팅 전에 열어봤는지(열람 로그) |
| 평가 지표 | 팀원 4명 × 미팅 10건 대상 **준비 시간 실측**(브리핑 없이 vs 있이), 브리핑 항목별 유용도 5점 척도, 사실 오류 건수(hallucination rate) |

### D. 응답 패턴 학습형 리마인드

| 항목 | 내용 |
| --- | --- |
| 추가 모듈 | `backend/.../reminder/{ReminderScheduler, ReminderRuleService, BusinessDayCalculator}`, `backend/.../jandi/JandiWebhookClient` |
| 변경 모듈 | `PipelineService`(`STALE_DAYS` 고정값 → 고객별 임계치), `DashboardLayout.jsx`(서버 리마인드 수신) |
| 신규 테이블 | `customer_response_stats(customer_contact_id, sample_count, mean_response_hours, stddev_hours, p80_response_hours, updated_at)` |
| | `reminders(id, user_id, customer_contact_id, analysis_id, schedule_id, kind, due_at, sent_at, channel, status, reason)` |
| 신규 엔드포인트 | `GET /api/reminders`, `PATCH /api/reminders/{id}/dismiss`(=부정 피드백), `PATCH /api/reminders/{id}/snooze` |
| 리마인드 종류 | ① 무응답 팔로업(고객별 임계치 기반) ② 미팅 D-1 / D-0 (브리핑 링크 첨부) ③ 승인 대기 분석 적체 ④ 확정 후 후속조치 미완 |
| 영업일 처리 | `BusinessDayCalculator` — 주말 제외 + 공휴일 테이블(`holidays`). 공휴일은 [공공데이터포털 특일정보 API]로 연 1회 적재하거나 하드코딩(2026~2027분) |
| 학습 데이터 수집 | 고객별 (수신→우리 답장) 간격, (우리 발신→고객 회신) 간격을 `customer_activities`에서 야간 배치로 집계. 리마인드 dismiss/snooze를 부정 라벨로 |
| cold start | 표본 < 3건이면 전체 평균, < 10건이면 전체 평균과 개인값의 가중 혼합(shrinkage) |
| 평가 지표 | 과거 데이터 **백테스트**: 고정 3일 규칙 vs 학습형 → 발송 건수, 정밀도(알림 후 실제로 팔로업했는가), 놓친 무응답 건수 |

### F. JANDI 다이제스트

| 항목 | 내용 |
| --- | --- |
| 추가 모듈 | `JandiWebhookClient`(D와 공유), `DailyDigestScheduler` |
| 변경 모듈 | `IntegrationService`(JANDI provider 등록 — enum에 이미 존재), `SettingsPage.jsx`(웹훅 URL 입력) |
| 신규 테이블 | 없음 (`integrations`의 `access_token` 컬럼에 웹훅 URL 저장) |
| 신규 엔드포인트 | `POST /api/integrations/jandi`(웹훅 등록), `POST /api/integrations/jandi/test`(테스트 발송) |
| 지표 | 3-1의 "가능" 표시 항목 전부 + A가 완료되면 수정률 추가 |

## 4-3. 8주 일정 (4명 병렬)

역할 가정 — **FE**(프론트), **BE**(백엔드), **AI**(AI 모듈/프롬프트), **DATA**(평가셋/학습/분석). 실제로는 겹치지만 **주 담당(owner)**을 정해두면 병목이 줄어듭니다.

| 주 | FE | BE | AI | DATA | 공통 마일스톤 |
| --- | --- | --- | --- | --- | --- |
| **1** | 기존 목업 제거(B4 데모 일정, B5 Salesmap 로그인), 화면 구조 정리 | **`analysis_revisions` + AI 스냅샷 스키마 설계·적용**, Flyway 도입, `.gitignore` 정리(D5) | `analyzer.py` 중복 정의 제거(A1), `app/llm/` 뼈대, Provider 인터페이스 | **평가셋 v0**: 플레이북 21통 → `dataset.jsonl` 변환 + 라벨링 규칙 문서 | 🚩 **수정 이력이 쌓이기 시작** (가장 중요) |
| **2** | "AI 원본 보기" + 영업/비영업 수정 토글 | `businessType` 수정 허용, 수정 델타 기록, `llm_usage` 테이블 | OpenAI Provider + **Structured Outputs 스키마 v1**, 폴백 체인 + 서킷브레이커 | 평가셋 80건까지 확장(팀 4명 분담), `run_eval.py` | 🚩 **arm Q/L 첫 비교 숫자** |
| **3** | 브리핑 화면 프로토타입 | 브리핑 도메인(엔티티·API·스케줄러) | 프롬프트 v2(few-shot 4~6개로 캐싱 문턱 돌파), `briefing_v1` 프롬프트 | 평가셋 150건 완성, arm R/Q/L/L+/T+ 전량 실행 | 🚩 **"로컬 vs API" 비교표 초안 = 중간발표 재료** |
| **4** | 브리핑 화면 완성 + 피드백 버튼 | 브리핑 자동 생성 배치, `customer_response_stats` 집계 배치 | 브리핑 품질 튜닝, 환각 억제(근거 문장 인용 강제) | 브리핑 사실성 평가(사람 채점 30건), 모델 라우팅 확정 | 🚩 **C 완료** |
| **5** | 리마인드 UI(목록/스누즈/디스미스) | `ReminderScheduler` + `BusinessDayCalculator` + `holidays` | few-shot 자동 선택(임베딩 검색) 1차 | 응답 패턴 통계 검증 + **백테스트 스크립트** | 🚩 **D 코어 동작** |
| **6** | JANDI 설정 화면, 파이프라인 카드에 위험 신호 표시 | `JandiWebhookClient` + 리마인드/다이제스트 발송 | few-shot 주입 A/B 실험 | 백테스트 결과 정리, 수정 이력 30건 시점 정확도 측정 | 🚩 **D+F 완료, A의 1차 효과 측정** |
| **7** | 회의록 업로드 UI + 진행 표시 | 비동기 잡(업로드→STT→분석), `MEETING_NOTE` 경로 연결 | STT 연동 + 회의록 구조화 프롬프트 | **STT 품질 실측**(3개 엔진 비교), 경량 분류기 학습(데이터 충분 시) | 🚩 **B 편입 — 여기서 어려우면 과감히 드롭하고 8주차로** |
| **8** | 전체 UI 다듬기, 시연 리허설 | 버그 픽스, 성능(D4 backfill 개선) | 프롬프트 최종 고정 | **최종 평가 전량 재실행**, 그래프·표 생성, 한계 분석 | 🚩 **최종 발표 자료 + 시연** |

### 주차별 게이트 (여기서 못 넘으면 범위를 줄인다)

| 시점 | 통과 조건 | 미달 시 |
| --- | --- | --- |
| 2주차 말 | 수정 이력이 실제로 DB에 쌓이고 있음 + OpenAI 호출 성공 | **최우선 복구.** 다른 작업 중단 |
| 3주차 말 | 평가셋 150건 + arm 5개 결과표 존재 | 평가셋을 100건으로 축소, arm을 Q/L+/T+ 3개로 축소 |
| 5주차 말 | C(브리핑) 시연 가능 | D를 "고정 규칙 + 영업일 계산"으로 축소 |
| 7주차 초 | STT가 한국어 회의 5분 녹음에서 WER 20% 이하 | **B 드롭.** C 강화(과거 메일 인용 근거 표시)로 대체 |

## 4-4. 리스크와 대안

| # | 리스크 | 발생 가능성 | 영향 | 대응 / 대안 |
| --- | --- | --- | --- | --- |
| **R1** | **학습 데이터 부족 (cold start)** — 8주간 팀원 4명이 쓰는 것만으로는 수정 이력이 수십 건 | **높음** | A의 "학습 효과"를 수치로 못 보여줌 | ① **1주차에 스키마를 깔아 최대 기간 확보** ② 평가셋 150건을 **초기 few-shot 풀로 전용** (라벨이 곧 "이상적인 수정 결과") ③ 팀원 4명이 각자 실제 메일 50통씩 분석·수정하는 **라벨링 세션 2회** 운영 → 200건 확보 ④ 발표에서는 "0/30/100/200건 시점" 곡선으로 제시 — 절대값이 낮아도 **추세가 우상향이면 증명됨** |
| **R2** | **STT 품질** — 한국어 + 회의 잡음 + 다중 화자 + 영업 전문용어 | **높음** | B 전체가 무의미해짐 | ① **7주차 게이트로 늦게 배치**해 실패해도 C가 남게 설계(이미 반영) ② 3개 엔진 비교: Whisper large-v3(로컬/faster-whisper), gpt-4o-transcribe, 국내 STT(CLOVA Speech·VITO) — **한국어 벤치마크 확인 필요** ③ 화자분리(diarization)는 어려우니 **화자 라벨 없는 전사 + LLM으로 후처리**로 목표 하향 ④ 최후: 실시간 녹음 대신 **"회의록 텍스트 붙여넣기"** 입력만 지원 (핵심 가치인 "회의 내용 → 파이프라인 연결"은 그대로 살아남음) |
| **R3** | LLM 경계선 재설계 후 정확도가 **오히려 떨어짐** (규칙이 6단 가드로 튜닝되어 있어 특정 케이스에 강함) | 중간 | "고도화했는데 나빠졌다" | **2-3의 3단계 전략을 반드시 지킬 것.** Phase 1(둘 다 돌리고 규칙 채택)에서 **필드 단위로** 승자를 가린 뒤 전환. 결과적으로 "규칙이 이긴 필드는 규칙 유지"가 될 수도 있는데, 이것도 **측정으로 얻은 결론이므로 발표 가치가 있음** |
| **R4** | 평가셋 라벨 품질 — 4명이 각자 기준으로 라벨링하면 일관성 붕괴 | 중간 | 모든 숫자가 무의미 | 1주차에 **라벨링 가이드 문서화** + 20건 교차 라벨링으로 κ 측정 + 불일치 케이스 토론. `demo-playbook.md`의 기대값을 기준으로 삼으면 출발이 쉬움 |
| **R5** | OpenAI API 장애·레이트리밋으로 **시연 당일 실패** | 낮음(영향 큼) | 발표 사고 | ① 로컬 Ollama 폴백을 **끝까지 유지**(2-3-(4)) ② 시연 전 **결과 캐시 워밍업**(같은 메일을 미리 한 번 돌려 캐시) ③ 오프라인 백업 시연 영상 |
| **R6** | 개인정보 지적 — "고객 메일을 외부로 보내도 되나?" | **높음(질의응답에서 반드시 나옴)** | 감점 | 2-6을 **발표 슬라이드에 선제적으로 1장** 넣기: 마스킹 설계도 + on/off 토글 화면 + 보존 정책. 마스킹 A/B 정확도 손실 수치까지 있으면 오히려 가점 |
| **R7** | 범위 과다 — G+A+C+D+F+B는 8주에 많음 | **높음** | 전부 어중간 | 4-3의 **주차별 게이트**를 지킬 것. 드롭 우선순위는 **B → F → D 축소 → E(이미 제외)**. **G와 A는 절대 드롭하지 않음**(나머지의 기반이므로) |
| **R8** | `ddl-auto=update`로 4명이 동시에 스키마 변경 → 충돌 | 중간 | 로컬 DB 깨짐 | 1주차에 **Flyway 도입**(부채 A5). 마이그레이션 파일로 변경을 직렬화 |
| **R9** | 비용 폭주 (평가 실험 반복) | 낮음 | 금전 손실 | OpenAI 대시보드 월 한도 + `llm_usage` 애플리케이션 상한 + 평가는 **Batch API 50% 할인**으로. 최악 시나리오도 월 $30 수준(2-1) |
| **R10** | 브리핑 환각 — 없는 사실을 지어냄 | 중간 | 실무 신뢰도 붕괴 | 프롬프트에 **"모든 주장에 근거가 된 메일 제목·날짜를 함께 출력"** 강제(Structured Outputs로 `evidence[]` 필드) → 화면에서 근거 링크 표시. 근거 없는 문장은 렌더링하지 않음 |

## 4-5. 지금 당장(이번 주) 해야 할 3가지

1. **`analysis_revisions` + AI 원본 스냅샷 스키마를 먼저 넣으세요.** 다른 모든 논의보다 우선입니다. 이번 주에 넣으면 7주치 데이터, 4주차에 넣으면 4주치입니다.
2. **`demo/demo-playbook.md`의 21통을 `eval/dataset.jsonl`로 변환하세요.** 반나절이면 됩니다. 평가 기준선 없이 "고도화"를 시작하면 8주 뒤에 할 말이 없습니다.
3. **`analyzer.py`의 중복 함수 정의 5개를 정리하세요.** 지금은 "파일 끝의 정의가 이긴다"는 암묵 규칙에 의존하고 있어, 4명이 동시에 손대면 반드시 사고가 납니다.

---

## 부록: 확인이 필요한 항목

| 항목 | 왜 확인이 필요한가 |
| --- | --- |
| OpenAI 데이터 보존·학습 이용 정책의 현행 문구 | 발표 슬라이드에 인용할 것이므로 팀이 직접 공식 문서에서 확인 |
| Zero Data Retention의 학생/일반 계정 적용 가능 여부 | 적용 불가 시 마스킹 설계가 유일한 대응 |
| 한국어 STT 엔진별 실측 품질(WER) 및 가격 | Whisper / gpt-4o-transcribe / CLOVA Speech / VITO — 공개 한국어 벤치마크가 신뢰하기 어려워 자체 측정 필요 |
| Salesmap 공개 API의 실제 존재 여부 | `HttpSalesmapClient`는 구현되어 있으나 엔드포인트·인증 방식이 검증되지 않음. 멘토에게 직접 문의 권장 |
| `ai-module/.env`가 git에 추적되고 있는지 | API 키 커밋 사고 방지 |
| 공휴일 데이터 출처(공공데이터포털 특일정보 API 등) | D의 영업일 계산에 필요 |
| JANDI Incoming Webhook의 팀 계정 사용 가능 여부 | 유료 플랜 제약이 있는지 확인 필요 |

---

### 참고 자료

- [OpenAI API 가격](https://developers.openai.com/api/docs/pricing)
- [OpenAI Structured Outputs 가이드](https://developers.openai.com/api/docs/guides/structured-outputs)
- [GPT-5.6 Sol/Terra/Luna 티어 비교](https://www.vellum.ai/blog/gpt-5-6-sol-terra-luna-explained)
- [JANDI 커넥트 인커밍 웹훅](https://support.jandi.com/ko/articles/6352697)
- [JANDI 팀 인커밍 웹훅](https://support.jandi.com/ko/articles/6352712)
