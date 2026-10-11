"""LLM 기반 분석 의미 필드 보정 (고객사·담당자·제품·참석자·일정 제목·요약).

어떤 LLM을 쓸지는 app.llm.router가 결정한다(OpenAI → Ollama). 실패 시 호출부가 규칙 결과로 폴백한다.
"""
import json
from dataclasses import dataclass
from typing import Any, Dict

from app.config import OLLAMA_TEMPERATURE
from app.llm import LLMRequest, generate
from app.schemas.models import AnalysisResultSchema
from app.schemas.request import ExistingScheduleInfo, HistoricalAnalysisInfo, MessageItem


ALLOWED_ACTION_TYPES = {"CREATE", "UPDATE", "CANCEL", "CONFIRM", "UNKNOWN"}
ALLOWED_BUSINESS_TYPES = {"SALES_ACTIVITY", "NON_BUSINESS", "UNKNOWN"}

# OpenAI Structured Outputs용 스키마. strict 모드라 모든 필드가 required, 없으면 null.
ANALYSIS_PATCH_SCHEMA: Dict[str, Any] = {
    "type": "object",
    "additionalProperties": False,
    "required": ["customerName", "contactName", "productName", "attendees", "scheduleTitle", "summary"],
    "properties": {
        "customerName": {"type": ["string", "null"]},
        "contactName": {"type": ["string", "null"]},
        "productName": {"type": ["string", "null"]},
        "attendees": {"type": "array", "items": {"type": "string"}},
        "scheduleTitle": {"type": ["string", "null"]},
        "summary": {"type": "string"},
    },
}

# 고정 지시문 + 예시. 매 요청 동일하므로 프롬프트 캐시 대상이 되도록 앞쪽(system)에 둔다.
ANALYSIS_SYSTEM_PROMPT = """너는 한국어 B2B 영업 메일에서 핵심 정보를 뽑는 CRM 보조자다.
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
{"customerName": string|null, "contactName": string|null, "productName": string|null, "attendees": [string], "scheduleTitle": string|null, "summary": string}

예시)
메일: 안녕하세요, 마루상사 김도윤 부장입니다. 스마트 물류 관제 시스템 도입 상담을 6월 30일 오후 2시에 진행하고 싶습니다. 참석자는 저와 기술팀 박민수 대리입니다.
출력: {"customerName": "마루상사", "contactName": "김도윤", "productName": "스마트 물류 관제 시스템", "attendees": ["김도윤", "박민수"], "scheduleTitle": "스마트 물류 관제 시스템 도입 상담", "summary": "마루상사 / 스마트 물류 관제 시스템 / 도입 상담"}"""


@dataclass
class AnalysisPatch:
    data: Dict[str, Any]
    provider: str
    model: str
    fallback_reason: str | None = None


def request_llm_analysis_patch(
    text: str,
    messages: list[MessageItem],
    existing_schedules: list[ExistingScheduleInfo],
    recent_sender_analyses: list[HistoricalAnalysisInfo],
    rule_result: AnalysisResultSchema,
) -> AnalysisPatch:
    """의미 필드만 LLM으로 보정한다. 실패하면 LLMUnavailable을 던진다(호출부가 규칙 결과로 폴백)."""
    response = generate(LLMRequest(
        task="analysis",
        system=ANALYSIS_SYSTEM_PROMPT,
        user=build_analysis_user_prompt(text, recent_sender_analyses, rule_result),
        json_schema=ANALYSIS_PATCH_SCHEMA,
        schema_name="sales_email_patch",
        max_output_tokens=700,
        temperature=OLLAMA_TEMPERATURE,
    ))
    return AnalysisPatch(
        data=normalize_patch(response.data),
        provider=response.provider,
        model=response.model,
        fallback_reason=response.fallback_reason,
    )


def build_analysis_user_prompt(
    text: str,
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

    return f"""규칙 추출 힌트(참고용, 틀릴 수 있음): {json.dumps(hints, ensure_ascii=False)}
최근 동일 발신자 기억: {json.dumps(recent, ensure_ascii=False)}

메일:
{text[:4000]}""".strip()


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
