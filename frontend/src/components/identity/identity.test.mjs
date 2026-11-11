import test from 'node:test';
import assert from 'node:assert/strict';
import { findReviewItem, hasIdentityClue, registrationBody, identityError } from './identityUtils.js';
import { createIdentityApi } from './identityApi.js';

const item = (id, name, email = null) => ({ resolution: { resolutionId: id, mention: { name, email } }, etag: '"irv-0"' });
test('matches exact IDs and email-only people without selecting first homonym', () => {
  const items = [item('ir_a','가상인물'),item('ir_b','가상인물'),item('ir_c',null,'synthetic@people.example')];
  assert.equal(findReviewItem({ resolutionId:'ir_a',displayName:'가상인물' },items).resolution.resolutionId,'ir_a');
  assert.equal(findReviewItem({displayName:'가상인물'},items),null);
  assert.equal(findReviewItem({displayName:'synthetic@people.example'},items).resolution.resolutionId,'ir_c');
  assert.equal(findReviewItem({resolutionId:'ir_stale',displayName:'synthetic@people.example'},items),null);
  const duplicate = [{displayName:'가상인물',resolutionId:'ir_a'},{displayName:'가상인물',resolutionId:'ir_a'}];
  assert.equal(findReviewItem(duplicate[0],items,duplicate),null);
});
test('missing clues include blanks, title-only names and unusable contacts', () => {
  for(const mention of [{},{name:'  '},{name:'부장님'},{email:'bad'},{phone:'000-****'}]) assert.equal(hasIdentityClue(mention),false);
  for(const mention of [{name:'가상인물 부장님'},{email:'synthetic@people.example'},{phone:'000-0000-0001'}]) assert.equal(hasIdentityClue(mention),true);
});
test('registration requires explicit name and data type and does not invent missing fields', () => {
  assert.throws(() => registrationBody({name:' '}));
  assert.throws(() => registrationBody({name:'가상인물',isSynthetic:''}));
  assert.deepEqual(registrationBody({name:' 가상인물 ',isSynthetic:'true'}),{
    name:'가상인물',company:null,emails:[],phones:[],channels:[],nameVariants:[],companyHistory:[],isSynthetic:true,
  });
});
test('uses only server selection endpoint with version, not separate person/revision writes', async () => {
  const calls=[];
  const saved={resolution:{resolutionId:'ir_a',decidedBy:'HUMAN',personId:'p_17'},etag:'"irv-1"'};
  const api=createIdentityApi({ post:async (...args) => {calls.push(args);return {data:{data:saved}};} });
  assert.deepEqual(await api.select('mn_21','ir_a',{personId:'p_17'},'"irv-0"'),saved);
  assert.deepEqual(calls,[['/api/person-identities/for-note/mn_21/ir_a/selection',{personId:'p_17'},{headers:{'If-Match':'"irv-0"'}}]]);
  await assert.rejects(api.select('mn_21','ir_a',{personId:'p_17'},null));
  assert.equal(calls.length,1);
});
test('failed selection is not presented as success', async () => {
  const conflict=Object.assign(new Error('conflict'),{response:{status:409}});
  const api=createIdentityApi({post:async () => {throw conflict;}});
  await assert.rejects(api.select('mn_21','ir_a',{personId:'p_17'},'"irv-0"'),error => error===conflict);
  assert.match(identityError(conflict),/다시 불러와/);
});
test('rejects unconfirmed success payloads instead of faking HUMAN locally', async () => {
  const api=createIdentityApi({post:async () => ({data:{data:{resolution:{resolutionId:'ir_a',decidedBy:'RULE',personId:'p_17'}}}})});
  await assert.rejects(api.select('mn_21','ir_a',{personId:'p_17'},'"irv-0"'));
});
test('searches real paged roster and reloads saved resolutions', async () => {
  const calls=[];
  const api=createIdentityApi({get:async (...args) => {calls.push(args);return {data:{data:[]}};}});
  await api.list('mn_21');await api.search('가상',1);await api.person('p_17');
  assert.equal(calls[0][0],'/api/person-identities/for-note/mn_21');
  assert.deepEqual(calls[1],['/api/persons',{params:{q:'가상',page:1,size:20}}]);
  assert.equal(calls[2][0],'/api/persons/p_17');
});
