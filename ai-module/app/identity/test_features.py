"""단서 비교의 모호함·원문 근거·데이터 누수 방지를 검사한다."""

import json
from copy import deepcopy
from dataclasses import asdict
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator

from app.identity.features import compare_people, compare_person

ROOT = Path(__file__).resolve().parents[2]
CONTRACTS = ROOT.parent / "contracts"


def make_person(number=1, **updates):
    person = {"personId": f"p_{number:03}", "name": f"가상인물_{number:04}",
              "nameVariants": [f"Synthetic Person {number:04}"],
              "company": "가상회사_002", "isSynthetic": True,
              "emails": [f"synthetic{number}@people.example"], "phones": [f"000-0000-{number:04}"],
              "companyHistory": [{"company": "가상회사_001", "title": None, "until": None}]}
    person.update(updates)
    schema = json.loads((CONTRACTS / "person.schema.json").read_text())
    Draft202012Validator(schema).validate(person)
    return person


def make_mention(**updates):
    mention = {"name": "가상인물_0001", "channel": "MEETING"}
    mention.update(updates)
    schema = json.loads((CONTRACTS / "identity_resolution.schema.json").read_text())
    Draft202012Validator(schema["properties"]["mention"]).validate(mention)
    return mention


@pytest.mark.parametrize("name,path", [
    (" 가상인물_0001 부장님 ", "name"),
    (" SYNTHETIC  PERSON 0001 ", "nameVariants[0]"),
])
def test_name_matches_primary_or_registered_alias(name, path):
    result = compare_person(make_mention(name=name), make_person())
    assert result.name.status == "MATCH"
    assert result.name.matched_paths == (path,)
    assert result.name.observed == name


@pytest.mark.parametrize("company,path", [
    (" 가상회사_002 ", "company"), ("가상회사_001", "companyHistory[0].company"),
])
def test_current_and_history_are_distinct(company, path):
    result = compare_person(make_mention(company=company), make_person())
    assert result.company.status == "MATCH"
    assert result.company.matched_paths == (path,)
    assert (path, company.strip()) in result.company.registered


def test_contact_normalization_and_original_evidence():
    mention = make_mention(email="synthetic1@PEOPLE.EXAMPLE", phone="(000) 0000 0001")
    result = compare_person(mention, make_person())
    assert result.email.status == result.phone.status == "MATCH"
    assert result.phone.observed == "(000) 0000 0001"
    assert result.phone.registered == (("phones[0]", "000-0000-0001"),)


@pytest.mark.parametrize("value", [None, "", "  "])
def test_missing_is_never_a_match(value):
    result = compare_person(make_mention(name=value, company=value, email=value, phone=value), make_person())
    for field in (result.name, result.company, result.email, result.phone):
        assert field.status == "UNKNOWN"
        assert field.matched_paths == ()


def test_missing_or_unusable_registry_is_unknown():
    person = make_person(name=" ", nameVariants=[], company=None, companyHistory=[], emails=["bad"], phones=["***"])
    result = compare_person(make_mention(company="가상회사_001", email="synthetic1@people.example", phone="000-0000-0001"), person)
    assert all(field.status == "UNKNOWN" for field in (result.name, result.company, result.email, result.phone))


def test_different_is_not_identity_decision_and_no_fuzzy_prefix_match():
    result = compare_person(make_mention(name="가상인물_0002", company="가상회사_003"), make_person())
    assert result.name.status == result.company.status == "DIFFERENT"
    assert result.warnings == ()
    assert "decision" not in asdict(result)
    assert "score" not in asdict(result)


def test_contact_match_with_other_name_is_warning():
    result = compare_person(make_mention(name="가상인물_0002", email="synthetic1@people.example"), make_person())
    assert result.warnings == ("CONTACT_MATCH_NAME_DIFFERS",)


def test_homonyms_and_shared_contacts_do_not_get_resolved():
    people = [make_person(), make_person(2, name="가상인물_0001", emails=["synthetic1@people.example"], phones=["000-0000-0001"])]
    results = compare_people(make_mention(email="synthetic1@people.example", phone="00000000001"), people)
    assert all(r.name.status == "MATCH" for r in results)
    assert all(r.warnings == ("SHARED_EMAIL", "SHARED_PHONE") for r in results)


def test_email_and_phone_point_to_different_people():
    people = [make_person(), make_person(2), make_person(3)]
    results = compare_people(make_mention(name=None, email="synthetic1@people.example", phone="000-0000-0002"), people)
    assert results[0].warnings == results[1].warnings == ("CONTACT_TARGETS_DISAGREE",)
    assert results[2].warnings == ()


def test_shared_contact_with_overlapping_target_remains_ambiguous():
    people = [make_person(), make_person(2, emails=["synthetic1@people.example"])]
    results = compare_people(make_mention(name=None, email="synthetic1@people.example", phone="000-0000-0001"), people)
    assert all(r.warnings == ("SHARED_EMAIL",) for r in results)


def test_empty_registry_and_duplicate_ids():
    assert compare_people(make_mention(), []) == []
    with pytest.raises(ValueError, match="personId"):
        compare_people(make_mention(), [make_person(), make_person()])


def test_inputs_preserved_and_metadata_not_compared():
    mention = make_mention(email="synthetic1@people.example")
    people = [make_person(2), make_person()]
    before = deepcopy((mention, people))
    results = compare_people(mention, people)
    assert (mention, people) == before
    assert [r.person_id for r in results] == ["p_002", "p_001"]
    changed = dict(mention, channel="PHONE", sourceRef="src_999")
    assert compare_people(changed, people) == results
    # 실험 메타데이터가 실수로 들어와도 정답·채널을 비교 특성으로 읽지 않는다.
    changed.update(label=0, personIdA="p_999", evidence="가짜 원문", caseType="same_name")
    assert compare_people(changed, people) == results


def test_generated_cases_exercise_expected_clues():
    persons = {p["personId"]: p for p in json.loads((ROOT / "ml/identity/data/persons.json").read_text())}
    seen = set()
    for filename in ("pairs_train.json", "pairs_eval.json"):
        for pair in json.loads((ROOT / "ml/identity/data" / filename).read_text()):
            # label은 검사 정답에만 사용하며 비교 함수에는 전달하지 않는다.
            result = compare_person(pair["mentionB"], persons[pair["personIdA"]])
            case = pair["caseType"]
            seen.add(case)
            if pair["label"] == 1:
                if case in {"exact", "honorific", "english", "company_change", "missing_company"}:
                    assert result.name.status == "MATCH"
                if case == "company_change":
                    assert any(path.startswith("companyHistory[") for path in result.company.matched_paths)
                if case == "missing_company":
                    assert result.company.status == "UNKNOWN"
            elif case == "same_name":
                assert result.name.status == "MATCH"
            elif case == "same_company":
                assert result.company.status == "MATCH"
                assert result.name.status == "DIFFERENT"
    assert {"same_name", "same_company", "company_change", "english"} <= seen
