"""후보 포함/제외 기준과 모호한 후보를 숨기지 않는지 검사한다."""

from copy import deepcopy
from dataclasses import asdict

import pytest

from app.identity.candidates import find_candidates


def person(number, **updates):
    result = {"personId": f"p_{number:03}", "name": f"가상인물_{number:04}",
              "company": "가상회사_001", "isSynthetic": True,
              "emails": [f"synthetic{number}@people.example"],
              "phones": [f"000-0000-{number:04}"]}
    result.update(updates)
    return result


def mention(**updates):
    return {"name": None, "channel": "MEETING", **updates}


@pytest.mark.parametrize("fields,supports", [
    ({"name": "가상인물_0001 부장님"}, ("name",)),
    ({"email": "synthetic1@PEOPLE.EXAMPLE"}, ("email",)),
    ({"phone": "(000) 0000-0001"}, ("phone",)),
    ({"name": "가상인물_0001", "company": "가상회사_001"}, ("name", "company")),
    ({"email": "synthetic1@people.example", "phone": "00000000001"}, ("email", "phone")),
])
def test_exact_support_selects_candidate(fields, supports):
    results = find_candidates(mention(**fields), [person(2), person(1)])
    assert [r.person_id for r in results] == ["p_001"]
    assert results[0].supporting_fields == supports


def test_registered_alias_and_history_preserve_paths():
    p = person(1, nameVariants=["Synthetic Person 0001"],
               companyHistory=[{"company": "가상회사_002", "until": None}])
    result, = find_candidates(mention(name="synthetic person 0001", company="가상회사_002"), [p])
    assert result.supporting_fields == ("name", "company")
    assert result.comparison.name.matched_paths == ("nameVariants[0]",)
    assert result.comparison.company.matched_paths == ("companyHistory[0].company",)


@pytest.mark.parametrize("fields", [
    {}, {"name": "  ", "email": "bad", "phone": "***"},
    {"company": "가상회사_001"}, {"title": "부장"},
    {"name": "가상인물_9999"},
    {"name": "가상인물_0002", "company": "가상회사_001"},
])
def test_insufficient_or_unmatched_support_does_not_select(fields):
    assert find_candidates(mention(**fields), [person(1)]) == []


def test_same_name_different_company_is_retained_for_review():
    results = find_candidates(mention(name="가상인물_0001", company="가상회사_999"), [person(1)])
    assert results[0].supporting_fields == ("name",)
    assert results[0].comparison.company.status == "DIFFERENT"


def test_shared_contact_keeps_all_people_and_warnings():
    people = [person(1), person(2, emails=["synthetic1@people.example"])]
    results = find_candidates(mention(name="가상인물_0001", email="synthetic1@people.example"), people)
    assert len(results) == 2
    assert all("SHARED_EMAIL" in r.comparison.warnings for r in results)
    assert "CONTACT_MATCH_NAME_DIFFERS" in results[1].comparison.warnings


def test_contact_target_conflict_is_retained_on_both_candidates():
    results = find_candidates(mention(email="synthetic1@people.example", phone="000-0000-0002"),
                              [person(1), person(2), person(3)])
    assert [r.person_id for r in results] == ["p_001", "p_002"]
    assert all("CONTACT_TARGETS_DISAGREE" in r.comparison.warnings for r in results)


def test_more_than_three_homonyms_are_not_arbitrarily_hidden():
    people = [person(n, name="가상인물_0001") for n in (4, 2, 1, 3)]
    results = find_candidates(mention(name="가상인물_0001"), people)
    assert [r.person_id for r in results] == [p["personId"] for p in people]
    assert all(r.supporting_fields == ("name",) for r in results)
    assert not ({"score", "decision", "rank"} & asdict(results[0]).keys())
    reverse = find_candidates(mention(name="가상인물_0001"), people[::-1])
    assert [r.person_id for r in reverse] == [p["personId"] for p in people[::-1]]


def test_original_input_and_observed_evidence_preserved():
    m = mention(name=" 가상인물_0001 부장님 ", sourceRef="src_001")
    people = [person(1)]
    original = deepcopy((m, people))
    results = find_candidates(m, people)
    assert (m, people) == original
    assert results[0].comparison.name.observed == m["name"]
    assert find_candidates(dict(m, channel="EMAIL", sourceRef="src_999", label=0), people) == results


def test_empty_roster_and_duplicate_id():
    assert find_candidates(mention(name="가상인물_0001"), []) == []
    with pytest.raises(ValueError, match="personId"):
        find_candidates(mention(), [person(1), person(1)])
