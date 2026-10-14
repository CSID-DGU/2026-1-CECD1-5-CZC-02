"""회의록 엔진 입력 전처리.

근거(evidence)는 원문 글자 그대로여야 하므로 문장을 고쳐 쓰지 않는다. 줄바꿈 통일과 줄 단위 삭제(메일의 인용·서명)만 한다.
"""
import re
from typing import List

# 이 줄부터 아래는 이전 메일 인용으로 보고 버린다.
REPLY_HEADER_PATTERNS = [
    re.compile(r"^-{2,}\s*(original message|원본 메시지|원래 메시지|forwarded message|전달된 메시지)\s*-{2,}$", re.I),
    re.compile(r"^on .+ wrote:$", re.I),
    re.compile(r"^\d{4}[년./-].*(작성|wrote)[^:]*:$"),
    re.compile(r"^(from|보낸 사람|발신자?)\s*:", re.I),
]
SIGNATURE_DELIMITER = re.compile(r"^--\s*$")


def normalize_newlines(text: str) -> str:
    return (text or "").replace("\r\n", "\n").replace("\r", "\n")


def clean_email_body(text: str) -> str:
    """메일 본문에서 이전 메일 인용과 서명(-- 구분선 아래)을 줄 단위로 뺀다."""
    kept: List[str] = []
    for line in normalize_newlines(text).split("\n"):
        stripped = line.strip()
        if SIGNATURE_DELIMITER.match(stripped) or any(p.match(stripped) for p in REPLY_HEADER_PATTERNS):
            break
        if stripped.startswith(">"):
            continue
        kept.append(line)
    return "\n".join(kept).strip()


def prepare_source(text: str, is_email: bool) -> str:
    """LLM에 보내고 근거를 대조할 원문. 메일이면 인용·서명을 뺀다."""
    return clean_email_body(text) if is_email else normalize_newlines(text).strip()
