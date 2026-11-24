from fastapi.testclient import TestClient

from .api import app
from .engine import CONTRACTS, _validator
import json


def test_extract_endpoint_contract():
    note = json.loads((CONTRACTS / "examples/meeting_note.example.json").read_text(encoding="utf-8"))
    response = TestClient(app).post("/tasks/extract?assignee=user_demo", json=note)
    assert response.status_code == 200
    assert len(response.json()) == 3
    for task in response.json():
        _validator("task").validate(task)
        assert task["assignee"] == "user_demo"
        assert task["status"] == "DRAFT"


def test_extract_endpoint_rejects_bad_note():
    client = TestClient(app)
    assert client.post("/tasks/extract", json={}).status_code == 422
    assert client.post("/tasks/extract", json=[]).status_code == 422
    note = json.loads((CONTRACTS / "examples/meeting_note.example.json").read_text(encoding="utf-8"))
    note["occurredAt"] = "2026-02-30T10:00:00"
    assert client.post("/tasks/extract", json=note).status_code == 422


def test_endpoint_uses_llm_only_after_rule_none(monkeypatch):
    from unittest.mock import Mock
    suggestion = Mock(return_value="MEETING_PREP")
    monkeypatch.setattr("app.tasks.api.suggest_task_type", suggestion)
    note = json.loads((CONTRACTS / "examples/meeting_note.example.json").read_text(encoding="utf-8"))
    note["fields"]["nextContact"] = [{"text": "가상 대상과 일정 조율", "evidence": "가상 대상과 일정 조율"}]
    response = TestClient(app).post("/tasks/extract?assignee=user_demo", json=note)
    assert response.status_code == 200
    suggestion.assert_called_once_with("가상 대상과 일정 조율")
    tasks = response.json()
    assert tasks[-1]["taskTypeSource"] == "LLM"
    for task in tasks:
        _validator("task").validate(task)
