# C 인수인계 — 독립 구현과 통합할 부분

브랜치: `s2/c-task-extractor`. 최초 추출기 커밋은 `b1bcdbf`다.
공용 contracts·AGENTS·docs/project·app/main.py·App.jsx·메뉴와 다른 역할의 백엔드는 수정하지 않았다.
기존 프론트의 C 자리 `components/minutes/TaskDraftPanel.jsx`는 C 컴포넌트 wrapper로 교체했다.

## 구현 위치

| 범위 | 위치 |
| --- | --- |
| 추출·규칙 분류·날짜·독립 AI API·테스트 | ai-module/app/tasks/ |
| 추출 평가·분류 학습/평가·데이터·검수 도구 | ai-module/ml/task_type/ |
| 저장·조회·수정·확정·수정 전달 대기·테스트 | backend/.../task/ 및 src/test/.../task/ |
| 확인 영역·할 일 보드·독립 진입점·검증 | frontend/src/components/tasks/ |

## A에게

- 지금 A 코드 변경은 필요 없다. 연결 시 person 계약의 personId/name 목록을 C 화면의 `people` prop으로 전달하면 상대방 선택과 이름 표시가 활성화된다.
- LIGHT는 perPerson.personId를 사용한다. FULL은 참석자가 정확히 한 명일 때만 해당 ID를 사용한다.
  여러 명이면 추측하지 않고 null이며 사람이 확인해야 한다.
- 사람 검색 UI를 연결할 때 선택한 ID를 task counterpartPersonId로 저장한다. A 인물 저장소의 존재/소유권 검증 연결도 그때 확인한다. 이름을 새로 추정하지 않는다.

## B에게 — 연결 필요

1. FULL의 commitments·nextContact, LIGHT의 followUp을 입력으로 사용한다.
   D-018대로 고객 측 약속에 `(고객 측)`을 붙이고, 항목 하나에 약속 하나를 유지한다.
2. 날짜는 요약 text에서 계산하므로 원문의 기한 표현을 text에도 보존한다.
   evidence는 수정하지 않고 전달한다. 모호한 기간/조건은 null이다.
3. 독립 C AI는 `POST http://localhost:8001/tasks/extract?assignee=user_<PK>`에
   meeting_note 본문을 받고 task 배열을 반환한다. `router`를 공용 main.py에 등록하는 것은 총괄 연결 사항이다.
4. C 백엔드의 `POST /api/tasks/from-note/{noteId}`는 B의 조회 서비스로 소유권을 검사한 뒤
   AI를 호출하고 DRAFT로 저장한다. 저장 응답의 `t_<PK>`가 영구 ID다.
5. 현재 B의 FakeTaskDraftClient는 그대로다. C 패키지의 MeetingTaskAdapter를 빈으로 등록하고 Fake를 제거하면 된다. 수정·재수집 시 중복/이전 초안 처리를
   검토해야 한다. **회의록 확정만으로 모든 task를 일괄 확정하면 안 된다.** 선택한 할 일을 사람이 확인한다. 실제 어댑터는 남은 DRAFT가 있으면 회의록 확정을 거부하며, 스스로 상태를 바꾸지 않는다.
6. 확인 화면에 서버 저장 task 목록을 조회해서 전달하고 `TaskDraftPanel.onSave`를 연결한다.
   현재 사용자 ID와 인물 목록도 currentUserId/people prop으로 전달한다. GET task로 받은 ETag를 PUT의 If-Match로 전달해야 한다. `tasks/taskApi.js`를 재사용할 수 있다.
   저장과 revision 대기 기록이 성공한 뒤 화면을 갱신한다. 409면 새로고침해서 다시 확인한다.
7. B 부모 화면의 '샘플' 건수/제목 및 taskSamples fallback은 아직 남아 있으니 실제 연결 시 제거한다.
   C panel 자체는 더 이상 샘플을 대신 표시하거나 저장되지 않는 편집을 허용하지 않는다.

## D에게 — 연결 필요

- task API의 상태는 기존 DRAFT/CONFIRMED/DONE/CANCELLED만 사용한다. 진행 중은 추가하지 않았다.
- 자동 일정·브리핑 대상은 사람이 확인한 CONFIRMED 이상이라는 기존 규칙을 따르되
  CANCELLED를 제외한다. 기한·담당자·상대는 null일 수 있다.
- task_entries는 C 소유이고, 수정과 task_revision_outbox 기록은 같은 트랜잭션이다.
  `GET /api/tasks/revisions/pending`으로 본인의 revision 계약 배열을 조회할 수 있다.
- D API가 없어서 아직 전송/확인 처리는 연결하지 않았다. 큐는 보관만 하며 공식 D 저장소를 대체하지 않는다.
  `rv_task_<outbox PK>`는 임시 전달 ID다. 공식 rv_<PK>와의 매핑·멱등 수신·전달 완료 표시를 협의해야 한다.
- taskTypeSource=HUMAN인 최종값과 별도로 최초 제안값을 보존한다. 종류를 사람이 수정하면 HUMAN/1.0이다.
- 자동 상태 변경 제안, 캘린더, 브리핑, 알림, 외부 발송은 D 범위라 추가하지 않았다.

## B·D에게 — 검토할 정책 / 데이터

- 고객 측 약속은 원문+`이행 여부 확인`이라는 IMPLICIT 초안, NONE, dueDate=null로 만든다.
  고객의 일을 우리 담당자의 수행 약속으로 배정하지 않는다.
- 기한 기본값은 구체 합의가 없어 넣지 않는다. dueDateIsDefault=false이며 미정은 null이다.
- 규칙 분류 1.0은 단일 규칙 일치 표시이고 100% 정확도/보정 확률이 아니다.
- ML-2 학습 후보와 평가셋은 PENDING이다. 만든 C 외 역할의 교차 검수와 부류 경계 검토가 필요하다.
  ml/task_type/review.py로 검수 CSV를 만들고 불일치율을 계산할 수 있다.
- B의 LLM 직접 판정 비교에는 evaluation.json의 같은 문장/라벨 집합을 사용하되,
  학습 프롬프트에는 평가 문장을 넣지 않는다. 별도 실발화 가명 평가셋을 받으면 최종 평가 전용으로 추가한다.

## 총괄에게 — 공용 연결/결정 제안

- C AI router를 app/main.py에 등록하고 필요하면 TASK_AI_BASE_URL을 공용 AI 주소로 설정한다.
- TasksPage를 로그인 보호 경로 및 메뉴에 연결한다. 독립 화면은 기존 Vite의
  `/src/components/tasks/preview.html`로 미리 볼 수 있다.
- C DB 테이블 SQL은 backend task/sql에 있다. 실제 MySQL 통합 검증을 진행한다.
- 위 고객 측 후속·기한 기본값 보류·규칙 신뢰도·수정 전달 대기 정책이 합의되면
  decisions.md에 기록하고, BOUNDARIES.md 후보는 task-types.md에 반영하는 것을 제안한다.
  공용 문서를 이번 작업에서 직접 변경하지는 않았다.

## 완료의 한계

C 코드/오프라인 테스트/가짜 HTTP 브라우저 검증과, 다른 역할까지 이어진 실제 DB 흐름의 완성은 구분한다.
공용 라우트/메뉴, B Fake 교체, D revision 수신, 사람의 데이터 교차 검수와 실제 MySQL 통합은 남아 있다.
코드가 존재하거나 합성 테스트가 통과했다고 운영 목표를 달성했다고 주장하지 않는다.
