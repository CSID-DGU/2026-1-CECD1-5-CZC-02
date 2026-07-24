"""Local Ollama integration for optional LLM analysis enhancement."""
import json
import re
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any, Dict, Iterable, Optional

from app.config import (
    OLLAMA_BASE_URL,
    OLLAMA_MODEL,
    OLLAMA_TEMPERATURE,
    OLLAMA_TIMEOUT_SECONDS,
)
from app.schemas.models import AnalysisResultSchema
from app.schemas.request import ExistingScheduleInfo, HistoricalAnalysisInfo, MessageItem


ALLOWED_ACTION_TYPES = {"CREATE", "UPDATE", "CANCEL", "CONFIRM", "UNKNOWN"}
ALLOWED_BUSINESS_TYPES = {"SALES_ACTIVITY", "NON_BUSINESS", "UNKNOWN"}


@dataclass
class OllamaAnalysisPatch:
    data: Dict[str, Any]


def request_llm_analysis_patch(
    text: str,
    messages: list[MessageItem],
    existing_schedules: list[ExistingScheduleInfo],
    recent_sender_analyses: list[HistoricalAnalysisInfo],
    rule_result: AnalysisResultSchema,
) -> OllamaAnalysisPatch:
    """Ask Ollama to improve semantic fields while keeping deterministic fallback outside."""
    prompt = build_analysis_prompt(text, messages, existing_schedules, recent_sender_analyses, rule_result)
    payload = {
        "model": OLLAMA_MODEL,
        "prompt": prompt,
        "stream": False,
        "format": "json",
        "options": {
            "temperature": OLLAMA_TEMPERATURE,
            "num_predict": 700,
        },
    }

    request = urllib.request.Request(
        f"{OLLAMA_BASE_URL.rstrip('/')}/api/generate",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=OLLAMA_TIMEOUT_SECONDS) as response:
            raw_body = response.read().decode("utf-8")
    except (urllib.error.URLError, TimeoutError) as exception:
        raise RuntimeError(f"Ollama request failed: {exception}") from exception

    try:
        response_payload = json.loads(raw_body)
    except json.JSONDecodeError as exception:
        raise RuntimeError("Ollama response envelope is not valid JSON.") from exception

    response_text = response_payload.get("response")
    if not isinstance(response_text, str) or not response_text.strip():
        raise RuntimeError("Ollama response field is empty.")

    patch_data = parse_json_object(response_text)
    return OllamaAnalysisPatch(data=normalize_patch(patch_data))


def build_analysis_prompt(
    text: str,
    messages: list[MessageItem],
    existing_schedules: list[ExistingScheduleInfo],
    recent_sender_analyses: list[HistoricalAnalysisInfo],
    rule_result: AnalysisResultSchema,
) -> str:
    # 규칙이 이미 뽑은 힌트(틀릴 수 있음)와 최근 동일 발신자 기억만 최소로 전달한다.
    hints = {
        "customerName": rule_result.customer_name,
        "productName": rule_result.product_name,
        "attendees": rule_result.attendees,
    }
    recent = [
        {"customerName": analysis.customerName, "productName": analysis.productName}
        for analysis in recent_sender_analyses[:3]
        if analysis.customerName or analysis.productName
    ]

    return f"""너는 한국어 B2B 영업 메일에서 핵심 정보를 뽑는 CRM 보조자다.
아래 메일을 읽고 지정된 6개 필드만 JSON으로 반환해라. (일정 액션·날짜·금액은 다른 모듈이 처리하므로 뽑지 않는다.)

규칙:
- JSON 객체만 출력한다. 설명/마크다운/코드블록 금지.
- 없는 정보는 null. 추측으로 만들지 않는다.
- customerName: 발신자 서명·인사말의 회사명. 예: "한빛소프트 정우성 팀장입니다" -> "한빛소프트". 접미사(고객사/기업/Systems) 없어도 회사명이면 추출.
- contactName: 상대 담당자(발신자) 한 명의 이름. 예: "정우성".
- attendees: 메일에 명시된 실제 사람 이름 배열. "저/제가/저희" 같은 본인 지칭은 이름이 아니므로 넣지 않되, 발신자 본인 이름이 서명에 있으면 포함. 예: "저와 이서연 과장" -> ["정우성","이서연"].
- productName: 논의되는 제품/서비스명. 날짜·조사·인사말 제거.
- scheduleTitle: 제품·주제 기반의 짧은 미팅 제목. 날짜·시간·요일은 넣지 않는다. 예: "재고관리 자동화 솔루션 데모 미팅". 일정 얘기가 없으면 null.
- summary: 고객사·제품·일정 중심의 한 줄 요약.

반환 JSON:
{{"customerName": string|null, "contactName": string|null, "productName": string|null, "attendees": [string], "scheduleTitle": string|null, "summary": string}}

예시)
메일: 안녕하세요, 마루상사 김도윤 부장입니다. 스마트 물류 관제 시스템 도입 상담을 6월 30일 오후 2시에 진행하고 싶습니다. 참석자는 저와 기술팀 박민수 대리입니다.
출력: {{"customerName": "마루상사", "contactName": "김도윤", "productName": "스마트 물류 관제 시스템", "attendees": ["김도윤", "박민수"], "scheduleTitle": "스마트 물류 관제 시스템 도입 상담", "summary": "마루상사 / 스마트 물류 관제 시스템 / 도입 상담"}}

규칙 추출 힌트(참고용, 틀릴 수 있음): {json.dumps(hints, ensure_ascii=False)}
최근 동일 발신자 기억: {json.dumps(recent, ensure_ascii=False)}

메일:
{text[:4000]}
""".strip()


def parse_json_object(value: str) -> Dict[str, Any]:
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", value, re.DOTALL)
        if not match:
            raise RuntimeError("Ollama response does not contain a JSON object.")
        parsed = json.loads(match.group(0))

    if not isinstance(parsed, dict):
        raise RuntimeError("Ollama response JSON is not an object.")
    return parsed


def dump_model(model: Any) -> Dict[str, Any]:
    if hasattr(model, "model_dump"):
        return model.model_dump()
    if hasattr(model, "dict"):
        return model.dict()
    return dict(model)


def normalize_patch(data: Dict[str, Any]) -> Dict[str, Any]:
    normalized = dict(data)

    action_type = normalized.get("actionType")
    if action_type not in ALLOWED_ACTION_TYPES:
        normalized.pop("actionType", None)

    business_type = normalized.get("businessType")
    if business_type not in ALLOWED_BUSINESS_TYPES:
        normalized.pop("businessType", None)

    score = normalized.get("businessRelevanceScore")
    if isinstance(score, (int, float)):
        normalized["businessRelevanceScore"] = max(0.0, min(1.0, float(score)))
    else:
        normalized.pop("businessRelevanceScore", None)

    amount = normalized.get("amount")
    if amount is not None and not isinstance(amount, int):
        try:
            normalized["amount"] = int(str(amount).replace(",", ""))
        except ValueError:
            normalized.pop("amount", None)

    attendees = normalized.get("attendees")
    if isinstance(attendees, str):
        normalized["attendees"] = [item.strip() for item in attendees.split(",") if item.strip()]
    elif attendees is not None and not isinstance(attendees, list):
        normalized.pop("attendees", None)

    schedule = normalized.get("schedule")
    if schedule is not None and not isinstance(schedule, dict):
        normalized.pop("schedule", None)

    return normalized


def first_text(data: Dict[str, Any], keys: Iterable[str]) -> Optional[str]:
    for key in keys:
        value = data.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return None
