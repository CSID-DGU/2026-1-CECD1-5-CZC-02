"""운영 API 후보 보완·규칙 보호·JSON 추론과 고정 실험의 일치 검사."""
import json
from copy import deepcopy
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.identity import logistic
from app.identity.features import compare_people
from app.identity.hybrid import resolve_identities
from app.identity.engine import resolve_identities as rule_resolve, validators
from ml.identity.learned_comparison import features as training_features
from app.main import app

ROOT = Path(__file__).resolve().parents[2] / "ml/identity"


@pytest.fixture(autouse=True)
def hybrid_mode(monkeypatch):
    monkeypatch.setenv("IDENTITY_CANDIDATE_MODE", "hybrid")


def fixture():
    data = json.loads((ROOT/"results/fresh_validation_v1/dataset.json").read_text(encoding="utf-8"))
    return data


def test_runtime_features_and_responses_match_frozen_experiment():
    data = fixture()
    expected = {p["queryId"]: p for p in json.loads((ROOT/"results/fresh_validation_v1/predictions.json").read_text(encoding="utf-8")) if p["model"] == "logistic"}
    # 전체 새 검증의 14개 유형을 포함한 앞 4그룹. 실제 엔진 응답과 저장된 실험 결과 대조.
    for q in data["queries"][:56]:
        before = deepcopy(q["mention"])
        result, = resolve_identities([q["mention"]], data["persons"])
        validators()[2].validate(result)
        assert result["mention"] == before == q["mention"]
        frozen = expected[q["queryId"]]
        assert result["decision"] == frozen["decision"]
        assert result["personId"] == frozen["personId"]
        actual_ids = [c["personId"] for c in result["candidates"]]
        if actual_ids != frozen["candidateIds"]:
            # sklearn BLAS와 순수 Python의 1e-16 오차는 우열로 사용하지 않는다.
            comparisons = {c.person_id: c for c in compare_people(q["mention"], data["persons"])}
            model = logistic.load_model()
            def priority(pid):
                c = comparisons[pid]
                return (int("MATCH" in (c.email.status, c.phone.status)), int(c.name.status == "MATCH"), round(model.score(c), 12))
            assert [priority(pid) for pid in actual_ids] == [priority(pid) for pid in frozen["candidateIds"]]
        if q["expected"]["kind"] == "KNOWN":
            assert set(q["expected"]["targetPersonIds"]) <= set(actual_ids)
        for comparison in compare_people(q["mention"], data["persons"]):
            assert logistic.features(comparison) == training_features(comparison)


def test_actual_api_recovers_typo_without_changing_confirmation_policy():
    data = fixture()
    q = next(q for q in data["queries"] if q["caseType"] == "compound_typo_missing_company")
    assert rule_resolve([q["mention"]], data["persons"])[0]["decision"] == "NEW"
    response = TestClient(app).post("/identity/resolve", json={"mentions": [q["mention"]], "persons": data["persons"]})
    assert response.status_code == 200
    r, = response.json()
    assert r["decision"] == "CANDIDATES" and r["personId"] is None and r["decidedBy"] == "MODEL"
    assert q["expected"]["targetPersonIds"][0] in [c["personId"] for c in r["candidates"]]
    assert all(0 <= c["score"] <= 1 and c["reasons"] for c in r["candidates"])
    contact = next(q for q in data["queries"] if q["caseType"] == "domain_case_honorific")
    r, = resolve_identities([contact["mention"]], data["persons"])
    assert r["decision"] == "CONFIRMED" and r["decidedBy"] == "RULE"
    assert all(c["score"] is None for c in r["candidates"])


def test_maximal_model_score_never_confirms_or_discards_contact_candidates(monkeypatch):
    class AlwaysHigh:
        threshold, version = 0.02, "test-high"
        def score(self, comparison):
            return 1.0
    monkeypatch.setattr(logistic, "load_model", lambda: AlwaysHigh())
    data = fixture()
    for case in ("conflicting_name_contact", "shared_email_missing_name", "four_homonyms_overflow", "homonym_current_company"):
        q = next(q for q in data["queries"] if q["caseType"] == case)
        r, = resolve_identities([q["mention"]], data["persons"])
        assert r["decision"] == "CANDIDATES" and r["personId"] is None
        assert len(r["candidates"]) == 3
        if case in ("conflicting_name_contact", "shared_email_missing_name"):
            assert set(q["expected"]["targetPersonIds"]) <= {c["personId"] for c in r["candidates"]}
        assert any("직접 검색" in reason for c in r["candidates"] for reason in c["reasons"])


@pytest.mark.parametrize("failure", [FileNotFoundError, ValueError, KeyError])
def test_missing_or_invalid_model_falls_back_to_rule(monkeypatch, failure):
    def fail():
        raise failure()
    monkeypatch.setattr(logistic, "load_model", fail)
    data = fixture()
    q = data["queries"][7]
    r, = resolve_identities([q["mention"]], data["persons"])
    assert r["decidedBy"] == "RULE" and all(c["score"] is None for c in r["candidates"])


def test_inference_error_rule_switch_empty_roster_and_missing_clues(monkeypatch):
    class Broken:
        threshold, version = 0.02, "test-broken"
        def score(self, c):
            raise ValueError("test")
    monkeypatch.setattr(logistic, "load_model", lambda: Broken())
    data = fixture()
    q = data["queries"][7]
    assert resolve_identities([q["mention"]], data["persons"])[0]["decidedBy"] == "RULE"
    monkeypatch.setenv("IDENTITY_CANDIDATE_MODE", "rule")
    assert resolve_identities([q["mention"]], data["persons"])[0]["decidedBy"] == "RULE"
    monkeypatch.setenv("IDENTITY_CANDIDATE_MODE", "hybrid")
    for mention, people in (({"name": None, "channel": "MEETING"}, data["persons"]), (q["mention"], [])):
        r, = resolve_identities([mention], people)
        assert r["decision"] == "NEW" and r["candidates"] == []


@pytest.mark.parametrize("field,value", [("scale", [0]*14), ("coefficients", [float("nan")]*14), ("threshold", 1.5), ("features", [])])
def test_json_artifact_rejects_invalid_dimensions_and_nonfinite_numbers(field, value):
    bundle = json.loads(logistic.ARTIFACT.read_text(encoding="utf-8"))
    bundle["payload"][field] = value
    bundle["sha256"] = logistic.fingerprint(bundle["payload"])
    with pytest.raises(ValueError):
        logistic.LogisticModel(bundle)


def test_json_checksum_corruption_is_rejected():
    bundle = json.loads(logistic.ARTIFACT.read_text(encoding="utf-8"))
    bundle["payload"]["threshold"] = 0.9
    with pytest.raises(ValueError):
        logistic.LogisticModel(bundle)


@pytest.mark.parametrize("dataset", ["original", "fresh"])
def test_exported_runtime_preserves_full_frozen_metrics(dataset):
    from ml.identity.controlled_data import make_corpus
    from ml.identity.controlled_experiment import evaluate, prepare
    if dataset == "fresh":
        data = fixture()
        expected = json.loads((ROOT/"results/fresh_validation_v1/report.json").read_text(encoding="utf-8"))["reports"]["logistic"]["all"]
    else:
        source = json.loads((ROOT/"results/controlled_v1/report.json").read_text(encoding="utf-8"))
        data = make_corpus(source["protocol"])["test"]
        expected = next(r["test"] for r in source["runs"] if r["family"] == "logistic" and r["seed"] == 42)
    prepared = prepare(data)
    model = logistic.load_model()
    scores = [[round(model.score(c), 12) for c in row["comparisons"]] for row in prepared]
    assert evaluate(prepared, scores, model.threshold)[0] == expected
