"""회의록 초안 라우트 (B). 저장·수정·확정은 백엔드 minutes 도메인이 한다."""
from fastapi import APIRouter, HTTPException

from app.minutes import build_meeting_note
from app.minutes.schemas import MinutesRequest, MinutesResponse

router = APIRouter(tags=["minutes"])


@router.post("/minutes", response_model=MinutesResponse)
def create_minutes(request: MinutesRequest) -> MinutesResponse:
    """메모·메일 텍스트 → meeting_note 초안(DRAFT). LLM 호출이 동기라 일반 함수로 둔다(스레드풀에서 실행)."""
    if not request.text.strip():
        raise HTTPException(status_code=400, detail="text는 필수입니다.")

    result = build_meeting_note(request)
    return MinutesResponse(
        meetingNote=result.note,
        llmProvider=result.llm_provider,
        llmModel=result.llm_model,
        llmFallbackReason=result.fallback_reason,
        droppedItems=result.dropped_items,
    )
