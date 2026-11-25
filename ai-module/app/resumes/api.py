"""이력서 추출 라우트 (B). 저장·수정·확정·후보 풀은 백엔드 talent 도메인이 한다."""
import base64
import binascii
from datetime import date

from fastapi import APIRouter, HTTPException

from app.resumes.docx_reader import DocxError
from app.resumes.engine import extract_resume
from app.resumes.schemas import ResumeExtractRequest, ResumeExtractResponse

router = APIRouter(prefix="/resumes", tags=["resumes"])
MAX_FILE_BYTES = 10 * 1024 * 1024


@router.post("/extract", response_model=ResumeExtractResponse)
def extract(request: ResumeExtractRequest) -> ResumeExtractResponse:
    """이력서(.docx base64 또는 텍스트) → resume_profile 초안(DRAFT). LLM 호출이 동기라 일반 함수로 둔다."""
    has_file = bool(request.contentBase64 and request.contentBase64.strip())
    has_text = bool(request.text and request.text.strip())
    if has_file == has_text:
        raise HTTPException(status_code=422, detail="contentBase64(.docx)와 text 중 하나만 보내야 합니다.")
    file_name = (request.fileName or "").lower()
    if file_name.endswith(".doc") or file_name.endswith(".hwp"):
        raise HTTPException(status_code=422, detail="워드(.docx)만 읽을 수 있습니다. 워드에서 .docx로 다시 저장해 올려 주세요.")

    content = None
    if request.contentBase64:
        try:
            content = base64.b64decode(request.contentBase64, validate=True)
        except (binascii.Error, ValueError) as exception:
            raise HTTPException(status_code=422, detail="contentBase64가 올바른 base64가 아닙니다.") from exception
        if len(content) > MAX_FILE_BYTES:
            raise HTTPException(status_code=413, detail="파일이 너무 큽니다 (10MB 이하).")

    source_refs = [ref.model_dump() for ref in request.sourceRefs] or [{
        "sourceId": "src_draft",
        "sourceType": "RESUME_FILE" if content is not None else "MANUAL_INPUT",
        "fileName": request.fileName,
    }]
    try:
        result = extract_resume(
            content=content,
            text=request.text if not has_file else None,
            profile_id=request.profileId,
            source_refs=source_refs,
            reference_date=date.fromisoformat(request.referenceDate) if request.referenceDate else None,
            mode=request.mode,
        )
    except DocxError as exception:
        raise HTTPException(status_code=422, detail=str(exception)) from exception

    return ResumeExtractResponse(
        resumeProfile=result.profile,
        llmProvider=result.llm_provider,
        llmModel=result.llm_model,
        llmFallbackReason=result.fallback_reason,
        droppedItems=result.dropped_items,
        elapsedMs=result.elapsed_ms,
        sourceText=result.source_text,
    )
