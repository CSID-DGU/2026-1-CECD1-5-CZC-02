import re


def preprocess_text(text: str) -> str:
    """Normalize line breaks and keep Korean business email content intact."""
    if not text:
        return ""

    normalized = text.replace("\n", " ").replace("\r", " ")
    normalized = re.sub(r"[^0-9A-Za-z가-힣\s:./,\-()·]", " ", normalized)
    normalized = re.sub(r"\s+", " ", normalized)
    return normalized.strip()
