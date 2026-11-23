import assert from 'node:assert/strict';
import test from 'node:test';
import { buildMorningBundle, isPastMorningTime, kstDate } from './morningBundle.js';

const task = (taskId, content, dueDate = '2026-11-24') => ({ taskId, content, dueDate, taskType: 'NONE' });

test('KST 기준으로 날짜와 아침 시간을 판단한다', () => {
  const justBefore = new Date('2026-11-23T23:59:00Z');
  const atNine = new Date('2026-11-24T00:00:00Z');
  assert.equal(kstDate(new Date('2026-11-23T15:30:00Z')), '2026-11-24');
  assert.equal(isPastMorningTime(justBefore, '09:00'), false);
  assert.equal(isPastMorningTime(atNine, '09:00'), true);
});

test('알릴 것이 없으면 묶음을 만들지 않는다', () => {
  assert.equal(buildMorningBundle({ overdue: [], dueToday: [], tomorrowPreparation: [], pendingConfirmation: [] }, [], '2026-11-24'), null);
});

test('기한 지남부터 묶고 내부 ID·코드는 보이지 않는다', () => {
  const bundle = buildMorningBundle({
    overdue: [task('t_1', '계약서 초안 송부', '2026-11-20')],
    dueToday: [task('t_2', '견적서 송부')],
    tomorrowPreparation: [task('t_3', '제품 시연 준비', '2026-11-25')],
    pendingConfirmation: [],
  }, [
    { scheduleId: 9, title: '가상물산 미팅', scheduleDateTime: '2026-11-24T14:00:00', status: 'SCHEDULED' },
    { scheduleId: 10, title: '취소된 미팅', scheduleDateTime: '2026-11-24T15:00:00', status: 'CANCELED' },
    { scheduleId: 11, title: '내일 미팅', scheduleDateTime: '2026-11-25T10:00:00', status: 'SCHEDULED' },
  ], '2026-11-24');
  assert.equal(bundle.title, '기한 지남 1 · 오늘 일정 1 · 오늘 기한 1 · 내일 준비 1');
  assert.deepEqual(bundle.items.map((item) => item.title), ['계약서 초안 송부', '가상물산 미팅', '견적서 송부', '제품 시연 준비']);
  assert.equal(bundle.items[1].meta, '오늘 일정 · 14:00');
  assert.ok(!JSON.stringify(bundle.items.map(({ title, meta }) => [title, meta])).includes('t_1'));
});

test('많으면 6건만 보여 주고 나머지 수를 알린다', () => {
  const dueToday = Array.from({ length: 8 }, (_, index) => task(`t_${index}`, `할 일 ${index}`));
  const bundle = buildMorningBundle({ dueToday }, [], '2026-11-24');
  assert.equal(bundle.items.length, 7);
  assert.equal(bundle.items[6].title, '외 2건');
  assert.equal(bundle.title, '오늘 기한 8');
});
