"""D-022 규칙 식별. 계약 응답만 만들며 인물 등록·병합·저장·외부 호출은 하지 않는다."""

import json
from copy import deepcopy
from functools import lru_cache
from pathlib import Path
from uuid import uuid4

from jsonschema import Draft202012Validator

from app.identity.features import compare_people
from app.identity.normalization import normalize_company, normalize_email, normalize_name, normalize_phone, normalize_text
from app.identity.ranking import rank_comparisons

MODEL_VERSION = "identity-rule-0.2-d022"
FUZZY_THRESHOLD = 0.88
CONTRACTS = Path(__file__).resolve().parents[3] / "contracts"


@lru_cache(maxsize=1)
def validators():
    resolution = json.loads((CONTRACTS / "identity_resolution.schema.json").read_text(encoding="utf-8"))
    person = json.loads((CONTRACTS / "person.schema.json").read_text(encoding="utf-8"))
    return (Draft202012Validator(resolution["properties"]["mention"]),
            Draft202012Validator(person), Draft202012Validator(resolution))


def has_identity_clue(mention):
    """D-022 정보 부족: 이름·이메일·전화 중 비교 가능한 정보가 하나도 없는지 판단한다."""
    return any(normalizer(mention.get(field)) is not None for field, normalizer in (
        ("name", normalize_name), ("email", normalize_email), ("phone", normalize_phone)))


def _validate_inputs(mentions, persons):
    mention_validator, person_validator, _ = validators()
    for field, items, validator in (("mentions", mentions, mention_validator), ("persons", persons, person_validator)):
        if not isinstance(items, list):
            raise ValueError(f"{field}는 배열이어야 합니다")
        for index, item in enumerate(items):
            error = next(validator.iter_errors(item), None)
            if error:
                # 오류 응답에 인물 대장 전체나 필드 값을 복사하지 않는다.
                raise ValueError(f"{field}[{index}]{error.json_path[1:]}: {error.validator} 형식 검사 실패")
    ids = [person["personId"] for person in persons]
    if len(ids) != len(set(ids)):
        raise ValueError("persons의 personId는 중복될 수 없습니다")


def _can_confirm(comparison, contact_matches, mention):
    if len(contact_matches) != 1 or comparison.person_id != contact_matches[0].person_id:
        return False
    if comparison.warnings or comparison.name.status == "DIFFERENT" or comparison.company.status == "DIFFERENT":
        return False
    # 다른 연락처의 불일치·미지원 표기를 무시한 자동 연결도 피한다.
    for field, normalizer in (("email", normalize_email), ("phone", normalize_phone)):
        raw = mention.get(field)
        if normalize_text(raw) is not None:
            if normalizer(raw) is None or getattr(comparison, field).status == "DIFFERENT":
                return False
    return True


def _reasons(comparison, mention):
    reasons = []
    if comparison.name.status == "MATCH":
        reasons.append("이름 일치" if "name" in comparison.name.matched_paths else "등록된 이름 별칭 일치")
    elif comparison.name.status == "DIFFERENT":
        if "MATCH" in (comparison.email.status, comparison.phone.status):
            reasons.append("연락처는 일치하지만 이름이 등록 표기와 다름 — 확인 필요")
        else:
            best = max(comparison.name_similarities, key=lambda item: item.similarity, default=None)
            if best:
                reasons.append(f"이름 표기 유사: {best.observed} / {best.registered} — 동일 인물인지 확인 필요")
    else:
        reasons.append("비교할 이름 정보 부족")
    for field, label in (("email", "이메일"), ("phone", "전화번호")):
        status = getattr(comparison, field).status
        if status == "MATCH":
            reasons.append(f"{label} 정확 일치")
        elif status == "DIFFERENT":
            reasons.append(f"{label}이 등록 연락처와 다름 — 확인 필요")
    if comparison.company.status == "MATCH":
        if "company" in comparison.company.matched_paths:
            reasons.append("현재 소속 일치")
        if any(path.startswith("companyHistory[") for path in comparison.company.matched_paths):
            reasons.append("이전 소속 이력 일치 — 현재 소속으로 덮어쓰지 않음")
    elif comparison.company.status == "DIFFERENT":
        reasons.append("입력 소속이 현재·이전 소속과 다름 — 확인 필요")
    elif normalize_company(mention.get("company")) is None:
        reasons.append("소속 정보 없음")
    warning_reasons = {
        "SHARED_EMAIL": "같은 이메일이 여러 인물에 등록됨 — 사용자 확인 필요",
        "SHARED_PHONE": "같은 전화번호가 여러 인물에 등록됨 — 사용자 확인 필요",
        "CONTACT_TARGETS_DISAGREE": "이메일과 전화번호가 서로 다른 인물에 일치 — 사용자 확인 필요",
    }
    reasons.extend(warning_reasons[w] for w in comparison.warnings if w in warning_reasons)
    for field, label, normalizer in (("email", "이메일", normalize_email), ("phone", "전화번호", normalize_phone)):
        if normalize_text(mention.get(field)) is not None and normalizer(mention[field]) is None:
            reasons.append(f"{label} 표기를 비교할 수 없음 — 확인 필요")
    return reasons


def _resolve_one(mention, persons):
    comparisons = compare_people(mention, persons)
    ranked = rank_comparisons(comparisons, fuzzy_threshold=FUZZY_THRESHOLD)
    contacts = [c for c in comparisons if "MATCH" in (c.email.status, c.phone.status)]
    decision, person_id = "NEW", None
    selected = ranked[:3]
    if ranked:
        decision = "CANDIDATES"
        first = ranked[0].candidate.comparison
        if _can_confirm(first, contacts, mention):
            decision, person_id, selected = "CONFIRMED", first.person_id, ranked[:1]
    name_matches = sum(c.name.status == "MATCH" for c in comparisons)
    candidates = []
    for item in selected:
        reasons = _reasons(item.candidate.comparison, mention)
        if decision == "CANDIDATES":
            if name_matches > 1 and item.candidate.comparison.name.status == "MATCH":
                reasons.append("같은 이름이 여러 인물에 일치 — 동명이인·소속 이동 중 누구인지 확인 필요")
            if len(ranked) > 3:
                reasons.append(f"후보가 {len(ranked)}명입니다. 표시되지 않은 인물은 인물 대장에서 직접 검색해 주세요.")
            reasons.append("이 중 누구인지 확인해 주세요. 신규 등록은 사람이 결정합니다.")
        else:
            reasons.append("회의록 확정 화면에서 상대를 한 번 더 확인해 주세요")
        candidates.append({"personId": item.person_id, "score": None, "reasons": reasons})
    result = {"resolutionId": f"ir_{uuid4().hex}", "mention": deepcopy(mention),
              "decision": decision, "personId": person_id, "candidates": candidates,
              "decidedBy": "RULE", "modelVersion": MODEL_VERSION}
    validators()[2].validate(result)
    return result


def resolve_identities(mentions, persons):
    """사용자 범위로 조회된 대장을 명시적으로 전달한다. 예제/학습 대장을 자동으로 읽지 않는다."""
    _validate_inputs(mentions, persons)
    return [_resolve_one(mention, persons) for mention in mentions]
