from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime


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
    todo_required: bool
    todo_content: Optional[str] = None
    schedule: Optional[ScheduleSchema] = None
    confidence: float  # 0.0 ~ 1.0


class AnalyzeRequest(BaseModel):
    """분석 요청 스키마 (Backend → AI)"""
    request_id: str
    user_id: int
    message: str
    created_at: str  # ISO8601: YYYY-MM-DDTHH:MM:SS


class AnalyzeResponse(BaseModel):
    """분석 응답 스키마 (AI → Backend)"""
    success: bool
    analysis_result: Optional[AnalysisResultSchema] = None
    error: Optional[str] = None
