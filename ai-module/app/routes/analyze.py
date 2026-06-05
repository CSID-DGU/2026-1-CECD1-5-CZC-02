"""분석 요청 라우트"""
from fastapi import APIRouter, HTTPException

from app.schemas.request import AnalyzeRequest, ReplyDraftRequest
from app.schemas.response import AnalyzeResponse, ReplyDraftResponse, ScheduleAnalyzeResponse
from app.services.analyzer import analyze_message, analyze_schedule
from app.services.reply_draft import generate_reply_draft
from app.services.regex_parser import to_backend_datetime

router = APIRouter(tags=["analyze"])


@router.post("/analyze", response_model=AnalyzeResponse)
async def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    """Spring Backend의 HttpAiClient가 호출하는 분석 엔드포인트"""
    message = request.analysis_message()
    if not message.strip():
        raise HTTPException(status_code=400, detail="message 또는 content는 필수입니다.")

    analysis_result = await analyze_message(
        _build_analysis_text(request),
        request.messages,
        _requester_name(request),
        request.existingSchedules,
        request.analysisMode,
        request.recentSenderAnalyses,
    )
    schedule = analysis_result.schedule

    return AnalyzeResponse(
        customerName=analysis_result.customer_name,
        contactName=analysis_result.contact_person,
        productName=analysis_result.product_name,
        attendees=", ".join(analysis_result.attendees or []) or None,
        amount=analysis_result.amount,
        actionType=analysis_result.action_type,
        targetScheduleId=analysis_result.target_schedule_id,
        targetScheduleTitle=analysis_result.target_schedule_title,
        actionReason=analysis_result.action_reason,
        businessType=analysis_result.business_type,
        businessRelevanceScore=analysis_result.business_relevance_score,
        businessReason=analysis_result.business_reason,
        scheduleTitle=schedule.title if schedule else None,
        scheduleDateTime=to_backend_datetime(
            schedule.date if schedule else None,
            schedule.time if schedule else None,
        ),
        todoContent=analysis_result.todo_content,
        keyIssues=None,
        summary=analysis_result.summary,
        confidenceScore=analysis_result.confidence,
    )


@router.post("/analyze/schedule", response_model=ScheduleAnalyzeResponse)
async def analyze_schedule_only(request: AnalyzeRequest) -> ScheduleAnalyzeResponse:
    """AI Module 단독 확인용 일정 추출 엔드포인트"""
    message = request.analysis_message()
    if not message.strip():
        raise HTTPException(status_code=400, detail="message 또는 content는 필수입니다.")

    return ScheduleAnalyzeResponse(
        success=True,
        schedule=analyze_schedule(message, request.messages, _requester_name(request)),
    )


@router.post("/reply-draft", response_model=ReplyDraftResponse)
async def reply_draft(request: ReplyDraftRequest) -> ReplyDraftResponse:
    if not request.emailContent.strip():
        raise HTTPException(status_code=400, detail="emailContent is required.")

    return generate_reply_draft(request)


@router.post("/api/analyze", response_model=AnalyzeResponse, include_in_schema=False)
async def analyze_legacy(request: AnalyzeRequest) -> AnalyzeResponse:
    return await analyze(request)


@router.get("/health", tags=["health"])
@router.get("/api/health", tags=["health"], include_in_schema=False)
async def health_check():
    return {"status": "healthy"}


def _build_analysis_text(request: AnalyzeRequest) -> str:
    title = request.analysis_title()
    message = request.analysis_message()
    if title and title not in message:
        return f"{title} {message}"
    return message


def _requester_name(request: AnalyzeRequest) -> str | None:
    return request.requester.name if request.requester else None
