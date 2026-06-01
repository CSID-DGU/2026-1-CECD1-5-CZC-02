"""키워드 기반 분류기"""
from typing import Dict, List, Tuple


CREATE_KEYWORDS = [
    "가능하",
    "잡아",
    "잡을",
    "일정",
    "회의",
    "미팅",
    "뵙",
    "만나",
]

CANCEL_KEYWORDS = [
    "취소",
    "캔슬",
    "못 갈",
    "못가",
    "어렵",
    "불가",
    "안 될",
    "안될",
    "보류",
    "무산",
]

UPDATE_KEYWORDS = [
    "변경",
    "수정",
    "미루",
    "연기",
    "앞당",
    "바꾸",
    "옮기",
    "다시 잡",
    "리스케줄",
]

CONFIRM_KEYWORDS = [
    "확정",
    "좋습니다",
    "좋아요",
    "가능합니다",
    "네",
    "그때 뵙",
    "진행하",
]


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


def classify_action_type(text: str) -> Tuple[str, str]:
    normalized = text.lower()

    if _contains_any(normalized, CANCEL_KEYWORDS):
        return "CANCEL", "취소/불가 표현이 포함되어 있습니다."
    if _contains_any(normalized, UPDATE_KEYWORDS):
        return "UPDATE", "일정 변경 표현이 포함되어 있습니다."
    if _contains_any(normalized, CONFIRM_KEYWORDS):
        return "CONFIRM", "일정 확정 표현이 포함되어 있습니다."
    if _contains_any(normalized, CREATE_KEYWORDS):
        return "CREATE", "새 일정 생성으로 볼 수 있는 표현이 포함되어 있습니다."
    return "UNKNOWN", "일정 생성/수정/취소 의도를 확정하기 어렵습니다."


def classify_keywords(text: str, nouns: List[str], participants: List[str]) -> Dict[str, List[str]]:
    event_keywords = ["회의", "미팅", "통화", "전화", "상담", "논의", "리뷰", "보고"]
    project_patterns = [
        r"([A-Za-z0-9가-힣]+)\s*프로젝트",
        r"([A-Za-z0-9가-힣]+)\s*과제",
    ]

    events = [keyword for keyword in event_keywords if keyword in text]
    projects = []
    for pattern in project_patterns:
        for match in re_findall(pattern, text):
            projects.append(f"{match} 프로젝트" if "프로젝트" in pattern else f"{match} 과제")

    if not projects:
        for index, noun in enumerate(nouns):
            if noun == "프로젝트" and index > 0:
                projects.append(f"{nouns[index - 1]} 프로젝트")

    return {
        "people": participants,
        "event": _dedupe(events),
        "project": _dedupe(projects),
    }


def re_findall(pattern: str, text: str) -> List[str]:
    import re

    return re.findall(pattern, text)


def _dedupe(values: List[str]) -> List[str]:
    result = []
    seen = set()
    for value in values:
        if value and value not in seen:
            result.append(value)
            seen.add(value)
    return result


def _contains_any(text: str, keywords: List[str]) -> bool:
    return any(keyword in text for keyword in keywords)
