# C task 백엔드

Spring 컴포넌트 스캔으로 등록된다. task 패키지 외의 설정·B의 Fake 빈은 수정하지 않았다.
DB가 연결된 기존 백엔드를 실행하고 C AI를 별도로 실행한다.

```bash
# ai-module에서
.venv/bin/python -m uvicorn app.tasks.api:app --port 8001
```

`task.ai-base-url` (환경변수 `TASK_AI_BASE_URL`) 기본값은 `http://localhost:8001`이다.
나중에 총괄이 C router를 공용 AI 앱에 등록하면 8000 주소로 설정할 수 있다.
B 연결용 MeetingTaskAdapter는 준비했지만 빈으로 등록하지 않았다. Fake를 제거하고 등록하면
남은 DRAFT가 없는지 확인할 뿐 task를 일괄 확정하지 않는다.
AI 호출은 DB 트랜잭션 밖에서 수행한다. 유료 API는 사용하지 않는다.

## 인증된 API

응답은 기존 `ApiResponse.data`에 task 계약 객체 또는 배열이 들어간다.

| 메서드·경로 | 기능 |
| --- | --- |
| GET `/api/tasks` | 본인 소유 할 일 목록 |
| GET `/api/tasks/{id}` | task + HTTP ETag (CORS 노출 포함) |
| POST `/api/tasks/from-note/{noteId}` | B 서비스로 소유권 확인 → AI 추출 → DRAFT 저장 |
| POST `/api/tasks` | task 계약 본문으로 직접 추가. noteId=null, status=DRAFT, taskTypeSource=HUMAN |
| PUT `/api/tasks/{id}` | 전체 task 본문 + 필수 If-Match 헤더로 사람 수정·확정 |
| GET `/api/tasks/revisions/pending` | 본인 수정 기록 전달 대기본, revision 계약 배열 |

공용 security의 인증 규칙을 그대로 적용한다. 목록은 본인 소유만 조회하며, 수정은 owner 조건으로
잠근 뒤 ETag를 비교한다. stale 수정은 409이고 자동 재시도/덮어쓰기를 하지 않는다.
생성 시 요청 taskId는 임시값이며 저장 응답에서 `t_<DB PK>`로 바뀐다.

재수집은 회의록 ID+내용+근거+상대가 같으면 기존 task를 반환하고 사람 수정을 보존한다.
원본 회의록 내용이 바뀌면 새 초안이 추가될 수 있다. 이전 초안은 자동 취소하지 않으므로
사람이 비교하고 취소해야 한다. 동시 중복 수집의 unique 충돌도 409로 반환한다.

허용 상태: DRAFT → CONFIRMED/CANCELLED, CONFIRMED → DONE/CANCELLED, DONE → CONFIRMED.
CANCELLED는 수정/재개하지 않는다. 자동 상태 변경·캘린더·메일·알림은 구현하지 않는다.
ID·noteId·evidence는 변경할 수 없다. 종류를 사람이 바꾸면 HUMAN/1.0으로 기록한다.
기한을 바꾸는 호출자는 dueDateIsDefault=false로 보내야 한다 (C 화면에서 수행).

## 수정 기록 전달 대기

`task_entries`와 `task_revision_outbox`는 C 소유 테이블이다. 변경과 전달 대기 기록은
같은 트랜잭션에 들어가며 기록 저장 실패 시 task 변경도 롤백된다. 초기 AI 제안은 별도 보존한다.
아직 D의 API가 없어 이 큐는 자동 발송하거나 삭제하지 않는다. D의 공식 revision 저장소를 대체하지 않는다.

조회 시 `rv_task_<outbox PK>`라는 임시 네임스페이스 ID를 붙인다. D의 `rv_<revision PK>`와
충돌하지 않도록 한 전달용 ID이며, D 연결 때 수신 중복 방지와 공식 ID 매핑을 협의해야 한다.
생성/수정은 CREATED_NEW/EDITED, 확정 시 유지한 필드는 ACCEPTED, 취소 상태는 REJECTED로 기록한다.
수동 생성의 proposedBy=RULE은 AI 제안 없이 동작한 폼의 초기 규칙값을 뜻한다 (proposedValue=null).
공식 ML-4 수집 완료나 전달 성공률 100%는 D 연결 전에는 주장하지 않는다.

## 검증

```bash
# backend에서 (gradlew 실행 권한이 없으면 bash로 실행)
bash ./gradlew test --tests '*Test'
```

MySQL 없이 저장소 mock으로 권한, 상태, 중복, 충돌, audit 실패, 실제 공용 스키마 형식을 검사한다.
실제 MySQL의 DDL/트랜잭션 통합 실행은 별도 연결 검증이 필요하다.
