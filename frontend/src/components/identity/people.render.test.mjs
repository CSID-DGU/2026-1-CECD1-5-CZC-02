import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';
let vite, components;
before(async () => {
  vite = await createServer({ server: { middlewareMode: true, hmr: false, ws: false }, appType: 'custom' });
  components = await vite.ssrLoadModule('/src/components/identity/PeoplePage.jsx');
});
after(async () => { await vite?.close(); });
test('person details preserve registered aliases and unknown values without guessing a type', () => {
  const html = renderToStaticMarkup(createElement(components.PersonDetails, { person: { personId: 'p_17', name: '가상인물_001',
    nameVariants: ['Synthetic Person 001'], company: null, isSynthetic: true, companyHistory: [{ company: '가상회사_이전', title: null, until: null }] } }));
  assert.match(html, /Synthetic Person 001/); assert.match(html, /미등록/); assert.match(html, /종료일 미기록/);
});
test('empty linked history explains pending B integration rather than fabricating meetings', () => {
  const html = renderToStaticMarkup(createElement(components.MeetingHistory, { meetings: [], personId: 'p_17' }));
  assert.match(html, /B 회의록에 반영되지 않은/);
});
test('promise records show original evidence and do not claim open status', () => {
  const html = renderToStaticMarkup(createElement(components.MeetingHistory, { personId: 'p_17', tab: '약속 기록', meetings: [{ noteId: 'mn_1', status: 'CONFIRMED', mode: 'LIGHT',
    perPerson: [{ personId: 'p_17', followUp: { text: '가상 자료 전달', evidence: '가상 자료를 전달하기로 함' } }] }] }));
  assert.match(html, /이행 상태는 확인되지/); assert.match(html, /원문 근거: 가상 자료를 전달하기로 함/); assert.match(html, /\/meeting-notes\/mn_1/);
});
