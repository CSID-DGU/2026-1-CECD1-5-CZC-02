import re
from datetime import datetime, timedelta
from typing import Optional, Tuple


WEEKDAYS = ["월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"]


def extract_date(text: str) -> Optional[str]:
    """월/일, 요일, 오늘/내일/모레 표현을 추출합니다."""
    month_days = re.findall(r"(\d{1,2})월\s*(\d{1,2})일", text)
    if month_days:
        month, day = month_days[-1]
        return f"{int(month)}월 {int(day)}일"

    for weekday in WEEKDAYS:
        if weekday in text:
            return weekday

    for relative_date in ("오늘", "내일", "모레"):
        if relative_date in text:
            return relative_date

    iso_dates = re.findall(r"(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})", text)
    if iso_dates:
        year, month, day = iso_dates[-1]
        return f"{year}-{int(month):02d}-{int(day):02d}"

    return None


def extract_time(text: str) -> Optional[str]:
    """오전/오후가 붙은 시간과 일반 '3시' 형태를 추출합니다."""
    meridiem_times = re.findall(r"(오전|오후)\s*(\d{1,2})시(?:\s*(\d{1,2})분)?", text)
    if meridiem_times:
        meridiem, hour, minute = meridiem_times[-1]
        minute_text = f" {int(minute)}분" if minute else ""
        return f"{meridiem} {int(hour)}시{minute_text}"

    simple_times = re.findall(r"(?<!월\s)(\d{1,2})시(?:\s*(\d{1,2})분)?", text)
    if simple_times:
        hour, minute = simple_times[-1]
        minute_text = f" {int(minute)}분" if minute else ""
        return f"{int(hour)}시{minute_text}"

    colon_times = re.findall(r"(\d{1,2}):(\d{2})", text)
    if colon_times:
        hour, minute = colon_times[-1]
        return f"{int(hour)}:{minute}"

    return None


def extract_date_and_time(text: str) -> Tuple[Optional[str], Optional[str]]:
    return extract_date(text), extract_time(text)


def to_backend_datetime(date_text: Optional[str], time_text: Optional[str]) -> Optional[datetime]:
    """백엔드 LocalDateTime 역직렬화를 위한 ISO datetime으로 변환합니다."""
    if not date_text:
        return None

    date_value = _date_to_datetime(date_text)
    if not date_value:
        return None

    hour, minute = _parse_time(time_text)
    return date_value.replace(hour=hour, minute=minute, second=0, microsecond=0)


def _date_to_datetime(date_text: str) -> Optional[datetime]:
    today = datetime.now()

    if date_text == "오늘":
        return today
    if date_text == "내일":
        return today + timedelta(days=1)
    if date_text == "모레":
        return today + timedelta(days=2)

    if date_text in WEEKDAYS:
        target_weekday = WEEKDAYS.index(date_text)
        days_ahead = target_weekday - today.weekday()
        if days_ahead <= 0:
            days_ahead += 7
        return today + timedelta(days=days_ahead)

    month_day = re.match(r"(\d{1,2})월\s*(\d{1,2})일", date_text)
    if month_day:
        month = int(month_day.group(1))
        day = int(month_day.group(2))
        year = today.year
        candidate = datetime(year, month, day)
        if candidate.date() < today.date():
            candidate = datetime(year + 1, month, day)
        return candidate

    try:
        return datetime.fromisoformat(date_text)
    except ValueError:
        return None


def _parse_time(time_text: Optional[str]) -> Tuple[int, int]:
    if not time_text:
        return 0, 0

    meridiem_time = re.search(r"(오전|오후)\s*(\d{1,2})시(?:\s*(\d{1,2})분)?", time_text)
    if meridiem_time:
        meridiem, hour_text, minute_text = meridiem_time.groups()
        hour = int(hour_text)
        if meridiem == "오후" and hour != 12:
            hour += 12
        if meridiem == "오전" and hour == 12:
            hour = 0
        return hour, int(minute_text or 0)

    simple_time = re.search(r"(\d{1,2})시(?:\s*(\d{1,2})분)?", time_text)
    if simple_time:
        return int(simple_time.group(1)), int(simple_time.group(2) or 0)

    colon_time = re.search(r"(\d{1,2}):(\d{2})", time_text)
    if colon_time:
        return int(colon_time.group(1)), int(colon_time.group(2))

    return 0, 0
