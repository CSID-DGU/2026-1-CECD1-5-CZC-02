"""POST /resumes/extract 요청·응답. 응답의 resumeProfile은 contracts/resume_profile.schema.json 형식 그대로다."""
from typing import Any, Dict, List, Literal, Optional

from pydantic import BaseModel, Field


class ResumeSourceRef(BaseModel):
    sourceId: str = Field(min_length=1)
    sourceType: Literal["RESUME_FILE", "MANUAL_INPUT"]
    fileName: Optional[str] = None


class ResumeExtractRequest(BaseModel):
    # 백엔드가 저장 전에 부르면 아직 PK가 없으므로 임시 ID. 저장 후 백엔드가 rp_<PK>로 바꾼다.
    profileId: str = Field(default="rp_draft", pattern=r"^rp_[0-9a-z_]+$")
    sourceRefs: List[ResumeSourceRef] = Field(default_factory=list)
    fileName: Optional[str] = Field(default=None, max_length=255)
    # .docx 파일 내용(base64). 파일 대신 text를 보내도 된다
    contentBase64: Optional[str] = None
    text: Optional[str] = None
    referenceDate: Optional[str] = Field(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$",
                                         description="진행 중·총 경력 계산 기준일. 비우면 오늘")
    mode: Literal["rule", "llm", "hybrid"] = "hybrid"


class ResumeDroppedItem(BaseModel):
    """검증에서 버린 LLM 항목. 디버깅·평가용이며 프로필에는 들어가지 않는다."""
    field: str
    text: Optional[str] = None
    evidence: Optional[str] = None
    reason: str


class ResumeExtractResponse(BaseModel):
    resumeProfile: Dict[str, Any]
    llmProvider: str                      # ollama, LLM을 안 썼거나 못 썼으면 rule
    llmModel: Optional[str] = None
    llmFallbackReason: Optional[str] = None
    droppedItems: List[ResumeDroppedItem] = Field(default_factory=list)
    elapsedMs: int = 0
