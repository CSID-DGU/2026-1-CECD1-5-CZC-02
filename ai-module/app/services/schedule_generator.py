from typing import Dict, List, Optional

from app.schemas.response import ScheduleResponse


def generate_schedule(
    text: str,
    classified: Dict[str, List[str]],
    date: Optional[str],
    time: Optional[str],
) -> ScheduleResponse:
    project = classified["project"][0] if classified["project"] else None
    event = classified["event"][0] if classified["event"] else "일정"

    if project and event not in project:
        title = f"{project} {event}"
    elif project:
        title = project
    else:
        title = _fallback_title(text, event)

    return ScheduleResponse(
        title=title,
        date=date,
        time=time,
        participants=classified["people"],
    )


def _fallback_title(text: str, event: str) -> str:
    if event != "일정":
        return event
    return text[:30] if len(text) > 30 else text
