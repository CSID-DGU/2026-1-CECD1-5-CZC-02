"""C 독립 API. uvicorn app.tasks.api:app --port 8001 로 실행한다."""

from fastapi import APIRouter, Body, FastAPI, HTTPException, Query
from jsonschema import ValidationError

from .engine import extract_tasks

router = APIRouter(prefix="/tasks", tags=["tasks"])


@router.post("/extract")
def extract(note: dict = Body(...), assignee: str | None = Query(default=None, max_length=255)) -> list[dict]:
    """요청 본문은 meeting_note 계약, 응답은 task 계약 배열이다."""
    try:
        return extract_tasks(note, assignee=assignee)
    except (ValidationError, ValueError) as exc:
        message = exc.message if isinstance(exc, ValidationError) else str(exc)
        raise HTTPException(status_code=422, detail=message) from exc


app = FastAPI(title="C Task API")
app.include_router(router)


@app.get("/health")
def health():
    return {"status": "ok"}
