"""D-022의 확정 경계·모호함·충돌·응답 계약을 네트워크 없이 검사한다."""

import json
import re
from copy import deepcopy
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator

from app.identity.engine import has_identity_clue, resolve_identities
from ml.identity.ambiguity import check_ambiguous_response

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = json.loads((ROOT.parent / 'contracts/identity_resolution.schema.json').read_text(encoding='utf-8'))


def person(number=1, **updates):
    return {'personId':f'p_{number:03}', 'name':f'가상인물_{number:04}', 'company':'가상회사_001',
            'nameVariants':[f'Synthetic Person {number:04}'], 'title':'과장',
            'emails':[f'synthetic{number}@people.example'], 'phones':[f'000-0000-{number:04}'],
            'companyHistory':[{'company':'가상회사_002','title':'대리','until':'2025-12-31'}],
            'isSynthetic':True, **updates}


def mention(**updates):
    return {'name':'가상인물_0001', 'company':'가상회사_001', 'channel':'MEETING','sourceRef':'src_demo', **updates}


def resolve(m, people=None):
    result, = resolve_identities([m], people if people is not None else [person()])
    Draft202012Validator(SCHEMA).validate(result)
    assert result['mention'] == m
    assert result['decidedBy'] == 'RULE'
    assert all(c['score'] is None and c['reasons'] for c in result['candidates'])
    return result


@pytest.mark.parametrize('contacts', [
    {'email':'synthetic1@PEOPLE.EXAMPLE'}, {'phone':'(000) 0000-0001'},
    {'email':'synthetic1@people.example','phone':'00000000001'},
])
def test_unique_contact_without_conflict_can_confirm(contacts):
    result = resolve(mention(**contacts))
    assert result['decision'] == 'CONFIRMED'
    assert result['personId'] == 'p_001'
    assert [c['personId'] for c in result['candidates']] == ['p_001']


def test_name_and_company_alone_do_not_confirm():
    result = resolve(mention())
    assert result['decision'] == 'CANDIDATES'
    assert result['personId'] is None


@pytest.mark.parametrize('conflict', [
    {'name':'가상인물_9999'}, {'company':'가상회사_999'},
    {'phone':'000-0000-9999'}, {'phone':'000-****-0001'}, {'email':'bad-email'},
])
def test_conflicting_or_unusable_clues_do_not_confirm(conflict):
    m = mention(email='synthetic1@people.example', **({'phone':'00000000001'} if 'email' in conflict else {}))
    m.update(conflict)
    result = resolve(m)
    assert result['decision'] == 'CANDIDATES'
    assert result['personId'] is None
    assert any('확인 필요' in reason for c in result['candidates'] for reason in c['reasons'])


def test_registered_alias_and_previous_company_with_unique_contact():
    result = resolve(mention(name=' SYNTHETIC PERSON 0001 ',company='가상회사_002',email='synthetic1@people.example'))
    assert result['decision'] == 'CONFIRMED'
    reasons = result['candidates'][0]['reasons']
    assert '등록된 이름 별칭 일치' in reasons
    assert any('이전 소속' in r for r in reasons)


def test_contact_only_with_no_known_name_company_conflict():
    result = resolve(mention(name=None,company=None,email='synthetic1@people.example'))
    assert result['decision'] == 'CONFIRMED'


def test_shared_contact_is_not_auto_confirmed_even_with_same_name():
    people = [person(), person(2,name='가상인물_0001',emails=['synthetic1@people.example'])]
    result = resolve(mention(email='synthetic1@people.example'),people)
    assert result['decision'] == 'CANDIDATES'
    assert len(result['candidates']) == 2
    assert all(any('여러 인물' in r for r in c['reasons']) for c in result['candidates'])


def test_email_and_phone_target_conflict_keeps_both_candidates():
    result = resolve(mention(name=None,company=None,email='synthetic1@people.example',phone='000-0000-0002'),[person(),person(2)])
    assert result['decision'] == 'CANDIDATES'
    assert {c['personId'] for c in result['candidates']} == {'p_001','p_002'}
    assert all(any('서로 다른 인물' in r for r in c['reasons']) for c in result['candidates'])


def test_typos_return_candidates_with_observed_name_reason():
    result = resolve(mention(name='가상인믈_0001'))
    assert result['decision'] == 'CANDIDATES'
    assert any('가상인믈_0001 / 가상인물_0001' in reason for reason in result['candidates'][0]['reasons'])


@pytest.mark.parametrize('name', [None, '', '  ', '부장님'])
def test_insufficient_information_is_new_not_guessed_or_registered(name):
    m = mention(name=name)
    assert not has_identity_clue(m)
    result = resolve(m)
    assert result['decision'] == 'NEW'
    assert result['personId'] is None and result['candidates'] == []


def test_unmatched_person_is_new_and_empty_roster_does_not_load_samples():
    result = resolve(mention(name='가상미등록_다른표기',company='가상회사_999'))
    assert result['decision'] == 'NEW'
    assert has_identity_clue(result['mention'])
    assert resolve(mention(),[])['candidates'] == []
    assert resolve_identities([],[]) == []


def test_more_than_three_homonyms_have_stable_order_and_direct_search_guidance():
    people = [person(n,name='가상인물_0001') for n in (4,2,3,1)]
    first = resolve(mention(),people)
    second = resolve(mention(),people[::-1])
    assert first['decision'] == 'CANDIDATES'
    assert [c['personId'] for c in first['candidates']] == ['p_001','p_002','p_003']
    assert first['candidates'] == second['candidates']
    assert all(any('직접 검색' in r for r in c['reasons']) for c in first['candidates'])


def test_all_64_cross_review_cases_get_candidate_sets_from_observations_only():
    data = ROOT / 'ml/identity/data'
    people = json.loads((data/'persons.json').read_text(encoding='utf-8'))
    split = json.loads((data/'split.json').read_text(encoding='utf-8'))
    people = [p for p in people if p['personId'] in split['trainPersonIds']]
    pairs = [p for p in json.loads((data/'pairs_train.json').read_text(encoding='utf-8')) if p['label'] is None]
    assert len(pairs) == 64
    # expected·label·출처 인물 ID는 엔진에 전달하지 않는다.
    results = resolve_identities([p['mentionB'] for p in pairs],people)
    for pair, result in zip(pairs,results):
        Draft202012Validator(SCHEMA).validate(result)
        assert check_ambiguous_response(pair,result)


def test_preserves_inputs_and_assigns_unique_contract_ids():
    m, people = mention(name=' 가상인물_0001 과장님 '), [person()]
    before = deepcopy((m,people))
    results = resolve_identities([m,m],people)
    assert (m,people) == before
    assert results[0]['resolutionId'] != results[1]['resolutionId']
    assert all(re.fullmatch('ir_[0-9a-f]{32}',r['resolutionId']) for r in results)
    assert all('최근 미팅' not in reason for r in results for c in r['candidates'] for reason in c['reasons'])


def test_duplicate_ids_are_rejected_before_any_results():
    with pytest.raises(ValueError,match='중복'):
        resolve_identities([mention()],[person(),person()])
