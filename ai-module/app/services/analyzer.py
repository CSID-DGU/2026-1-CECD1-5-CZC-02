"""규칙 기반 분석 통합 모듈"""
from typing import Iterable, List, Optional

from app.schemas.models import AnalysisResultSchema, ScheduleSchema
from app.schemas.request import ExistingScheduleInfo, MessageItem
from app.schemas.response import ScheduleResponse
from app.services.classifier import classify_action_type, classify_activity_type, classify_keywords
from app.services.extractor import extract_nouns, extract_participants
from app.services.preprocess import preprocess_text
from app.services.regex_parser import extract_date_and_time
from app.services.schedule_generator import generate_schedule


CONNECTOR_KEYWORDS = {
    "연결",
    "전달",
    "확인",
    "조율",
    "잡아주세요",
    "담당자",
    "팀원",
    "분들",
}


def analyze_schedule(
    message: str,
    messages: Optional[List[MessageItem]] = None,
    requester_name: Optional[str] = None,
) -> ScheduleResponse:
    """전처리 -> Komoran 명사 -> regex -> 키워드 분류 -> 일정 JSON 생성"""
    cleaned_text = preprocess_text(message)
    nouns = extract_nouns(cleaned_text)
    date, time = extract_date_and_time(cleaned_text)
    participants = select_participants(cleaned_text, nouns, messages or [], requester_name)
    classified = classify_keywords(cleaned_text, nouns, participants)
    return generate_schedule(cleaned_text, classified, date, time)


async def analyze_message(
    message: str,
    messages: Optional[List[MessageItem]] = None,
    requester_name: Optional[str] = None,
    existing_schedules: Optional[List[ExistingScheduleInfo]] = None,
) -> AnalysisResultSchema:
    schedule_response = analyze_schedule(message, messages, requester_name)
    cleaned_text = preprocess_text(message)
    activity_type, activity_confidence = classify_activity_type(cleaned_text)
    action_type, action_reason = classify_action_type(cleaned_text)
    target_schedule = select_target_schedule(cleaned_text, schedule_response, existing_schedules or [])

    schedule = ScheduleSchema(
        title=schedule_response.title,
        date=schedule_response.date or "",
        time=schedule_response.time or "",
        participants=schedule_response.participants,
        location=None,
    )

    return AnalysisResultSchema(
        summary=_build_summary(schedule_response),
        customer_name=None,
        contact_person=schedule_response.participants[0] if schedule_response.participants else None,
        activity_type=activity_type,
        action_type=action_type,
        target_schedule_id=target_schedule.scheduleId if target_schedule else None,
        target_schedule_title=target_schedule.title if target_schedule else None,
        action_reason=action_reason,
        todo_required=action_type in {"CREATE", "UPDATE", "CANCEL"} and bool(schedule_response.title),
        todo_content=_build_todo_content(action_type, schedule_response),
        schedule=schedule,
        confidence=max(activity_confidence, 0.7),
    )


def _build_summary(schedule: ScheduleResponse) -> str:
    parts = [schedule.title]
    if schedule.date:
        parts.append(schedule.date)
    if schedule.time:
        parts.append(schedule.time)
    if schedule.participants:
        parts.append(", ".join(schedule.participants))
    return " / ".join(parts)


def _build_todo_content(action_type: str, schedule: ScheduleResponse) -> Optional[str]:
    if not schedule.title:
        return None
    if action_type == "CANCEL":
        return f"{schedule.title} 취소 확인"
    if action_type == "UPDATE":
        return f"{schedule.title} 일정 변경 확인"
    if action_type == "CONFIRM":
        return f"{schedule.title} 확정"
    if action_type == "CREATE":
        return f"{schedule.title} 준비"
    return None


def select_target_schedule(
    text: str,
    schedule: ScheduleResponse,
    existing_schedules: List[ExistingScheduleInfo],
) -> Optional[ExistingScheduleInfo]:
    if not existing_schedules:
        return None

    scored = []
    for existing in existing_schedules:
        score = _schedule_match_score(text, schedule, existing)
        if score > 0:
            scored.append((score, existing))

    if not scored:
        return None

    scored.sort(key=lambda item: item[0], reverse=True)
    return scored[0][1]


def _schedule_match_score(
    text: str,
    schedule: ScheduleResponse,
    existing: ExistingScheduleInfo,
) -> int:
    score = 0
    title = existing.title or ""
    if title and title in text:
        score += 4
    if schedule.title and title and _has_shared_token(schedule.title, title):
        score += 2
    if existing.scheduleDateTime:
        date_text = existing.scheduleDateTime.strftime("%Y-%m-%d")
        month_day_text = f"{existing.scheduleDateTime.month}월 {existing.scheduleDateTime.day}일"
        time_text = f"{existing.scheduleDateTime.hour}시"
        if date_text in text:
            score += 4
        if month_day_text in text:
            score += 4
        if time_text in text:
            score += 2
    for participant in existing.participants:
        if participant and participant in text:
            score += 1
    return score


def _has_shared_token(left: str, right: str) -> bool:
    left_tokens = {token for token in left.split() if len(token) >= 2}
    right_tokens = {token for token in right.split() if len(token) >= 2}
    return bool(left_tokens & right_tokens)


def select_participants(
    text: str,
    nouns: List[str],
    messages: List[MessageItem],
    requester_name: Optional[str] = None,
) -> List[str]:
    """본문 직접 언급자를 우선하고, sender/receiver는 비어 있을 때만 보조로 씁니다."""
    requester_aliases = _name_aliases(requester_name)
    metadata_names = _metadata_names(messages)
    direct_participants = [
        name for name in extract_participants(text, nouns)
        if name not in requester_aliases
    ]
    if _has_connector_context(text):
        group_participants = _extract_group_participants(text)
        direct_participants = [
            name for name in direct_participants
            if name not in metadata_names
        ]
        return _dedupe([*direct_participants, *group_participants])

    if direct_participants:
        return _dedupe(direct_participants)

    metadata_candidates = []
    for message in messages:
        if message.direction == "RECEIVED":
            metadata_candidates.append(message.senderName)
        elif message.direction == "SENT":
            metadata_candidates.extend(message.receiverNames)
        else:
            metadata_candidates.append(message.senderName)
            metadata_candidates.extend(message.receiverNames)

    return _dedupe([
        name for name in metadata_candidates
        if name and name not in requester_aliases
    ])


def _has_connector_context(text: str) -> bool:
    return any(keyword in text for keyword in CONNECTOR_KEYWORDS)


def _name_aliases(name: Optional[str]) -> set[str]:
    if not name:
        return set()

    aliases = {name}
    if len(name) >= 2:
        aliases.add(name[1:])

    title_words = ["대리", "과장", "차장", "부장", "팀장", "이사", "대표", "님", "씨"]
    for title in title_words:
        aliases.add(f"{name}{title}")
        if len(name) >= 2:
            aliases.add(f"{name[1:]}{title}")
        aliases.add(f"{name[0]}{title}")

    return aliases


def _metadata_names(messages: List[MessageItem]) -> set[str]:
    names = set()
    for message in messages:
        if message.senderName:
            names.add(message.senderName)
        names.update(message.receiverNames)
    return names


def _extract_group_participants(text: str) -> List[str]:
    import re

    patterns = [
        r"([가-힣A-Za-z0-9]+팀)",
        r"([가-힣A-Za-z0-9]+부서)",
        r"([가-힣A-Za-z0-9]+담당자)",
    ]
    groups = []
    for pattern in patterns:
        groups.extend(re.findall(pattern, text))
    return _dedupe(groups)


def _dedupe(values: Iterable[str]) -> List[str]:
    result = []
    seen = set()
    for value in values:
        value = value.strip()
        if value and value not in seen:
            result.append(value)
            seen.add(value)
    return result
