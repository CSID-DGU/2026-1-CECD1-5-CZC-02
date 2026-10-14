"""POST /minutes 요청·응답. 응답의 meetingNote는 contracts/meeting_note.schema.json 형식 그대로다."""
from typing import Any, Dict, List, Literal, Optional

from pydantic import BaseModel, Field

SourceType = Literal["EMAIL", "JANDI_MESSAGE", "MEETING_NOTE", "MANUAL_INPUT", "VOICE_MEMO"]


class SourceRef(BaseModel):
    sourceId: str = Field(min_length=1)
    sourceType: SourceType


class ResolutionMention(BaseModel):
    name: Optional[str] = None


class IdentityResolutionRef(BaseModel):
    """A의 identity_resolution 중 회의록에 필요한 값만 받는다. 나머지 필드는 무시한다."""
    resolutionId: str
    mention: ResolutionMention
    personId: Optional[str] = None


class MinutesRequest(BaseModel):
    # 백엔드가 저장 전에 부르면 아직 PK가 없으므로 임시 ID. 저장 후 백엔드가 mn_<PK>로 바꾼다.
    noteId: str = Field(default="mn_draft", pattern=r"^mn_[0-9a-z_]+$")
    sourceRefs: List[SourceRef] = Field(min_length=1)
    text: str = Field(min_length=1)
    occurredAt: Optional[str] = Field(default=None, pattern=r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$")
    location: Optional[str] = None
    # 비우면 메모 모양을 보고 규칙으로 정한다 (engine.detect_mode)
    mode: Optional[Literal["FULL", "LIGHT"]] = None
    attendees: List[str] = Field(default_factory=list, description="사용자가 입력한 만난 사람 이름")
    identityResolutions: List[IdentityResolutionRef] = Field(default_factory=list)


class DroppedItem(BaseModel):
    """검증에서 버린 LLM 항목. 디버깅·평가용이며 회의록에는 들어가지 않는다."""
    field: str
    text: Optional[str] = None
    evidence: Optional[str] = None
    reason: str


class MinutesResponse(BaseModel):
    meetingNote: Dict[str, Any]
    llmProvider: str                      # ollama / openai, LLM을 못 썼으면 rule
    llmModel: Optional[str] = None
    llmFallbackReason: Optional[str] = None
    droppedItems: List[DroppedItem] = Field(default_factory=list)
