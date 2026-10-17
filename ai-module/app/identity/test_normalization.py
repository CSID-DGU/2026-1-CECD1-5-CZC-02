"""원문 보존·과도한 통합 방지·mention 계약을 네트워크 없이 검사한다."""
import json
import unicodedata
from copy import deepcopy
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator

from app.identity.normalization import (
    normalize_company, normalize_email, normalize_mention,
    normalize_name, normalize_phone, normalize_text,
)


@pytest.mark.parametrize("raw, expected", [
    (None, None), (" \t ", None), ("가상인물_0001 부장님", "가상인물_0001"),
    ("부장 가상인물_0001", "가상인물_0001"), ("가상인물_0001님", "가상인물_0001"),
    ("  Synthetic   Person 0001 ", "synthetic person 0001"),
    ("가상부장인물_0001", "가상부장인물_0001"),
    ("가상인물_0001부장", "가상인물_0001부장"), ("부장님", None),
    (unicodedata.normalize("NFD", "가상인물_0001"), "가상인물_0001"),
])
def test_names(raw, expected):
    assert normalize_name(raw) == expected


@pytest.mark.parametrize("raw, expected", [
    (None, None), (" ", None),
    (" Person.Tag+test@PEOPLE.EXAMPLE ", "Person.Tag+test@people.example"),
    ("missing-at", None), ("a@@people.example", None),
    ("@people.example", None), ("synthetic@", None),
    ("synthetic person@people.example", None),
])
def test_emails(raw, expected):
    assert normalize_email(raw) == expected


@pytest.mark.parametrize("raw, expected", [
    (None, None), ("  ", None), ("(000) 0000-0001", "00000000001"),
    ("+00 (000) 0000-0001", "+0000000000001"),
    ("000-0000-0001 내선 2", None), ("000-****-0001", None),
    ("+", None), ("000/0001", None),
])
def test_phones(raw, expected):
    assert normalize_phone(raw) == expected


def test_does_not_guess_equivalence():
    assert normalize_email("Synthetic@people.example") != normalize_email("synthetic@people.example")
    assert normalize_phone("+00 000-0001") != normalize_phone("000-0001")
    assert normalize_company("가상회사_001") != normalize_company("가상회사_002")
    assert normalize_company("가상회사_001") != normalize_company("(주)가상회사_001")
    assert normalize_company("  가상회사_001   가상부문 ") == "가상회사_001 가상부문"


def test_mention_keeps_original_and_contract():
    mention = {"name": "가상인물_0001 부장님", "company": " 가상회사_001 ",
               "title": " 부장 ", "email": "Synthetic+tag@PEOPLE.EXAMPLE",
               "phone": "000-0000-0001", "channel": "MEETING", "sourceRef": "src_001"}
    original = deepcopy(mention)
    normalized = normalize_mention(mention)
    assert mention == original
    assert normalized is not mention
    assert normalized["name"] == "가상인물_0001"
    assert normalized["email"] == "Synthetic+tag@people.example"
    assert normalized["sourceRef"] == original["sourceRef"]
    assert normalized["channel"] == original["channel"]
    schema_path = Path(__file__).resolve().parents[3] / "contracts/identity_resolution.schema.json"
    mention_schema = json.loads(schema_path.read_text(encoding="utf-8"))["properties"]["mention"]
    Draft202012Validator(mention_schema).validate(normalized)
    assert set(normalized) == set(original)


def test_missing_fields_remain_missing():
    assert normalize_mention({"name": None, "channel": "EMAIL"}) == {"name": None, "channel": "EMAIL"}


@pytest.mark.parametrize("normalizer", [normalize_text, normalize_name, normalize_company, normalize_email, normalize_phone])
def test_rejects_non_text_instead_of_guessing(normalizer):
    with pytest.raises(TypeError):
        normalizer(123)


def test_generated_mentions_remain_valid_and_idempotent():
    root = Path(__file__).resolve().parents[2]
    schema = json.loads((root.parent / "contracts/identity_resolution.schema.json").read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema["properties"]["mention"])
    for filename in ("pairs_train.json", "pairs_eval.json"):
        pairs = json.loads((root / "ml/identity/data" / filename).read_text(encoding="utf-8"))
        for pair in pairs:
            for key in ("mentionA", "mentionB"):
                mention = pair[key]
                original = deepcopy(mention)
                normalized = normalize_mention(mention)
                validator.validate(normalized)
                assert mention == original
                assert normalize_mention(normalized) == normalized
