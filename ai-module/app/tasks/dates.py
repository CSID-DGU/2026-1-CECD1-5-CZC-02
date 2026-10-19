"""접촉일 기준 KST 날짜 계산. 불명확한 기간·조건은 날짜로 바꾸지 않는다."""

import calendar
import re
from datetime import date, datetime, timedelta


def resolve_due_date(text: str, occurred_at: str | None) -> str | None:
    # 조건부 약속이나 범위 표현에는 임의 대표 날짜를 부여하지 않는다.
    if re.search(r"(?:되면|끝나면|검토 후|교체 후|다음\s*달|첫\s*주|월초|월중)", text):
        return None
    explicit = re.findall(r"(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)", text)
    if explicit:
        try:
            values = {date(*map(int, parts)).isoformat() for parts in explicit}
        except ValueError:
            return None
        return values.pop() if len(values) == 1 else None
    if not occurred_at:
        return None
    base_time = datetime.fromisoformat(occurred_at)
    if base_time.tzinfo is not None:
        raise ValueError("occurredAt은 시간대 표기 없는 KST여야 합니다.")
    base = base_time.date()
    candidates = set()
    for token, offset in (("오늘", 0), ("내일", 1), ("모레", 2)):
        if token in text:
            candidates.add(base + timedelta(days=offset))
    for match in re.finditer(r"(?:(이번\s*주|다음\s*주|다다음\s*주)\s*)?([월화수목금토일])요일", text):
        week, weekday = match.groups()
        target = "월화수목금토일".index(weekday)
        if week:
            weeks = {"이번주": 0, "다음주": 1, "다다음주": 2}[week.replace(" ", "")]
            delta = target - base.weekday() + weeks * 7
        else:
            delta = (target - base.weekday()) % 7
        candidates.add(base + timedelta(days=delta))
    if "월말" in text:
        # '11월 말'을 이번 달 말로 오해하지 않는다.
        candidates.add(base.replace(day=calendar.monthrange(base.year, base.month)[1]))
    for month, day in re.findall(r"(?<!\d)(\d{1,2})월\s*(\d{1,2})일", text):
        try:
            target = date(base.year, int(month), int(day))
        except ValueError:
            return None
        # 연도가 생략된 과거 날짜는 다음 해라고 추측하지 않는다.
        if target < base:
            return None
        candidates.add(target)
    return next(iter(candidates)).isoformat() if len(candidates) == 1 else None
