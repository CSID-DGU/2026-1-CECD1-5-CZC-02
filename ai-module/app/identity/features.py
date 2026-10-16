"""mention과 Person의 관측 단서 비교. 내부 자료이며 외부 계약 응답이 아니다."""

from dataclasses import dataclass, replace
from typing import Callable, Literal

from app.identity.similarity import NameSimilarity, compare_name_similarity

from app.identity.normalization import (
    normalize_company, normalize_email, normalize_name, normalize_phone,
)


@dataclass(frozen=True)
class FieldComparison:
    # DIFFERENT는 알려진 표기가 다르다는 뜻이며 다른 사람이라는 판정이 아니다.
    status: Literal["MATCH", "DIFFERENT", "UNKNOWN"]
    observed: str | None
    registered: tuple[tuple[str, str], ...]
    matched_paths: tuple[str, ...]


@dataclass(frozen=True)
class PersonComparison:
    person_id: str
    name: FieldComparison
    email: FieldComparison
    phone: FieldComparison
    company: FieldComparison
    warnings: tuple[str, ...] = ()
    name_similarities: tuple[NameSimilarity, ...] = ()

    @property
    def name_similarity(self) -> float | None:
        """등록 표기와의 최대 문자열 유사도. 비교할 수 없으면 None."""
        return max((item.similarity for item in self.name_similarities), default=None)


def _compare(observed, sources, normalizer: Callable) -> FieldComparison:
    value = normalizer(observed)
    known = [(path, raw, normalizer(raw)) for path, raw in sources]
    known = [(path, raw, normalized) for path, raw, normalized in known if normalized is not None]
    matches = tuple(path for path, _, normalized in known if value is not None and value == normalized)
    status = "UNKNOWN" if value is None or not known else "MATCH" if matches else "DIFFERENT"
    return FieldComparison(status, observed, tuple((path, raw) for path, raw, _ in known), matches)


def compare_person(mention: dict, person: dict) -> PersonComparison:
    """계약에 맞는 mention·Person을 읽기 전용으로 비교한다."""
    name_sources = [("name", person["name"])] + [
        (f"nameVariants[{i}]", raw) for i, raw in enumerate(person.get("nameVariants", []))
    ]
    company_sources = [("company", person.get("company"))] + [
        (f"companyHistory[{i}].company", item["company"])
        for i, item in enumerate(person.get("companyHistory", []))
    ]
    result = PersonComparison(
        person_id=person["personId"],
        name=_compare(mention.get("name"), name_sources, normalize_name),
        name_similarities=compare_name_similarity(mention.get("name"), name_sources),
        email=_compare(mention.get("email"), [(f"emails[{i}]", raw) for i, raw in enumerate(person.get("emails", []))], normalize_email),
        phone=_compare(mention.get("phone"), [(f"phones[{i}]", raw) for i, raw in enumerate(person.get("phones", []))], normalize_phone),
        company=_compare(mention.get("company"), company_sources, normalize_company),
    )
    warnings = []
    if result.name.status == "DIFFERENT" and "MATCH" in (result.email.status, result.phone.status):
        warnings.append("CONTACT_MATCH_NAME_DIFFERS")
    return replace(result, warnings=tuple(warnings))


def compare_people(mention: dict, persons: list[dict]) -> list[PersonComparison]:
    """전체를 입력 순서로 비교하고 공유 연락처·이메일/전화 대상 충돌을 확인한다."""
    ids = [person["personId"] for person in persons]
    if len(ids) != len(set(ids)):
        raise ValueError("personId가 중복된 인물 대장입니다")
    results = [compare_person(mention, person) for person in persons]
    email_ids = {r.person_id for r in results if r.email.status == "MATCH"}
    phone_ids = {r.person_id for r in results if r.phone.status == "MATCH"}
    # 공통 인물이 하나라도 있으면 대상을 단정하지 않는다. 공유 연락처 경고는 별도다.
    disagreement = bool(email_ids and phone_ids and email_ids.isdisjoint(phone_ids))
    annotated = []
    for result in results:
        warnings = list(result.warnings)
        if result.person_id in email_ids and len(email_ids) > 1:
            warnings.append("SHARED_EMAIL")
        if result.person_id in phone_ids and len(phone_ids) > 1:
            warnings.append("SHARED_PHONE")
        if disagreement and result.person_id in email_ids | phone_ids:
            warnings.append("CONTACT_TARGETS_DISAGREE")
        annotated.append(replace(result, warnings=tuple(warnings)))
    return annotated
