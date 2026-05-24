"""FastAPI 메인 앱"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import SERVER_HOST, SERVER_PORT, BACKEND_URL
from app.routes.analyze import router as analyze_router

# FastAPI 앱 생성
app = FastAPI(
    title="SALESMAP AI Module",
    description="메시지 분석 및 일정 추출 AI 모듈",
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


@app.get("/")
async def root():
    """루트 엔드포인트"""
    return {
        "message": "SALESMAP AI Module",
        "docs": "/docs",
        "health": "/api/health"
    }


@app.on_event("startup")
async def startup_event():
    """앱 시작 시"""
    print("🚀 AI Module 시작")
    print(f"📌 서버: http://{SERVER_HOST}:{SERVER_PORT}")
    print(f"📌 문서: http://{SERVER_HOST}:{SERVER_PORT}/docs")
    print(f"📌 Backend: {BACKEND_URL}")


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
