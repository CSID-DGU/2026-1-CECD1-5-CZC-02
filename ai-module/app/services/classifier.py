import re
from typing import Dict, List, Tuple


CREATE_PATTERNS = [
    "일정 등록 부탁드립니다",
    "일정 등록 부탁",
    "미팅을 진행하고 싶습니다",
    "미팅 요청",
    "일정 생성 요청",
    "미팅을 잡아주세요",
    "일정을 잡아주세요",
    "상담 요청",
    "회의 요청",
    "통화 요청",
    "진행하면 좋겠습니다",
    "진행하고 싶습니다",
]

UPDATE_PATTERNS = [
    "변경해주세요",
    "변경하고 싶습니다",
    "연기해주세요",
    "앞당겨주세요",
    "시간을 바꿔주세요",
    "날짜를 바꿔주세요",
    "일정 변경",
    "변경 요청",
]

CANCEL_PATTERNS = [
    "취소해주세요",
    "취소합니다",
    "취소해야 할 것 같습니다",
    "진행하지 않습니다",
    "미팅 취소",
    "일정 취소",
]

CONFIRM_PATTERNS = [
    "확정합니다",
    "그대로 진행",
    "확인했습니다",
    "참석 가능합니다",
    "예정대로 진행",
]

GENERAL_PATTERNS = [
    "견적서 전달",
    "자료 전달",
    "첨부드립니다",
    "검토 부탁드립니다",
    "문의사항 있으시면 연락",
    "문의사항 있으면 연락",
]

SCHEDULE_WORDS = ["미팅", "회의", "상담", "통화", "일정"]


def classify_activity_type(text: str) -> Tuple[str, float]:
    if any(word in text for word in ["미팅", "회의", "상담", "만남"]):
        return "MEETING", 0.9
    if any(word in text for word in ["통화", "전화"]):
        return "CALL", 0.85
    if any(word in text for word in ["메일", "이메일", "첨부", "견적서"]):
        return "EMAIL", 0.8
    return "TASK", 0.5


def classify_action_type(text: str) -> Tuple[str, str]:
    normalized = _normalize(text)
    has_date_time = _has_date_or_time(normalized)
    has_schedule_word = any(word in normalized for word in SCHEDULE_WORDS)

    if _contains_any(normalized, CANCEL_PATTERNS):
        return "CANCEL", "일정 취소 표현이 포함되어 있습니다."
    if _contains_any(normalized, UPDATE_PATTERNS):
        return "UPDATE", "일정 변경 표현이 포함되어 있습니다."

    # 날짜/시간이 포함된 "진행하겠습니다"는 사용자가 일정을 확정한 것으로 보고 내부 일정 생성 대상으로 처리한다.
    if has_date_time and has_schedule_word and any(word in normalized for word in ["진행하겠습니다", "진행하면 좋겠습니다"]):
        return "CREATE", "날짜/시간과 미팅 진행 의사가 포함되어 있어 일정 생성 대상으로 판단했습니다."

    if _contains_any(normalized, CREATE_PATTERNS) or (
        has_date_time and has_schedule_word and _has_request_expression(normalized)
    ):
        return "CREATE", "일정 생성 요청 표현과 날짜/시간 정보가 포함되어 있습니다."
    if _contains_any(normalized, CONFIRM_PATTERNS):
        return "CONFIRM", "일정 확인 또는 확정 표현이 포함되어 있습니다."
    if _contains_any(normalized, GENERAL_PATTERNS):
        return "UNKNOWN", "일정 생성/수정/취소 표현이나 명확한 날짜/시간이 없어 일반 영업 메일로 판단했습니다."
    return "UNKNOWN", "일정 의도가 명확하지 않아 확인이 필요합니다."


def classify_keywords(text: str, nouns: List[str], participants: List[str]) -> Dict[str, List[str]]:
    event_keywords = [keyword for keyword in ["미팅", "회의", "상담", "통화"] if keyword in text]
    project_patterns = [
        r"([A-Za-z0-9가-힣]+?)\s*프로젝트",
        r"([A-Za-z0-9가-힣]+?)\s*과제",
    ]

    projects = []
    for pattern in project_patterns:
        for match in re.findall(pattern, text):
            projects.append(match.strip())

    return {
        "people": participants,
        "event": _dedupe(event_keywords),
        "project": _dedupe(projects),
    }


def _normalize(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def _contains_any(text: str, patterns: List[str]) -> bool:
    return any(pattern in text for pattern in patterns)


def _has_date_or_time(text: str) -> bool:
    return bool(
        re.search(r"\d{4}[-/.]\d{1,2}[-/.]\d{1,2}", text)
        or re.search(r"\d{4}년\s*\d{1,2}월\s*\d{1,2}일", text)
        or re.search(r"\d{1,2}월\s*\d{1,2}일", text)
        or re.search(r"(오전|오후)\s*\d{1,2}시", text)
        or re.search(r"\d{1,2}:\d{2}", text)
        or any(word in text for word in ["다음 주", "이번 주", "오늘", "내일"])
    )


def _has_request_expression(text: str) -> bool:
    return any(word in text for word in ["요청", "부탁", "진행하고 싶", "잡고 싶", "잡아주세요", "등록"])


def _dedupe(values: List[str]) -> List[str]:
    result = []
    seen = set()
    for value in values:
        value = value.strip()
        if value and value not in seen:
            result.append(value)
            seen.add(value)
    return result
