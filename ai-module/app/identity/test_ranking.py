"""유사도 후보의 제한·순위·동점·충돌 보존 검사."""
from copy import deepcopy

import pytest

from app.identity.ranking import rank_candidates


def person(number, **updates):
    return {"personId": f"p_{number:03}", "name": f"가상인물_{number:04}",
            "company": "가상회사_001", "isSynthetic": True, **updates}


def mention(**updates):
    return {"name": "가상인믈_0001", "channel": "MEETING", **updates}


def test_fuzzy_needs_explicit_threshold_and_company():
    people = [person(1)]
    assert rank_candidates(mention(company="가상회사_001"), people) == []
    assert rank_candidates(mention(), people, fuzzy_threshold=.88) == []
    result, = rank_candidates(mention(company="가상회사_001"), people, fuzzy_threshold=.88)
    assert result.candidate.supporting_fields == ("name_similarity", "company")
    assert result.candidate.comparison.name.status == "DIFFERENT"
    assert rank_candidates(mention(company="가상회사_001"), people, fuzzy_threshold=.9) == []


def test_short_names_not_added_by_similarity():
    assert rank_candidates(mention(name="가샹", company="가상회사_001"),
                           [person(1, name="가상")], fuzzy_threshold=.5) == []


def test_historical_company_and_alias_fuzzy_support():
    result, = rank_candidates(mention(name="Synthetic Persom 0001", company="가상회사_002"),
        [person(1, nameVariants=["Synthetic Person 0001"], companyHistory=[{"company": "가상회사_002"}])],
        fuzzy_threshold=.88)
    assert result.person_id == "p_001"
    assert result.candidate.comparison.company.matched_paths == ("companyHistory[0].company",)


def test_contact_then_exact_then_fuzzy_order():
    people = [person(1), person(2, name="가상인믈_0001"),
              person(3, name="가상인물_9999", emails=["synthetic@people.example"])]
    m = mention(company="가상회사_001", email="synthetic@people.example")
    original = deepcopy((m, people))
    results = rank_candidates(m, people, fuzzy_threshold=.88)
    assert [r.person_id for r in results] == ["p_003", "p_002", "p_001"]
    assert "CONTACT_MATCH_NAME_DIFFERS" in results[0].candidate.comparison.warnings
    assert (m, people) == original


def test_ties_are_retained_and_explicit():
    results = rank_candidates(mention(name="가상인물_0001"),
                              [person(n, name="가상인물_0001") for n in (4, 3, 2, 1)])
    assert len(results) == 4
    assert len({r.priority for r in results}) == 1
    assert [r.person_id for r in results] == ["p_001", "p_002", "p_003", "p_004"]


def test_shared_contacts_and_disagreement_are_not_hidden():
    people = [person(1, emails=["shared@people.example"]),
              person(2, emails=["shared@people.example"]), person(3, phones=["000-0000-0003"])]
    results = rank_candidates(mention(name=None, email="shared@people.example", phone="000-0000-0003"), people)
    assert len(results) == 3
    assert all("CONTACT_TARGETS_DISAGREE" in r.candidate.comparison.warnings for r in results)
    assert all("SHARED_EMAIL" in r.candidate.comparison.warnings for r in results if r.person_id != "p_003")


@pytest.mark.parametrize("threshold", [0, -1, 1.1, float("nan"), float("inf")])
def test_invalid_threshold(threshold):
    with pytest.raises(ValueError):
        rank_candidates(mention(), [], fuzzy_threshold=threshold)
