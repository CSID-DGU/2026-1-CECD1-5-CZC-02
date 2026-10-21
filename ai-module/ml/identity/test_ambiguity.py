"""교차 검수 재현·관측상 모호함·후보 응답 채점을 검사한다."""

from collections import Counter
from copy import deepcopy

import pytest

from app.identity.features import compare_people
from ml.identity.ambiguity import ambiguous_expectation, check_ambiguous_response
from ml.identity.evaluate_candidates import measure
from ml.identity.generate_dataset import build_dataset, validate_dataset


@pytest.fixture(scope='module')
def reviewed():
    return build_dataset()


def test_cross_review_counts_and_no_source_or_split_loss(reviewed):
    train, evaluation = reviewed['trainPairs'], reviewed['evalPairs']
    assert Counter(p['label'] for p in train) == {0: 1147, 1: 1189, None: 64}
    assert Counter(p['label'] for p in evaluation) == {0: 300, 1: 300}
    ambiguous = [p for p in train if p['label'] is None]
    assert Counter(p['caseType'] for p in ambiguous) == {'same_name': 53, 'missing_company': 11}
    assert {(p['personIdA'], p['personIdB']) for p in ambiguous if p['caseType']=='same_name'} == {('p_016','p_015')}
    assert all(p['expected']['kind'] == 'AMBIGUOUS' and len(p['expected']['targetPersonIds']) == 2 for p in ambiguous)
    assert len(reviewed['persons']) == 300
    validate_dataset(reviewed)


@pytest.mark.parametrize('corruption', ['binary_for_ambiguous', 'missing_expected', 'wrong_target', 'fake_ambiguity'])
def test_wrong_or_forced_labels_are_rejected(reviewed, corruption):
    broken = deepcopy(reviewed)
    pair = next(p for p in broken['trainPairs'] if p['label'] is None)
    if corruption == 'binary_for_ambiguous':
        pair['label'] = int(pair['personIdA'] == pair['personIdB'])
    elif corruption == 'missing_expected':
        del pair['expected']
    elif corruption == 'wrong_target':
        pair['expected']['targetPersonIds'] = [broken['split']['evalPersonIds'][0]]
    else:
        pair = next(p for p in broken['trainPairs'] if p['caseType']=='exact')
        pair['label'] = None
        pair['expected'] = {'kind':'AMBIGUOUS','targetPersonIds':['p_001','p_002']}
    with pytest.raises(ValueError):
        validate_dataset(broken)


def fixture_pair():
    people = [
        {'personId':'p_a', 'name':'가상인물_0001', 'company':'가상회사_002','title':'팀장',
         'emails':['synthetic-a@people.example'], 'isSynthetic': True,
         'companyHistory':[{'company':'가상회사_001','title':'과장'}]},
        {'personId':'p_b', 'name':'가상인물_0001', 'company':'가상회사_001','title':'과장',
         'emails':['synthetic-b@people.example'], 'isSynthetic': True},
    ]
    pair = {'pairId':'pair_demo', 'caseType':'same_name', 'label':None,
            'personIdA':'p_a','personIdB':'p_b',
            'mentionB':{'name':'가상인물_0001','company':'가상회사_001','title':'과장','channel':'MEETING'}}
    pair['expected'] = ambiguous_expectation(pair,people)
    return pair, people


def test_exact_previous_affiliation_is_not_evidence_for_different_people():
    pair, people = fixture_pair()
    original = deepcopy((pair,people))
    assert ambiguous_expectation(pair, people)['targetPersonIds'] == ['p_a','p_b']
    pair['mentionB']['email'] = 'synthetic-b@people.example'
    assert ambiguous_expectation(pair, people) is None
    assert people == original[1]


def test_company_missing_does_not_use_title_to_guess_homonym():
    pair, people = fixture_pair()
    pair['caseType'] = 'missing_company'
    pair['personIdA'] = pair['personIdB'] = 'p_b'
    pair['mentionB']['company'] = None
    pair['mentionB']['title'] = '과장'
    assert ambiguous_expectation(pair, people)['targetPersonIds'] == ['p_a','p_b']
    pair['mentionB']['name'] = None
    assert ambiguous_expectation(pair, people) is None


def good_response():
    return {'decision':'CANDIDATES','personId':None,'candidates':[
        {'personId':'p_a','score':None,'reasons':['동명이인·이전 소속 중복 — 확인 필요']},
        {'personId':'p_b','score':None,'reasons':['같은 이름·소속 — 이 중 누구인지 확인 필요']},
    ]}


@pytest.mark.parametrize('decision', ['CONFIRMED','NEW'])
def test_ambiguous_response_cannot_confirm_or_create(decision):
    pair,_ = fixture_pair()
    response = good_response()
    assert check_ambiguous_response(pair,response)
    response['decision'] = decision
    assert not check_ambiguous_response(pair,response)


@pytest.mark.parametrize('corruption',['one_candidate','unknown','empty_reasons','filled_person_id','duplicates'])
def test_candidates_need_legitimate_options_and_reasons(corruption):
    pair,_ = fixture_pair()
    response = good_response()
    if corruption == 'one_candidate': response['candidates'].pop()
    elif corruption == 'unknown': response['candidates'][0]['personId']='p_unknown'
    elif corruption == 'empty_reasons': response['candidates'][0]['reasons']=[]
    elif corruption == 'filled_person_id': response['personId']='p_a'
    else: response['candidates'][1]['personId']='p_a'
    assert not check_ambiguous_response(pair,response)


def test_ambiguous_examples_are_not_binary_false_positives_or_single_id_retrieval():
    pair, people = fixture_pair()
    prepared = [(pair,compare_people(pair['mentionB'],people))]
    metrics = measure(prepared,.88,ambiguous_responses={pair['pairId']:good_response()})
    assert metrics['retrieval']['queries'] == 0
    assert metrics['retrieval']['top1Guaranteed'] is None
    assert metrics['pairCandidateInclusion']['fp'] == metrics['pairCandidateInclusion']['tp'] == 0
    assert metrics['pairCandidateInclusion']['excludedAmbiguous'] == 1
    assert metrics['ambiguity']['allTargetsIncluded'] == 1
    assert metrics['ambiguity']['responsesEvaluated'] == 1
    assert metrics['ambiguity']['responseAccuracy'] == 1
    no_response = measure(prepared,.88)
    assert no_response['ambiguity']['responseAccuracy'] is None
    assert no_response['ambiguity']['responsesEvaluated'] == 0


def test_more_than_three_options_require_search_guidance_and_valid_three():
    pair,_ = fixture_pair()
    pair['expected']['targetPersonIds'] = ['p_a','p_b','p_c','p_d']
    response = good_response()
    response['candidates'].append({'personId':'p_c','score':None,'reasons':['같은 이름 후보']})
    assert not check_ambiguous_response(pair,response)
    response['candidates'][0]['reasons'].append('나머지 후보는 인물 대장에서 직접 검색해 주세요')
    assert check_ambiguous_response(pair,response)
    response['candidates'].append({'personId':'p_d','score':None,'reasons':['다른 후보']})
    assert not check_ambiguous_response(pair,response)
