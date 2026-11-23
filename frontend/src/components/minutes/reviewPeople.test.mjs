import assert from 'node:assert/strict';
import test from 'node:test';
import { reviewPeople } from './meetingNoteUtils.js';

const item = (resolutionId, decision, personId) => ({ resolution: { resolutionId, decision, personId } });

test('확정 전에도 저장된 인물 선택을 상대방 목록에 넣는다', () => {
  const participants = [{ displayName: '이가상', personId: null, resolutionId: 'ir_1' }];
  assert.deepEqual(reviewPeople(participants, [item('ir_1', 'CONFIRMED', 'p_1')]), [{ personId: 'p_1', name: '이가상' }]);
});

test('아직 사람이 정해지지 않은 참석자는 넣지 않는다', () => {
  const participants = [{ displayName: '이가상', personId: null, resolutionId: 'ir_1' }, { displayName: '박샘플', personId: null, resolutionId: 'ir_2' }];
  assert.deepEqual(reviewPeople(participants, [item('ir_1', 'CANDIDATES', null), item('ir_2', 'NEW', null)]), []);
});

test('확정된 참석자는 그대로 쓰고 같은 사람은 한 번만 넣는다', () => {
  const participants = [
    { displayName: '이가상', personId: 'p_1', resolutionId: 'ir_1' },
    { displayName: '이 부장', personId: null, resolutionId: 'ir_2' },
  ];
  assert.deepEqual(reviewPeople(participants, [item('ir_2', 'CONFIRMED', 'p_1')]), [{ personId: 'p_1', name: '이가상' }]);
});
