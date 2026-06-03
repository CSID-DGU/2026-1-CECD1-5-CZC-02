import re
from typing import Dict, List, Optional

from app.schemas.response import ScheduleResponse


def generate_schedule(
    text: str,
    classified: Dict[str, List[str]],
    date: Optional[str],
    time: Optional[str],
) -> ScheduleResponse:
    event = classified["event"][0] if classified["event"] else "일정"
    title = _fallback_title(text, event)

    return ScheduleResponse(
        title=title,
        date=date,
        time=time,
        participants=classified["people"],
    )


def _fallback_title(text: str, event: str) -> str:
    crm_match = re.search(r"\b([A-Za-z][A-Za-z0-9 ]{1,30})\s*도입", text)
    if crm_match and "미팅" in text:
        return f"{crm_match.group(1).strip()} 도입 관련 미팅"

    if "온라인 미팅" in text:
        return "온라인 미팅"

    title_patterns = [
        r"([A-Za-z0-9가-힣 ]{2,50}?(?:제품 소개 미팅|도입 관련 미팅|도입 건|미팅|회의|상담|통화))",
        r"([A-Za-z0-9가-힣 ]{2,40}?관련 후속 논의)",
    ]

    for pattern in title_patterns:
        match = re.search(pattern, text)
        if match:
            title = _clean_title(match.group(1))
            if title:
                return title

    if event != "일정":
        return event

    return _clean_title(text[:30]) or "영업 일정"


def _clean_title(value: str) -> str:
    value = re.sub(r"\d{4}[-/.]\d{1,2}[-/.]\d{1,2}", "", value)
    value = re.sub(r"\d{4}년\s*\d{1,2}월\s*\d{1,2}일", "", value)
    value = re.sub(r"(오전|오후)\s*\d{1,2}시(?:\s*\d{1,2}분)?", "", value)
    value = re.sub(r"^\s*\d{1,2}\s*", "", value)
    value = re.sub(r"^\s*(에|에\s*)", "", value)
    return re.sub(r"\s+", " ", value).strip(" .,\n\t")
