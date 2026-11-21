# Briefing API

## `GET /api/briefings/preparation`

Returns the next day's preparation briefing. Only `CONFIRMED` tasks due exactly
tomorrow are included. Preparation items use deterministic task-type rules; this
endpoint does not send email or messages.

```text
GET /api/briefings/preparation?date=2026-10-20
```

## `GET /api/briefings/today`

Returns the data bundle for the D-owned home screen: tasks due today, overdue
confirmed tasks, draft tasks waiting for confirmation, and confirmed tasks due
tomorrow. The result is read-only and bundled in one response.

```text
GET /api/briefings/today?date=2026-10-20
```

## `POST /api/briefings/calendar-registration`

Registers a human-selected time for one `CONFIRMED` task. The endpoint does not
invent a time from a date-only task and is idempotent for the same task. When
Google Calendar is enabled, the existing calendar service receives the event.

```json
{
  "taskId": "t_001",
  "scheduleDateTime": "2026-10-20T10:00:00"
}
```

## `GET /api/briefings/morning`

`CONFIRMED` 상태이고 요청한 날짜 또는 다음 날이 기한인 task를 한 번에 묶어 반환한다. 날짜를 생략하면 KST 오늘을 사용한다.

```text
GET /api/briefings/morning?date=2026-10-20
```

이 API는 조회와 브리핑 데이터 생성만 수행하며 메일·메시지를 발송하지 않는다. 준비 항목은 task의 `prerequisites`를 우선 사용하고, 없을 때 taskType별 기본 안내를 제공한다.
