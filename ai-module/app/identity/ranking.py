"""A 내부 후보 검색·순위. 확률 점수나 외부 계약 응답을 생성하지 않는다."""

import math
from dataclasses import dataclass

from app.identity.candidates import ReviewCandidate
from app.identity.features import PersonComparison, compare_people
from app.identity.normalization import normalize_name


@dataclass(frozen=True)
class RankedCandidate:
    candidate: ReviewCandidate
    # 사전식 우선순위: 연락처 일치 개수, 정확 이름, 회사, 문자열 유사도.
    priority: tuple[int, int, int, float]

    @property
    def person_id(self) -> str:
        return self.candidate.person_id


def rank_comparisons(
    comparisons: list[PersonComparison], *, fuzzy_threshold: float | None = None,
) -> list[RankedCandidate]:
    """완료된 전체 대장 비교를 재사용한다. 임계값은 학습 데이터에서 별도 결정한다.

    유사 이름은 회사도 일치하고 정규화한 이름 양쪽이 4자 이상일 때만 후보에 넣는다.
    동점은 ID순으로 표시할 뿐 우열이 아니다. 전체 후보·경고를 보존한다.
    """
    if fuzzy_threshold is not None and (
        not math.isfinite(fuzzy_threshold) or not 0 < fuzzy_threshold <= 1
    ):
        raise ValueError("fuzzy_threshold는 0 초과 1 이하의 유한수여야 합니다")
    ranked = []
    for comparison in comparisons:
        supports = tuple(field for field in ("name", "email", "phone")
                         if getattr(comparison, field).status == "MATCH")
        fuzzy = False
        if not supports and fuzzy_threshold is not None and comparison.company.status == "MATCH":
            fuzzy = any(
                item.similarity >= fuzzy_threshold
                and len(normalize_name(item.observed) or "") >= 4
                and len(normalize_name(item.registered) or "") >= 4
                for item in comparison.name_similarities
            )
        if not supports and not fuzzy:
            continue
        if fuzzy:
            supports = ("name_similarity",)
        if comparison.company.status == "MATCH":
            supports += ("company",)
        priority = (
            int(comparison.email.status == "MATCH") + int(comparison.phone.status == "MATCH"),
            int(comparison.name.status == "MATCH"),
            int(comparison.company.status == "MATCH"),
            comparison.name_similarity if comparison.name_similarity is not None else 0.0,
        )
        ranked.append(RankedCandidate(ReviewCandidate(comparison, supports), priority))
    # ID는 순위 키(priority)에 포함하지 않아 동점 평가에서 우연한 정답 맞히기를 막는다.
    return sorted(ranked, key=lambda r: (tuple(-v for v in r.priority), r.person_id))


def rank_candidates(mention: dict, persons: list[dict], *, fuzzy_threshold: float | None = None) -> list[RankedCandidate]:
    return rank_comparisons(compare_people(mention, persons), fuzzy_threshold=fuzzy_threshold)
