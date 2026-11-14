"""새 검증 생성 규칙과 자동 정답의 안전 조건. sklearn 학습은 CLI에서만 수행한다."""
from copy import deepcopy

import pytest

from ml.identity.fresh_validation import make_fresh, sections, validate_data
from ml.identity.controlled_experiment import prepare, evaluate


def test_fresh_scenarios_are_reproducible_and_schema_valid():
    data = make_fresh(groups=4)
    assert data == make_fresh(groups=4)
    validate_data(data)
    assert len(data["persons"]) == 24
    assert len(data["queries"]) == 56
    assert len({q["queryId"] for q in data["queries"]}) == 56
    assert {q["expected"]["kind"] for q in data["queries"]} == {"KNOWN", "AMBIGUOUS", "CONFLICT", "NEW", "INSUFFICIENT"}


def test_previous_company_and_shared_contact_are_not_forced_binary_gold():
    data = make_fresh(groups=2)
    for q in data["queries"]:
        e = q["expected"]
        if q["caseType"] in {"homonym_previous_company", "shared_email_missing_name"}:
            assert e["kind"] == "AMBIGUOUS" and len(e["targetPersonIds"]) == 2
        if q["caseType"] == "conflicting_name_contact":
            assert e["kind"] == "CONFLICT" and len(e["targetPersonIds"]) == 3
        if q["caseType"] == "four_homonyms_overflow":
            assert e["kind"] == "AMBIGUOUS" and len(e["targetPersonIds"]) == 4


def test_capacity_ceiling_is_reported_separately_without_dropping_hard_cases():
    data = make_fresh(groups=2)
    prepared = prepare(data)
    _, rows = evaluate(prepared)
    report = sections(rows, prepared)
    assert report["all"]["queries"] == 28
    assert report["withinThreeCandidateCapacity"]["queries"] == 26
    assert report["byCase"]["four_homonyms_overflow"]["counts"]["ambiguous"] == 2
    assert report["byCase"]["four_homonyms_overflow"]["ambiguityCoverage"] == 0


def test_validation_rejects_duplicate_observations_and_unknown_gold_ids():
    data = make_fresh(groups=1)
    data["queries"].append(deepcopy(data["queries"][0]))
    with pytest.raises(AssertionError):
        validate_data(data)
    data = make_fresh(groups=1)
    data["queries"][0]["expected"]["targetPersonIds"] = ["p_missing"]
    with pytest.raises(AssertionError):
        validate_data(data)


def test_archived_data_reproduces_original_hash_and_never_overwrites(tmp_path):
    import json
    from ml.identity.reproduce_fresh_data import ROOT, reproduce
    from ml.identity.controlled_data import digest
    output = tmp_path / "dataset.json"
    result = reproduce(output)
    data = json.loads(output.read_text(encoding="utf-8"))
    frozen = json.loads((ROOT/"frozen_protocol.json").read_text(encoding="utf-8"))
    assert digest(data) == frozen["datasetSha256"] == result["datasetSha256"]
    assert result["persons"] == 240 and result["queries"] == 560
    before = output.read_bytes()
    with pytest.raises(FileExistsError):
        reproduce(output)
    assert output.read_bytes() == before
