"""B 회의록 엔진. 메모·메일 텍스트 → meeting_note 초안."""
from app.minutes.engine import MinutesResult, build_meeting_note

__all__ = ["MinutesResult", "build_meeting_note"]
