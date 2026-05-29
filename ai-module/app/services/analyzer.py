"""규칙 기반 분석 통합 모듈"""
from app.schemas.models import AnalysisResultSchema, ScheduleSchema
from app.schemas.response import ScheduleResponse
from app.services.classifier import classify_activity_type, classify_keywords
from app.services.extractor import extract_nouns, extract_participants
from app.services.preprocess import preprocess_text
from app.services.regex_parser import extract_date_and_time
from app.services.schedule_generator import generate_schedule


def analyze_schedule(message: str) -> ScheduleResponse:
    """전처리 -> Komoran 명사 -> regex -> 키워드 분류 -> 일정 JSON 생성"""
    cleaned_text = preprocess_text(message)
    nouns = extract_nouns(cleaned_text)
    date, time = extract_date_and_time(cleaned_text)
    participants = extract_participants(cleaned_text, nouns)
    classified = classify_keywords(cleaned_text, nouns, participants)
    return generate_schedule(cleaned_text, classified, date, time)


async def analyze_message(message: str) -> AnalysisResultSchema:
    schedule_response = analyze_schedule(message)
    cleaned_text = preprocess_text(message)
    activity_type, activity_confidence = classify_activity_type(cleaned_text)

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
        todo_required=bool(schedule_response.title),
        todo_content=f"{schedule_response.title} 준비" if schedule_response.title else None,
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
