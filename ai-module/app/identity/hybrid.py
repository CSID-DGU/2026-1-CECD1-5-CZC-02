"""연락처 보호 규칙 + 고정 로지스틱 후보 보완. 사람 확정·등록·병합은 하지 않는다."""
import logging
import os
from copy import deepcopy
from uuid import uuid4

from app.identity import logistic
from app.identity.engine import _can_confirm, _reasons, _resolve_one, _validate_inputs, has_identity_clue, validators
from app.identity.features import compare_people

logger = logging.getLogger(__name__)


def _resolve(mention, persons, model):
    if not persons or not has_identity_clue(mention):
        return _resolve_one(mention, persons)
    comparisons = compare_people(mention, persons)
    contacts = [c for c in comparisons if "MATCH" in (c.email.status, c.phone.status)]
    if len(contacts) == 1 and _can_confirm(contacts[0], contacts, mention):
        # 확정 경계와 확정 점수(null)는 기존 RULE 응답 그대로다.
        return _resolve_one(mention, persons)
    ranked = []
    for comparison in comparisons:
        score = model.score(comparison)
        contact = "MATCH" in (comparison.email.status, comparison.phone.status)
        exact = comparison.name.status == "MATCH"
        if contact or exact or score >= model.threshold:
            ranked.append((comparison, score, (int(contact), int(exact), round(score, 12))))
    ranked.sort(key=lambda item: (tuple(-v for v in item[2]), item[0].person_id))
    name_matches = sum(c.name.status == "MATCH" for c in comparisons)
    candidates = []
    for comparison, score, _ in ranked[:3]:
        reasons = _reasons(comparison, mention)
        reasons.append("이름 유사도·연락처・소속 비교 단서를 로지스틱 모델로 함께 평가한 후보입니다.")
        if name_matches > 1 and comparison.name.status == "MATCH":
            reasons.append("같은 이름이 여러 인물에 일치 — 동명이인·소속 이동 중 누구인지 확인 필요")
        if len(ranked) > 3:
            reasons.append(f"후보가 {len(ranked)}명입니다. 표시되지 않은 인물은 인물 대장에서 직접 검색해 주세요.")
        reasons.append("이 중 누구인지 확인해 주세요. 신규 등록은 사람이 결정합니다.")
        candidates.append({"personId": comparison.person_id, "score": score, "reasons": reasons})
    result = {"resolutionId": f"ir_{uuid4().hex}", "mention": deepcopy(mention),
              "decision": "CANDIDATES" if candidates else "NEW", "personId": None,
              "candidates": candidates, "decidedBy": "MODEL", "modelVersion": model.version}
    validators()[2].validate(result)
    return result


def resolve_identities(mentions, persons):
    _validate_inputs(mentions, persons)
    model = None
    if os.environ.get("IDENTITY_CANDIDATE_MODE", "hybrid") == "hybrid":
        try:
            model = logistic.load_model()
        except (OSError, ValueError, KeyError, TypeError, OverflowError):
            logger.warning("인물 후보 모델을 불러올 수 없어 기존 규칙으로 처리합니다")
    results = []
    for mention in mentions:
        if model is None:
            results.append(_resolve_one(mention, persons))
            continue
        try:
            results.append(_resolve(mention, persons, model))
        except (ValueError, OverflowError):
            logger.warning("인물 후보 모델 추론 오류로 기존 규칙으로 처리합니다")
            results.append(_resolve_one(mention, persons))
    return results
