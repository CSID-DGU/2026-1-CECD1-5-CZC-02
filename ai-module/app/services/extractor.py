from typing import List, Optional
import re


try:
    from konlpy.tag import Komoran
    komoran = Komoran()
    KOMORAN_AVAILABLE = True
except Exception:
    KOMORAN_AVAILABLE = False


def extract_nouns(text: str) -> List[str]:
    if KOMORAN_AVAILABLE:
        try:
            tokens = komoran.pos(text)
            nouns = [word for word, pos in tokens if pos.startswith("NN") or pos == "SN"]
            return _dedupe(nouns)
        except Exception:
            pass

    return _extract_nouns_fallback(text)


def extract_participants(text: str, nouns: Optional[List[str]] = None) -> List[str]:
    candidates = []
    patterns = [
        r"([가-힣]{2,4})\s*(팀장|과장|차장|부장|대리|대표|이사)",
        r"(영업팀\s*[가-힣]{2,4})",
        r"([A-Za-z0-9가-힣 ]{2,20}\s*(?:고객사|Corp)\s*[가-힣]{2,4})",
    ]

    for pattern in patterns:
        for match in re.findall(pattern, text):
            if isinstance(match, tuple):
                candidates.append("".join(match))
            else:
                candidates.append(match)

    return _dedupe([candidate for candidate in candidates if _is_valid_person(candidate)])


def _extract_nouns_fallback(text: str) -> List[str]:
    tokens = re.findall(r"[가-힣A-Za-z]+|\d+원?|\d+만원?", text)
    stop_words = {"안녕하세요", "감사합니다", "부탁드립니다", "진행하고", "싶습니다"}
    return _dedupe([token for token in tokens if token not in stop_words])


def classify_activity_type(text: str) -> str:
    if any(kw in text for kw in {"회의", "미팅", "만남", "상담", "통화"}):
        return "MEETING"
    if any(kw in text for kw in {"메일", "이메일", "첨부", "견적서"}):
        return "EMAIL"
    return "TASK"


def extract_location(text: str) -> Optional[str]:
    location_patterns = [
        r"(회의실\s*\d*)",
        r"([A-Za-z0-9가-힣 ]+\s*회의실)",
        r"([A-Za-z0-9가-힣 ]+\s*카페)",
        r"([A-Za-z0-9가-힣 ]+\s*온라인)",
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
        value = re.sub(r"\s+", " ", value or "").strip(" .,")
        if value and value not in seen:
            result.append(value)
            seen.add(value)
    return result


def _is_valid_person(value: str) -> bool:
    return bool(value) and len(value) >= 2
