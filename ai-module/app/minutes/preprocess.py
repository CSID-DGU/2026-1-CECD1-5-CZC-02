"""회의록 엔진 입력 전처리.

근거(evidence)는 원문 글자 그대로여야 하므로 문장을 고쳐 쓰지 않는다. 줄바꿈 통일과 줄 단위 삭제(메일의 인용·서명)만 한다.

메일 스레드는 백엔드가 메일마다 구분 줄을 넣어 한 텍스트로 보낸다 (B-5):
    ------ 메일 2/3 · 2026-10-14 15:00 · 홍길동 ------
메일마다 아래쪽에 이전 메일 인용이 붙어 있으므로 구분 줄로 나눠 메일별로 인용·서명을 빼고, 구분 줄은 남긴다
(누가 언제 쓴 말인지 LLM이 알 수 있게). 구분 줄 형식을 바꾸면 백엔드 MailThreadText도 같이 바꾼다.
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
# 스레드 안 메일 사이 구분 줄 (백엔드가 넣음)
THREAD_SEPARATOR = re.compile(r"^-{4,} 메일 \d+/\d+ .*-{4,}$")


def normalize_newlines(text: str) -> str:
    return (text or "").replace("\r\n", "\n").replace("\r", "\n")


def clean_email_body(text: str) -> str:
    """메일 한 통에서 이전 메일 인용과 서명(-- 구분선 아래)을 줄 단위로 뺀다."""
    kept: List[str] = []
    for line in normalize_newlines(text).split("\n"):
        stripped = line.strip()
        if SIGNATURE_DELIMITER.match(stripped) or any(p.match(stripped) for p in REPLY_HEADER_PATTERNS):
            break
        if stripped.startswith(">"):
            continue
        kept.append(line)
    return "\n".join(kept).strip()


def clean_email_thread(text: str) -> str:
    """구분 줄로 나눈 메일마다 인용·서명을 빼고 구분 줄과 함께 다시 잇는다. 구분 줄이 없으면 메일 한 통으로 본다."""
    parts: List[str] = []
    current: List[str] = []
    for line in normalize_newlines(text).split("\n"):
        if THREAD_SEPARATOR.match(line.strip()):
            body = clean_email_body("\n".join(current))
            if body:
                parts.append(body)
            parts.append(line.strip())
            current = []
        else:
            current.append(line)
    body = clean_email_body("\n".join(current))
    if body:
        parts.append(body)
    return "\n\n".join(parts).strip()


def prepare_source(text: str, is_email: bool) -> str:
    """LLM에 보내고 근거를 대조할 원문. 메일이면 메일마다 인용·서명을 뺀다."""
    return clean_email_thread(text) if is_email else normalize_newlines(text).strip()
