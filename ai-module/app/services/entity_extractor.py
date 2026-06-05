"""Optional GLiNER based entity extraction for sales emails."""
from dataclasses import dataclass
from functools import lru_cache
from typing import Optional

from app.config import GLINER_ENABLED, GLINER_MODEL_NAME, GLINER_THRESHOLD


@dataclass(frozen=True)
class EntityExtractionResult:
    customer_name: Optional[str] = None
    product_name: Optional[str] = None
    attendees: tuple[str, ...] = ()


LABELS = [
    "customer company",
    "product or service",
    "person",
    "organization",
]


def extract_entities_with_gliner(text: str) -> Optional[EntityExtractionResult]:
    if not GLINER_ENABLED:
        return None

    try:
        model = get_gliner_model()
        entities = model.predict_entities(text[:4000], LABELS, threshold=GLINER_THRESHOLD)
    except Exception as exception:
        print(f"[GLINER] skipped. reason={exception}")
        return None

    customer = first_entity(entities, {"customer company", "organization"}, text)
    product = first_entity(entities, {"product or service"}, text)
    attendees = tuple(dedupe_entities(entity.get("text") for entity in entities if entity.get("label") == "person"))

    return EntityExtractionResult(
        customer_name=customer,
        product_name=product,
        attendees=attendees,
    )


@lru_cache(maxsize=1)
def get_gliner_model():
    from gliner import GLiNER

    print(f"[GLINER] loading model={GLINER_MODEL_NAME}")
    return GLiNER.from_pretrained(GLINER_MODEL_NAME)


def first_entity(entities: list[dict], labels: set[str], text: str) -> Optional[str]:
    candidates = [
        entity.get("text", "").strip()
        for entity in entities
        if entity.get("label") in labels and entity.get("text")
    ]
    for candidate in dedupe_entities(candidates):
        if is_reasonable_entity(candidate, text):
            return candidate
    return None


def dedupe_entities(values) -> list[str]:
    result = []
    seen = set()
    for value in values:
        cleaned = " ".join(str(value or "").strip(" .,").split())
        if cleaned and cleaned not in seen:
            seen.add(cleaned)
            result.append(cleaned)
    return result


def is_reasonable_entity(value: str, text: str) -> bool:
    if len(value) < 2:
        return False
    lowered = value.lower()
    blocked = {
        "gmail",
        "email",
        "meeting",
        "미팅",
        "회의",
        "일정",
        "안녕하세요",
        "감사합니다",
    }
    if lowered in blocked or value in blocked:
        return False
    return value in text
