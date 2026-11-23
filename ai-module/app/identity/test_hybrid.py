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
from ml.identity.fresh_validation import make_fresh
from ml.identity.controlled_data import digest

ROOT = Path(__file__).resolve().parents[2] / "ml/identity"


@pytest.fixture(autouse=True)
def hybrid_mode(monkeypatch):
    monkeypatch.setenv("IDENTITY_CANDIDATE_MODE", "hybrid")


def fixture():
    frozen = json.loads((ROOT/"results/fresh_validation_v1/frozen_protocol.json").read_text(encoding="utf-8"))
    data = make_fresh(seed=frozen["seed"])
    assert digest(data) == frozen["datasetSha256"]
    return data


def test_runtime_features_and_responses_match_frozen_experiment():
    data = fixture()
    snapshot = json.loads((ROOT/"results/fresh_validation_v1/runtime_predictions.json").read_text(encoding="utf-8"))
    assert digest(data) == snapshot["datasetSha256"]
    expected = {p["queryId"]: p for p in snapshot["predictions"]}
    assert len(expected) == 56 and set(expected) == {q["queryId"] for q in data["queries"][:56]}
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
        expected_count = 2 if case == "shared_email_missing_name" else 3
        assert len(r["candidates"]) == expected_count
        if case in ("conflicting_name_contact", "shared_email_missing_name"):
            assert set(q["expected"]["targetPersonIds"]) <= {c["personId"] for c in r["candidates"]}
        overflow = case in ("four_homonyms_overflow", "homonym_current_company")
        assert sum(reason.startswith("후보가 ") for c in r["candidates"] for reason in c["reasons"]) == int(overflow)


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


@pytest.mark.parametrize("names", [("가상인물_파랑", "가상인물_노랑"), ("가상갑", "가상을")])
@pytest.mark.parametrize("scenario", ["name", "alias", "homonyms", "contact_conflict", "unknown_contact"])
def test_api_does_not_fill_direct_matches_with_other_names(scenario, names):
    # 공통 접두어 때문에 유사도와 모델 점수가 높아지는 가짜 이름이다.
    people = [
        {"personId": "p_exact", "name": names[0], "isSynthetic": True},
        {"personId": "p_other_1", "name": names[1], "isSynthetic": True},
        {"personId": "p_other_2", "name": names[1], "isSynthetic": True},
    ]
    mention = {"name": names[0], "channel": "MEETING"}
    expected = {"p_exact"}
    if scenario == "alias":
        people[0].update(name="가상인물_별칭대상", nameVariants=[mention["name"]])
    elif scenario == "homonyms":
        people[1]["name"] = mention["name"]
        expected.add("p_other_1")
    elif scenario == "contact_conflict":
        people[1]["emails"] = ["contact@fake.example"]
        mention["email"] = "contact@fake.example"
        expected.add("p_other_1")  # 이름이 달라도 연락처 충돌 후보는 사람에게 보여 준다.
    elif scenario == "unknown_contact":
        mention.update(name=None, company="가상회사_파랑", email="unknown@fake.example")
        for person in people:
            person["company"] = mention["company"]
        expected = set()
    response = TestClient(app).post("/identity/resolve", json={"mentions": [mention], "persons": people})
    assert response.status_code == 200
    result, = response.json()
    validators()[2].validate(result)
    assert {c["personId"] for c in result["candidates"]} == expected
    assert result["decision"] == ("CANDIDATES" if expected else "NEW")
    assert result["personId"] is None


def test_inexact_names_remain_available_when_no_direct_match_exists():
    people = [{"personId": "p_typo", "name": "가상인물_파랑", "isSynthetic": True}]
    result, = resolve_identities([{"name": "가상인물_파란", "channel": "MEETING"}], people)
    assert result["decision"] == "CANDIDATES"
    assert [c["personId"] for c in result["candidates"]] == ["p_typo"]


@pytest.mark.parametrize("company", [None, "가상회사_파랑"])
def test_short_names_with_different_family_name_are_not_model_only_candidates(company):
    people = [{"personId": "p_short", "name": "이가상", "company": company, "isSynthetic": True}]
    result, = resolve_identities([{"name": "김가상", "company": company, "channel": "MEETING"}], people)
    assert result["decision"] == "NEW"
    assert result["candidates"] == []


@pytest.mark.parametrize("company", [None, "가상회사_파랑"])
def test_three_letter_typo_with_same_family_name_stays_a_candidate(company):
    people = [{"personId": "p_typo", "name": "김가상", "company": company, "isSynthetic": True}]
    result, = resolve_identities([{"name": "김가샹", "company": company, "channel": "MEETING"}], people)
    assert result["decision"] == "CANDIDATES"
    assert [c["personId"] for c in result["candidates"]] == ["p_typo"]
    assert result["personId"] is None
