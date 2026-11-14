import test from 'node:test';
import assert from 'node:assert/strict';
import { createPeopleApi } from './peopleApi.js';
import { commitmentRecords, duplicateNames } from './peopleUtils.js';

test('people API preserves paging and abort signal and uses server-owned identity paths', async () => {
  const calls = []; const signal = new AbortController().signal;
  const transport = { get: async (...args) => { calls.push(args); return { data: { data: [] } }; } };
  const api = createPeopleApi(transport);
  await api.list('가상', 2, signal); await api.person('p_17', signal); await api.meetings('p_17', 3, signal);
  assert.deepEqual(calls[0], ['/api/persons', { params: { q: '가상', page: 2, size: 20 }, signal }]);
  assert.equal(calls[1][0], '/api/persons/p_17');
  assert.deepEqual(calls[2], ['/api/persons/p_17/meetings', { params: { page: 3, size: 20 }, signal }]);
});
test('failed history retrieval is not replaced by samples or an empty success', async () => {
  const api = createPeopleApi({ get: async () => { throw new Error('failed'); } });
  await assert.rejects(api.meetings('p_17'), /failed/);
});
test('homonym badges describe only names observed in the current page', () => {
  assert.deepEqual([...duplicateNames([{ name: '가상인물_001' }, { name: ' 가상인물_001 ' }, { name: '가상인물_002' }])], ['가상인물_001']);
});
test('meeting promises retain evidence and never imply an individual owner in multi-party FULL', () => {
  const note = { noteId: 'mn_1', status: 'CONFIRMED', mode: 'FULL', participants: [{ personId: 'p_17' }, { personId: null }],
    fields: { commitments: [{ text: '가상 자료 제공', evidence: '가상 자료를 보내기로 함' }] } };
  const [record] = commitmentRecords([note], 'p_17');
  assert.equal(record.evidence, note.fields.commitments[0].evidence);
  assert.match(record.scope, /개인 귀속 미확인/);
  assert.deepEqual(commitmentRecords([note], 'p_18'), []);
  assert.deepEqual(commitmentRecords([{ ...note, status: 'DRAFT' }], 'p_17'), []);
});
test('LIGHT follow-ups require exact person IDs and do not match display names', () => {
  const followUp = { text: '가상 후속', evidence: '가상 후속을 약속함' };
  const note = { noteId: 'mn_1', status: 'CONFIRMED', mode: 'LIGHT', perPerson: [{ personId: 'p_17', followUp }, { personId: 'p_18', followUp }] };
  assert.equal(commitmentRecords([note], 'p_17').length, 1);
});

test('explicit preview is isolated from real data and each read returns a copy', async () => {
  const { peoplePreviewApi } = await import('./peoplePreview.js');
  const people = await peoplePreviewApi.list();
  assert.equal(people.length, 3);
  assert.ok(people.every(p => p.isSynthetic && p.personId.startsWith('p_preview_')));
  people[0].name = 'changed locally';
  assert.equal((await peoplePreviewApi.person('p_preview_001')).name, '가상인물_001');
  assert.equal((await peoplePreviewApi.list('가상회사_초록')).length, 1);
  assert.equal((await peoplePreviewApi.list('', 1)).length, 0);
});
test('preview history uses exact IDs and preserves cancellation and missing records', async () => {
  const { peoplePreviewApi } = await import('./peoplePreview.js');
  const notes = await peoplePreviewApi.meetings('p_preview_001');
  assert.equal(notes.length, 2);
  assert.deepEqual(await peoplePreviewApi.meetings('p_preview_002'), []);
  assert.ok(notes.every(n => n.participants.every(p => p.personId === 'p_preview_001')));
  await assert.rejects(peoplePreviewApi.person('p_unknown'), /미리보기 인물이 없습니다/);
  const controller = new AbortController(); controller.abort();
  await assert.rejects(peoplePreviewApi.list('', 0, controller.signal), { name: 'AbortError' });
});
