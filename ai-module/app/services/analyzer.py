"""Rule-based analysis orchestration for sales activity emails."""
import re
from datetime import datetime, timedelta
from typing import Iterable, List, Optional, Tuple

from app.schemas.models import AnalysisResultSchema, ScheduleSchema
from app.schemas.request import ExistingScheduleInfo, MessageItem
from app.schemas.response import ScheduleResponse


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
    "요청드립니다",
)
UPDATE_WORDS = ("변경", "연기", "앞당", "시간을 바", "날짜를 바", "조율")
CANCEL_WORDS = ("취소", "진행하지 않습니다", "진행하지 않겠습니다")
CONFIRM_WORDS = ("확정", "그대로 진행", "확인했습니다", "참석 가능", "예정대로 진행", "진행하겠습니다")
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
) -> ScheduleResponse:
    text = normalize(message)
    date, time = extract_date_and_time(text)
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
) -> AnalysisResultSchema:
    text = normalize(message)
    schedule_response = analyze_schedule(text, messages, requester_name)
    action_type, action_reason = classify_action_type(text)
    customer_name = extract_customer(text)
    product_name = extract_product(text)
    amount = extract_amount(text)
    attendees = select_participants(text, messages or [], requester_name)
    business_type, business_score, business_reason = classify_business_email(
        text,
        messages or [],
        action_type,
        customer_name,
        product_name,
        amount,
    )

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

    return AnalysisResultSchema(
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


def classify_activity_type(text: str) -> str:
    if any(word in text for word in ("미팅", "회의", "상담", "만남")):
        return "MEETING"
    if any(word in text for word in ("통화", "전화")):
        return "CALL"
    if any(word in text for word in ("메일", "이메일", "첨부", "견적서", "자료")):
        return "EMAIL"
    return "TASK"


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


def extract_date_and_time(text: str) -> Tuple[Optional[str], Optional[str]]:
    return extract_date(text), extract_time(text)


def extract_date(text: str) -> Optional[str]:
    iso = re.findall(r"(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})", text)
    if iso:
        year, month, day = iso[-1]
        return f"{year}-{int(month):02d}-{int(day):02d}"

    korean = re.findall(r"(?:(\d{4})년\s*)?(\d{1,2})월\s*(\d{1,2})일", text)
    if korean:
        year, month, day = korean[-1]
        return f"{int(year) if year else datetime.now().year}-{int(month):02d}-{int(day):02d}"

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
                return next_weekday(index, offset)
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


def next_weekday(target_weekday: int, base_offset: int = 0) -> str:
    today = datetime.now()
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
    blocked = {"제품", "고객", "신규 고객", "안녕하세요", "Delta Systems 최유진입니다"}
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
