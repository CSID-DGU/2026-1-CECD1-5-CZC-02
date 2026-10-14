"""회의록 엔진: 메모·메일 텍스트 → meeting_note 초안 (DRAFT).

순서: 전처리 → LLM(5문항 또는 사람별 3칸, 항목마다 원문 인용) → 규칙 검증 → 스키마 검사.
- 근거가 원문에 글자 그대로 없는 항목은 버린다 (공백 차이만 허용하고, 저장은 원문 쪽 글자로).
- 빈 문항은 missingFields에 적는다. LIGHT는 perPerson[n].칸 (n은 0부터, D-018).
- LLM을 못 쓰면 빈 회의록 + 전 문항 missingFields. 지어내지 않는다.
"""
import json
import re
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from app.config import OLLAMA_TEMPERATURE
from app.llm import LLMError, LLMRequest, LLMUnavailable, generate
from app.minutes.preprocess import prepare_source
from app.minutes.prompts import (
    FIELD_NAMES,
    FULL_OUTPUT_SCHEMA,
    FULL_SYSTEM_PROMPT,
    LIGHT_OUTPUT_SCHEMA,
    LIGHT_SYSTEM_PROMPT,
    PER_PERSON_FIELDS,
    build_user_prompt,
)
from app.minutes.schemas import MinutesRequest

SCHEMA_PATH = Path(__file__).resolve().parents[3] / "contracts" / "meeting_note.schema.json"
QUOTE_CHARS = "\"'“”‘’「」『』"
LINE_BULLETS = "-*•·▶ \t"


@dataclass
class MinutesResult:
    note: Dict[str, Any]
    llm_provider: str = "rule"
    llm_model: Optional[str] = None
    fallback_reason: Optional[str] = None
    dropped_items: List[Dict[str, Any]] = field(default_factory=list)


def build_meeting_note(request: MinutesRequest) -> MinutesResult:
    source = prepare_source(request.text, is_email=any(ref.sourceType == "EMAIL" for ref in request.sourceRefs))
    participants = link_participants(request)
    names = [participant["displayName"] for participant in participants]
    mode = request.mode or detect_mode(source, names)
    base = {
        "noteId": request.noteId,
        "sourceRefs": [ref.model_dump() for ref in request.sourceRefs],
        "occurredAt": request.occurredAt,
        "location": request.location,
        "mode": mode,
        "participants": participants,
    }

    full = mode == "FULL"
    try:
        response = generate(LLMRequest(
            task="minutes",
            system=FULL_SYSTEM_PROMPT if full else LIGHT_SYSTEM_PROMPT,
            user=build_user_prompt(source, request.occurredAt, request.location, names),
            json_schema=FULL_OUTPUT_SCHEMA if full else LIGHT_OUTPUT_SCHEMA,
            schema_name="meeting_note_full" if full else "meeting_note_light",
            max_output_tokens=2000,
            temperature=OLLAMA_TEMPERATURE,
        ))
    except (LLMUnavailable, LLMError) as exception:
        return MinutesResult(note=empty_note(base, participants), fallback_reason=str(exception))

    data = response.data if isinstance(response.data, dict) else {}
    dropped: List[Dict[str, Any]] = []
    if full:
        fields = {name: clean_items(data.get(name), source, name, dropped) for name in FIELD_NAMES}
        note = assemble(base, fields, None, generated_by="LLM", llm_model=response.model)
    else:
        per_person = clean_per_person(data.get("perPerson"), source, participants, dropped)
        note = assemble(base, {name: [] for name in FIELD_NAMES}, per_person, generated_by="LLM", llm_model=response.model)

    errors = schema_errors(note)
    if errors:  # 규칙 검증을 거쳤으므로 정상이면 일어나지 않는다. 형식이 깨진 회의록을 내보내지 않는다.
        reason = f"회의록 스키마 검사 실패: {errors[0]}"
        return MinutesResult(note=empty_note(base, participants), fallback_reason=reason, dropped_items=dropped)
    return MinutesResult(note=note, llm_provider=response.provider, llm_model=response.model,
                         fallback_reason=response.fallback_reason, dropped_items=dropped)


# ---------- 참석자·모드 ----------

def compact(name: Optional[str]) -> str:
    return re.sub(r"\s+", "", name or "")


def link_participants(request: MinutesRequest) -> List[Dict[str, Any]]:
    """만난 사람 이름에 A의 식별 결과를 이름이 같으면 연결한다. 식별 결과에만 있는 사람도 참석자로 넣는다."""
    resolutions = {compact(r.mention.name): r for r in request.identityResolutions if compact(r.mention.name)}
    names: List[str] = []
    for name in [*request.attendees, *(r.mention.name for r in request.identityResolutions)]:
        if name and name.strip() and compact(name) not in {compact(n) for n in names}:
            names.append(name.strip())

    participants = []
    for name in names:
        resolution = resolutions.get(compact(name))
        participants.append({
            "displayName": name,
            "resolutionId": resolution.resolutionId if resolution else None,
            "personId": resolution.personId if resolution else None,
        })
    return participants


def detect_mode(source: str, names: List[str]) -> str:
    """만난 사람 2명 이상이 각자 줄 머리에 이름으로 적혀 있으면 사람별로 적은 다인 방문 메모(LIGHT)로 본다."""
    if len(names) < 2:
        return "FULL"
    line_heads = [compact(line.lstrip(LINE_BULLETS)) for line in source.split("\n")]
    led = sum(1 for name in names if any(head.startswith(compact(name)) for head in line_heads))
    return "LIGHT" if led >= 2 else "FULL"


# ---------- 항목 검증 ----------

def find_evidence(evidence: str, source: str) -> Optional[str]:
    """근거가 원문에 있으면 원문 쪽 글자를 돌려준다. 공백·줄바꿈 차이만 허용한다."""
    tokens = evidence.strip().strip(QUOTE_CHARS).split()
    if not tokens:
        return None
    match = re.search(r"\s+".join(re.escape(token) for token in tokens), source)
    return match.group(0) if match else None


def clean_item(raw: Any, source: str) -> Tuple[Optional[Dict[str, str]], Optional[str]]:
    """(정리된 항목, 버린 이유). 항목이 유효하면 이유는 None."""
    if not isinstance(raw, dict):
        return None, "항목 형식 오류"
    text = raw.get("text").strip() if isinstance(raw.get("text"), str) else ""
    evidence = raw.get("evidence") if isinstance(raw.get("evidence"), str) else ""
    if not text or not evidence.strip():
        return None, "text 또는 evidence 없음"
    span = find_evidence(evidence, source)
    if span is None:
        return None, "근거가 원문에 없음"
    return {"text": text, "evidence": span}, None


def drop(dropped: List[Dict[str, Any]], field_name: str, raw: Any, reason: str) -> None:
    raw = raw if isinstance(raw, dict) else {}
    dropped.append({
        "field": field_name,
        "text": raw.get("text") if isinstance(raw.get("text"), str) else None,
        "evidence": raw.get("evidence") if isinstance(raw.get("evidence"), str) else None,
        "reason": reason,
    })


def clean_items(raw_items: Any, source: str, field_name: str, dropped: List[Dict[str, Any]]) -> List[Dict[str, str]]:
    items, seen = [], set()
    for raw in raw_items if isinstance(raw_items, list) else []:
        item, reason = clean_item(raw, source)
        if item is None:
            drop(dropped, field_name, raw, reason)
            continue
        key = (compact(item["text"]), item["evidence"])
        if key in seen:
            continue
        seen.add(key)
        items.append(item)
    return items


def clean_per_person(raw_people: Any, source: str, participants: List[Dict[str, Any]],
                     dropped: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """사람별 3칸. 만난 사람 순서대로 만들고, 목록에 없는 사람의 칸은 버린다 (목록이 비었으면 원문에 이름이 있는 사람만)."""
    raw_by_name: Dict[str, Dict[str, Any]] = {}
    for raw in raw_people if isinstance(raw_people, list) else []:
        if isinstance(raw, dict) and isinstance(raw.get("displayName"), str) and compact(raw["displayName"]):
            raw_by_name.setdefault(compact(raw["displayName"]), raw)

    people = [(p["displayName"], p["personId"]) for p in participants]
    if not people:
        people = [(raw["displayName"].strip(), None) for key, raw in raw_by_name.items() if key in compact(source)]
    known = {compact(name) for name, _ in people}
    for key, raw in raw_by_name.items():
        if key not in known:
            for slot in PER_PERSON_FIELDS:
                if raw.get(slot) is not None:
                    drop(dropped, f"perPerson.{slot}", raw.get(slot), f"만난 사람 목록에 없는 사람({raw['displayName']})")

    result = []
    for name, person_id in people:
        raw = raw_by_name.get(compact(name), {})
        entry: Dict[str, Any] = {"displayName": name, "personId": person_id}
        for slot in PER_PERSON_FIELDS:
            entry[slot] = None
            if raw.get(slot) is not None:
                item, reason = clean_item(raw[slot], source)
                if item is None:
                    drop(dropped, f"perPerson.{slot}", raw[slot], reason)
                entry[slot] = item
        result.append(entry)
    return result


# ---------- 조립 ----------

def missing_fields(fields: Dict[str, List[Any]], per_person: Optional[List[Dict[str, Any]]]) -> List[str]:
    if per_person is None:
        return [name for name in FIELD_NAMES if not fields[name]]
    return [
        f"perPerson[{index}].{slot}"
        for index, person in enumerate(per_person)
        for slot in PER_PERSON_FIELDS
        if person[slot] is None
    ]


def assemble(base: Dict[str, Any], fields: Dict[str, List[Any]], per_person: Optional[List[Dict[str, Any]]],
             generated_by: str, llm_model: Optional[str]) -> Dict[str, Any]:
    note = {**base, "fields": fields}
    if per_person is not None:
        note["perPerson"] = per_person
    note.update({
        "missingFields": missing_fields(fields, per_person),
        "uncertainSegments": [],
        "status": "DRAFT",
        "generatedBy": generated_by,
        "llmModel": llm_model,
    })
    return note


def empty_note(base: Dict[str, Any], participants: List[Dict[str, Any]]) -> Dict[str, Any]:
    """LLM을 못 썼을 때. 내용은 비우고 모든 칸을 missingFields에 적는다."""
    fields = {name: [] for name in FIELD_NAMES}
    per_person = None
    if base["mode"] == "LIGHT":
        per_person = [{"displayName": p["displayName"], "personId": p["personId"],
                       **{slot: None for slot in PER_PERSON_FIELDS}} for p in participants]
    note = assemble(base, fields, per_person, generated_by="RULE", llm_model=None)
    if per_person is not None and not per_person:
        note["missingFields"] = list(FIELD_NAMES)  # 사람도 모르면 칸 단위로 적을 수 없으므로 5문항 전부
    return note


@lru_cache(maxsize=1)
def _validator():
    try:
        from jsonschema import Draft202012Validator
    except ImportError:
        return None
    if not SCHEMA_PATH.exists():  # contracts 폴더 없이 배포된 경우 검사를 건너뛴다
        return None
    return Draft202012Validator(json.loads(SCHEMA_PATH.read_text(encoding="utf-8")))


def schema_errors(note: Dict[str, Any]) -> List[str]:
    validator = _validator()
    if validator is None:
        return []
    return [f"{list(error.path)}: {error.message}" for error in validator.iter_errors(note)]
