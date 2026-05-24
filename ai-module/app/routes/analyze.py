"""분석 요청 라우트"""
from fastapi import APIRouter, HTTPException
from app.schemas.models import AnalyzeRequest, AnalyzeResponse
from app.services.analyzer import analyze_message

router = APIRouter(prefix="/api", tags=["analyze"])


@router.post("/analyze", response_model=AnalyzeResponse)
async def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    """
    메시지 분석 엔드포인트
    
    Backend에서 메시지를 받아 AI 분석 후 결과 반환
    """
    try:
        # 입력 검증
        if not request.message or not request.message.strip():
            raise HTTPException(status_code=400, detail="message는 필수입니다")
        
        if request.user_id <= 0:
            raise HTTPException(status_code=400, detail="user_id는 양수여야 합니다")
        
        # 분석 수행
        analysis_result = await analyze_message(request.message)
        
        return AnalyzeResponse(
            success=True,
            analysis_result=analysis_result
        )
        
    except HTTPException as e:
        return AnalyzeResponse(
            success=False,
            error=e.detail
        )
    except Exception as e:
        return AnalyzeResponse(
            success=False,
            error=f"분석 중 오류 발생: {str(e)}"
        )


@router.get("/health", tags=["health"])
async def health_check():
    """헬스 체크"""
    return {"status": "healthy"}
