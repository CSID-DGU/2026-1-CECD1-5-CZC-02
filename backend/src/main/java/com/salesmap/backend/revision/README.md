# Revision API

D가 제공하는 사람 수정 기록 저장 API다. A/B/C 화면은 AI 제안과 사람이 확정한 값을 이 API에 전달한다.

## Endpoints

### `POST /api/revisions`

인증된 사용자 소유로 수정 기록을 저장한다. `actorId`와 `decidedAt`은 서버가 현재 사용자와 KST 현재 시각으로 채운다.

```json
{
  "screen": "TASK_REVIEW",
  "targetType": "TASK",
  "targetId": "t_001",
  "fieldName": "taskType",
  "proposedValue": "DOC_COLLECTION",
  "finalValue": "CONTRACT_PREP",
  "action": "EDITED",
  "proposedBy": "MODEL",
  "modelVersion": "task-type-lr-0.1"
}
```

응답의 `revisionId`는 저장된 DB PK를 사용한 `rv_<PK>` 형식이다.

### `GET /api/revisions`

현재 인증된 사용자의 수정 기록만 오래된 순서로 반환한다.

이 API는 메일이나 메시지를 발송하지 않으며, 사람의 최종 확인값만 기록한다.
