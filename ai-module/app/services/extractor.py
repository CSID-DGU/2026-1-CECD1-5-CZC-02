"""Komoran 형태소 분석 기반 정보 추출"""
from typing import List, Optional, Set
import re

# Komoran 설치 필요 (konlpy 라이브러리)
try:
    from konlpy.tag import Komoran
    komoran = Komoran()
    KOMORAN_AVAILABLE = True
except:
    KOMORAN_AVAILABLE = False


def extract_participants(text: str) -> List[str]:
    """
    텍스트에서 참여자/인물명 추출
    형태소 분석을 통해 명사 중 인물명 추출
    """
    participants = []
    
    if KOMORAN_AVAILABLE:
        try:
            # Komoran 형태소 분석
            tokens = komoran.pos(text)
            
            # NNP (고유명사), NNG (일반명사) 중 사람 관련 추출
            for word, pos in tokens:
                if pos in ['NNP', 'NNG']:
                    # 제외할 단어들
                    exclude_words = {'회의', '통화', '전화', '이메일', '메시지', '일정', '업무', '프로젝트'}
                    if word not in exclude_words and len(word) > 1:
                        # 직급/직책 패턴 (대리, 과장, 팀장 등)
                        if any(title in word for title in ['대리', '과장', '팀장', '이사', '부장']):
                            participants.append(word)
                        # 숫자, 특수문자 제외
                        elif not re.search(r'[\d\W]', word):
                            participants.append(word)
        except Exception as e:
            # Komoran 실패 시 폴백
            print(f"Komoran 분석 실패: {e}")
            participants = _extract_participants_fallback(text)
    else:
        participants = _extract_participants_fallback(text)
    
    # 중복 제거 및 정렬
    return list(set(participants))


def _extract_participants_fallback(text: str) -> List[str]:
    """
    Komoran 실패 시 폴백: 정규식 기반 추출
    """
    participants = []
    
    # 패턴: "김대리", "이과장" 등 (한글명 + 직책)
    pattern = r'([가-힣]+)(대리|과장|팀장|이사|부장|님|선생|씨|님께)'
    matches = re.findall(pattern, text)
    for name, title in matches:
        participants.append(f"{name}{title}")
    
    return list(set(participants))


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
