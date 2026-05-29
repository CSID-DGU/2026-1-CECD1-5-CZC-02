from datetime import datetime
from typing import Literal, Optional

from pydantic import BaseModel, Field


class AnalyzeRequest(BaseModel):
    """Backend 또는 단독 테스트에서 들어오는 분석 요청"""
    user_id: Optional[int] = Field(default=None, gt=0)
    message: Optional[str] = Field(default=None, min_length=1)

    sourceId: Optional[int] = Field(default=None, gt=0)
    sourceType: Optional[Literal["EMAIL", "JANDI_MESSAGE", "MEETING_NOTE", "MANUAL_INPUT"]] = None
    externalSourceId: Optional[str] = None
    title: Optional[str] = None
    content: Optional[str] = None
    collectedAt: Optional[datetime] = None

    def analysis_message(self) -> str:
        return self.message or self.content or ""

    def analysis_title(self) -> str:
        return self.title or ""
