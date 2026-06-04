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
    metadata = {
        "messages": [
            {
                "direction": message.direction,
                "senderName": message.senderName,
                "senderEmail": message.senderEmail,
                "receiverNames": message.receiverNames,
                "receiverEmails": message.receiverEmails,
                "sentAt": message.sentAt.isoformat() if message.sentAt else None,
            }
            for message in messages[:8]
        ],
        "existingSchedules": [
            {
                "scheduleId": schedule.scheduleId,
                "title": schedule.title,
                "scheduleDateTime": schedule.scheduleDateTime.isoformat()
                if schedule.scheduleDateTime else None,
            }
            for schedule in existing_schedules[:20]
        ],
        "recentSenderAnalyses": [
            {
                "analysisId": analysis.analysisId,
                "sourceId": analysis.sourceId,
                "title": analysis.title,
                "customerName": analysis.customerName,
                "productName": analysis.productName,
                "attendees": analysis.attendees,
                "amount": analysis.amount,
                "actionType": analysis.actionType,
                "scheduleText": analysis.scheduleText,
                "summary": analysis.summary,
            }
            for analysis in recent_sender_analyses[:5]
        ],
        "ruleResult": {
            "customerName": rule_result.customer_name,
            "productName": rule_result.product_name,
            "attendees": rule_result.attendees,
            "amount": rule_result.amount,
            "actionType": rule_result.action_type,
            "targetScheduleId": rule_result.target_schedule_id,
            "targetScheduleTitle": rule_result.target_schedule_title,
            "actionReason": rule_result.action_reason,
            "businessType": rule_result.business_type,
            "businessRelevanceScore": rule_result.business_relevance_score,
            "businessReason": rule_result.business_reason,
            "summary": rule_result.summary,
            "todoContent": rule_result.todo_content,
            "schedule": dump_model(rule_result.schedule) if rule_result.schedule else None,
        },
    }

    return f"""
Current-email-first rules:
- recentSenderAnalyses is only a memory hint for customerName/productName/context.
- Do not copy actionType from recentSenderAnalyses. Decide actionType from the current email first.
- If the current email says an existing, previous, or scheduled meeting should be moved, adjusted, delayed, changed, or replaced with another time, return actionType UPDATE.

너는 한국어 B2B 영업 메일을 분석하는 CRM AI 보조자다.
아래 메일과 규칙 기반 분석 결과를 보고, 더 자연스럽고 정확한 JSON 보정값만 반환해라.

중요 규칙:
- JSON 객체만 출력한다. 설명 문장, 마크다운, 코드블록은 금지한다.
- 없는 정보는 null로 둔다. 추측으로 고객사/제품/금액을 만들지 않는다.
- actionType은 CREATE, UPDATE, CANCEL, CONFIRM, UNKNOWN 중 하나만 사용한다.
- businessType은 SALES_ACTIVITY, NON_BUSINESS, UNKNOWN 중 하나만 사용한다.
- scheduleDateTime은 "YYYY-MM-DDTHH:mm:ss" 또는 null이다.
- 일정 의도가 없는 견적/자료/첨부 메일은 actionType UNKNOWN, schedule null로 둔다.
- UPDATE/CANCEL은 기존 일정에 영향을 주므로 targetScheduleTitle을 가능한 한 채운다.
- 고객사와 제품명에서 날짜, 시간, 인사말, "고객사와" 같은 조사, 긴 문장을 제거한다.

반환 JSON 필드:
{{
  "summary": string,
  "customerName": string|null,
  "contactName": string|null,
  "productName": string|null,
  "attendees": [string],
  "amount": number|null,
  "actionType": "CREATE"|"UPDATE"|"CANCEL"|"CONFIRM"|"UNKNOWN",
  "targetScheduleTitle": string|null,
  "actionReason": string,
  "businessType": "SALES_ACTIVITY"|"NON_BUSINESS"|"UNKNOWN",
  "businessRelevanceScore": number,
  "businessReason": string,
  "todoContent": string|null,
  "schedule": {{
    "title": string,
    "date": "YYYY-MM-DD",
    "time": "HH:mm",
    "participants": [string]
  }}|null
}}

메일:
{text[:6000]}

컨텍스트:
{json.dumps(metadata, ensure_ascii=False)}
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
