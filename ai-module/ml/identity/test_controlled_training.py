"""고정 규정·합성 정답·전체 대장 채점 검사. sklearn 학습과 네트워크는 기본 테스트에서 제외한다."""
import json
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator

from app.identity.engine import resolve_identities
from app.identity.features import compare_people
from ml.identity.controlled_data import digest, make_corpus, make_partition
from ml.identity.controlled_experiment import (
    PROTOCOL, cluster_interval, evaluate, gate_result, outcome, prepare, row_metrics, summarize, training_rows,
)


def protocol():
    return json.loads(PROTOCOL.read_text(encoding="utf-8"))


def test_corpus_is_reproducible_with_disjoint_people_and_contract_safe_fake_data():
    config = protocol()
    config["partitionPeople"] = {name: 8 for name in config["partitionPeople"]}
    corpus = make_corpus(config)
    assert digest(corpus) == digest(make_corpus(config))
    contracts = Path(__file__).resolve().parents[3] / "contracts"
    person_validator = Draft202012Validator(json.loads((contracts / "person.schema.json").read_text(encoding="utf-8")))
    mention_validator = Draft202012Validator(json.loads((contracts / "identity_resolution.schema.json").read_text(encoding="utf-8"))["properties"]["mention"])
    for data in corpus.values():
        assert len(data["persons"]) == 8
        ids = {p["personId"] for p in data["persons"]}
        for person in data["persons"]:
            person_validator.validate(person)
            assert person["isSynthetic"] and person["name"].startswith("가상인물_")
            assert all(e.endswith("@synthetic.example") for e in person["emails"])
        observations = set()
        for query in data["queries"]:
            mention_validator.validate(query["mention"])
            assert set(query["expected"]["targetPersonIds"]) <= ids
            key = tuple(query["mention"].get(k) for k in ("name", "company", "email", "phone"))
            assert key not in observations
            observations.add(key)
        assert {q["expected"]["kind"] for q in data["queries"]} >= {"KNOWN", "AMBIGUOUS", "CONFLICT", "NEW", "INSUFFICIENT"}


def test_generator_does_not_derive_gold_from_rule_predictions():
    import ast
    tree = ast.parse(Path(__file__).with_name("controlled_data.py").read_text(encoding="utf-8"))
    assert all(not (isinstance(node, ast.ImportFrom) and node.module in {
        "app.identity.engine", "app.identity.ranking", "ml.identity.learned_comparison"}) for node in ast.walk(tree))


def test_baseline_matches_actual_engine_decisions_and_candidate_order():
    data = make_partition("probe", 817, 8)
    # 여러 유형의 관측을 실제 운영 엔진과 대조한다. 실험 전용 baseline을 유리하게 바꾸지 않는다.
    samples = [q for i, q in enumerate(data["queries"]) if i % 9 == 0]
    actual = resolve_identities([q["mention"] for q in samples], data["persons"])
    for query, expected in zip(samples, actual):
        row = {"query": query, "comparisons": compare_people(query["mention"], data["persons"])}
        answer = outcome(row)
        assert answer["decision"] == expected["decision"]
        assert answer["personId"] == expected["personId"]
        assert [p for p, _ in answer["ranked"][:3]] == [c["personId"] for c in expected["candidates"]]


def test_models_cannot_confirm_by_score_or_drop_exact_name_contact_candidates():
    data = make_partition("probe", 817, 8)
    query = next(q for q in data["queries"] if q["caseType"] == "missing_company" and q["expected"]["kind"] == "AMBIGUOUS")
    row = {"query": query, "comparisons": compare_people(query["mention"], data["persons"])}
    answer = outcome(row, [0.0] * 8, threshold=0.9)
    assert answer["decision"] == "CANDIDATES"
    assert set(query["expected"]["targetPersonIds"]) <= {p for p, _ in answer["ranked"][:3]}
    query = next(q for q in data["queries"] if q["caseType"] == "contact_conflict")
    row = {"query": query, "comparisons": compare_people(query["mention"], data["persons"])}
    assert outcome(row, [1.0] * 8)["decision"] == "CANDIDATES"
    with pytest.raises(ValueError):
        outcome(row, [float("nan")] * 8)


def test_ambiguous_conflict_insufficient_never_get_binary_labels_and_query_weights_are_equal():
    config = protocol()
    data = make_partition("probe", 817, 8)
    prepared = prepare(data)
    x, y, weights = training_rows(prepared, config, sampled=True)
    expected_queries = sum(q["expected"]["kind"] in {"KNOWN", "NEW"} for q in data["queries"])
    assert len(x) == len(y) == len(weights)
    assert sum(weights) == pytest.approx(expected_queries)
    assert set(y) == {0, 1}
    changed = [dict(row, query=dict(row["query"], queryId="secret", caseType="gold_secret")) for row in prepared]
    assert training_rows(prepared, config, sampled=True) == training_rows(changed, config, sampled=True)


def test_metrics_count_wrong_confirmed_and_ties_without_credit_for_id_order():
    query = {"cluster": "group", "caseType": "probe", "expected": {"kind": "KNOWN", "targetPersonIds": ["p_1"]}}
    answer = {"decision": "CANDIDATES", "personId": None, "ranked": [(f"p_{i}", (0.5,)) for i in range(1, 5)]}
    row = row_metrics(query, answer)
    assert row["top3"] == 1 and row["top3Guaranteed"] == 0
    wrong = row_metrics(query, {"decision": "CONFIRMED", "personId": "p_2", "ranked": [("p_2", (1,))]})
    assert summarize([wrong])["confirmedPrecision"] == 0


def test_gate_requires_prespecified_gain_and_paired_cluster_uncertainty():
    config = protocol()
    baseline = {"confirmedPrecision": 1.0, "top3GuaranteedRecall": 0.9, "ambiguityCoverage": 1.0, "newAccuracy": 1.0,
                "insufficientSafety": 1.0, "meanFalseCandidates": 0.5}
    improved = dict(baseline, top3GuaranteedRecall=0.95)
    interval = {"top3Gain": {"lower": 0.01}, "falseCandidatesReduction": {"lower": -0.01}}
    assert gate_result(improved, baseline, interval, 20, config)["passed"]
    interval["top3Gain"]["lower"] = -0.01
    assert not gate_result(improved, baseline, interval, 20, config)["passed"]
    assert not gate_result(baseline, baseline, interval, 20, config)["passed"]
    assert not gate_result(improved, baseline, interval, 200, config)["latencyPassed"]


def test_bootstrap_is_paired_by_group_and_reproducible():
    config = protocol(); config["bootstrap"]["replicates"] = 20
    query = {"cluster": "a", "caseType": "probe", "expected": {"kind": "KNOWN", "targetPersonIds": ["p_1"]}}
    empty = row_metrics(query, {"decision": "NEW", "personId": None, "ranked": []})
    found = row_metrics(query, {"decision": "CANDIDATES", "personId": None, "ranked": [("p_1", (0.9,))]})
    result = cluster_interval([empty], [found], config)
    assert result["top3Gain"] == {"lower": 1.0, "upper": 1.0}
    assert result == cluster_interval([empty], [found], config)
    with pytest.raises(ValueError):
        cluster_interval([empty], [dict(found, cluster="b")], config)
