"""기간 읽기와 계산 (전부 규칙, LLM 안 씀).

- 기간 표기: 2019.03 ~ 2020.05 / 19.03-20.05 / 2019/03/01~2020/05/31 / 2019년 3월 ~ 현재 / 2019 03 – 2020 05 …
- 달 단위로 다룬다 (YYYY-MM). 연도만 있으면 달을 지어내지 않고 비운다.
- 총 경력: 겹친 기간은 한 번만 센다. 시작·끝 달을 모두 넣어 센다 (2019.03~2019.05 = 3개월).
- 투입 가능일: 진행 중 프로젝트 종료 달의 다음 달 1일.
"""
import re
from dataclasses import dataclass
from datetime import date
from typing import Iterable, List, Optional, Tuple

ONGOING_RE = re.compile(r"현재|재직\s*중|진행\s*중|계속|근무\s*중|투입\s*중|\bpresent\b", re.IGNORECASE)
# 4자리 연도: 점·빗금·하이픈·공백·'년'으로 달과 나뉜다. 일(day)은 있어도 버린다.
DATE4_RE = re.compile(
    r"(?<!\d)((?:19|20)\d{2})\s*(?:[./\-]|년|\s)\s*(\d{1,2})(?!\d)(?:\s*(?:[./\-]|월)?\s*(\d{1,2})(?!\d)\s*일?)?\s*월?"
)
# 2자리 연도: 공백 구분은 다른 숫자와 헷갈리므로 점·빗금·'년'만 허용
DATE2_RE = re.compile(r"(?<![\d.])(\d{2})\s*(?:[./]|년)\s*(\d{1,2})(?!\d)(?:\s*(?:[./]|월)\s*(\d{1,2})(?!\d)\s*일?)?\s*월?")
OPEN_END_RE = re.compile(r"[~∼〜\-–—]\s*$")


@dataclass(frozen=True)
class Period:
    start: Optional[str]      # YYYY-MM
    end: Optional[str]        # YYYY-MM, 진행 중이고 종료 예정이 없으면 None
    ongoing: bool

    def as_dict(self) -> dict:
        return {"start": self.start, "end": self.end, "ongoing": self.ongoing}


EMPTY_PERIOD = Period(None, None, False)


def month_text(year: int, month: int) -> Optional[str]:
    if not 1 <= month <= 12 or not 1900 <= year <= 2100:
        return None
    return f"{year:04d}-{month:02d}"


def expand_year(two_digits: int, reference: date) -> int:
    """두 자리 연도: 기준 연도 끝 두 자리 이하면 2000년대, 아니면 1900년대."""
    return 2000 + two_digits if two_digits <= reference.year % 100 else 1900 + two_digits


def find_months(text: str, reference: date) -> List[str]:
    """글 안의 날짜를 나온 순서대로 YYYY-MM으로 꺼낸다."""
    found: List[Tuple[int, str]] = []
    taken: List[Tuple[int, int]] = []
    for match in DATE4_RE.finditer(text):
        value = month_text(int(match.group(1)), int(match.group(2)))
        if value:
            found.append((match.start(), value))
            taken.append(match.span())
    for match in DATE2_RE.finditer(text):
        if any(start <= match.start() < end for start, end in taken):
            continue
        value = month_text(expand_year(int(match.group(1)), reference), int(match.group(2)))
        if value:
            found.append((match.start(), value))
    return [value for _, value in sorted(found)]


def parse_period(text: Optional[str], reference: date) -> Period:
    """기간 칸 하나를 읽는다. 날짜가 없으면 EMPTY_PERIOD."""
    if not text or not text.strip():
        return EMPTY_PERIOD
    months = find_months(text, reference)
    says_ongoing = bool(ONGOING_RE.search(text))
    if not months:
        return Period(None, None, says_ongoing)

    start = months[0]
    end = months[1] if len(months) >= 2 else None
    if end is None and not says_ongoing and OPEN_END_RE.search(text.strip()):
        says_ongoing = True   # "2024.03 ~" 처럼 끝이 비어 있으면 진행 중
    ongoing = says_ongoing or (end is not None and end > month_of(reference))
    return Period(start, end, ongoing)


def parse_single_month(text: Optional[str], reference: date) -> Optional[str]:
    months = find_months(text or "", reference)
    return months[0] if months else None


def month_of(day: date) -> str:
    return f"{day.year:04d}-{day.month:02d}"


def month_index(value: str) -> int:
    year, month = value.split("-")
    return int(year) * 12 + int(month) - 1


def period_bounds(period: Period, reference: date) -> Optional[Tuple[int, int]]:
    """계산에 쓸 (시작, 끝) 달 번호. 진행 중이고 끝이 없거나 기준일 뒤면 기준 달까지. 읽을 수 없으면 None."""
    if period.start is None:
        return None
    reference_month = month_index(month_of(reference))
    start = month_index(period.start)
    if period.end is None:
        if not period.ongoing:
            return None
        end = reference_month
    else:
        end = min(month_index(period.end), reference_month)
    if start > reference_month or end < start:
        return None
    return start, end


def total_months(periods: Iterable[Period], reference: date) -> Tuple[int, int]:
    """(겹친 기간을 한 번만 센 총 개월 수, 계산에 쓴 기간 수)."""
    bounds = sorted(b for b in (period_bounds(p, reference) for p in periods) if b is not None)
    total, used = 0, len(bounds)
    current_start: Optional[int] = None
    current_end: Optional[int] = None
    for start, end in bounds:
        if current_end is None or start > current_end + 1:
            if current_end is not None:
                total += current_end - current_start + 1
            current_start, current_end = start, end
        else:
            current_end = max(current_end, end)
    if current_end is not None:
        total += current_end - current_start + 1
    return total, used


def first_day_after(month: str) -> str:
    year, number = (int(part) for part in month.split("-"))
    year, number = (year + 1, 1) if number == 12 else (year, number + 1)
    return f"{year:04d}-{number:02d}-01"
