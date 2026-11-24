"""합성 공개 API 비교의 분할, 지표, 확정 경계와 재현 경로 검사."""
import json
from copy import deepcopy

import pytest

from ml.identity import public_comparison as comparison


def query(kind="KNOWN", targets=None):
    return {"queryId": "q_test", "cluster": "c_test", "caseType": "metric_fixture",
            "expected": {"kind": kind, "targetPersonIds": ["p_a"] if targets is None else targets}}


def result(decision="CANDIDATES", ids=("p_a",)):
    return {"decision": decision, "personId": ids[0] if decision == "CONFIRMED" else None,
            "candidates": [{"personId": value} for value in ids], "decidedBy": "RULE"}


def test_splits_are_reproducible_grouped_and_have_no_identity_clue_overlap():
    data = comparison.make_splits(groups=4)
    assert data == comparison.make_splits(groups=4)
    assert [len(d["persons"]) for d in data.values()] == [12, 12]
    assert [len(d["queries"]) for d in data.values()] == [28, 28]
    assert all(len({q["caseType"] for q in d["queries"]}) == 14 for d in data.values())
    data["test"]["persons"][0]["name"] = data["validation"]["persons"][0]["name"]
    with pytest.raises(ValueError, match="겹칩니다"):
        comparison.validate_splits(data)


def test_partition_cannot_split_a_six_person_ambiguity_cluster():
    with pytest.raises(ValueError):
        comparison.make_splits(groups=3)
    data = comparison.make_splits(groups=2)
    for part in data.values():
        ids = {p["personId"] for p in part["persons"]}
        assert all(set(q["expected"]["targetPersonIds"]) <= ids for q in part["queries"])


def test_extra_candidates_are_not_wrong_confirmations_and_do_not_hide_misses():
    hit = comparison.measure(query(), result(ids=("p_b", "p_a")))
    miss = comparison.measure(query(), result(ids=("p_b",)))
    assert hit["knownHit"] and hit["falseSuggestedIds"] == 1
    assert not miss["knownHit"] and not miss["wrongConfirmation"]
    summary = comparison.summarize([hit, miss])
    assert summary["knownHits"] == 1 and summary["knownTop3Recall"] == 0.5
    assert summary["falseSuggestedIds"] == 2 and summary["totalSuggestedIds"] == 3
    assert summary["wrongConfirmations"] == 0
    assert comparison.measure(query(), result("CONFIRMED", ("p_b",)))["wrongConfirmation"]


def test_ambiguous_confirmation_is_unsafe_even_when_selected_id_is_plausible():
    q = query("AMBIGUOUS", ["p_a", "p_b"])
    confirmed = comparison.measure(q, result("CONFIRMED", ("p_a",)))
    candidate = comparison.measure(q, result(ids=("p_a",)))
    assert confirmed["wrongConfirmation"] and not confirmed["ambiguityHandled"]
    assert candidate["ambiguityHandled"] and not candidate["allTargetsIncluded"]


def test_overflow_cannot_be_reported_as_complete_three_slot_coverage():
    q = query("AMBIGUOUS", ["p_a", "p_b", "p_c", "p_d"])
    summary = comparison.summarize([comparison.measure(q, result(ids=("p_a", "p_b", "p_c")))])
    assert summary["overflowQueries"] == 1
    assert summary["overflowValidTargetCoverage"] == 0.75
    assert summary["ambiguousFeasibleQueries"] == 0
    assert summary["knownTop3Recall"] is None


@pytest.mark.parametrize("kind,targets", [(None, []), ("UNREVIEWED", []), ("KNOWN", []),
                                          ("AMBIGUOUS", ["p_a"]), ("NEW", ["p_a"])])
def test_unknown_or_inconsistent_gold_is_not_a_correct_negative(kind, targets):
    with pytest.raises(ValueError):
        comparison.measure(query(kind, targets), result("NEW", ()))


def test_new_and_insufficient_have_separate_denominators():
    values = [comparison.measure(query(kind, []), result("NEW", ())) for kind in ("NEW", "INSUFFICIENT")]
    summary = comparison.summarize(values)
    assert summary["newCorrect"] == summary["newQueries"] == 1
    assert summary["insufficientSafe"] == summary["insufficientQueries"] == 1
    assert summary["knownTop3Recall"] is None and summary["falseSuggestionRate"] is None


def test_paired_interval_resamples_clusters_and_rejects_unpaired_queries():
    rule = [comparison.measure(query(), result("NEW", ()))]
    mixed = [comparison.measure(query(), result())]
    interval = comparison.paired_interval(rule, mixed, resamples=30)
    assert interval["clusters"] == 1 and interval["delta"] == 1
    assert interval["percentile95CI"] == [1, 1]
    mixed[0]["queryId"] = "q_different"
    with pytest.raises(ValueError):
        comparison.paired_interval(rule, mixed)


def test_confirmation_boundary_requires_rule_unique_contact_and_no_conflict():
    mention = {"name": "가상검사_쀼뇨뿌", "email": "check@fixture.example", "channel": "MEETING"}
    people = [{"personId": "p_a", "name": mention["name"], "emails": [mention["email"]], "isSynthetic": True}]
    response = result("CONFIRMED")
    comparison.check_confirmation(mention, people, response)
    response["decidedBy"] = "MODEL"
    with pytest.raises(ValueError, match="MODEL"):
        comparison.check_confirmation(mention, people, response)
    response["decidedBy"] = "RULE"
    conflict = {**mention, "name": "가상별개_뾰쮸쪼"}
    with pytest.raises(ValueError, match="충돌"):
        comparison.check_confirmation(conflict, people, response)
    shared = [*people, {**people[0], "personId": "p_b"}]
    with pytest.raises(ValueError, match="한 사람"):
        comparison.check_confirmation(mention, shared, response)


def test_cli_path_freezes_before_prediction_restores_env_and_never_overwrites(tmp_path, monkeypatch):
    splits = comparison.make_splits(groups=2)
    before = deepcopy(splits)
    monkeypatch.setattr(comparison, "make_splits", lambda: splits)
    monkeypatch.setenv("IDENTITY_CANDIDATE_MODE", "rule")
    original = comparison.engine.resolve_identities
    output = tmp_path/"comparison"
    def check_freeze(*args):
        assert (output/"frozen_protocol.json").exists()
        assert (output/"test_dataset.json").exists()
        return original(*args)
    monkeypatch.setattr(comparison.engine, "resolve_identities", check_freeze)
    report = comparison.run(output)
    assert splits == before
    assert comparison.os.environ["IDENTITY_CANDIDATE_MODE"] == "rule"
    assert report["responsesSchemaValidated"] == 56
    assert report["realDerivedDataUsed"] is False and report["productionChanged"] is False
    saved = json.loads((output/"report.json").read_text(encoding="utf-8"))
    assert saved == report
    with pytest.raises(FileExistsError):
        comparison.run(output)


def test_hybrid_warning_does_not_silently_become_a_model_comparison(tmp_path, monkeypatch):
    comparison_data = comparison.make_splits(groups=2)
    monkeypatch.setattr(comparison, "make_splits", lambda: comparison_data)
    original = comparison.hybrid.resolve_identities
    def warn(*args):
        comparison.hybrid.logger.warning("가상 폴백 검사")
        return original(*args)
    monkeypatch.setattr(comparison.hybrid, "resolve_identities", warn)
    monkeypatch.setenv("IDENTITY_CANDIDATE_MODE", "rule")
    with pytest.raises(ValueError, match="폴백"):
        comparison.run(tmp_path/"failed")
    assert comparison.os.environ["IDENTITY_CANDIDATE_MODE"] == "rule"
    assert not (tmp_path/"failed/report.json").exists()
