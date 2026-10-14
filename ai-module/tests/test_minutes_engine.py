"""회의록 엔진(app/minutes) 테스트. LLM은 가짜 provider로 바꿔 네트워크 없이 돈다."""
import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from jsonschema import Draft202012Validator

from app.llm import LLMResponse, LLMUnavailable
from app.minutes import engine
from app.minutes.engine import build_meeting_note, detect_mode
from app.minutes.preprocess import clean_email_body
from app.minutes.prompts import FIELD_NAMES, PER_PERSON_FIELDS
from app.minutes.schemas import MinutesRequest
from eval.minutes.score import is_perfect, load_cases, score

CONTRACTS_DIR = Path(__file__).resolve().parents[2] / "contracts"
VALIDATOR = Draft202012Validator(json.loads((CONTRACTS_DIR / "meeting_note.schema.json").read_text(encoding="utf-8")))
CASES = load_cases()
CASE_IDS = [case["caseId"] for case in CASES]

MEMO = """가상전자 홍길동 부장 미팅
- 견적서는 이번 주 금요일까지 메일로 보내드리기로 함
- 11월 첫 주에 다시 뵙기로"""


def item(text, evidence):
    return {"text": text, "evidence": evidence}


def fake_llm(monkeypatch, data, calls=None):
    def fake_generate(request):
        if calls is not None:
            calls.append(request)
        return LLMResponse(data=data(request) if callable(data) else data, provider="ollama", model="fake-model")

    monkeypatch.setattr(engine, "generate", fake_generate)


def full_output(**fields):
    return {name: fields.get(name, []) for name in FIELD_NAMES}


def request_for(text=MEMO, **overrides):
    payload = {
        "sourceRefs": [{"sourceId": "src_101", "sourceType": "MEETING_NOTE"}],
        "text": text,
        "occurredAt": "2026-10-14T15:00:00",
        "attendees": ["홍길동 부장"],
        **overrides,
    }
    return MinutesRequest(**payload)


def assert_valid(note):
    errors = [f"{list(error.path)}: {error.message}" for error in VALIDATOR.iter_errors(note)]
    assert not errors, errors


def test_normal_llm_output_becomes_draft_note(monkeypatch):
    fake_llm(monkeypatch, full_output(
        commitments=[item("견적서 이번 주 금요일까지 송부", "견적서는 이번 주 금요일까지 메일로 보내드리기로 함")],
        nextContact=[item("11월 첫 주 재방문", "11월 첫 주에 다시 뵙기로")],
    ))

    result = build_meeting_note(request_for())

    note = result.note
    assert_valid(note)
    assert note["status"] == "DRAFT" and note["generatedBy"] == "LLM" and note["llmModel"] == "fake-model"
    assert note["mode"] == "FULL"
    assert [i["text"] for i in note["fields"]["commitments"]] == ["견적서 이번 주 금요일까지 송부"]
    assert note["missingFields"] == ["discussion", "customerRequests", "concerns"]
    assert note["participants"] == [{"displayName": "홍길동 부장", "resolutionId": None, "personId": None}]
    assert (result.llm_provider, result.dropped_items) == ("ollama", [])


def test_items_without_evidence_in_source_are_dropped(monkeypatch):
    fake_llm(monkeypatch, full_output(
        concerns=[item("가격 부담", "가격이 너무 비싸다고 하심")],                    # 원문에 없는 근거
        commitments=[
            item("견적서 송부", "견적서는 이번 주 금요일까지 메일로 보내드리기로 함"),
            item("데모 진행", ""),                                                  # 근거 없음
        ],
        discussion=["문자열 항목"],                                                 # 형식 오류
    ))

    result = build_meeting_note(request_for())

    note = result.note
    assert_valid(note)
    assert note["fields"]["concerns"] == [] and "concerns" in note["missingFields"]
    assert [i["text"] for i in note["fields"]["commitments"]] == ["견적서 송부"]
    assert {(d["field"], d["reason"]) for d in result.dropped_items} == {
        ("concerns", "근거가 원문에 없음"),
        ("commitments", "text 또는 evidence 없음"),
        ("discussion", "항목 형식 오류"),
    }
    every_evidence = [i["evidence"] for name in FIELD_NAMES for i in note["fields"][name]]
    assert all(evidence in MEMO for evidence in every_evidence)


def test_evidence_with_different_spacing_is_stored_as_source_text(monkeypatch):
    fake_llm(monkeypatch, full_output(
        commitments=[item("견적서 송부", '"견적서는 이번 주  금요일까지\n메일로 보내드리기로 함"')],
    ))

    note = build_meeting_note(request_for()).note

    assert note["fields"]["commitments"][0]["evidence"] == "견적서는 이번 주 금요일까지 메일로 보내드리기로 함"


@pytest.mark.parametrize("mode", ["FULL", "LIGHT"])
def test_llm_failure_returns_empty_note_with_all_fields_missing(monkeypatch, mode):
    def unavailable(request):
        raise LLMUnavailable("ollama: connection refused")

    monkeypatch.setattr(engine, "generate", unavailable)

    result = build_meeting_note(request_for(mode=mode, attendees=["홍길동 부장", "박가상 대리"]))

    note = result.note
    assert_valid(note)
    assert all(note["fields"][name] == [] for name in FIELD_NAMES)
    assert note["generatedBy"] == "RULE" and note["llmModel"] is None and note["status"] == "DRAFT"
    assert result.llm_provider == "rule" and "connection refused" in result.fallback_reason
    if mode == "FULL":
        assert note["missingFields"] == list(FIELD_NAMES)
    else:
        assert note["missingFields"] == [f"perPerson[{i}].{slot}" for i in range(2) for slot in PER_PERSON_FIELDS]


def test_participants_link_identity_resolutions():
    resolutions = json.loads((CONTRACTS_DIR / "examples" / "identity_resolution.example.json").read_text(encoding="utf-8"))
    request = request_for(attendees=["홍 부장", "박가상 대리"], identityResolutions=resolutions)

    participants = engine.link_participants(request)

    by_name = {p["displayName"]: p for p in participants}
    assert by_name["홍 부장"] == {"displayName": "홍 부장", "resolutionId": "ir_002", "personId": None}  # 후보 상태
    assert by_name["홍길동"]["personId"] == "p_001"  # 식별 결과에만 있는 사람도 참석자로
    assert [p["displayName"] for p in participants][:2] == ["홍 부장", "박가상 대리"]


def gold_echo(case):
    """정답 내용을 그대로 돌려주는 가짜 LLM. 엔진의 후처리가 정답을 망가뜨리지 않는지 본다."""
    gold = case["gold"]

    def respond(request):
        if request.schema_name == "meeting_note_light":
            people = [{key: person[key] for key in ("displayName", *PER_PERSON_FIELDS)} for person in gold["perPerson"]]
            return {"perPerson": people}
        return {name: gold["fields"][name] for name in FIELD_NAMES}

    return respond


def case_request(case):
    given = case["input"]
    return MinutesRequest(
        noteId=f"mn_eval_{case['caseId']}",
        sourceRefs=[{"sourceId": f"src_eval_{case['caseId']}", "sourceType": given["sourceType"]}],
        text=case["text"],
        occurredAt=given["occurredAt"],
        location=given["location"],
        attendees=given["attendees"],
    )


@pytest.mark.parametrize("case", CASES, ids=CASE_IDS)
def test_eval_cases_pass_schema_and_keep_gold_content(monkeypatch, case):
    fake_llm(monkeypatch, gold_echo(case))

    note = build_meeting_note(case_request(case)).note

    assert_valid(note)
    assert note["mode"] == case["gold"]["mode"]  # 모드는 규칙으로 판정
    assert note["missingFields"] == case["gold"]["missingFields"]
    assert is_perfect(score([case], {case["caseId"]: note}))


def test_mode_detection_by_per_person_lines():
    lines = "예시바이오 방문\n김예시 박사: 잘 쓰고 계심\n- 이예시 연구원: 첫 인사"
    assert detect_mode(lines, ["김예시 박사", "이예시 연구원"]) == "LIGHT"
    assert detect_mode("참석: 김예시 박사, 이예시 연구원\n김 박사님이 요청하심", ["김예시 박사", "이예시 연구원"]) == "FULL"
    assert detect_mode(lines, ["김예시 박사"]) == "FULL"


def test_light_mode_drops_people_not_in_attendees(monkeypatch):
    text = "층별 인사\n김예시 박사: 잘 쓰고 계심\n이예시 연구원: 첫 인사만 드림"
    fake_llm(monkeypatch, {"perPerson": [
        {"displayName": "김예시 박사", "status": item("잘 사용 중", "잘 쓰고 계심"), "request": None, "followUp": None},
        {"displayName": "최가상 팀장", "status": item("지어낸 사람", "첫 인사만 드림"), "request": None, "followUp": None},
    ]})

    result = build_meeting_note(request_for(text=text, attendees=["김예시 박사", "이예시 연구원"]))

    note = result.note
    assert_valid(note)
    assert [p["displayName"] for p in note["perPerson"]] == ["김예시 박사", "이예시 연구원"]
    assert note["perPerson"][1]["status"] is None
    assert "perPerson[1].status" in note["missingFields"]
    assert result.dropped_items[0]["reason"].startswith("만난 사람 목록에 없는 사람")


def test_email_source_drops_quote_and_signature(monkeypatch):
    mail = """부장님, 견적서 오늘 중으로 보내드리겠습니다.

--
가상솔루션 영업팀 김예시
> 견적서 언제 받을 수 있나요?
"""
    assert clean_email_body(mail) == "부장님, 견적서 오늘 중으로 보내드리겠습니다."
    reply = "네 확인했습니다.\n\n2026년 10월 14일 (수) 오후 3:00, 홍길동 <gildong@example.com>님이 작성:\n이전 메일 본문"
    assert clean_email_body(reply) == "네 확인했습니다."

    fake_llm(monkeypatch, full_output(customerRequests=[item("견적 일정 문의", "견적서 언제 받을 수 있나요?")]))
    result = build_meeting_note(request_for(text=mail, sourceRefs=[{"sourceId": "src_7", "sourceType": "EMAIL"}]))

    assert result.note["fields"]["customerRequests"] == []  # 인용된 이전 메일은 근거가 될 수 없다
    assert result.dropped_items[0]["reason"] == "근거가 원문에 없음"


def test_route_returns_schema_valid_note(monkeypatch):
    from app.main import app

    calls = []
    fake_llm(monkeypatch, full_output(nextContact=[item("11월 첫 주 재방문", "11월 첫 주에 다시 뵙기로")]), calls)
    client = TestClient(app)

    response = client.post("/minutes", json={
        "sourceRefs": [{"sourceId": "src_101", "sourceType": "MEETING_NOTE"}],
        "text": MEMO,
        "attendees": ["홍길동 부장"],
    })

    assert response.status_code == 200
    body = response.json()
    assert_valid(body["meetingNote"])
    assert body["meetingNote"]["noteId"] == "mn_draft"
    assert (body["llmProvider"], body["llmModel"], body["droppedItems"]) == ("ollama", "fake-model", [])
    assert calls[0].task == "minutes" and "홍길동 부장" in calls[0].user

    assert client.post("/minutes", json={"sourceRefs": [{"sourceId": "src_1", "sourceType": "MEETING_NOTE"}],
                                         "text": "   "}).status_code == 400
    assert client.post("/minutes", json={"sourceRefs": [], "text": MEMO}).status_code == 422
