import assert from 'node:assert/strict';
import test from 'node:test';
import { calendarRegistrationResult } from './calendarRegistrationResult.js';

test('이벤트 ID가 있으면 등록됨과 실제 일시를 보여 준다', () => {
  const result = calendarRegistrationResult({
    googleCalendarEventId: 'salesmap12',
    scheduleDateTime: '2026-12-01T10:00:00',
  });
  assert.equal(result.registered, true);
  assert.match(result.message, /2026-12-01 10:00/);
});

test('이벤트 ID가 없으면 완료라고 말하지 않는다', () => {
  for (const schedule of [{ googleCalendarEventId: null }, { googleCalendarEventId: '' }, null]) {
    const result = calendarRegistrationResult(schedule);
    assert.equal(result.registered, false);
    assert.match(result.message, /등록되지 않았습니다/);
    assert.doesNotMatch(result.message, /완료/);
  }
});
