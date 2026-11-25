"""시연용 가짜 후보 풀이 규칙만으로 읽히고 시나리오대로 갈리는지 (가짜 데이터만, 네트워크 없음)."""
from datetime import date

import pytest

from app.resumes.demo_pool import DEMO_POOL, demo_pool_docx
from app.resumes.engine import extract_resume, schema_errors

REF = date(2026, 12, 3)
FAKE_MARKS = ("가상", "예시", "모의", "샘플", "테스트")


@pytest.fixture(scope="module")
def profiles():
    return {name: extract_resume(content=content, reference_date=REF, mode="rule").profile
            for name, content in demo_pool_docx()}


def test_names_are_obviously_fake():
    for spec in DEMO_POOL:
        assert any(mark in spec["name"] for mark in FAKE_MARKS)


def test_every_profile_matches_contract(profiles):
    for profile in profiles.values():
        assert schema_errors(profile) == []


def skills(profile):
    return {skill["name"] for skill in profile["skills"]}


def test_scenario_split(profiles):
    junho, hyunwoo, taeo, jimin, minsu, seoyeon = (profiles[spec["file"]] for spec in DEMO_POOL)
    for profile in (junho, hyunwoo, taeo, jimin, minsu):
        assert "Java" in skills(profile)
    assert "Java" not in skills(seoyeon) and {"C#", ".NET"} <= skills(seoyeon)
    assert minsu["totalCareer"]["months"] < 60 <= min(p["totalCareer"]["months"] for p in (junho, hyunwoo, taeo, jimin))
    assert hyunwoo["availableFrom"]["date"] == "2027-02-01"
    assert jimin["availableFrom"]["date"] == "2027-04-01"
    assert taeo["availableFrom"]["basis"] == "ONGOING_WITHOUT_END" and taeo["availableFrom"]["date"] is None
    assert junho["availableFrom"]["basis"] == "NO_ONGOING_PROJECT"
    assert "금융" in {item["name"] for item in junho["industries"]}
