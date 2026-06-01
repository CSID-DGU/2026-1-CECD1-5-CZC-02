"""Komoran 형태소 분석 기반 정보 추출"""
from typing import List, Optional
import re

# Komoran 설치 필요 (konlpy 라이브러리)
try:
    from konlpy.tag import Komoran
    komoran = Komoran()
    KOMORAN_AVAILABLE = True
except Exception:
    KOMORAN_AVAILABLE = False


def extract_nouns(text: str) -> List[str]:
    """Komoran으로 명사를 추출하고, 실패 시 규칙 기반 토큰으로 대체합니다."""
    if KOMORAN_AVAILABLE:
        try:
            tokens = komoran.pos(text)
            nouns = [word for word, pos in tokens if pos.startswith("NN") or pos == "SN"]
            return _dedupe(nouns)
        except Exception:
            pass

    return _extract_nouns_fallback(text)


def extract_participants(text: str, nouns: Optional[List[str]] = None) -> List[str]:
    """직급/호칭 패턴을 중심으로 참여자를 추출합니다."""
    candidates = []
    pattern = r"([가-힣]{1,5})(대리|과장|차장|부장|팀장|이사|대표|님|씨)"
    for name, title in re.findall(pattern, text):
        if title in {"님", "씨"}:
            candidates.append(name)
        else:
            candidates.append(f"{name}{title}")

    if nouns:
        title_words = {"대리", "과장", "차장", "부장", "팀장", "이사", "대표"}
        for index, noun in enumerate(nouns):
            if noun in title_words and index > 0:
                previous = nouns[index - 1]
                if previous not in title_words:
                    candidates.append(f"{previous}{noun}")

    return _dedupe([candidate for candidate in candidates if _is_valid_person(candidate)])


def _extract_nouns_fallback(text: str) -> List[str]:
    tokens = re.findall(r"[가-힣A-Za-z]+|\d+시", text)
    stop_words = {"가능", "하실까요", "해주세요", "입니다", "합니다"}
    return _dedupe([token for token in tokens if token not in stop_words])


def classify_activity_type(text: str) -> str:
    """
    활동 유형 분류 (MEETING, CALL, EMAIL, TASK)
    규칙 기반 분류
    """
    text_lower = text.lower()
    
    # 회의/미팅 키워드
    meeting_keywords = {'회의', '미팅', '만남', '협의', '토론', '회담', '논의', '세션', '리뷰'}
    if any(kw in text for kw in meeting_keywords):
        return "MEETING"
    
    # 통화/전화 키워드
    call_keywords = {'통화', '전화', '전화주', '전화받', '통화요청', '콜', '전화기'}
    if any(kw in text for kw in call_keywords):
        return "CALL"
    
    # 이메일 관련 키워드
    email_keywords = {'이메일', '메일', '메시지', '발송', '회신', 'email', 'mail'}
    if any(kw in text_lower for kw in email_keywords):
        return "EMAIL"
    
    # 업무/태스크 키워드
    task_keywords = {'업무', '작업', '태스크', '일정', '과제', '프로젝트', '수행', '완료', '진행', '처리'}
    if any(kw in text for kw in task_keywords):
        return "TASK"
    
    # 기본값: 일정으로 분류
    return "MEETING"


def extract_location(text: str) -> Optional[str]:
    """
    장소 추출
    패턴: "회의실", "스타벅스", "카페" 등
    """
    location_patterns = [
        r'(회의실\s*\d+)',
        r'([\w가-힣]*\s*회의실)',
        r'([\w가-힣]*\s*카페)',
        r'([\w가-힣]*\s*식당)',
        r'([\w가-힣]*\s*사무실)',
    ]
    
    for pattern in location_patterns:
        match = re.search(pattern, text)
        if match:
            return match.group(1).strip()
    
    return None


def _dedupe(values: List[str]) -> List[str]:
    result = []
    seen = set()
    for value in values:
        value = value.strip()
        if value and value not in seen:
            result.append(value)
            seen.add(value)
    return result


def _is_valid_person(value: str) -> bool:
    title_words = ["대리", "과장", "차장", "부장", "팀장", "이사", "대표"]
    if value in title_words:
        return False
    title_count = sum(1 for title in title_words if title in value)
    if title_count > 1:
        return False
    return len(value) >= 2
