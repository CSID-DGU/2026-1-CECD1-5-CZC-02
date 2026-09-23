"""Reply draft generation for analyzed sales emails.

규칙 기반 템플릿을 기본값(fallback)으로 두고, Ollama LLM이 더 자연스러운 초안을
만들면 그 결과로 대체하는 하이브리드 전략을 쓴다.
"""
import json
import re
import urllib.error
import urllib.request

from app.config import OLLAMA_BASE_URL, OLLAMA_MODEL, OLLAMA_TEMPERATURE, OLLAMA_TIMEOUT_SECONDS
from app.schemas.request import ReplyDraftRequest
from app.schemas.response import ReplyDraftResponse


def generate_reply_draft(request: ReplyDraftRequest) -> ReplyDraftResponse:
    # 규칙 기반 템플릿을 기본값으로 두고, Ollama가 더 자연스러운 초안을 만들면 그것으로 대체한다.
    template = build_template_reply(request)

    try:
        return generate_with_ollama(request, template)
    except Exception:
        return template


def generate_with_ollama(request: ReplyDraftRequest, fallback: ReplyDraftResponse) -> ReplyDraftResponse:
    prompt = build_prompt(request, fallback)
    payload = {
        "model": OLLAMA_MODEL,
        "prompt": prompt,
        "stream": False,
        "format": "json",
        "options": {
            "temperature": OLLAMA_TEMPERATURE,
            "num_predict": 500,
        },
    }

    http_request = urllib.request.Request(
        f"{OLLAMA_BASE_URL.rstrip('/')}/api/generate",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(http_request, timeout=OLLAMA_TIMEOUT_SECONDS) as response:
            raw_body = response.read().decode("utf-8")
    except (urllib.error.URLError, TimeoutError) as exception:
        raise RuntimeError(f"Ollama reply draft request failed: {exception}") from exception

    envelope = json.loads(raw_body)
    response_text = envelope.get("response")
    if not isinstance(response_text, str) or not response_text.strip():
        raise RuntimeError("Ollama reply draft response is empty.")

    data = parse_json_object(response_text)
    subject = clean_subject(clean_text(data.get("subject")) or fallback.subject)
    body = clean_text(data.get("body"), multiline=True) or fallback.body

    if is_low_quality_draft(subject, body):
        return fallback

    return ReplyDraftResponse(subject=subject, body=body, generatedBy=f"ollama:{OLLAMA_MODEL}")


def build_prompt(request: ReplyDraftRequest, fallback: ReplyDraftResponse) -> str:
    context = {
        "emailTitle": request.emailTitle,
        "emailContent": request.emailContent[:4000],
        "senderEmail": request.senderEmail,
        "customerName": request.customerName,
        "contactName": request.contactName,
        "productName": request.productName,
        "attendees": request.attendees,
        "actionType": request.actionType,
        "scheduleInfo": request.scheduleInfo,
        "summary": request.summary,
        "nextAction": request.nextAction,
        "fallback": fallback.model_dump(),
    }

    return f"""
You are a Korean B2B sales assistant.
Write a polite Korean email reply draft based on the original customer email and AI analysis.

Rules:
- Output JSON only.
- JSON fields: subject, body.
- Keep the body concise: 4 to 7 sentences.
- Do not invent pricing, contract terms, or promises.
- Prefer the fallback subject/body structure if the analysis context is clear.
- Remove awkward internal labels like department names from the recipient greeting.
- If the customer shares pre-meeting questions, say those topics will be prepared for the meeting.
- If the customer asks for materials, say the requested materials will be prepared/shared.
- If actionType is CREATE, acknowledge the proposed meeting schedule and preparation.
- If actionType is UPDATE, acknowledge the changed schedule.
- If actionType is CANCEL, acknowledge the cancellation politely.
- If actionType is CONFIRM, confirm receipt and say the schedule will proceed as shared.
- Express dates and times in natural Korean like "7월 29일 오후 3시". Never copy machine formats such as "2026-07-29 15:00" into the body.
- Use grammatically correct, natural Korean. Avoid awkward particles or phrases (e.g. do not write "계신 말씀대로"; prefer "말씀해주신 대로" or "주신 일정대로").
- Write ONLY in Korean (Hangul). Never output Chinese characters or Chinese/English sentences in the subject or body.
- Use a natural Korean business tone.

Context:
{json.dumps(context, ensure_ascii=False)}
""".strip()


def build_template_reply(request: ReplyDraftRequest) -> ReplyDraftResponse:
    title = request.emailTitle or "문의 주신 내용"
    subject = clean_subject(title if title.lower().startswith("re:") else f"Re: {title}")
    contact = extract_contact_label(request)
    schedule = format_schedule_natural(request.scheduleInfo)
    product = clean_text(request.productName)

    if looks_like_pre_meeting_context(request):
        body = "\n\n".join([
            f"안녕하세요, {contact}.",
            "공유해주신 미팅 전 확인 사항 잘 확인했습니다.",
            f"{product + ' 관련 ' if product else ''}논의 주제를 중심으로 자료와 설명 내용을 준비하겠습니다.",
            "미팅 때 뵙겠습니다.",
            "감사합니다.",
        ])
        return ReplyDraftResponse(subject=subject, body=body, generatedBy="hybrid-template")

    action_type = (request.actionType or "UNKNOWN").upper()
    if action_type == "CREATE":
        body = "\n\n".join([
            f"안녕하세요, {contact}.",
            f"보내주신 내용 확인했습니다. {schedule} 기준으로 미팅 일정을 등록하고 준비하겠습니다.",
            f"{product + ' 관련 ' if product else ''}논의에 필요한 내용을 사전에 검토해두겠습니다.",
            "감사합니다.",
        ])
    elif action_type == "UPDATE":
        body = "\n\n".join([
            f"안녕하세요, {contact}.",
            f"일정 변경 요청 확인했습니다. 변경된 일정은 {schedule} 기준으로 반영하겠습니다.",
            "참석자 및 준비 사항도 함께 확인하겠습니다.",
            "감사합니다.",
        ])
    elif action_type == "CANCEL":
        body = "\n\n".join([
            f"안녕하세요, {contact}.",
            "미팅 취소 요청 확인했습니다. 해당 일정은 내부 캘린더에서도 정리하겠습니다.",
            "추후 다시 일정이 필요하시면 편하게 공유 부탁드립니다.",
            "감사합니다.",
        ])
    elif action_type == "CONFIRM":
        body = "\n\n".join([
            f"안녕하세요, {contact}.",
            f"공유해주신 내용 확인했습니다. {schedule} 일정은 전달주신 내용대로 진행하겠습니다.",
            "참석자 정보도 함께 확인했습니다.",
            "감사합니다.",
        ])
    else:
        body = "\n\n".join([
            f"안녕하세요, {contact}.",
            "보내주신 내용 확인했습니다.",
            "검토 후 필요한 사항이 있으면 추가로 연락드리겠습니다.",
            "감사합니다.",
        ])

    return ReplyDraftResponse(subject=subject, body=body, generatedBy="hybrid-template")


def extract_contact_label(request: ReplyDraftRequest) -> str:
    contact = clean_text(request.contactName)
    if is_good_person_name(contact):
        return f"{strip_company_prefix(contact)}님"

    customer = clean_text(request.customerName)
    if customer:
        return f"{customer} 담당자님"

    return "담당자님"


def strip_company_prefix(value: str) -> str:
    if not value:
        return value
    parts = value.split()
    if len(parts) >= 2 and (
        parts[0].endswith("Tech")
        or parts[0].endswith("Systems")
        or parts[0].endswith("Soft")
        or parts[0].endswith("고객사")
    ):
        return parts[-1]
    return value


def is_good_person_name(value: str | None) -> bool:
    if not value:
        return False
    blocked = ("학부", "컴퓨터", "기존과 동일", "확인 부탁", "영업팀", "운영팀", "기술팀")
    if any(marker in value for marker in blocked):
        return False
    return len(value) <= 30


def format_schedule_natural(schedule_info: str | None) -> str:
    """'2026-07-28 14:00' 같은 기계 포맷을 '7월 28일 오후 2시'처럼 자연스럽게 바꾼다."""
    text = clean_text(schedule_info)
    if not text:
        return "공유해주신 일정"
    match = re.search(r"(\d{4})-(\d{1,2})-(\d{1,2})[ T](\d{1,2}):(\d{2})", text)
    if not match:
        return text
    _, month, day, hour, minute = match.groups()
    hour, minute = int(hour), int(minute)
    meridiem = "오전" if hour < 12 else "오후"
    hour12 = hour % 12 or 12
    time_str = f"{meridiem} {hour12}시" + (f" {minute}분" if minute else "")
    return f"{int(month)}월 {int(day)}일 {time_str}"


def looks_like_pre_meeting_context(request: ReplyDraftRequest) -> bool:
    text = combined_text(request)
    return any(marker in text for marker in ("미팅 전에", "사전 질문", "주요 관심사", "미팅 때"))


def combined_text(request: ReplyDraftRequest) -> str:
    values = (
        request.emailTitle,
        request.emailContent,
        request.customerName,
        request.contactName,
        request.productName,
        request.attendees,
        request.actionType,
        request.scheduleInfo,
        request.summary,
        request.nextAction,
    )
    return " ".join(value for value in values if isinstance(value, str))


def is_low_quality_draft(subject: str, body: str) -> bool:
    text = f"{subject} {body}"
    bad_markers = ("meeting", "??", "기존과 동일합니다")
    if any(marker in text for marker in bad_markers):
        return True
    # 로컬 모델이 간혹 중국어(한자)로 새는 경우를 차단하고 한국어 템플릿으로 폴백한다.
    if re.search(r"[一-鿿]", text):
        return True
    return False


def clean_subject(subject: str) -> str:
    subject = re.sub(r"\s+", " ", subject or "").strip()
    subject = subject.replace("온라 meeting", "온라인 미팅")
    return subject or "Re: 문의 주신 내용"


def parse_json_object(value: str) -> dict:
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", value, re.DOTALL)
        if not match:
            raise RuntimeError("Reply draft response does not contain JSON.")
        parsed = json.loads(match.group(0))
    if not isinstance(parsed, dict):
        raise RuntimeError("Reply draft response is not JSON object.")
    return parsed


def clean_text(value, multiline: bool = False) -> str | None:
    if not isinstance(value, str):
        return None
    text = value.strip()
    if not text:
        return None
    if multiline:
        return re.sub(r"\n{3,}", "\n\n", text)
    return re.sub(r"\s+", " ", text)
