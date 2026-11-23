import assert from 'node:assert/strict';
import test from 'node:test';
import { buildInboxSummary, pendingPeopleCount, runWithLimit } from './inboxItems.js';

const notes = [{ noteId: 'mn_1' }, { noteId: 'mn_2' }];
const person = (decision, decidedBy) => ({ resolution: { decision, decidedBy } });

test('사람이 골라야 하는 참석자만 센다', () => {
  assert.equal(pendingPeopleCount([
    person('CANDIDATES', 'MODEL'),
    person('NEW', 'RULE'),
    person('CONFIRMED', 'RULE'),
    person('CONFIRMED', 'HUMAN'),
  ]), 2);
});

test('식별 결과가 없는 참석자는 끝난 것으로 세지 않는다', () => {
  assert.equal(pendingPeopleCount([], 2), 2);
  assert.equal(pendingPeopleCount([person('CONFIRMED', 'RULE')], 2), 1);
  const summary = buildInboxSummary([{ noteId: 'mn_1', participantNames: ['홍 부장'] }], [], { mn_1: [] });
  assert.equal(summary.rows[0].pendingPeople, 1);
});

test('회의록별 확인 전 할 일·사람과 합계를 만든다', () => {
  const tasks = [
    { taskId: 't_1', noteId: 'mn_1', status: 'DRAFT' },
    { taskId: 't_2', noteId: 'mn_1', status: 'CONFIRMED' },
    { taskId: 't_3', noteId: 'mn_2', status: 'DRAFT' },
  ];
  const summary = buildInboxSummary(notes, tasks, { mn_1: [person('CANDIDATES', 'MODEL')], mn_2: [] });
  assert.deepEqual(summary.rows.map(({ draftTasks, pendingPeople }) => [draftTasks, pendingPeople]), [[1, 1], [1, 0]]);
  assert.deepEqual(summary.totals, { notes: 2, tasks: 2, people: 1 });
  assert.deepEqual(summary.otherDraftTasks, []);
});

test('대기함에 없는 회의록의 초안은 따로 모은다', () => {
  const tasks = [{ taskId: 't_9', noteId: 'mn_8', status: 'DRAFT' }, { taskId: 't_8', noteId: 'mn_8', status: 'DONE' }];
  const summary = buildInboxSummary(notes, tasks, { mn_1: [], mn_2: [] });
  assert.deepEqual(summary.otherDraftTasks.map((task) => task.taskId), ['t_9']);
  assert.equal(summary.totals.tasks, 1);
});

test('불러오지 못한 값을 0으로 보이지 않는다', () => {
  const failed = buildInboxSummary(notes, null, { mn_1: [], mn_2: null });
  assert.equal(failed.rows[0].draftTasks, null);
  assert.equal(failed.rows[1].pendingPeople, null);
  assert.equal(failed.totals.tasks, null);
  assert.equal(failed.totals.people, null);

  const loading = buildInboxSummary(notes, undefined, { mn_1: [] });
  assert.equal(loading.rows[0].draftTasks, undefined);
  assert.equal(loading.rows[1].pendingPeople, undefined);
  assert.equal(loading.totals.people, undefined);
});

test('동시 실행 수를 넘지 않고 전부 실행한다', async () => {
  let running = 0;
  let peak = 0;
  const done = [];
  await runWithLimit([1, 2, 3, 4, 5], 2, async (value) => {
    running += 1;
    peak = Math.max(peak, running);
    await new Promise((resolve) => { setTimeout(resolve, 1); });
    running -= 1;
    done.push(value);
  });
  assert.equal(peak, 2);
  assert.deepEqual(done.sort(), [1, 2, 3, 4, 5]);
});
