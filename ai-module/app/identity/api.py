"""A의 읽기 전용 규칙 식별 API. 요청·응답의 인물 객체는 contracts를 따른다."""

from typing import Any

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, ConfigDict

from app.identity.engine import resolve_identities

router = APIRouter(prefix="/identity", tags=["identity"])


class IdentityRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    mentions: list[dict[str, Any]]
    persons: list[dict[str, Any]]


@router.post("/resolve", response_model=list[dict[str, Any]])
def resolve(request: IdentityRequest):
    """단서별 identity_resolution 배열 반환. 저장·등록·확정 화면 처리는 호출자가 담당한다."""
    try:
        return resolve_identities(request.mentions, request.persons)
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error
