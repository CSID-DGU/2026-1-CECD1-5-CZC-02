import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';
let vite;
let Row;
before(async () => {
  vite = await createServer({ server:{middlewareMode:true,hmr:false,ws:false}, appType:'custom' });
  ({IdentityCandidateRow:Row} = await vite.ssrLoadModule('/src/components/identity/IdentityCandidatesPanel.jsx'));
});
after(async () => { await vite?.close(); });
const person={personId:'p_17',name:'가상인물_0001',company:'가상회사_001'};
const base={resolutionId:'ir_owned',mention:{name:'가상인물_0001',channel:'MEETING'},decision:'CANDIDATES',personId:null,
  candidates:[{personId:'p_17',score:null,reasons:['동명이인 — 확인 필요']}],decidedBy:'RULE'};
const render=(resolution=base,readOnly=false) => renderToStaticMarkup(createElement(Row,{
  participant:{displayName:'가상인물_0001'}, item:resolution ? {resolution,etag:'"irv-0"'} : null,
  people:{p_17:person},readOnly,noteId:'mn_21',onSaved:async () => {},
}));
test('candidate view shows real labels, reasons and search/registration without a fake probability',() => {
  const html=render();assert.match(html,/가상회사_001/);assert.match(html,/동명이인/);assert.match(html,/인물 대장 직접 검색/);
  assert.match(html,/>새 사람으로 등록<\/button>/);assert.doesNotMatch(html,/\d+%/);
});
test('read only state disables selection and action buttons',() => {
  const html=render(base,true);assert.match(html,/<input[^>]*disabled/);
  for(const button of html.match(/<button\b[^>]*>/g)) assert.match(button,/disabled/);
});
test('information missing view does not encourage immediate new registration',() => {
  const html=render({...base,decision:'NEW',candidates:[],mention:{name:'부장님',channel:'MEETING'}});
  assert.match(html,/정보 부족/);assert.doesNotMatch(html,/>새 사람으로 등록<\/button>/);
});
test('saved human selection is shown without another editable candidate list',() => {
  const html=render({...base,decidedBy:'HUMAN',decision:'CONFIRMED',personId:'p_17'});
  assert.match(html,/사람이 선택/);assert.match(html,/저장됨/);assert.doesNotMatch(html,/type="radio"/);
});
test('missing stored resolution is shown honestly without sample fallback',() => {
  const html=render(null);assert.match(html,/저장된 식별 결과가 없거나/);assert.doesNotMatch(html,/type="radio"/);
});
