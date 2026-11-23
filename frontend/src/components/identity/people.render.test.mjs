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
test('empty linked history explains exact identity links without fabricating meetings', () => {
  const html = renderToStaticMarkup(createElement(components.MeetingHistory, { meetings: [], personId: 'p_17' }));
  assert.match(html, /인물이 연결된 확정 회의록/);
  assert.doesNotMatch(html, /B 회의록/);
});
test('promise records show original evidence and do not claim open status', () => {
  const html = renderToStaticMarkup(createElement(components.MeetingHistory, { personId: 'p_17', tab: '약속 기록', meetings: [{ noteId: 'mn_1', status: 'CONFIRMED', mode: 'LIGHT',
    perPerson: [{ personId: 'p_17', followUp: { text: '가상 자료 전달', evidence: '가상 자료를 전달하기로 함' } }] }] }));
  assert.match(html, /이행 상태는 확인되지/); assert.match(html, /원문 근거: 가상 자료를 전달하기로 함/); assert.match(html, /\/meeting-notes\/mn_1/);
});

test('people screen shares the existing app menu and account header without a second sidebar', async () => {
  const { MemoryRouter } = await import('react-router-dom');
  const { DashboardLayout } = await vite.ssrLoadModule('/src/components/DashboardLayout.jsx');
  const { PeopleScreen } = await vite.ssrLoadModule('/src/components/identity/PeopleScreen.jsx');
  const storage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: { getItem: () => null } });
  try {
    const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/people'] },
      createElement(DashboardLayout, null, createElement(PeopleScreen))));
    assert.equal((html.match(/aria-current="page"/g) || []).length, 1);
    assert.match(html, /로그아웃/);
    assert.match(html, /사람 목록/);
    assert.match(html, /가상 화면 예시/);
    const peopleLink=html.match(/<button[^>]*aria-current="page"[\s\S]*?<\/button>/)?.[0];
    assert.match(peopleLink,/lucide-contact-round/);
    assert.doesNotMatch(peopleLink,/lucide-users-round/);
    assert.doesNotMatch(html, /people-sidebar/);
  } finally {
    if (storage) Object.defineProperty(globalThis, 'localStorage', storage);
    else delete globalThis.localStorage;
  }
});

test('merge comparison preserves conflicting company/contact values and labels the retained primary identity', async () => {
  const { MergeComparison } = await vite.ssrLoadModule('/src/components/identity/PersonMergePanel.jsx');
  const target = { personId: 'p_17', name: '가상인물_대표', company: '가상회사_파랑', emails: ['target@fake.example'] };
  const source = { personId: 'p_18', name: '가상인물_중복', company: '가상회사_초록', emails: ['source@fake.example'] };
  const before = JSON.stringify([target, source]);
  const html = renderToStaticMarkup(createElement(MergeComparison, { target, source }));
  assert.match(html, /유지할 대표 인물/); assert.match(html, /합칠 인물 · 원본 보존/);
  assert.match(html, /가상회사_파랑/); assert.match(html, /가상회사_초록/);
  assert.match(html, /target@fake.example/); assert.match(html, /source@fake.example/);
  assert.match(html, /전화: 미등록/); assert.equal(JSON.stringify([target, source]), before);
});
