from datetime import datetime
from typing import List, Literal, Optional

from pydantic import BaseModel, Field


class RequesterInfo(BaseModel):
    userId: Optional[int] = Field(default=None, gt=0)
    name: Optional[str] = None
    email: Optional[str] = None


class SourceGroupInfo(BaseModel):
    groupId: Optional[str] = None
    sourceType: Optional[Literal["EMAIL", "JANDI_MESSAGE", "MEETING_NOTE", "MANUAL_INPUT"]] = None
    title: Optional[str] = None
    deduplicated: Optional[bool] = None


class MessageItem(BaseModel):
    sourceId: Optional[int] = Field(default=None, gt=0)
    externalSourceId: Optional[str] = None
    direction: Optional[Literal["SENT", "RECEIVED", "UNKNOWN"]] = None
    senderName: Optional[str] = None
    senderEmail: Optional[str] = None
    receiverNames: List[str] = Field(default_factory=list)
    receiverEmails: List[str] = Field(default_factory=list)
    sentAt: Optional[datetime] = None
    content: str = Field(min_length=1)


class ExistingScheduleInfo(BaseModel):
    scheduleId: Optional[int] = Field(default=None, gt=0)
    title: Optional[str] = None
    scheduleDateTime: Optional[datetime] = None
    participants: List[str] = Field(default_factory=list)


class HistoricalAnalysisInfo(BaseModel):
    analysisId: Optional[int] = Field(default=None, gt=0)
    sourceId: Optional[int] = Field(default=None, gt=0)
    title: Optional[str] = None
    customerName: Optional[str] = None
    productName: Optional[str] = None
    attendees: Optional[str] = None
    amount: Optional[int] = None
    actionType: Optional[str] = None
    scheduleText: Optional[str] = None
    summary: Optional[str] = None


class AnalyzeRequest(BaseModel):
    """Backend 또는 단독 테스트에서 들어오는 분석 요청"""
    user_id: Optional[int] = Field(default=None, gt=0)
    message: Optional[str] = Field(default=None, min_length=1)

    requester: Optional[RequesterInfo] = None
    sourceGroup: Optional[SourceGroupInfo] = None
    messages: List[MessageItem] = Field(default_factory=list)
    existingSchedules: List[ExistingScheduleInfo] = Field(default_factory=list)
    recentSenderAnalyses: List[HistoricalAnalysisInfo] = Field(default_factory=list)
    analysisMode: Optional[Literal["rule", "ollama"]] = None

    sourceId: Optional[int] = Field(default=None, gt=0)
    sourceType: Optional[Literal["EMAIL", "JANDI_MESSAGE", "MEETING_NOTE", "MANUAL_INPUT"]] = None
    externalSourceId: Optional[str] = None
    title: Optional[str] = None
    content: Optional[str] = None
    collectedAt: Optional[datetime] = None

    def analysis_message(self) -> str:
        if self.messages:
            return "\n".join(message.content for message in self.messages)
        return self.message or self.content or ""

    def analysis_title(self) -> str:
        if self.sourceGroup and self.sourceGroup.title:
            return self.sourceGroup.title
        return self.title or ""


class ReplyDraftRequest(BaseModel):
    emailTitle: Optional[str] = None
    emailContent: str = Field(min_length=1)
    senderEmail: Optional[str] = None
    customerName: Optional[str] = None
    contactName: Optional[str] = None
    productName: Optional[str] = None
    attendees: Optional[str] = None
    actionType: Optional[str] = None
    scheduleInfo: Optional[str] = None
    summary: Optional[str] = None
    nextAction: Optional[str] = None
