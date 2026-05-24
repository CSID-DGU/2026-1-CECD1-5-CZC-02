"""활동 유형 분류기"""
from typing import Tuple


def classify_activity_type(text: str) -> Tuple[str, float]:
    """
    텍스트 기반 활동 유형 분류
    Returns: (activity_type, confidence)
    
    활동 유형: MEETING, CALL, EMAIL, TASK
    """
    text = text.lower()
    
    # 점수 계산
    scores = {
        "MEETING": 0.0,
        "CALL": 0.0,
        "EMAIL": 0.0,
        "TASK": 0.0,
    }
    
    # MEETING 키워드
    meeting_keywords = {
        '회의': 1.0, '미팅': 1.0, '만남': 0.8, '협의': 0.9,
        '토론': 0.8, '회담': 0.9, '논의': 0.7, '세션': 0.8,
        '리뷰': 0.7, '컨퍼런스': 0.9, '모임': 0.7
    }
    for kw, score in meeting_keywords.items():
        if kw in text:
            scores["MEETING"] += score
    
    # CALL 키워드
    call_keywords = {
        '통화': 1.0, '전화': 1.0, '콜': 0.9, '전화주': 0.8,
        '전화받': 0.8, '음성': 0.7, '목소리': 0.6
    }
    for kw, score in call_keywords.items():
        if kw in text:
            scores["CALL"] += score
    
    # EMAIL 키워드
    email_keywords = {
        '이메일': 1.0, '메일': 0.8, '메시지': 0.6, '발송': 0.7,
        '회신': 0.8, 'email': 0.9, 'mail': 0.8, '편지': 0.5
    }
    for kw, score in email_keywords.items():
        if kw in text:
            scores["EMAIL"] += score
    
    # TASK 키워드
    task_keywords = {
        '업무': 0.9, '작업': 0.9, '태스크': 1.0, '일정': 0.7,
        '과제': 0.8, '프로젝트': 0.8, '수행': 0.6, '완료': 0.5,
        '진행': 0.5, '처리': 0.5, '확인': 0.4, '검토': 0.6
    }
    for kw, score in task_keywords.items():
        if kw in text:
            scores["TASK"] += score
    
    # 최고 점수 활동 유형 선택
    best_type = max(scores, key=scores.get)
    best_score = scores[best_type]
    
    # 신뢰도 정규화 (0.0 ~ 1.0)
    max_possible_score = 2.0  # 단어가 최대 2번 나올 수 있다고 가정
    confidence = min(best_score / max_possible_score, 1.0)
    
    # 점수가 모두 0이면 기본값 (낮은 신뢰도)
    if best_score == 0:
        best_type = "MEETING"
        confidence = 0.3
    
    return best_type, confidence
