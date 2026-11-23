import assert from 'node:assert/strict';
import test from 'node:test';
import { FLUSH_EVERY_SECONDS, IDLE_LIMIT_MS, createReviewTimer } from './reviewTimer.js';

function clock() {
  let value = 1000;
  return { now: () => value, move: (ms) => { value += ms; } };
}

test('탭이 보이고 최근 조작이 있을 때만 센다', async () => {
  const time = clock();
  const sent = [];
  const timer = createReviewTimer(async (seconds) => sent.push(seconds), time.now);
  timer.tick(true);
  timer.tick(false);
  time.move(IDLE_LIMIT_MS + 1);
  timer.tick(true);
  timer.markActivity();
  timer.tick(true);
  await timer.flush();
  assert.deepEqual(sent, [2]);
  assert.equal(timer.pendingSeconds(), 0);
});

test('일정 시간마다 자동으로 보낸다', async () => {
  const time = clock();
  const sent = [];
  const timer = createReviewTimer(async (seconds) => sent.push(seconds), time.now);
  for (let i = 0; i < FLUSH_EVERY_SECONDS; i += 1) timer.tick(true);
  await timer.flush();
  assert.deepEqual(sent, [FLUSH_EVERY_SECONDS]);
});

test('전송에 실패해도 오류를 내지 않고 다음에 다시 보낸다', async () => {
  const time = clock();
  const sent = [];
  let fail = true;
  const timer = createReviewTimer(async (seconds) => {
    if (fail) throw new Error('network');
    sent.push(seconds);
  }, time.now);
  timer.tick(true);
  await timer.flush();
  assert.equal(timer.pendingSeconds(), 1);
  fail = false;
  timer.tick(true);
  await timer.flush();
  assert.deepEqual(sent, [2]);
});

test('전송 중에 쌓인 시간도 빠뜨리지 않는다', async () => {
  const time = clock();
  const sent = [];
  let release;
  const gate = new Promise((resolve) => { release = resolve; });
  const timer = createReviewTimer(async (seconds) => {
    sent.push(seconds);
    if (sent.length === 1) await gate;
  }, time.now);
  timer.tick(true);
  const first = timer.flush();
  await Promise.resolve();
  timer.tick(true);
  const second = timer.flush();
  release();
  await Promise.all([first, second]);
  assert.deepEqual(sent, [1, 1]);
});
