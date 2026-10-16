"""가짜 데이터의 계약·정답·변형·분할·재현성을 네트워크 없이 검사한다."""
import json
import random
from collections import Counter, defaultdict
from copy import deepcopy
from datetime import date

import pytest
from jsonschema import Draft202012Validator, ValidationError

from ml.identity.generate_dataset import (
    COMPANIES, CONTRACTS_DIR, NEGATIVE_CASES, POSITIVE_CASES,
    build_dataset, source_text, validate_dataset, write_dataset,
)


@pytest.fixture(scope="module")
def dataset():
    return build_dataset()


def test_person_contract_and_explicitly_synthetic_values(dataset):
    schema = json.loads((CONTRACTS_DIR / "person.schema.json").read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema)
    assert len(dataset["persons"]) == 300
    assert len({p["personId"] for p in dataset["persons"]}) == 300
    for person in dataset["persons"]:
        validator.validate(person)
        assert person["isSynthetic"] is True
        assert person["name"].startswith("가상인물_")
        assert person["company"] in COMPANIES
        assert all(email.endswith("@people.example") for email in person["emails"])
        assert all(phone.startswith("000-0000-") for phone in person["phones"])
        assert all(alias.startswith((person["name"], "Synthetic Person ")) for alias in person["nameVariants"])
        for history in person["companyHistory"]:
            assert history["company"] in COMPANIES and history["company"] != person["company"]
            date.fromisoformat(history["until"])


def test_homonyms_are_different_people_at_different_companies(dataset):
    groups = defaultdict(list)
    for person in dataset["persons"]:
        groups[person["name"]].append(person)
    homonyms = [group for group in groups.values() if len(group) > 1]
    assert len(homonyms) == 10
    assert all(len(group) == 2 and len({person["company"] for person in group}) == 2 for group in homonyms)


def test_person_split_and_pair_labels_have_no_leakage(dataset):
    train = set(dataset["split"]["trainPersonIds"])
    evaluation = set(dataset["split"]["evalPersonIds"])
    assert len(train) == 240 and len(evaluation) == 60
    assert train.isdisjoint(evaluation)
    assert train | evaluation == {p["personId"] for p in dataset["persons"]}
    by_id = {p["personId"]: p for p in dataset["persons"]}
    assert {by_id[pid]["name"] for pid in train}.isdisjoint({by_id[pid]["name"] for pid in evaluation})
    all_pair_ids, all_source_ids = set(), set()
    for key, allowed, count in (("trainPairs", train, 2400), ("evalPairs", evaluation, 600)):
        pairs = dataset[key]
        assert len(pairs) == count
        assert Counter(p["label"] for p in pairs) == {0: count // 2, 1: count // 2}
        assert {p["caseType"] for p in pairs} == set(POSITIVE_CASES + NEGATIVE_CASES)
        for pair in pairs:
            assert {pair["personIdA"], pair["personIdB"]} <= allowed
            assert pair["label"] == int(pair["personIdA"] == pair["personIdB"])
            assert pair["pairId"] not in all_pair_ids
            all_pair_ids.add(pair["pairId"])
            for side in ("mentionA", "mentionB"):
                assert pair[side]["sourceRef"] not in all_source_ids
                all_source_ids.add(pair[side]["sourceRef"])


def test_mentions_follow_identity_contract_and_have_synthetic_evidence(dataset):
    schema = json.loads((CONTRACTS_DIR / "identity_resolution.schema.json").read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema["properties"]["mention"])
    for pair in dataset["trainPairs"] + dataset["evalPairs"]:
        for side in ("mentionA", "mentionB"):
            mention = pair[side]
            validator.validate(mention)
            assert pair["evidence"][side] == source_text(mention)
            for key in ("name", "company", "title", "email", "phone", "channel"):
                if mention[key] is not None:
                    assert mention[key] in pair["evidence"][side]


def test_variations_and_hard_negative_examples_are_real(dataset):
    by_id = {p["personId"]: p for p in dataset["persons"]}
    for pair in dataset["trainPairs"] + dataset["evalPairs"]:
        left, right = by_id[pair["personIdA"]], by_id[pair["personIdB"]]
        a, b, case = pair["mentionA"], pair["mentionB"], pair["caseType"]
        if pair["label"] == 1:
            if case == "honorific":
                assert b["name"] == right["nameVariants"][0] and b["name"].endswith("님")
            elif case == "english":
                assert b["name"] == right["nameVariants"][1] and b["name"].isascii()
            elif case == "typo":
                assert b["name"] not in [right["name"], *right["nameVariants"]]
                assert len(b["name"]) == len(right["name"])
                assert sum(x != y for x, y in zip(b["name"], right["name"])) == 1
            elif case == "company_change":
                assert a["company"] == right["company"] != b["company"]
                assert any(b["company"] == h["company"] and b["title"] == h["title"] for h in right["companyHistory"])
            elif case == "missing_company":
                assert b["company"] is None
            if case != "exact":
                assert b["email"] is None and b["phone"] is None
        elif case == "same_name":
            assert a["name"] == b["name"] and left["company"] != right["company"]
        elif case == "same_company":
            assert a["company"] == b["company"] and left["name"] != right["name"]


@pytest.mark.parametrize("seed", [0, 42, 20261015])
def test_different_seeds_keep_contracts_and_split(seed):
    validate_dataset(build_dataset(seed))


def test_repeatable_without_changing_global_random_state(dataset):
    state = random.getstate()
    assert build_dataset() == dataset == build_dataset()
    assert random.getstate() == state
    assert build_dataset(43) != dataset


def test_output_roundtrip_and_repeated_generation_match(dataset, tmp_path):
    write_dataset(dataset, tmp_path)
    files = {"persons.json": "persons", "pairs_train.json": "trainPairs", "pairs_eval.json": "evalPairs", "split.json": "split"}
    before = {name: (tmp_path / name).read_bytes() for name in files}
    for filename, key in files.items():
        assert json.loads(before[filename]) == dataset[key]
    write_dataset(build_dataset(), tmp_path)
    assert before == {name: (tmp_path / name).read_bytes() for name in files}


def test_validation_rejects_leakage_and_wrong_labels_before_writing(dataset, tmp_path):
    invalid = deepcopy(dataset)
    invalid["evalPairs"][0]["personIdA"] = invalid["split"]["trainPersonIds"][0]
    with pytest.raises(ValueError, match="인물 소속"):
        write_dataset(invalid, tmp_path)
    assert not list(tmp_path.iterdir())
    invalid = deepcopy(dataset)
    invalid["trainPairs"][0]["label"] = 1 - invalid["trainPairs"][0]["label"]
    with pytest.raises(ValueError):
        validate_dataset(invalid)


def test_validation_rejects_non_synthetic_people_and_contract_changes(dataset):
    invalid = deepcopy(dataset)
    invalid["persons"][0]["isSynthetic"] = False
    with pytest.raises(ValueError, match="가짜"):
        validate_dataset(invalid)
    invalid = deepcopy(dataset)
    invalid["persons"][0]["person_id"] = "P001"
    with pytest.raises(ValidationError):
        validate_dataset(invalid)


def test_saved_data_are_current_and_valid():
    from pathlib import Path

    root = Path(__file__).parent / "data"
    saved = {key: json.loads((root / filename).read_text(encoding="utf-8")) for filename, key in (
        ("persons.json", "persons"), ("pairs_train.json", "trainPairs"),
        ("pairs_eval.json", "evalPairs"), ("split.json", "split"),
    )}
    validate_dataset(saved)
    assert saved == build_dataset()
