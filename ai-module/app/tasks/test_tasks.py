"""네트워크 없이 계약, 날짜, 분류 경계와 고객 측 약속을 검증한다."""

import copy
import json
from pathlib import Path

import pytest
from jsonschema import ValidationError

from .classifier import classify_task
from .dates import resolve_due_date
from .engine import CONTRACTS, _validator, extract_tasks


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


@pytest.fixture
def note():
    return load(CONTRACTS / "examples/meeting_note.example.json")


def test_sample_contract_and_safety(note):
    original = copy.deepcopy(note)
    tasks = extract_tasks(note)
    assert len(tasks) == 3
    assert tasks[0]["dueDate"] == "2026-10-16"
    assert tasks[0]["taskType"] == "CONTRACT_PREP"
    assert tasks[1]["commitmentKind"] == "IMPLICIT"
    assert "이행 여부 확인" in tasks[1]["content"]
    assert tasks[1]["taskType"] == "NONE"
    assert tasks[1]["dueDate"] is None
    assert tasks[2]["dueDate"] is None  # 첫 주를 월요일로 추측하지 않음
    assert tasks[2]["taskType"] == "MEETING_PREP"
    for task in tasks:
        _validator("task").validate(task)
        assert task["status"] == "DRAFT"
        assert task["assignee"] is None
        assert task["counterpartPersonId"] is None
        assert task["dueDateIsDefault"] is False
    assert note == original
    assert tasks == extract_tasks(note)


def test_explicit_assignee_and_single_participant(note):
    note["participants"] = note["participants"][:1]
    assert all(t["assignee"] == "user_demo" and t["counterpartPersonId"] == "p_001"
               for t in extract_tasks(note, assignee="user_demo"))


def test_deduplicate_without_losing_distinct_tasks(note):
    note["fields"]["commitments"] *= 2
    assert len(extract_tasks(note)) == 3


def test_no_tasks_from_requests_or_discussion(note):
    note["fields"]["commitments"] = []
    note["fields"]["nextContact"] = []
    assert extract_tasks(note) == []


def test_invalid_input(note):
    note["inventedField"] = True
    with pytest.raises(ValidationError):
        extract_tasks(note)


def test_light_followups_only():
    note = load(Path(__file__).resolve().parents[2] / "eval/minutes/gold/m05.json")
    note["perPerson"][0]["personId"] = "p_demo"
    tasks = extract_tasks(note)
    assert len(tasks) == 2
    assert tasks[0]["counterpartPersonId"] == "p_demo"
    assert tasks[1]["dueDate"] is None


@pytest.mark.parametrize("text,expected", [
    ("계약서 초안 금요일까지 송부", "CONTRACT_PREP"),
    ("현장 방문 미팅 준비", "VISIT_PREP"),
    ("다음 주 미팅", "NONE"),
    ("계약 만료 대응 면담", "RENEWAL_INTERVIEW"),
    ("후보 프로필 전달", "PROPOSAL_SUBMISSION"),
    ("탈락 후보 통보", "REJECTION_NOTICE"),
    ("지난주 보낸 계약서 회신 없음", "DOC_COLLECTION"),
    ("투입 종료 평가 작성", "END_EVALUATION"),
    ("마진 검토", "PROFITABILITY_REVIEW"),
    ("신규 인력 탐색 시작", "SOURCING_START"),
    ("안내 메일 송부", "NONE"),
    ("계약서 작성 및 신규 인력 소싱", "NONE"),
])
def test_classification_boundaries(text, expected):
    assert classify_task(text)[0] == expected


@pytest.mark.parametrize("text,expected", [
    ("이번 주 금요일까지 송부", "2026-10-23"),
    ("다음 주 수요일까지 전달", "2026-10-28"),
    ("내일 오전 송부", "2026-10-24"),
    ("오늘 중 발급", "2026-10-23"),
    ("월말까지 송부", "2026-10-31"),
    ("11월 2일 미팅", "2026-11-02"),
    ("2027-01-02 미팅", "2027-01-02"),
    ("2026-02-30 미팅", None),
    ("11월 첫 주 재방문", None),
    ("다음 달 초 미팅", None),
    ("다음 달 월말 송부", None),
    ("예산 확정되면 내일 연락", None),
    ("오늘 자료 내일 송부", None),
    ("10월 2일", None),
    ("기한 없음", None),
])
def test_dates(text, expected):
    assert resolve_due_date(text, "2026-10-23T10:00:00") == expected


def test_dates_without_reference_and_year_boundary():
    assert resolve_due_date("내일", None) is None
    assert resolve_due_date("2026-11-02", None) == "2026-11-02"
    assert resolve_due_date("내일", "2026-12-31T10:00:00") == "2027-01-01"
    assert resolve_due_date("월말", "2028-02-01T10:00:00") == "2028-02-29"


@pytest.mark.parametrize("path", sorted((Path(__file__).resolve().parents[2] / "eval/minutes/gold").glob("*.json")))
def test_minutes_gold_outputs_follow_contract(path):
    note = load(path)
    tasks = extract_tasks(note)
    items = ([p["followUp"] for p in note.get("perPerson", []) if p["followUp"]]
             if note["mode"] == "LIGHT" else
             note["fields"]["commitments"] + note["fields"]["nextContact"])
    assert len(tasks) == len(items)
    assert {t["evidence"] for t in tasks} == {i["evidence"] for i in items}
    for task in tasks:
        _validator("task").validate(task)


@pytest.mark.parametrize('text,expected', [
    ('협약 서류 초안 준비', 'CONTRACT_PREP'),
    ('재계약을 위한 면담 준비', 'RENEWAL_INTERVIEW'),
    ('재계약서 초안 작성', 'CONTRACT_PREP'),
    ('고객 회사 방문 준비', 'MEETING_PREP'),
    ('고객 사무실 방문 준비', 'MEETING_PREP'),
    ('고객 사무실로 출장하여 미팅 준비', 'VISIT_PREP'),
    ('오늘 방문 준비', 'NONE'),
    ('금주 방문 준비', 'NONE'),
    ('오늘 회의 자료 준비', 'NONE'),
    ('시장 조사를 위한 미팅 준비', 'NONE'),
    ('내부 미팅 준비', 'NONE'),
    ('고객 자료 검토를 위한 내부 미팅 준비', 'NONE'),
    ('다음 주 고객 미팅', 'MEETING_PREP'),
    ('다음 주 미팅', 'NONE'),
    ('매월 영업 활동 종료 평가', 'NONE'),
    ('투입 종료 후 수행 평가', 'END_EVALUATION'),
    ('문서 수집 작업 진행', 'DOC_COLLECTION'),
    ('계약서를 다시 보내야 합니다', 'CONTRACT_PREP'),
    ('계약서 서명본 반환 독촉', 'DOC_COLLECTION'),
    ('미채택 후보에게 다시 연락', 'NONE'),
    ('미채택 후보에게 결과를 알려드리기로 함', 'REJECTION_NOTICE'),
    ('예상 마진을 계산', 'PROFITABILITY_REVIEW'),
    ('계약 조건별 수익성 분석', 'PROFITABILITY_REVIEW'),
    ('계약 조건을 확인', 'CONTRACT_PREP'),
    ('마진을 확인', 'PROFITABILITY_REVIEW'),
    ('마진율 확인', 'PROFITABILITY_REVIEW'),
    ('수익률 분석', 'PROFITABILITY_REVIEW'),
    ('제안서 준비', 'PROPOSAL_SUBMISSION'),
    ('내일 제안서 제출 확인', 'NONE'),
    ('제안서 제출 준비 확인', 'PROPOSAL_SUBMISSION'),
    ('새 포지션 후보 탐색 착수', 'SOURCING_START'),
    ('인력 요청에 맞는 지원자를 물색', 'SOURCING_START'),
    ('인력 소싱 착수', 'SOURCING_START'),
    ('공급업체 소싱 착수', 'NONE'),
    ('새 공급업체 탐색 시작', 'NONE'),
    ('소싱 착수', 'NONE'),
])
def test_reviewed_domain_keywords_and_negative_boundaries(text, expected):
    assert classify_task(text)[0] == expected
