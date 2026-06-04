"""FastAPI 메인 앱"""
from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.encoders import jsonable_encoder
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from app.config import AI_ENGINE, OLLAMA_BASE_URL, OLLAMA_MODEL, SERVER_HOST, SERVER_PORT, BACKEND_URL
from app.routes.analyze import router as analyze_router
from app.schemas.models import AiErrorResponse

# FastAPI 앱 생성
app = FastAPI(
    title="SALESMAP AI Module",
    description="규칙 기반 메시지 분석 및 일정 추출 AI 모듈",
    version="1.0.0"
)

# CORS 설정 (Backend와 통신용)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 프로덕션에서는 구체적인 URL 지정
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 라우터 등록
app.include_router(analyze_router)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    request: Request,
    exception: RequestValidationError
) -> JSONResponse:
    error = AiErrorResponse(
        errorCode="AI_VALIDATION_ERROR",
        message="AI Module 요청 DTO 검증에 실패했습니다.",
        details={"errors": exception.errors()}
    )
    return JSONResponse(status_code=422, content=jsonable_encoder(error))


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exception: HTTPException) -> JSONResponse:
    error = AiErrorResponse(
        errorCode="AI_MODULE_ERROR",
        message=str(exception.detail),
        details={"statusCode": exception.status_code}
    )
    return JSONResponse(status_code=exception.status_code, content=jsonable_encoder(error))


@app.get("/")
async def root():
    """루트 엔드포인트"""
    return {
        "message": "AI module running",
        "docs": "/docs",
        "health": "/health"
    }


@app.on_event("startup")
async def startup_event():
    """앱 시작 시"""
    print("🚀 AI Module 시작")
    print(f"📌 서버: http://{SERVER_HOST}:{SERVER_PORT}")
    print(f"📌 문서: http://{SERVER_HOST}:{SERVER_PORT}/docs")
    print(f"📌 Backend: {BACKEND_URL}")
    print(f"📌 AI Engine: {AI_ENGINE}")
    if AI_ENGINE in {"ollama", "llm", "hybrid"}:
        print(f"📌 Ollama: {OLLAMA_BASE_URL} / {OLLAMA_MODEL}")


@app.on_event("shutdown")
async def shutdown_event():
    """앱 종료 시"""
    print("🛑 AI Module 종료")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=SERVER_HOST,
        port=SERVER_PORT,
        reload=True
    )
