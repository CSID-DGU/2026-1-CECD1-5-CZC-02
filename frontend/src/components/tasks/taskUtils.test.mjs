import { test } from 'node:test';
import assert from 'node:assert/strict';
import { filterTasks, kstToday, editedDocument, NEXT_STATUSES } from './taskUtils.js';

const tasks = [
  { taskId: 't_1', assignee: 'user_demo', counterpartPersonId: 'p_demo', taskType: 'NONE', dueDate: '2026-10-19', status: 'CONFIRMED' },
  { taskId: 't_2', assignee: null, counterpartPersonId: null, taskType: 'MEETING_PREP', dueDate: null, status: 'DRAFT' },
  { taskId: 't_3', assignee: 'user_other', counterpartPersonId: 'p_demo', taskType: 'NONE', dueDate: '2026-10-19', status: 'DONE' },
];
test('overdue excludes finished and unknown tasks', () => {
  assert.deepEqual(filterTasks(tasks, { due: 'overdue' }, '2026-10-20').map(t => t.taskId), ['t_1']);
});
test('filters compose and unknown user is never assumed', () => {
  assert.equal(filterTasks(tasks, { mine: true }).length, 0);
  assert.equal(filterTasks(tasks, { mine: true, currentUserId: 'user_demo', person: 'p_demo', type: 'NONE' }).length, 1);
  assert.equal(filterTasks(tasks, { due: 'unknown' })[0].taskId, 't_2');
});
test('KST day changes independently of host timezone', () => {
  assert.equal(kstToday(new Date('2026-10-19T15:01:00Z')), '2026-10-20');
});
test('manual deadline correction clears default marker without mutation', () => {
  const before = { dueDate: '2026-10-20', dueDateIsDefault: true };
  assert.deepEqual(editedDocument(before, { dueDate: null }), { dueDate: null, dueDateIsDefault: false });
  assert.equal(before.dueDateIsDefault, true);
});
test('human confirmation cannot be skipped; unsupported status absent', () => {
  assert.deepEqual(NEXT_STATUSES.DRAFT, ['CONFIRMED', 'CANCELLED']);
  assert.deepEqual(NEXT_STATUSES.CANCELLED, []);
  assert.equal(Object.hasOwn(NEXT_STATUSES, 'IN_PROGRESS'), false);
});
