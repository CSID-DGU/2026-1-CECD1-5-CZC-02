import re


def preprocess_text(text: str) -> str:
    """개행, 앞뒤 공백, 분석에 불필요한 특수문자를 정리합니다."""
    if not text:
        return ""

    normalized = text.replace("\n", " ").replace("\r", " ")
    normalized = re.sub(r"[^0-9A-Za-z가-힣\s:./-]", "", normalized)
    normalized = re.sub(r"\s+", " ", normalized)
    return normalized.strip()
