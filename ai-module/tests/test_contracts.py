"""contracts/ 데이터 규격 검사. 스키마가 올바른지, 예시가 스키마에 맞는지, 예시끼리 ID가 이어지는지 확인한다."""
import json
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator

CONTRACTS_DIR = Path(__file__).resolve().parents[2] / "contracts"
EXAMPLES_DIR = CONTRACTS_DIR / "examples"
SCHEMA_NAMES = sorted(path.name.removesuffix(".schema.json") for path in CONTRACTS_DIR.glob("*.schema.json"))


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def load_examples(name):
    data = load_json(EXAMPLES_DIR / f"{name}.example.json")
    return data if isinstance(data, list) else [data]


def ids_of(name, key):
    return {item[key] for item in load_examples(name)}


def test_contracts_exist():
    assert {"person", "identity_resolution", "meeting_note", "task", "revision"} <= set(SCHEMA_NAMES)


@pytest.mark.parametrize("name", SCHEMA_NAMES)
def test_schema_is_valid(name):
    Draft202012Validator.check_schema(load_json(CONTRACTS_DIR / f"{name}.schema.json"))


@pytest.mark.parametrize("name", SCHEMA_NAMES)
def test_examples_match_schema(name):
    validator = Draft202012Validator(load_json(CONTRACTS_DIR / f"{name}.schema.json"))
    for example in load_examples(name):
        errors = [f"{list(error.path)}: {error.message}" for error in validator.iter_errors(example)]
        assert not errors, errors


def test_person_examples_are_synthetic():
    assert all(person["isSynthetic"] for person in load_examples("person"))


def test_example_ids_are_linked():
    person_ids = ids_of("person", "personId")
    resolution_ids = ids_of("identity_resolution", "resolutionId")
    note_ids = ids_of("meeting_note", "noteId")
    task_ids = ids_of("task", "taskId")

    for resolution in load_examples("identity_resolution"):
        linked = [resolution["personId"]] + [candidate["personId"] for candidate in resolution["candidates"]]
        assert {pid for pid in linked if pid} <= person_ids

    for note in load_examples("meeting_note"):
        for participant in note["participants"]:
            assert participant["resolutionId"] in resolution_ids | {None}
            assert participant["personId"] in person_ids | {None}

    for task in load_examples("task"):
        assert task["noteId"] in note_ids | {None}
        assert task["counterpartPersonId"] in person_ids | {None}

    targets = {"IDENTITY_RESOLUTION": resolution_ids, "MEETING_NOTE": note_ids, "TASK": task_ids}
    for revision in load_examples("revision"):
        if revision["targetType"] in targets:
            assert revision["targetId"] in targets[revision["targetType"]]
