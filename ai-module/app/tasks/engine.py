"""FULL 약속·다음 접촉 / LIGHT 후속 조치 → task 계약 형식의 초안."""

import json
from pathlib import Path

from jsonschema import Draft202012Validator

from .classifier import classify_task
from .dates import resolve_due_date


CONTRACTS = Path(__file__).resolve().parents[3] / "contracts"


def _validator(name: str) -> Draft202012Validator:
    return Draft202012Validator(json.loads(
        (CONTRACTS / f"{name}.schema.json").read_text(encoding="utf-8")
    ))


def extract_tasks(note: dict, *, assignee: str | None = None) -> list[dict]:
    """assignee는 호출자가 명시한 우리 쪽 사용자 ID만 사용한다.

    고객 측 약속은 고객의 일을 대신 배정하지 않고 이행 확인 초안으로 남긴다.
    FULL의 복수 참석자 중 상대방을 임의로 선택하지 않는다.
    """
    _validator("meeting_note").validate(note)
    items = []
    if note["mode"] == "LIGHT":
        for person in note.get("perPerson", []):
            if person["followUp"]:
                items.append((person["followUp"], person["personId"]))
    else:
        participants = note["participants"]
        counterpart = participants[0]["personId"] if len(participants) == 1 else None
        for field in ("commitments", "nextContact"):
            items.extend((item, counterpart) for item in note["fields"][field])

    tasks = []
    seen = set()
    task_validator = _validator("task")
    for item, counterpart in items:
        key = (item["text"], item["evidence"], counterpart)
        if key in seen:
            continue
        seen.add(key)
        customer_side = "(고객 측)" in item["text"]
        content = item["text"]
        if customer_side:
            content = f"{content} — 이행 여부 확인"
        kind, confidence = ("NONE", 0.0) if customer_side else classify_task(content)
        task = {
            "taskId": f"t_{note['noteId'][3:]}_{len(tasks) + 1}",
            "noteId": note["noteId"],
            "content": content,
            "dueDate": None if customer_side else resolve_due_date(item["text"], note.get("occurredAt")),
            "dueDateIsDefault": False,
            "assignee": assignee,
            "counterpartPersonId": counterpart,
            "commitmentKind": "IMPLICIT" if customer_side else "EXPLICIT",
            "taskType": kind,
            "taskTypeConfidence": confidence,
            "taskTypeSource": "RULE",
            "evidence": item["evidence"],
            "status": "DRAFT",
        }
        task_validator.validate(task)
        tasks.append(task)
    return tasks
