"""AI 분석 통합 모듈 (Orchestrator)"""
from typing import Optional
from app.schemas.models import AnalysisResultSchema, ScheduleSchema
from app.services.preprocessor import preprocess_text
from app.utils.date_parser import extract_date_and_time
from app.services.extractor import extract_participants, extract_location
from app.services.classifier import classify_activity_type
from app.services.summarizer import generate_summary, determine_todo_requirement


async def analyze_message(message: str) -> AnalysisResultSchema:
    """
    메시지 분석 (전체 파이프라인)
    
    1. 전처리
    2. 날짜/시간 추출
    3. 참여자 추출
    4. 활동 유형 분류
    5. GPT 요약
    6. TODO 판정
    7. 결과 조합
    """
    
    # 1. 전처리
    cleaned_text = preprocess_text(message)
    
    # 2. 날짜/시간 추출
    date, time = extract_date_and_time(cleaned_text)
    
    # 3. 참여자 추출
    participants = extract_participants(cleaned_text)
    
    # 4. 활동 유형 분류
    activity_type, activity_confidence = classify_activity_type(cleaned_text)
    
    # 5. GPT 요약
    summary, summary_confidence = generate_summary(cleaned_text, activity_type)
    
    # 6. TODO 판정
    todo_required, todo_content = determine_todo_requirement(cleaned_text, activity_type)
    
    # 7. 위치 추출
    location = extract_location(cleaned_text)
    
    # 8. 일정 JSON 생성 (필요시)
    schedule = None
    if date or time:
        schedule = ScheduleSchema(
            title=summary,
            date=date or "",
            time=time or "",
            participants=participants if participants else None,
            location=location
        )
    
    # 9. 최종 신뢰도 계산 (평균)
    overall_confidence = (activity_confidence + summary_confidence) / 2
    
    # 고객명은 현재 추출하지 않음 (Backend와 연계 필요)
    customer_name = None
    
    # 연락처는 첫 번째 참여자 (필요시 조정)
    contact_person = participants[0] if participants else None
    
    return AnalysisResultSchema(
        summary=summary,
        customer_name=customer_name,
        contact_person=contact_person,
        activity_type=activity_type,
        todo_required=todo_required,
        todo_content=todo_content if todo_required else None,
        schedule=schedule,
        confidence=overall_confidence
    )
