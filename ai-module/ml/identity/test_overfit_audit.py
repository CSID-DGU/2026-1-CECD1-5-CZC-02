"""진단 기준과 독립 자료의 불변 조건 검사. 모델 학습은 별도 CLI에서 실행한다."""
import json
from pathlib import Path

from jsonschema import Draft202012Validator

from ml.identity.overfit_audit import AUDIT_PROTOCOL, diagnose
from ml.identity.stress_data import make_stress


def test_independent_stress_is_reproducible_and_contract_valid():
    data = make_stress()
    assert data == make_stress()
    contracts = Path(__file__).resolve().parents[3] / "contracts"
    person = Draft202012Validator(json.loads((contracts / "person.schema.json").read_text(encoding="utf-8")))
    mention = Draft202012Validator(json.loads((contracts / "identity_resolution.schema.json").read_text(encoding="utf-8"))["properties"]["mention"])
    ids = {p["personId"] for p in data["persons"]}
    assert len(ids) == 80
    for p in data["persons"]:
        person.validate(p)
        assert p["isSynthetic"] and p["name"].startswith("가상")
        assert all(email.endswith("@audit.example") for email in p["emails"])
    for q in data["queries"]:
        mention.validate(q["mention"])
        assert set(q["expected"]["targetPersonIds"]) <= ids
    assert {q["expected"]["kind"] for q in data["queries"]} == {"KNOWN", "AMBIGUOUS", "CONFLICT", "NEW", "INSUFFICIENT"}


def test_roster_expansion_keeps_questions_answers_and_base_people_fixed():
    base, expanded = make_stress(), make_stress(roster_size=800)
    assert base["queries"] == expanded["queries"]
    assert base["persons"] == expanded["persons"][:80]
    assert len({p["personId"] for p in expanded["persons"]}) == 800


def test_heavy_noise_removes_clues_and_can_widen_ambiguity():
    clean, heavy = make_stress(), make_stress(heavy=True)
    assert clean["persons"] == heavy["persons"]
    for first, second in zip(clean["queries"], heavy["queries"]):
        if first["caseType"] != "noisy_name":
            assert first == second
        else:
            assert first["mention"]["name"] != second["mention"]["name"]
            assert second["mention"]["company"] is None
            assert second["expected"]["kind"] == "AMBIGUOUS"
            assert len(second["expected"]["targetPersonIds"]) >= 2


def test_diagnosis_distinguishes_training_gap_from_distribution_shift():
    policy = json.loads(AUDIT_PROTOCOL.read_text(encoding="utf-8"))
    perfect = {"top3GuaranteedRecall": 1.0, "ambiguityCoverage": 1.0, "newAccuracy": 1.0, "meanFalseCandidates": 0.1}
    low = dict(perfect, top3GuaranteedRecall=0.7)
    gap = diagnose(perfect, low, low, low, low, policy)
    assert "SHARP_SAME_GENERATOR_GENERALIZATION_GAP" in gap["flags"]
    shift = diagnose(perfect, perfect, perfect, low, perfect, policy)
    assert "SHARP_SHIFT_SENSITIVITY_NOT_PROOF_OF_OVERFIT" in shift["flags"]
    assert "SHARP_SAME_GENERATOR_GENERALIZATION_GAP" not in shift["flags"]
    assert shift["response"] == "baseline_fallback_is_better_here"
    both_bad = diagnose(perfect, perfect, perfect, low, dict(low, top3GuaranteedRecall=0.5), policy)
    assert both_bad["response"].startswith("baseline_is_not_a_recall_fix")


def test_new_person_failure_is_flagged_even_without_recall_drop():
    policy = json.loads(AUDIT_PROTOCOL.read_text(encoding="utf-8"))
    original = {"top3GuaranteedRecall": 1.0, "ambiguityCoverage": 1.0, "newAccuracy": 1.0, "meanFalseCandidates": 0.1}
    current = dict(original, newAccuracy=0.1, meanFalseCandidates=2.0)
    result = diagnose(original, original, original, current, original, policy)
    assert "NEW_PERSON_FALSE_CANDIDATE_SURGE" in result["flags"]
    assert "FALSE_CANDIDATE_LOAD_SURGE" in result["flags"]


def test_ambiguity_failure_cannot_hide_behind_perfect_known_recall():
    policy = json.loads(AUDIT_PROTOCOL.read_text(encoding="utf-8"))
    original = {"top3GuaranteedRecall": 1.0, "ambiguityCoverage": 1.0, "newAccuracy": 1.0, "meanFalseCandidates": 0.1}
    current = dict(original, ambiguityCoverage=0.75)
    result = diagnose(original, original, original, current, original, policy)
    assert "SHARP_AMBIGUITY_COVERAGE_DROP" in result["flags"]
    assert result["response"] == "baseline_fallback_is_better_here"
