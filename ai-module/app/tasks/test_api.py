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
