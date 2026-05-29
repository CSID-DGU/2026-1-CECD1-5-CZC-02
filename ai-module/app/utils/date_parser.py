"""날짜/시간 파싱 유틸"""
import re
from datetime import datetime, timedelta
from typing import Optional, Tuple


def extract_date_and_time(text: str) -> Tuple[Optional[str], Optional[str]]:
    """
    텍스트에서 날짜/시간 추출
    Returns: (date: YYYY-MM-DD, time: HH:MM)
    """
    date = _extract_date(text)
    time = _extract_time(text)
    return date, time


def _extract_date(text: str) -> Optional[str]:
    """
    날짜 추출
    지원 패턴:
    - "2026-05-15", "2026/05/15"
    - "5월 15일", "5월15일"
    - "내일", "모레"
    - "금요일", "월요일" 등
    """
    today = datetime.now()
    
    # 절대 날짜: YYYY-MM-DD, YYYY/MM/DD
    match = re.search(r'(\d{4})[-/](\d{1,2})[-/](\d{1,2})', text)
    if match:
        year, month, day = match.groups()
        return f"{year}-{int(month):02d}-{int(day):02d}"
    
    # 상대 날짜: "내일", "모레"
    if "내일" in text:
        tomorrow = today + timedelta(days=1)
        return tomorrow.strftime("%Y-%m-%d")
    if "모레" in text:
        day_after = today + timedelta(days=2)
        return day_after.strftime("%Y-%m-%d")
    if "오늘" in text:
        return today.strftime("%Y-%m-%d")
    
    # 요일: "금요일", "월요일" 등
    weekdays = {
        "월요일": 0, "화요일": 1, "수요일": 2,
        "목요일": 3, "금요일": 4, "토요일": 5, "일요일": 6
    }
    for weekday_name, weekday_num in weekdays.items():
        if weekday_name in text:
            # 다음 해당 요일 찾기
            days_ahead = weekday_num - today.weekday()
            if days_ahead <= 0:
                days_ahead += 7
            target_date = today + timedelta(days=days_ahead)
            return target_date.strftime("%Y-%m-%d")
    
    # 월/일: "5월 15일", "5월15일"
    match = re.search(r'(\d{1,2})월\s*(\d{1,2})일', text)
    if match:
        month, day = match.groups()
        year = today.year
        # 이미 지난 월이면 내년
        if int(month) < today.month or (int(month) == today.month and int(day) < today.day):
            year += 1
        return f"{year}-{int(month):02d}-{int(day):02d}"
    
    return None


def _extract_time(text: str) -> Optional[str]:
    """
    시간 추출
    지원 패턴:
    - "15:30", "3:30"
    - "오후 3시", "오전 3시"
    - "3시 30분", "15시" 등
    """
    # 절대 시간: HH:MM, H:MM
    match = re.search(r'(\d{1,2}):(\d{2})', text)
    if match:
        hour, minute = match.groups()
        return f"{int(hour):02d}:{minute}"
    
    # 한글 시간: "오후 3시 30분", "오전 3시"
    match = re.search(r'(오전|오후)\s*(\d{1,2})시(?:\s*(\d{1,2})분)?', text)
    if match:
        period, hour, minute = match.groups()
        hour = int(hour)
        if period == "오후" and hour != 12:
            hour += 12
        elif period == "오전" and hour == 12:
            hour = 0
        minute = int(minute) if minute else 0
        return f"{hour:02d}:{minute:02d}"
    
    # 시간만: "3시", "15시" (오전/오후 표기 없음 - 기본 해석)
    match = re.search(r'(\d{1,2})시(?!\s*\d)', text)
    if match:
        hour = int(match.group(1))
        return f"{hour:02d}:00"
    
    return None
