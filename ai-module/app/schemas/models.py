from typing import Optional, List

from pydantic import BaseModel

from app.schemas.request import AnalyzeRequest
from app.schemas.response import AiErrorResponse, AnalyzeResponse, ScheduleAnalyzeResponse, ScheduleResponse


class ScheduleSchema(BaseModel):
    """일정 정보 스키마"""
    title: str
    date: str  # YYYY-MM-DD
    time: str  # HH:MM
    participants: Optional[List[str]] = None
    location: Optional[str] = None


class AnalysisResultSchema(BaseModel):
    """AI 분석 결과 스키마"""
    summary: str
    customer_name: Optional[str] = None
    contact_person: Optional[str] = None
    activity_type: str  # MEETING, CALL, EMAIL, TASK
    action_type: str = "UNKNOWN"  # CREATE, UPDATE, CANCEL, CONFIRM, UNKNOWN
    target_schedule_id: Optional[int] = None
    target_schedule_title: Optional[str] = None
    action_reason: Optional[str] = None
    todo_required: bool
    todo_content: Optional[str] = None
    schedule: Optional[ScheduleSchema] = None
    confidence: float  # 0.0 ~ 1.0


__all__ = [
    "AiErrorResponse",
    "AnalysisResultSchema",
    "AnalyzeRequest",
    "AnalyzeResponse",
    "ScheduleAnalyzeResponse",
    "ScheduleResponse",
    "ScheduleSchema",
]
