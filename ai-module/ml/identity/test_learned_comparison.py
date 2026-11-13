"""ML-1 분할·입력 누수·채점 검사. sklearn/모델/네트워크는 기본 CI에서 쓰지 않는다."""
import json
from pathlib import Path

import pytest

from app.identity.features import compare_person
from ml.identity.learned_comparison import (
    FEATURES, DEFAULT_DATA, examples, features, metrics, partition_pairs, select_threshold, split_training_people,
)


def test_features_exclude_identifiers_gold_labels_and_raw_text():
    person = {"personId": "p_1", "name": "가상인물_0001", "company": "가상회사_001", "isSynthetic": True}
    mention = {"name": "가상인물_0002", "company": "가상회사_001", "email": None}
    first = features(compare_person(mention, person))
    second = features(compare_person(dict(mention, sourceRef="gold_secret", evidence="label=1"), dict(person, personId="p_unrelated")))
    assert first == second
    assert len(first) == len(FEATURES)
    assert all(isinstance(v, (int, float)) for v in first)
    assert first[FEATURES.index("email_unknown")] == 1
    assert first[FEATURES.index("email_exact")] == 0


def test_real_training_split_keeps_homonyms_and_aliases_together_and_eval_unseen():
    persons = json.loads((DEFAULT_DATA / "persons.json").read_text(encoding="utf-8"))
    split = json.loads((DEFAULT_DATA / "split.json").read_text(encoding="utf-8"))
    fit, tune = split_training_people(persons, split["trainPersonIds"])
    assert fit.isdisjoint(tune)
    assert fit | tune == set(split["trainPersonIds"])
    assert (fit | tune).isdisjoint(split["evalPersonIds"])
    assert (fit, tune) == split_training_people(persons, split["trainPersonIds"])
    by_name = {}
    for p in persons:
        if p["personId"] not in fit | tune:
            continue
        for name in [p["name"], *p.get("nameVariants", [])]:
            side = "fit" if p["personId"] in fit else "tune"
            assert by_name.setdefault(name, side) == side


def test_transitive_alias_groups_stay_on_one_side():
    people = [
        {"personId": "p_1", "name": "가상인물_A", "nameVariants": ["alias AB"]},
        {"personId": "p_2", "name": "alias AB", "nameVariants": ["alias BC"]},
        {"personId": "p_3", "name": "alias BC"},
        {"personId": "p_4", "name": "가상인물_D"},
        {"personId": "p_5", "name": "가상인물_E"},
    ]
    fit, tune = split_training_people(people, [p["personId"] for p in people])
    assert {"p_1", "p_2", "p_3"} <= fit or {"p_1", "p_2", "p_3"} <= tune


def test_ambiguous_and_cross_partition_examples_never_become_binary_training_rows():
    base = {"personIdA": "p_1", "personIdB": "p_1", "label": 1}
    fit, tune, counts = partition_pairs([base, dict(base, label=None), dict(base, personIdB="p_2", label=0),
                                        dict(base, personIdA="p_2", personIdB="p_2")], {"p_1"}, {"p_2"})
    assert len(fit) == len(tune) == 1
    assert counts == {"excludedAmbiguous": 1, "excludedCrossPartitionPairs": 1}


def test_rule_and_learned_pair_inputs_have_same_candidate_and_no_label_features():
    person = {"personId": "p_1", "name": "가상인물_0001", "company": "가상회사_001", "isSynthetic": True}
    pair = {"personIdA": "p_1", "personIdB": "p_1", "label": 1,
            "mentionB": {"name": "가상인물_0001", "company": "가상회사_001", "channel": "MEETING"}}
    x, y, rule = examples([pair, dict(pair, label=0, caseType="unrelated", evidence="secret")], [person])
    assert x[0] == x[1]
    assert y == [1, 0]
    assert rule == [1.0, 1.0]
    with pytest.raises(ValueError, match="애매"):
        examples([dict(pair, label=None)], [person])
    with pytest.raises(ValueError, match="분할 밖"):
        examples([dict(pair, personIdA="p_9")], [person])


def test_metrics_handle_no_predictions_and_auc_ties_honestly():
    result = metrics([1, 0], [0.2, 0.2])
    assert result["precision"] is None
    assert result["recall"] == 0
    assert result["rocAuc"] == 0.5
    assert metrics([1, 0], [1, 0])["brier"] == 0
    with pytest.raises(ValueError):
        metrics([1], [float("nan")])


def test_threshold_selection_uses_only_supplied_tune_labels_and_prefers_precision():
    selected, trials = select_threshold([1, 0, 1], [0.95, 0.7, 0.6])
    assert selected["threshold"] >= 0.8
    assert selected["metrics"]["precision"] == 1
    assert len(trials) == 6


def test_experiment_has_no_production_or_holdout_dependency():
    # CLI module imports stdlib and existing rule/data helpers only. Optional sklearn stays inside run().
    import ast
    path = Path(__file__).with_name("learned_comparison.py")
    tree = ast.parse(path.read_text(encoding="utf-8"))
    assert not any(isinstance(node, (ast.Import, ast.ImportFrom)) and
                   any(name.startswith("sklearn") for name in
                       ([node.module or ""] if isinstance(node, ast.ImportFrom) else [alias.name for alias in node.names]))
                   for node in tree.body)
