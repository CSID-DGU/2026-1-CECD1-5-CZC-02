"""Rule-based analysis orchestration for sales activity emails."""
import re
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, Iterable, List, Optional, Tuple
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from app.config import AI_ENGINE, TIMEZONE
from app.schemas.models import AnalysisResultSchema, ScheduleSchema
from app.schemas.request import ExistingScheduleInfo, HistoricalAnalysisInfo, MessageItem
from app.schemas.response import ScheduleResponse
from app.services.entity_extractor import extract_entities_with_gliner
from app.services.embedding_classifier import EmbeddingClassification, classify_with_embeddings
from app.services.llm_analysis import request_llm_analysis_patch


SCHEDULE_WORDS = ("미팅", "회의", "상담", "통화", "일정")
CREATE_WORDS = (
    "일정 등록 부탁",
    "일정 등록",
    "미팅을 진행하고 싶",
    "미팅 요청",
    "일정 생성 요청",
    "미팅을 잡",
    "일정을 잡",
    "가능하실까요",
    "진행하면 좋겠습니다",
)
UPDATE_WORDS = ("변경", "연기", "앞당", "시간을 바", "날짜를 바", "조율")
CANCEL_WORDS = ("취소", "진행하지 않습니다", "진행하지 않겠습니다")
# 바 '확정'은 "확정된 단계는 아니라"처럼 부정 문맥에 오매칭되므로 확약형 표현만 인정한다.
CONFIRM_WORDS = (
    "확정하겠습니다",
    "확정합니다",
    "확정할게요",
    "확정했습니다",
    "확정입니다",
    "확정으로 진행",
    "그대로 진행",
    "확인했습니다",
    "참석 가능",
    "예정대로 진행",
    "진행하겠습니다",
)
GENERAL_WORDS = ("견적서", "첨부", "자료", "소개 자료", "기능 설명서", "검토 후", "문의사항")
SALES_BUSINESS_WORDS = (
    "고객사",
    "고객",
    "미팅",
    "회의",
    "상담",
    "통화",
    "일정",
    "견적",
    "견적서",
    "제품",
    "서비스",
    "솔루션",
    "도입",
    "계약",
    "제안서",
    "자료",
    "검토",
    "금액",
    "비용",
    "참석자",
    "후속",
    "문의",
    "sales",
    "crm",
    "platform",
    "solution",
    "automation",
    "analytics",
)
NON_BUSINESS_WORDS = (
    "facebook",
    "youtube",
    "릴스",
    "추천:",
    "상태 업데이트",
    "뉴스레터",
    "광고",
    "프로모션",
    "쿠폰",
    "할인",
    "구독",
    "인증번호",
    "보안 알림",
    "자동 발송",
    "수신 거부",
    "unsubscribe",
)
NON_BUSINESS_SENDERS = (
    "facebookmail.com",
    "youtube.com",
    "noreply",
    "no-reply",
    "notification",
    "notifications",
    "newsletter",
    "marketing@",
    "ad@",
    "ads@",
)


def analyze_schedule(
    message: str,
    messages: Optional[List[MessageItem]] = None,
    requester_name: Optional[str] = None,
    reference_time: Optional[datetime] = None,
) -> ScheduleResponse:
    text = normalize(message)
    date, time = extract_date_and_time(text, reference_time)
    attendees = select_participants(text, messages or [], requester_name)

    return ScheduleResponse(
        title=extract_schedule_title(text) or "영업 일정",
        date=date,
        time=time,
        participants=attendees,
    )


async def analyze_message(
    message: str,
    messages: Optional[List[MessageItem]] = None,
    requester_name: Optional[str] = None,
    existing_schedules: Optional[List[ExistingScheduleInfo]] = None,
    analysis_mode: Optional[str] = None,
    recent_sender_analyses: Optional[List[HistoricalAnalysisInfo]] = None,
    reference_time: Optional[datetime] = None,
) -> AnalysisResultSchema:
    """reference_time: 상대 날짜("다음 주 화요일")를 해석할 기준 시각. 보통 메일 발송 시각."""
    text = normalize(message)
    embedding_result = classify_with_embeddings(text)
    entity_result = extract_entities_with_gliner(text)
    schedule_response = analyze_schedule(text, messages, requester_name, reference_time)
    action_type, action_reason = classify_action_type(text)
    action_type, action_reason = apply_rule_action_corrections(action_type, action_reason, text)
    action_type, action_reason = merge_embedding_action(action_type, action_reason, embedding_result, text)
    action_type, action_reason = suppress_non_schedule_action(action_type, action_reason, text)
    recent_context = recent_sender_analyses or []
    customer_name = (
        extract_customer(text)
        or (entity_result.customer_name if entity_result else None)
        or first_recent_value(recent_context, "customerName")
    )
    product_name = (
        extract_product(text)
        or (entity_result.product_name if entity_result else None)
        or first_recent_value(recent_context, "productName")
    )
    amount = extract_amount(text)
    attendees = select_participants(text, messages or [], requester_name)
    if not attendees and entity_result and entity_result.attendees:
        attendees = list(entity_result.attendees)
    business_type, business_score, business_reason = classify_business_email(
        text,
        messages or [],
        action_type,
        customer_name,
        product_name,
        amount,
    )
    business_type, business_score, business_reason = merge_embedding_business(
        business_type,
        business_score,
        business_reason,
        embedding_result,
        action_type,
    )
    action_type, action_reason = apply_reschedule_request_guard(action_type, action_reason, text)

    target_schedule = None
    if action_type in {"UPDATE", "CANCEL", "CONFIRM"}:
        target_schedule = select_target_schedule(text, schedule_response, existing_schedules or [])

    has_schedule_datetime = bool(schedule_response.date and schedule_response.time)
    should_include_schedule = (
        action_type in {"CREATE", "UPDATE", "CANCEL", "CONFIRM"}
        and (has_schedule_datetime or action_type in {"UPDATE", "CANCEL", "CONFIRM"})
    )

    schedule = None
    if should_include_schedule:
        schedule = ScheduleSchema(
            title=schedule_response.title,
            date=schedule_response.date or "",
            time=schedule_response.time or "",
            participants=attendees,
            location=None,
        )

    rule_result = AnalysisResultSchema(
        summary=build_summary(action_type, schedule_response, text, customer_name, product_name, amount, attendees),
        customer_name=customer_name,
        contact_person=first_contact(attendees),
        product_name=product_name,
        attendees=attendees,
        amount=amount,
        activity_type=classify_activity_type(text),
        action_type=action_type,
        target_schedule_id=target_schedule.scheduleId if target_schedule else None,
        target_schedule_title=target_schedule.title if target_schedule else None,
        action_reason=action_reason,
        business_type=business_type,
        business_relevance_score=business_score,
        business_reason=business_reason,
        todo_required=action_type in {"CREATE", "UPDATE", "CANCEL"} and schedule is not None,
        todo_content=build_todo_content(action_type, schedule_response, product_name),
        schedule=schedule,
        confidence=0.85,
    )
    rule_result = apply_final_safety_guards(rule_result, text)

    effective_engine = (analysis_mode or AI_ENGINE).lower()
    # 비영업(NON_BUSINESS)으로 규칙 판정된 메일은 LLM이 뽑을 의미 정보(고객사/제품 등)가 없고,
    # LLM은 영업/비영업 판정을 바꾸지도 않으므로 무거운 LLM 단계를 건너뛴다(노이즈 메일 처리 대폭 가속).
    if effective_engine in {"ollama", "llm", "hybrid"} and rule_result.business_type != "NON_BUSINESS":
        return enhance_with_llm(
            text,
            messages or [],
            existing_schedules or [],
            recent_context,
            rule_result,
        )

    return rule_result


def classify_activity_type(text: str) -> str:
    if any(word in text for word in ("미팅", "회의", "상담", "만남")):
        return "MEETING"
    if any(word in text for word in ("통화", "전화")):
        return "CALL"
    if any(word in text for word in ("메일", "이메일", "첨부", "견적서", "자료")):
        return "EMAIL"
    return "TASK"


def enhance_with_llm(
    text: str,
    messages: List[MessageItem],
    existing_schedules: List[ExistingScheduleInfo],
    recent_sender_analyses: List[HistoricalAnalysisInfo],
    rule_result: AnalysisResultSchema,
) -> AnalysisResultSchema:
    try:
        patch = request_llm_analysis_patch(text, messages, existing_schedules, recent_sender_analyses, rule_result)
    except Exception as exception:
        print(f"[AI_ENGINE] LLM enhancement skipped. reason={exception}")
        return copy_analysis_result(rule_result, llm_provider="rule", llm_fallback_reason=str(exception))

    try:
        merged_result = merge_llm_patch(rule_result, patch.data, text)
        corrected = apply_precision_action_corrections(text, merged_result, rule_result)
    except Exception as exception:
        print(f"[AI_ENGINE] LLM patch merge failed. reason={exception}")
        return copy_analysis_result(rule_result, llm_provider="rule", llm_fallback_reason=f"merge failed: {exception}")

    return copy_analysis_result(
        corrected,
        llm_provider=patch.provider,
        llm_model=patch.model,
        llm_fallback_reason=patch.fallback_reason,
    )


def resolve_schedule_title(
    llm_title: Optional[str],
    product_name: Optional[str],
    text: str,
    fallback: Optional[str],
) -> Optional[str]:
    """LLM 제목에 날짜/시간이 섞이면 제품명 기반으로 깔끔하게 재구성한다."""
    date_markers = (
        "다음 주", "이번 주", "오늘", "내일", "모레", "오전", "오후",
        "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일",
    )

    def has_date(value: Optional[str]) -> bool:
        if not value:
            return False
        return (
            any(marker in value for marker in date_markers)
            or bool(re.search(r"\d+\s*시", value))
            or bool(re.search(r"\d{1,2}\s*월", value))
        )

    if llm_title and not has_date(llm_title):
        return llm_title

    if product_name:
        if "데모" in text:
            kind = "데모 미팅"
        elif "상담" in text:
            kind = "상담"
        elif "회의" in text:
            kind = "회의"
        else:
            kind = "관련 미팅"
        return f"{product_name} {kind}"

    return fallback


def merge_llm_patch(rule_result: AnalysisResultSchema, patch: Dict[str, Any], text: str = "") -> AnalysisResultSchema:
    """LLM은 의미 필드(고객사/제품/담당자/참석자/제목/요약)만 보정한다.

    액션 분류, 날짜/시간, 금액, businessType 같은 구조적 필드는 규칙이 더 안정적이므로
    규칙 결과를 그대로 유지하고 LLM이 덮어쓰지 못하게 한다.
    """
    attendees = patch.get("attendees")
    if not isinstance(attendees, list):
        attendees = rule_result.attendees or []
    attendees = dedupe([str(item).strip() for item in attendees if item and str(item).strip()])
    if not attendees:
        attendees = rule_result.attendees or []

    customer_name = text_or_none(patch.get("customerName")) or rule_result.customer_name
    product_name = text_or_none(patch.get("productName")) or rule_result.product_name
    contact_person = text_or_none(patch.get("contactName")) or first_contact(attendees) or rule_result.contact_person

    # 일정: 규칙이 뽑은 날짜/시간은 유지하고, LLM이 뽑은 제목·참석자만 반영한다.
    schedule = rule_result.schedule
    if schedule is not None:
        schedule = ScheduleSchema(
            title=resolve_schedule_title(
                text_or_none(patch.get("scheduleTitle")), product_name, text, schedule.title
            ),
            date=schedule.date,
            time=schedule.time,
            participants=attendees or schedule.participants,
            location=schedule.location,
        )

    # 요약은 보정된 최종 필드로 다시 구성해 화면 표시값과 항상 일치시킨다.
    schedule_for_summary = ScheduleResponse(
        title=(schedule.title if schedule else ""),
        date=(schedule.date if schedule else None),
        time=(schedule.time if schedule else None),
        participants=attendees,
    )
    summary = build_summary(
        rule_result.action_type,
        schedule_for_summary,
        text,
        customer_name,
        product_name,
        rule_result.amount,
        attendees,
    )

    return AnalysisResultSchema(
        summary=summary,
        customer_name=customer_name,
        contact_person=contact_person,
        product_name=product_name,
        attendees=attendees,
        amount=rule_result.amount,
        activity_type=rule_result.activity_type,
        action_type=rule_result.action_type,
        target_schedule_id=rule_result.target_schedule_id,
        target_schedule_title=rule_result.target_schedule_title,
        action_reason=rule_result.action_reason,
        business_type=rule_result.business_type,
        business_relevance_score=rule_result.business_relevance_score,
        business_reason=rule_result.business_reason,
        todo_required=rule_result.todo_required,
        todo_content=rule_result.todo_content,
        schedule=schedule,
        confidence=max(rule_result.confidence, 0.9),
    )


def build_llm_schedule(
    raw_schedule: Any,
    rule_result: AnalysisResultSchema,
    action_type: str,
) -> Optional[ScheduleSchema]:
    if isinstance(raw_schedule, dict):
        title = text_or_none(raw_schedule.get("title"))
        date = text_or_none(raw_schedule.get("date"))
        time = text_or_none(raw_schedule.get("time"))
        participants = raw_schedule.get("participants")
        if isinstance(participants, str):
            participants = [item.strip() for item in participants.split(",") if item.strip()]
        if not isinstance(participants, list):
            participants = []

        if title and date and time:
            return ScheduleSchema(
                title=title,
                date=date,
                time=time,
                participants=dedupe([str(item) for item in participants if item]),
                location=None,
            )

    if action_type in {"CREATE", "UPDATE", "CANCEL", "CONFIRM"}:
        return rule_result.schedule
    return None


# LLM(특히 소형 모델)이 값 대신 문자열로 뱉는 무효 토큰. 이런 값은 기억 fallback이 동작하도록 None 처리한다.
_NULL_TOKENS = {"null", "none", "n/a", "na", "nil", "undefined", "-", "없음", "해당 없음"}


def text_or_none(value: Any) -> Optional[str]:
    if isinstance(value, str) and value.strip():
        cleaned = value.strip()
        if cleaned.lower() in _NULL_TOKENS:
            return None
        return cleaned
    return None


def apply_precision_action_corrections(
    text: str,
    result: AnalysisResultSchema,
    rule_result: AnalysisResultSchema,
) -> AnalysisResultSchema:
    if result.action_type == "CANCEL":
        return result

    if looks_like_schedule_update(text):
        schedule = result.schedule or rule_result.schedule
        schedule_title = (
            result.target_schedule_title
            or (schedule.title if schedule else None)
            or rule_result.target_schedule_title
        )
        reason = append_reason(
            result.action_reason,
            "정밀 분석: 기존 일정 조정/변경 문맥이 포함되어 일정 변경으로 판단했습니다.",
        )
        todo_content = f"{schedule_title} 일정 변경 확인" if schedule_title else "일정 변경 확인"

        return copy_analysis_result(
            result,
            action_type="UPDATE",
            target_schedule_title=schedule_title,
            action_reason=reason,
            todo_required=schedule is not None,
            todo_content=todo_content,
            schedule=schedule,
            confidence=max(result.confidence, 0.92),
        )

    return result


def looks_like_schedule_update(text: str) -> bool:
    if "취소" in text:
        return False

    # 이미 잡힌 일정을 '옮기려는' 재조정 의도가 분명한 표현만 인정한다.
    # (단순히 '기존 WMS 연동'처럼 '기존'이 들어갔다고 일정 변경으로 보지 않는다.)
    reschedule_phrases = (
        "일정 변경",
        "일정을 변경",
        "일정 조정",
        "일정을 조정",
        "시간 변경",
        "시간을 변경",
        "시간 조정",
        "시간을 조정",
        "날짜 변경",
        "날짜를 변경",
        "날짜 조정",
        "미팅 변경",
        "미팅을 변경",
        "회의 변경",
        "상담 변경",
        "변경 가능",
        "조정 가능",
        "변경 부탁",
        "조정 부탁",
        "변경할 수 있",
        "조정할 수 있",
        "변경해도",
        "변경 요청",
        "연기",
        "앞당",
        "미뤄",
        "리스케줄",
        "다른 시간",
        "다른 날짜",
        "시간을 바",
        "날짜를 바",
    )

    return any(phrase in text for phrase in reschedule_phrases)


def append_reason(current_reason: Optional[str], next_reason: str) -> str:
    if not current_reason:
        return next_reason
    if next_reason in current_reason:
        return current_reason
    return f"{current_reason} / {next_reason}"


def copy_analysis_result(result: AnalysisResultSchema, **updates: Any) -> AnalysisResultSchema:
    if hasattr(result, "model_dump"):
        data = result.model_dump()
    else:
        data = result.dict()
    data.update(updates)
    return AnalysisResultSchema(**data)


def first_recent_value(
    recent_sender_analyses: List[HistoricalAnalysisInfo],
    field_name: str,
) -> Optional[str]:
    for analysis in recent_sender_analyses:
        value = getattr(analysis, field_name, None)
        if isinstance(value, str):
            cleaned = clean_entity(value)
            if cleaned and cleaned != "-":
                return cleaned
    return None


def merge_embedding_action(
    rule_action_type: str,
    rule_reason: str,
    embedding_result: Optional[EmbeddingClassification],
    text: str,
) -> Tuple[str, str]:
    if embedding_result is None or embedding_result.action_type is None:
        return rule_action_type, rule_reason

    embedding_action = embedding_result.action_type

    if rule_action_type == "CANCEL":
        return rule_action_type, rule_reason

    if embedding_action == "UPDATE" and looks_like_schedule_update(text):
        return "UPDATE", append_reason(rule_reason, embedding_result.reason)

    if rule_action_type == "UNKNOWN" and embedding_action in {"CREATE", "UPDATE", "CANCEL", "CONFIRM"}:
        return embedding_action, append_reason(rule_reason, embedding_result.reason)

    if embedding_action == "UNKNOWN" and rule_action_type == "CREATE" and not bool(extract_date(text) or extract_time(text)):
        return "UNKNOWN", append_reason(rule_reason, embedding_result.reason)

    return rule_action_type, rule_reason


def suppress_non_schedule_action(
    action_type: str,
    action_reason: str,
    text: str,
) -> Tuple[str, str]:
    if action_type not in {"CREATE", "UPDATE", "CONFIRM"}:
        return action_type, action_reason

    if looks_like_pre_meeting_material(text):
        return (
            "UNKNOWN",
            append_reason(action_reason, "미팅 전 자료/질문지 전달 메일로, 새 일정 생성 요청은 아닙니다."),
        )

    return action_type, action_reason


def looks_like_pre_meeting_material(text: str) -> bool:
    material_markers = (
        "첨부",
        "질문지",
        "자료",
        "검토 부탁",
        "검토 부탁드립니다",
        "전달드립니다",
        "확인하고 싶은",
        "주요 질문",
    )
    pre_meeting_markers = (
        "미팅 전",
        "회의 전",
        "상담 전",
        "전에 확인",
    )
    explicit_schedule_request_markers = (
        "일정 등록",
        "미팅 요청",
        "잡고 싶",
        "잡아주세요",
        "진행하고 싶",
        "가능하실까요",
        "시간 괜찮",
    )

    return (
        any(marker in text for marker in material_markers)
        and any(marker in text for marker in pre_meeting_markers)
        and not any(marker in text for marker in explicit_schedule_request_markers)
    )


def apply_rule_action_corrections(
    action_type: str,
    action_reason: str,
    text: str,
) -> Tuple[str, str]:
    if action_type == "CANCEL":
        return action_type, action_reason

    if looks_like_schedule_update(text):
        return (
            "UPDATE",
            append_reason(action_reason, "기존 일정 조정/변경 문맥이 포함되어 일정 변경으로 판단했습니다."),
        )

    return action_type, action_reason


def merge_embedding_business(
    rule_business_type: str,
    rule_business_score: float,
    rule_business_reason: str,
    embedding_result: Optional[EmbeddingClassification],
    action_type: str,
) -> Tuple[str, float, str]:
    if embedding_result is None or embedding_result.business_type is None:
        return rule_business_type, rule_business_score, rule_business_reason

    embedding_business = embedding_result.business_type

    if rule_business_type == "NON_BUSINESS":
        return rule_business_type, rule_business_score, rule_business_reason

    if embedding_business == "NON_BUSINESS" and action_type == "UNKNOWN":
        return (
            "NON_BUSINESS",
            min(rule_business_score, 0.2),
            append_reason(rule_business_reason, embedding_result.reason),
        )

    if embedding_business == "SALES_ACTIVITY" and rule_business_type == "UNKNOWN":
        return (
            "SALES_ACTIVITY",
            max(rule_business_score, min(0.9, embedding_result.business_score)),
            append_reason(rule_business_reason, embedding_result.reason),
        )

    return rule_business_type, rule_business_score, rule_business_reason


def classify_business_email(
    text: str,
    messages: List[MessageItem],
    action_type: str,
    customer_name: Optional[str],
    product_name: Optional[str],
    amount: Optional[int],
) -> Tuple[str, float, str]:
    lowered_text = text.lower()
    sender_values = " ".join(
        item.senderEmail or "" for item in messages if item.senderEmail
    ).lower()

    non_business_hit = next(
        (word for word in NON_BUSINESS_SENDERS if word in sender_values),
        None,
    ) or next(
        (word for word in NON_BUSINESS_WORDS if word.lower() in lowered_text),
        None,
    )
    if non_business_hit:
        return (
            "NON_BUSINESS",
            0.1,
            f"업무와 직접 관련이 낮은 발신자 또는 알림성 표현({non_business_hit})이 포함되어 있습니다.",
        )

    if action_type in {"CREATE", "UPDATE", "CANCEL", "CONFIRM"}:
        return (
            "SALES_ACTIVITY",
            0.95,
            "일정 생성/변경/취소/확인 표현이 있어 영업 활동 메일로 판단했습니다.",
        )

    if customer_name or product_name or amount:
        return (
            "SALES_ACTIVITY",
            0.9,
            "고객사, 제품, 금액 중 하나 이상의 영업 정보가 추출되었습니다.",
        )

    matched_words = [
        word for word in SALES_BUSINESS_WORDS
        if word.lower() in lowered_text
    ]
    if len(matched_words) >= 2:
        preview = ", ".join(matched_words[:3])
        return (
            "SALES_ACTIVITY",
            0.75,
            f"영업 관련 키워드({preview})가 포함되어 있습니다.",
        )
    if len(matched_words) == 1:
        return (
            "UNKNOWN",
            0.55,
            f"영업 관련 키워드({matched_words[0]})가 있으나 활동 여부는 추가 확인이 필요합니다.",
        )

    return (
        "UNKNOWN",
        0.4,
        "영업 활동으로 판단할 고객사, 제품, 일정, 견적 정보가 명확하지 않습니다.",
    )


def classify_action_type(text: str) -> Tuple[str, str]:
    has_date_time = bool(extract_date(text) or extract_time(text))
    has_schedule_word = any(word in text for word in SCHEDULE_WORDS)

    if contains_any(text, CANCEL_WORDS):
        return "CANCEL", "일정 취소 표현이 포함되어 있습니다."
    # 확약형 확정("확정하겠습니다" 등)은 "변경된 일정으로 확정합니다"처럼
    # '변경' 단어가 섞여 있어도 일정 확인(CONFIRM)으로 우선 판단한다.
    strong_confirm_words = ("확정하겠습니다", "확정합니다", "확정했습니다", "그대로 진행", "예정대로 진행")
    if contains_any(text, strong_confirm_words) and has_schedule_word:
        return "CONFIRM", "일정 확인 또는 확정 표현이 포함되어 있습니다."
    if contains_any(text, UPDATE_WORDS):
        return "UPDATE", "일정 변경 표현이 포함되어 있습니다."
    if contains_any(text, CONFIRM_WORDS) and has_schedule_word:
        return "CONFIRM", "일정 확인 또는 확정 표현이 포함되어 있습니다."
    if contains_any(text, CREATE_WORDS) and (has_schedule_word or has_date_time):
        return "CREATE", "일정 생성 요청 표현과 날짜/시간 정보가 포함되어 있습니다."
    if has_date_time and has_schedule_word and any(word in text for word in ("진행", "가능", "요청")):
        return "CREATE", "날짜/시간과 미팅 진행 의사가 포함되어 있어 일정 생성 대상으로 판단했습니다."
    if contains_any(text, GENERAL_WORDS):
        return "UNKNOWN", "자료 전달 또는 검토 요청 성격이며 일정 의도가 명확하지 않습니다."
    return "UNKNOWN", "일정 의도가 명확하지 않아 확인이 필요합니다."


def build_summary(
    action_type: str,
    schedule: ScheduleResponse,
    text: str,
    customer_name: Optional[str],
    product_name: Optional[str],
    amount: Optional[int],
    attendees: List[str],
) -> str:
    if action_type == "UNKNOWN":
        if any(word in text for word in ("견적서", "첨부")):
            return "견적서 전달 메일"
        if any(word in text for word in ("자료", "소개 자료", "기능 설명서")):
            target = product_name or "제품"
            company = customer_name or "고객사"
            return f"{company}의 {target} 자료 문의 메일"
        return "일정 의도가 명확하지 않은 일반 영업 메일"

    parts = []
    if customer_name:
        parts.append(customer_name)
    if product_name:
        parts.append(product_name)
    if amount:
        parts.append(f"{amount:,}원")
    if schedule.title:
        parts.append(schedule.title)
    if schedule.date:
        parts.append(schedule.date)
    if schedule.time:
        parts.append(format_time_for_summary(schedule.time))
    if attendees:
        parts.append("참석자: " + ", ".join(attendees))
    return " / ".join(parts) if parts else schedule.title


def build_todo_content(action_type: str, schedule: ScheduleResponse, product_name: Optional[str]) -> Optional[str]:
    if action_type == "UNKNOWN":
        if product_name:
            return f"{product_name} 자료 요청 검토 후 필요 시 후속 미팅 제안"
        return "검토 후 필요 시 문의 대응"
    if action_type == "CANCEL":
        return f"{schedule.title} 취소 확인"
    if action_type == "UPDATE":
        return f"{schedule.title} 일정 변경 확인"
    if action_type == "CONFIRM":
        return f"{schedule.title} 확정 확인"
    if action_type == "CREATE":
        return "미팅 일정 등록 및 미팅 준비"
    return None


def extract_customer(text: str) -> Optional[str]:
    patterns = [
        r"([A-Za-z][A-Za-z0-9&.\- ]{0,30}\s*(?:고객사|기업|주식회사|Corp|Systems))(?:와|과|에서|의|\s|$)",
        r"([가-힣A-Za-z0-9&.\- ]{2,40}?\s*(?:고객사|기업|주식회사|Corp|Systems))(?:와|과|에서|의|\s|$)",
        r"([A-Za-z][A-Za-z0-9&.\- ]{1,40})\s*(?:고객사|기업|주식회사|Corp|Systems)\s+[가-힣]{2,4}입니다",
        r"([가-힣A-Za-z0-9&.\- ]{2,40}?\s*(?:고객사|기업|주식회사|Corp|Systems))\s+[가-힣]{2,4}입니다",
        r"([A-Za-z][A-Za-z0-9&.\- ]{1,40})\s+[가-힣]{2,4}입니다",
    ]
    for pattern in patterns:
        match = re.search(pattern, text)
        if match:
            value = clean_company_name(clean_entity(strip_date_time_prefix(match.group(1))))
            if is_valid_customer(value):
                return value
    return None


def extract_product(text: str) -> Optional[str]:
    patterns = [
        r"([A-Za-z][A-Za-z0-9&.\- ]{1,50})\s*(?:관련\s*)?(?:소개 자료|주요 기능 설명서|기능 설명서|자료)",
        r"([A-Za-z][A-Za-z0-9&.\- ]{1,50})\s*(?:제품 소개|도입 관련|도입 검토|도입 건)",
        r"([A-Za-z][A-Za-z0-9&.\- ]{1,50})\s*(?:Platform|Solution|솔루션|서비스|시스템)",
        r"\b(CRM)\s*(?:도입|검토|관련|미팅)",
        r"([A-Za-z][A-Za-z0-9&.\- ]{1,50})\s*제품",
    ]
    for pattern in patterns:
        match = re.search(pattern, text)
        if match:
            value = clean_entity(strip_date_time_prefix(match.group(1)))
            value = remove_customer_tail(value)
            if is_valid_product(value):
                return value
    return None


def extract_amount(text: str) -> Optional[int]:
    match = re.search(r"약?\s*([0-9,]+)\s*(만원|원)", text)
    if not match:
        return None

    number = int(match.group(1).replace(",", ""))
    unit = match.group(2)
    return number * 10000 if unit == "만원" else number


def extract_schedule_title(text: str) -> Optional[str]:
    product = extract_product(text)
    if product and any(word in text for word in SCHEDULE_WORDS):
        return f"{product} 관련 미팅"

    context_candidates = title_contexts(text)
    patterns = [
        r"([가-힣A-Za-z0-9&.\- ]{2,45}?(?:제품 소개 미팅|도입 관련 미팅|도입 검토 미팅|미팅|회의|상담|통화))",
        r"([가-힣A-Za-z0-9&.\- ]{2,45}?(?:후속 논의|일정 조율))",
    ]
    for context in context_candidates:
        for pattern in patterns:
            match = re.search(pattern, context)
            if match:
                title = clean_schedule_title(clean_entity(strip_date_time_prefix(match.group(1))))
                if title:
                    return title
    return None


def extract_date_and_time(
    text: str,
    reference_time: Optional[datetime] = None,
) -> Tuple[Optional[str], Optional[str]]:
    return extract_date(text, reference_time), extract_time(text)


def resolve_reference_time(reference_time: Optional[datetime] = None) -> datetime:
    """기준 시각을 서울 시간대의 naive datetime으로 맞춘다. 없으면 현재 시각."""
    if reference_time is None:
        return datetime.now()
    if reference_time.tzinfo is not None:
        return reference_time.astimezone(local_timezone()).replace(tzinfo=None)
    return reference_time


def local_timezone():
    try:
        return ZoneInfo(TIMEZONE)
    except ZoneInfoNotFoundError:
        # Windows에는 tzdata가 기본 포함되지 않는다. 한국은 서머타임이 없어 고정 오프셋으로 충분하다.
        return timezone(timedelta(hours=9))


def extract_date(text: str, reference_time: Optional[datetime] = None) -> Optional[str]:
    base = resolve_reference_time(reference_time)
    iso = re.findall(r"(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})", text)
    if iso:
        year, month, day = iso[-1]
        return f"{year}-{int(month):02d}-{int(day):02d}"

    korean = re.findall(r"(?:(\d{4})년\s*)?(\d{1,2})월\s*(\d{1,2})일", text)
    if korean:
        year, month, day = korean[-1]
        return f"{int(year) if year else base.year}-{int(month):02d}-{int(day):02d}"

    weekdays = {
        "월요일": 0,
        "화요일": 1,
        "수요일": 2,
        "목요일": 3,
        "금요일": 4,
        "토요일": 5,
        "일요일": 6,
    }
    for prefix, offset in (("이번 주", 0), ("다음 주", 7)):
        for weekday, index in weekdays.items():
            if f"{prefix} {weekday}" in text:
                return next_weekday(index, offset, base)

    # 요일만 따로 언급된 경우("다음 주 미팅을 ... 화요일 오후 2시로"):
    # 본문에 '다음 주'가 있으면 다음 주로, 아니면 가장 가까운 다가오는 요일로 해석한다.
    # '지난/저번 화요일' 같은 과거 표현은 제외한다.
    week_offset = 7 if "다음 주" in text else 0
    for weekday, index in weekdays.items():
        if weekday in text and not re.search(r"(지난|저번|지지난)\s*" + weekday, text):
            return next_weekday(index, week_offset, base)
    return None


def extract_time(text: str) -> Optional[str]:
    matches = re.findall(r"(오전|오후)\s*(\d{1,2})시(?:\s*(\d{1,2})분)?", text)
    if matches:
        meridiem, hour_text, minute_text = matches[-1]
        hour = int(hour_text)
        if meridiem == "오후" and hour != 12:
            hour += 12
        if meridiem == "오전" and hour == 12:
            hour = 0
        return f"{hour:02d}:{int(minute_text or 0):02d}"

    colon = re.findall(r"(\d{1,2}):(\d{2})", text)
    if colon:
        hour, minute = colon[-1]
        return f"{int(hour):02d}:{int(minute):02d}"
    return None


def next_weekday(target_weekday: int, base_offset: int = 0, reference_time: Optional[datetime] = None) -> str:
    today = resolve_reference_time(reference_time)
    days_ahead = target_weekday - today.weekday() + base_offset
    if days_ahead <= 0:
        days_ahead += 7
    target = today + timedelta(days=days_ahead)
    return target.strftime("%Y-%m-%d")


def select_target_schedule(
    text: str,
    schedule: ScheduleResponse,
    existing_schedules: List[ExistingScheduleInfo],
) -> Optional[ExistingScheduleInfo]:
    if not existing_schedules:
        return None

    scored = []
    for existing in existing_schedules:
        score = schedule_match_score(text, schedule, existing)
        if score > 0:
            scored.append((score, existing))
    if not scored:
        return None

    scored.sort(key=lambda item: item[0], reverse=True)
    return scored[0][1]


def schedule_match_score(text: str, schedule: ScheduleResponse, existing: ExistingScheduleInfo) -> int:
    score = 0
    title = existing.title or ""
    if title and title in text:
        score += 5
    if schedule.title and title and has_shared_token(schedule.title, title):
        score += 3
    if existing.scheduleDateTime:
        month_day_text = f"{existing.scheduleDateTime.month}월 {existing.scheduleDateTime.day}일"
        hour_text = f"{existing.scheduleDateTime.hour}시"
        if month_day_text in text:
            score += 4
        if hour_text in text:
            score += 2
    return score


def select_participants(
    text: str,
    messages: List[MessageItem],
    requester_name: Optional[str] = None,
) -> List[str]:
    explicit = extract_attendees(text)
    if explicit:
        return explicit

    requester_aliases = name_aliases(requester_name)
    metadata_candidates = []
    for message in messages:
        if message.direction == "RECEIVED":
            metadata_candidates.append(message.senderName)
        elif message.direction == "SENT":
            metadata_candidates.extend(message.receiverNames)
        else:
            metadata_candidates.append(message.senderName)
            metadata_candidates.extend(message.receiverNames)

    return dedupe([
        name for name in metadata_candidates
        if name and name not in requester_aliases
    ])


def extract_attendees(text: str) -> List[str]:
    match = re.search(r"참석자는\s*(.+?)(?:입니다|입니다\.|$)", text, re.DOTALL)
    if not match:
        return []
    raw = match.group(1).replace("\n", " ")
    raw = re.sub(r"^기존과\s*동일하게\s*", "", raw)
    return dedupe([item.strip(" .") for item in re.split(r"[,，]", raw) if item.strip(" .")])


def first_contact(attendees: List[str]) -> Optional[str]:
    if not attendees:
        return None
    for attendee in attendees:
        if any(token in attendee for token in ("고객사", "Corp", "기업", "주식회사", "Systems")):
            return attendee
    return attendees[0]


def has_shared_token(left: str, right: str) -> bool:
    left_tokens = {token for token in left.split() if len(token) >= 2}
    right_tokens = {token for token in right.split() if len(token) >= 2}
    return bool(left_tokens & right_tokens)


def contains_any(text: str, patterns: Iterable[str]) -> bool:
    return any(pattern in text for pattern in patterns)


def normalize(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


def title_contexts(text: str) -> List[str]:
    contexts = []
    before_greeting = re.split(r"안녕하세요[.。]?", text, maxsplit=1)[0].strip()
    if before_greeting:
        contexts.append(before_greeting)

    for sentence in re.split(r"[.!?。]|(?:\s{2,})", text):
        sentence = clean_entity(sentence)
        if sentence and any(word in sentence for word in SCHEDULE_WORDS):
            contexts.append(sentence)

    contexts.append(text)
    return contexts


def clean_entity(value: str) -> str:
    value = re.sub(r"\s+", " ", value or "")
    return value.strip(" .,\n\t")


def strip_date_time_prefix(value: str) -> str:
    value = re.sub(r"\d{4}[-/.]\d{1,2}[-/.]\d{1,2}", "", value)
    value = re.sub(r"\d{4}년\s*\d{1,2}월\s*\d{1,2}일", "", value)
    value = re.sub(r"\d{1,2}월\s*\d{1,2}일", "", value)
    value = re.sub(r"(오전|오후)\s*\d{1,2}시(?:\s*\d{1,2}분)?", "", value)
    value = re.sub(r"^\s*\d{1,2}\s*", "", value)
    value = re.sub(r"^\s*(시에|에서|와|과)\s*", "", value)
    return clean_entity(value)


def clean_company_name(value: str) -> str:
    value = clean_entity(value)
    value = re.sub(r"\s+(제품|미팅|회의|상담|통화).*$", "", value)
    return clean_entity(value)


def clean_schedule_title(value: str) -> str:
    value = clean_entity(value)
    value = re.sub(r"\s*일정\s*(변경|취소|확정)?\s*요청.*$", "", value)
    value = re.sub(r"\s*안녕하세요.*$", "", value)
    if value.endswith("변경"):
        value = value[:-2].strip()
    if value.endswith("취소"):
        value = value[:-2].strip()
    return clean_entity(value)


def remove_customer_tail(value: str) -> str:
    value = re.sub(r".*?(?:시에|에서)\s*", "", value)
    value = re.sub(r"\b[A-Za-z0-9&.\- ]+?\s*(?:고객사|기업|Corp)와\s*", "", value)
    value = re.sub(r"\b[A-Za-z0-9&.\- ]+?\s*(?:고객사|기업|Corp)과\s*", "", value)
    return clean_entity(value)


def is_valid_customer(value: str) -> bool:
    if not value or len(value) < 2:
        return False
    lowered = value.lower()
    return not any(blocked in lowered for blocked in ("안녕하세요", "오후", "오전", "sales analytics platform"))


def is_valid_product(value: str) -> bool:
    if not value or len(value) < 2:
        return False
    blocked = {"제품", "고객", "신규 고객", "안녕하세요"}
    return value not in blocked


def format_time_for_summary(time_text: Optional[str]) -> str:
    if not time_text:
        return ""
    match = re.match(r"(\d{2}):(\d{2})", time_text)
    if not match:
        return time_text
    hour = int(match.group(1))
    minute = int(match.group(2))
    meridiem = "오전" if hour < 12 else "오후"
    display_hour = hour if hour <= 12 else hour - 12
    if display_hour == 0:
        display_hour = 12
    return f"{meridiem} {display_hour}시" + (f" {minute}분" if minute else "")


def name_aliases(name: Optional[str]) -> set[str]:
    if not name:
        return set()
    aliases = {name}
    if len(name) >= 2:
        aliases.add(name[1:])
    return aliases


def dedupe(values: Iterable[str]) -> List[str]:
    result = []
    seen = set()
    for value in values:
        value = clean_entity(value)
        if value and value not in seen:
            result.append(value)
            seen.add(value)
    return result


# 액션 판정 최종 보정 가드
def apply_reschedule_request_guard(
    action_type: str,
    action_reason: str,
    text: str,
) -> Tuple[str, str]:
    if looks_like_new_request_after_past_cancel(text):
        return (
            "CREATE",
            "이전 취소 이력은 과거 맥락이며, 다시 진행/재요청 표현이 있어 일정 생성으로 판단했습니다.",
        )

    return action_type, action_reason


def apply_final_safety_guards(
    result: AnalysisResultSchema,
    text: str,
) -> AnalysisResultSchema:
    if is_strong_non_business_email(text):
        return copy_analysis_result(
            result,
            summary="영업 활동과 관련 없는 일반 메일",
            customer_name=None,
            contact_person=None,
            product_name=None,
            attendees=[],
            amount=None,
            activity_type="EMAIL",
            action_type="UNKNOWN",
            target_schedule_id=None,
            target_schedule_title=None,
            action_reason="영업 고객사/제품/일정 요청과 관련 없는 안내성 메일로 판단했습니다.",
            business_type="NON_BUSINESS",
            business_relevance_score=0.05,
            business_reason="학교 행사, 택배, 개인 알림, 프로모션 등 영업 활동과 무관한 표현이 포함되어 있습니다.",
            todo_required=False,
            todo_content="등록 대상이 아닌 메일입니다.",
            schedule=None,
            confidence=max(result.confidence, 0.95),
        )

    if looks_like_new_request_after_past_cancel(text):
        return copy_analysis_result(
            result,
            action_type="CREATE",
            target_schedule_id=None,
            target_schedule_title=None,
            action_reason="이전 취소 이력은 과거 맥락이며, 다시 진행/재요청 표현이 있어 일정 생성으로 판단했습니다.",
            todo_required=result.schedule is not None,
            todo_content="미팅 일정 등록 및 미팅 준비",
            confidence=max(result.confidence, 0.92),
        )

    if result.action_type in {"CANCEL", "CONFIRM"} and is_weak_embedding_only_action(text):
        return copy_analysis_result(
            result,
            action_type="UNKNOWN",
            target_schedule_id=None,
            target_schedule_title=None,
            action_reason="명확한 일정 취소/확정 요청 표현이 없어 확인 필요로 보정했습니다.",
            todo_required=False,
            schedule=None,
            confidence=max(result.confidence, 0.88),
        )

    return result


def looks_like_new_request_after_past_cancel(text: str) -> bool:
    past_cancel_markers = (
        "취소했던",
        "취소했었던",
        "지난번 취소",
        "이전에 취소",
        "취소된",
    )
    new_request_markers = (
        "다시 진행",
        "다시 일정",
        "재요청",
        "다시 요청",
        "가능하실까요",
        "진행하고 싶습니다",
        "미팅 가능",
    )

    return any(marker in text for marker in past_cancel_markers) and any(
        marker in text for marker in new_request_markers
    )


def is_weak_embedding_only_action(text: str) -> bool:
    strong_cancel_markers = (
        "취소 부탁",
        "취소 요청",
        "취소해야",
        "취소합니다",
        "진행하지 않겠습니다",
        "진행하지 않습니다",
        "미팅 취소",
        "일정 취소",
    )
    strong_confirm_markers = (
        "확정합니다",
        "그대로 진행",
        "예정대로 진행",
        "참석 가능합니다",
        "일정으로 진행하겠습니다",
    )

    return not any(marker in text for marker in strong_cancel_markers + strong_confirm_markers)


def is_strong_non_business_email(text: str) -> bool:
    lowered = text.lower()
    non_business_markers = (
        "동아리",
        "학교 행사",
        "교내 행사",
        "강의실",
        "학교 포털",
        "학생",
        "택배",
        "배송 완료",
        "배송 내역",
        "택배사",
        "주문하신 상품",
        "뉴스레터",
        "구독",
        "무료 이용",
        "요금제",
        "업그레이드",
        "프로모션",
        "할인",
        "광고",
        "facebook",
        "youtube",
    )
    business_markers = (
        "고객사",
        "도입",
        "견적",
        "계약",
        "제품 소개",
        "상담 미팅",
        "데모",
        "crm",
        "sales analytics",
        "sales platform",
        "automation",
    )

    if any(marker in lowered for marker in non_business_markers):
        return not any(marker in lowered for marker in business_markers)

    return False
