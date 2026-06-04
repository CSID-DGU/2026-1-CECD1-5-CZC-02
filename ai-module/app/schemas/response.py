from datetime import datetime
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class ScheduleResponse(BaseModel):
    title: str
    date: Optional[str] = None
    time: Optional[str] = None
    participants: List[str] = Field(default_factory=list)


class AnalyzeResponse(BaseModel):
    """Spring Backend의 AiAnalysisResponse와 호환되는 응답"""
    customerName: Optional[str] = None
    contactName: Optional[str] = None
    productName: Optional[str] = None
    attendees: Optional[str] = None
    amount: Optional[int] = Field(default=None, ge=0)
    actionType: str = "UNKNOWN"
    targetScheduleId: Optional[int] = None
    targetScheduleTitle: Optional[str] = None
    actionReason: Optional[str] = None
    businessType: str = "UNKNOWN"
    businessRelevanceScore: float = Field(default=0.5, ge=0.0, le=1.0)
    businessReason: Optional[str] = None
    scheduleTitle: Optional[str] = None
    scheduleDateTime: Optional[datetime] = None
    todoContent: Optional[str] = None
    keyIssues: Optional[str] = None
    summary: str
    confidenceScore: float = Field(ge=0.0, le=1.0)


class ScheduleAnalyzeResponse(BaseModel):
    """AI Module 단독 테스트용 응답"""
    success: bool
    schedule: ScheduleResponse


class AiErrorResponse(BaseModel):
    errorCode: str
    message: str
    details: Optional[Dict[str, Any]] = None
