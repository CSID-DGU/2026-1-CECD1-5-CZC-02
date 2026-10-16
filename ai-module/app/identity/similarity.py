"""이름의 문자 편집 거리·유사도. 동일 인물 확률이나 일치 판정이 아니다."""

from dataclasses import dataclass
from functools import lru_cache

from app.identity.normalization import normalize_name


@dataclass(frozen=True)
class NameSimilarity:
    path: str
    observed: str
    registered: str
    edit_distance: int
    similarity: float


@lru_cache(maxsize=32768)
def _edit_distance(left: str, right: str) -> int:
    """문자 삽입·삭제·교체의 최소 횟수(각 비용 1). 자모나 발음은 비교하지 않는다."""
    if len(left) < len(right):
        left, right = right, left
    previous = list(range(len(right) + 1))
    for i, char_left in enumerate(left, start=1):
        current = [i]
        for j, char_right in enumerate(right, start=1):
            current.append(min(
                current[j - 1] + 1,
                previous[j] + 1,
                previous[j - 1] + (char_left != char_right),
            ))
        previous = current
    return previous[-1]


def compare_name_similarity(
    observed: str | None, sources: list[tuple[str, str | None]],
) -> tuple[NameSimilarity, ...]:
    """대표 이름·등록 별칭 각각과 비교한다. 비교 불가 값은 결과를 만들지 않는다.

    similarity = 1 - 편집 거리 / 두 이름 중 긴 쪽 길이.
    빈 값은 유사도 0이나 1로 바꾸지 않는다. 원문과 필드 경로를 함께 보존한다.
    """
    name = normalize_name(observed)
    if name is None:
        return ()
    results = []
    for path, raw in sources:
        other = normalize_name(raw)
        if other is None:
            continue
        distance = _edit_distance(name, other)
        results.append(NameSimilarity(
            path=path, observed=observed, registered=raw,
            edit_distance=distance,
            similarity=1 - distance / max(len(name), len(other)),
        ))
    return tuple(results)
