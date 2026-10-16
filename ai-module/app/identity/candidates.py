"""이름·연락처의 정확한 일치가 있는 인물을 내부 검토 후보로 모은다."""

from dataclasses import dataclass

from app.identity.features import PersonComparison, compare_people


@dataclass(frozen=True)
class ReviewCandidate:
    """순위·확률·판정이 없는 내부 후보. 외부 IdentityResolution 응답이 아니다."""

    comparison: PersonComparison
    supporting_fields: tuple[str, ...]

    @property
    def person_id(self) -> str:
        return self.comparison.person_id


def find_candidates(mention: dict, persons: list[dict]) -> list[ReviewCandidate]:
    """이름/등록 별칭 또는 연락처가 일치하는 인물을 대장 순서로 반환한다.

    회사만 일치한 사람은 제외한다. 충돌이 있는 후보도 근거·경고와 함께 남긴다.
    후보가 없다고 NEW를 판정하지 않으며, 결과를 임의로 3명까지 자르지 않는다.
    입력은 계약에 맞는 mention·Person 목록이며 원문을 변경하지 않는다.
    """
    candidates = []
    # 전체 비교를 먼저 하여 공유 연락처 경고가 후보 선별 과정에서 사라지지 않게 한다.
    for comparison in compare_people(mention, persons):
        supports = tuple(
            field for field in ("name", "email", "phone")
            if getattr(comparison, field).status == "MATCH"
        )
        if not supports:
            continue
        if comparison.company.status == "MATCH":
            supports += ("company",)
        candidates.append(ReviewCandidate(comparison, supports))
    return candidates
