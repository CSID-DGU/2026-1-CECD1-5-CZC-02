"""이력서 가짜 개발셋 검사: 정답 형식, 표 양식은 규칙만으로 정답과 같아야 한다 (네트워크 없음)."""
from datetime import date

import pytest

from app.resumes.engine import extract_resume, schema_errors
from eval.resumes.run import dev_inputs
from eval.resumes.score import predicted, score_case, summarize

CASES = dev_inputs()
GOLD_KEYS = {"format", "name", "birthYear", "grade", "projectCount", "skills", "industries", "totalCareerMonths",
             "availableFrom"}


def test_dev_gold_has_all_keys():
    assert len(CASES) >= 8
    for case_id, _, gold in CASES:
        assert set(gold) == GOLD_KEYS, case_id


@pytest.mark.parametrize("case_id, kwargs, gold", [case for case in CASES if case[2]["format"] == "TABLE_CARD"])
def test_table_cases_match_gold_with_rules_only(case_id, kwargs, gold):
    profile = extract_resume(reference_date=date(2026, 11, 24), mode="rule", **kwargs).profile
    assert schema_errors(profile) == []
    pred = predicted(profile)
    assert {key: pred[key] for key in GOLD_KEYS - {"skills", "industries"}} == \
           {key: gold[key] for key in GOLD_KEYS - {"skills", "industries"}}
    assert set(pred["skills"]) == set(gold["skills"])
    assert set(pred["industries"]) == set(gold["industries"])


def test_score_summary_counts():
    rows = []
    for case_id, kwargs, gold in CASES:
        profile = extract_resume(reference_date=date(2026, 11, 24), mode="rule", **kwargs).profile
        rows.append(score_case(profile, gold, ""))
    summary = summarize(rows, [1] * len(rows))
    assert summary["graded"] == len(CASES)
    assert summary["accuracy"]["format"] == 1.0
