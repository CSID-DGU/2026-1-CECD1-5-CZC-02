"""회의록 평가셋(eval/minutes) 검사. 정답이 스키마·원문과 맞는지, 채점기가 만점과 오류를 제대로 가리는지 확인한다."""
import copy
import json
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator

from app.minutes.preprocess import prepare_source
from eval.minutes.score import FIELD_NAMES, PER_PERSON_FIELDS, is_perfect, load_cases, match_items, score

SCHEMA = json.loads(
    (Path(__file__).resolve().parents[2] / "contracts" / "meeting_note.schema.json").read_text(encoding="utf-8")
)
CASES = load_cases()
CASE_IDS = [case["caseId"] for case in CASES]


def all_items(note):
    for name in FIELD_NAMES:
        yield from note["fields"][name]
    for person in note.get("perPerson", []):
        yield from (person[name] for name in PER_PERSON_FIELDS if person[name] is not None)


def gold_predictions():
    return {case["caseId"]: copy.deepcopy(case["gold"]) for case in CASES}


def test_case_set_covers_required_situations():
    # 메모 10건(B-1) + 메일 3건(B-5)
    assert sum(case["input"]["sourceType"] == "MEETING_NOTE" for case in CASES) == 10
    assert sum(case["input"]["sourceType"] == "EMAIL" for case in CASES) == 3
    tags = [tag for case in CASES for tag in case["tags"]]
    for required in ("multi_commitment", "no_concerns", "deadline_expression", "multi_counterpart", "chitchat",
                     "email_quote", "email_signature", "email_thread", "multi_recipient"):
        assert required in tags, required
    assert 1 <= sum(case["gold"]["mode"] == "LIGHT" for case in CASES) <= 2


@pytest.mark.parametrize("case", CASES, ids=CASE_IDS)
def test_gold_matches_schema(case):
    errors = [f"{list(error.path)}: {error.message}" for error in Draft202012Validator(SCHEMA).iter_errors(case["gold"])]
    assert not errors, errors


@pytest.mark.parametrize("case", CASES, ids=CASE_IDS)
def test_gold_evidence_is_verbatim_in_memo(case):
    # 메일은 엔진과 같은 전처리(인용·서명 제거) 뒤 원문에도 있어야 한다 — 인용된 이전 메일·서명은 근거가 될 수 없다
    source = prepare_source(case["text"], is_email=case["input"]["sourceType"] == "EMAIL")
    for item in all_items(case["gold"]):
        assert item["evidence"] in case["text"], item
        assert item["evidence"] in source, item


@pytest.mark.parametrize("case", CASES, ids=CASE_IDS)
def test_gold_missing_fields_are_exactly_the_empty_slots(case):
    gold = case["gold"]
    if gold["mode"] == "FULL":
        expected = [name for name in FIELD_NAMES if not gold["fields"][name]]
        assert "perPerson" not in gold
    else:
        assert all(gold["fields"][name] == [] for name in FIELD_NAMES)
        expected = [
            f"perPerson[{index}].{name}"
            for index, person in enumerate(gold["perPerson"])
            for name in PER_PERSON_FIELDS
            if person[name] is None
        ]
    assert gold["missingFields"] == expected


@pytest.mark.parametrize("case", CASES, ids=CASE_IDS)
def test_case_input_matches_gold(case):
    gold, given = case["gold"], case["input"]
    assert gold["noteId"] == f"mn_eval_{case['caseId']}"
    assert [ref["sourceType"] for ref in gold["sourceRefs"]] == [given["sourceType"]]
    assert gold["occurredAt"] == given["occurredAt"]
    assert gold["location"] == given["location"]
    assert [p["displayName"] for p in gold["participants"]] == given["attendees"]
    assert gold["status"] == "DRAFT"


def test_gold_against_gold_is_perfect():
    assert is_perfect(score(CASES, gold_predictions()))


def test_paraphrase_matches_but_different_item_does_not():
    gold = [{"text": "11월 첫 주 재방문", "evidence": "11월 첫 주에 다시 뵙기로"}]
    paraphrase = [{"text": "11월 첫째 주에 다시 방문 예정", "evidence": "11월 첫 주에 다시 뵙기로"}]
    unrelated = [{"text": "계약서 초안 송부", "evidence": "계약서 초안은 이번 주 금요일까지 보내드리기로 함"}]
    assert match_items(paraphrase, gold) == [(0, 0)]
    assert match_items(unrelated, gold) == []


def test_scorer_penalizes_missed_fabricated_and_wrong_missing_fields():
    predictions = gold_predictions()
    m01 = predictions["m01"]
    m01["fields"]["commitments"].pop()  # 약속 하나 놓침
    m01["fields"]["concerns"].append({"text": "가격 부담", "evidence": "가격이 너무 비싸다고 하심"})  # 원문에 없는 근거
    predictions["m04"]["missingFields"] = []  # 빈 문항 표시 누락
    predictions["m05"] = None  # 예측 파일 없음

    report = score(CASES, predictions)

    assert report["predicted"] == len(CASES) - 1
    assert report["fields"]["commitments"]["recall"] < 1
    assert report["fields"]["concerns"]["precision"] < 1
    assert report["fields"]["perPerson.status"]["recall"] < 1
    assert report["evidenceInSourceRate"] < 1
    assert report["missingFields"]["exactMatchRate"] == round((len(CASES) - 2) / len(CASES), 4)  # m04, m05 불일치
    assert not is_perfect(report)
