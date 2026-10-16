"""실패 분류·동점 처리·속도 측정 요약·가짜 부하 대장을 검사한다."""
from copy import deepcopy

import pytest

from app.identity.features import compare_people
from ml.identity.analyze_failures import analyze
from ml.identity.benchmark_candidates import make_roster, select_queries, timing_summary


def person(n, **updates):
    return {"personId": f"p_{n:03}", "name": f"가상인물_{n:04}",
            "company": "가상회사_001", "isSynthetic": True, **updates}


def prepared(mention, people, target='p_001', case='typo'):
    pair = {"pairId": "pair_synthetic_001", "caseType": case, "personIdB": target,
            "mentionB": mention, "evidence": {"mentionB": "가짜 검사 원문"}}
    return [(pair, compare_people(mention, people))]


def test_missing_target_is_counted_and_evidence_preserved():
    m = {"name": "가상인믈_0001", "company": "가상회사_001", "channel": "MEETING"}
    p = prepared(m, [person(1)])
    exact = analyze(p, None)
    assert exact['counts']['missed'] == 1
    assert exact['samples']['TARGET_MISSED'][0]['targetNameStatus'] == 'DIFFERENT'
    assert exact['samples']['TARGET_MISSED'][0]['evidence'] == '가짜 검사 원문'
    fuzzy = analyze(p, .88)
    assert fuzzy['counts']['missed'] == 0
    assert fuzzy['counts']['extraCandidates'] == 0


def test_extra_fuzzy_candidate_is_not_a_final_identity_error():
    m = {"name": "가상인물_0001", "company": "가상회사_001", "channel": "MEETING"}
    result = analyze(prepared(m, [person(1), person(2)]), .88)
    assert result['counts']['missed'] == 0
    assert result['counts']['extraCandidates'] == 1
    assert result['extraCandidateReasons'] == {'SIMILAR_NAME_AND_COMPANY_OR_HISTORY': 1}
    sample = result['samples']['SIMILAR_NAME_AND_COMPANY_OR_HISTORY'][0]
    assert sample['targetPersonId'] == 'p_001'
    assert sample['extraPersonId'] == 'p_002'
    assert sample['companyMatchedPaths'] == ('company',)


def test_ties_and_repeated_query_counts_are_explicit():
    m = {"name": "가상인물_0001", "channel": "MEETING"}
    p = prepared(m, [person(1), person(2, name="가상인물_0001")], case='same_name')
    result = analyze(p * 2, .88, sample_limit=1)
    assert result['counts']['queries'] == 2
    assert result['uniqueObservedQueries'] == 1
    assert result['counts']['top1NotGuaranteed'] == 2
    assert result['counts']['top3NotGuaranteed'] == 0
    assert len(result['samples']['TOP1_TIE_OR_LOWER_RANK']) == 1


def test_synthetic_load_roster_has_unique_identity_and_preserves_source():
    baseline = [person(1)]
    before = deepcopy(baseline)
    result = make_roster(baseline, 4)
    assert baseline == before
    assert len({p['personId'] for p in result}) == 4
    assert all(p['isSynthetic'] for p in result)
    assert all(p['name'].startswith('가상부하인물_') for p in result[1:])
    assert all(p['emails'][0].endswith('@load.example') for p in result[1:])
    assert make_roster(baseline, 1) == baseline
    with pytest.raises(ValueError):
        make_roster(baseline, 0)


def test_nearest_rank_p95_and_median():
    summary = timing_summary(list(range(1, 21)))
    assert summary['medianMs'] == 10.5
    assert summary['p95Ms'] == 19
    assert summary['samples'] == 20
    assert timing_summary([3])['p95Ms'] == 3
    with pytest.raises(ValueError):
        timing_summary([])


def test_speed_queries_are_copied_one_per_case():
    pairs = [{'caseType': 'exact', 'mentionB': {'name': '가상인물_0001'}},
             {'caseType': 'exact', 'mentionB': {'name': '가상인물_0002'}},
             {'caseType': 'typo', 'mentionB': {'name': '가상인믈_0001'}}]
    queries = select_queries(pairs)
    assert len(queries) == 2
    queries[0]['name'] = None
    assert pairs[0]['mentionB']['name'] == '가상인물_0001'
