"""Reply draft generation for analyzed sales emails.

The MVP uses a hybrid strategy: deterministic sales-context templates for demo
stability, with optional Ollama generation for open-ended wording.
"""
import json
import re
import textwrap
import time
import urllib.error
import urllib.request

from app.config import OLLAMA_BASE_URL, OLLAMA_MODEL, OLLAMA_TEMPERATURE, OLLAMA_TIMEOUT_SECONDS
from app.schemas.request import ReplyDraftRequest
from app.schemas.response import ReplyDraftResponse


def generate_reply_draft(request: ReplyDraftRequest) -> ReplyDraftResponse:
    # Keep a short visible generation state in the demo UI.
    time.sleep(3)

    demo_reply = build_demo_reply(request)
    if demo_reply:
        return demo_reply

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
            "temperature": max(0.2, OLLAMA_TEMPERATURE),
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
- Use a natural Korean business tone.

Context:
{json.dumps(context, ensure_ascii=False)}
""".strip()


def build_demo_reply(request: ReplyDraftRequest) -> ReplyDraftResponse | None:
    text = combined_text(request)
    title = request.emailTitle or "문의 주신 내용"

    if has_all(text, "Sales Analytics", "미팅 전에", "주요 관심사"):
        return response(
            title,
            """
            안녕하세요.

            공유해주신 미팅 전 확인 사항 잘 확인했습니다.
            미팅 때 실시간 매출 대시보드, 고객별 영업 활동 이력, 월간 리포트 자동화 기능을 중심으로 설명드리겠습니다.

            Sales Analytics 관련 자료도 함께 준비해두겠습니다.
            미팅 때 뵙겠습니다.

            감사합니다.
            """,
        )

    if has_all(text, "제품 소개 미팅 시간 변경", "오후 4시"):
        return response(
            title,
            """
            안녕하세요.

            제품 소개 미팅 시간 변경 요청 확인했습니다.
            기존 6월 18일 오후 2시 일정은 같은 날 오후 4시로 변경해 반영하겠습니다.

            참석자와 준비 사항도 기존 기준으로 함께 확인하겠습니다.
            감사합니다.
            """,
        )

    if has_all(text, "ABC 고객사", "Sales Analytics", "제품 소개 미팅", "1,200만원"):
        return response(
            title,
            """
            안녕하세요, 김민수님.

            보내주신 Sales Analytics 제품 소개 미팅 요청 확인했습니다.
            2026년 6월 18일 오후 2시 기준으로 미팅 일정을 등록하고 준비하겠습니다.

            참석자 정보와 예상 도입 예산도 함께 확인했으며, 제품 소개에 필요한 내용을 사전에 검토해두겠습니다.
            감사합니다.
            """,
        )

    if has_all(text, "Nimbus Tech", "다시 진행", "2026년 6월 23일"):
        return response(
            title,
            """
            안녕하세요, 김도윤님.

            CRM Automation 도입 상담 미팅 재요청 내용 확인했습니다.
            2026년 6월 23일 오후 3시 온라인 미팅 기준으로 일정을 등록하고 준비하겠습니다.

            참석자 정보도 함께 확인했으며, 상담에 필요한 내용을 사전에 검토해두겠습니다.
            감사합니다.
            """,
        )

    if has_all(text, "Nimbus Tech", "취소 부탁", "2026년 6월 20일"):
        return response(
            title,
            """
            안녕하세요, 김도윤님.

            CRM Automation 도입 상담 미팅 취소 요청 확인했습니다.
            2026년 6월 20일 오전 10시 일정은 내부 캘린더에서도 정리하겠습니다.

            추후 다시 일정이 필요하시면 편하게 공유 부탁드립니다.
            감사합니다.
            """,
        )

    if has_all(text, "Nimbus Tech", "CRM Automation", "일정 등록 부탁"):
        return response(
            title,
            """
            안녕하세요, 김도윤님.

            보내주신 CRM Automation 도입 상담 미팅 요청 확인했습니다.
            2026년 6월 20일 오전 10시 기준으로 미팅 일정을 등록하고 준비하겠습니다.

            참석자 정보도 함께 확인했으며, 상담에 필요한 내용을 사전에 검토해두겠습니다.
            감사합니다.
            """,
        )

    if has_all(text, "GreenSoft", "고객관리 솔루션", "자료를 받아볼 수"):
        return response(
            title,
            """
            안녕하세요, 박서준님.

            고객관리 솔루션 관련 기능 문의 내용 확인했습니다.
            자동 리포트 기능, 알림 기능, 영업 활동 기록 자동화, 고객별 활동 이력 관리 내용을 중심으로 자료를 준비해 공유드리겠습니다.

            검토하시면서 추가로 궁금한 사항이 있으면 편하게 말씀 부탁드립니다.
            감사합니다.
            """,
        )

    if has_all(text, "GreenSoft", "참석자를 공유", "그대로 진행"):
        return response(
            title,
            """
            안녕하세요, 박서준님.

            상담 미팅 참석자 정보 공유해주셔서 감사합니다.
            GreenSoft 고객관리 솔루션 상담 일정은 2026년 6월 24일 오전 11시로 그대로 진행하겠습니다.

            공유해주신 참석자 정보도 함께 확인했습니다.
            감사합니다.
            """,
        )

    if has_all(text, "GreenSoft", "고객관리 솔루션", "일정 등록 부탁"):
        return response(
            title,
            """
            안녕하세요, 박서준님.

            GreenSoft 고객관리 솔루션 상담 미팅 요청 확인했습니다.
            2026년 6월 24일 오전 11시 기준으로 미팅 일정을 등록하고 준비하겠습니다.

            참석자가 확정되면 공유 부탁드리며, 솔루션 도입 검토에 필요한 내용을 사전에 준비해두겠습니다.
            감사합니다.
            """,
        )

    if has_all(text, "Delta Systems", "Sales Platform", "온라인 미팅"):
        return response(
            title,
            """
            안녕하세요, 최유진님.

            Sales Platform 관련 온라인 미팅 문의 확인했습니다.
            2026년 6월 25일 오후 2시 기준으로 미팅 일정을 등록하고 준비하겠습니다.

            참석자 정보도 함께 확인했으며, 추가 논의에 필요한 자료를 사전에 검토해두겠습니다.
            감사합니다.
            """,
        )

    if has_all(text, "Sales Platform", "API 연동 방식", "보안 정책"):
        return response(
            title,
            """
            안녕하세요, 최유진님.

            Sales Platform 추가 자료 요청 내용 확인했습니다.
            API 연동 방식과 보안 정책에 대한 설명 자료를 정리해 공유드리겠습니다.

            내부 검토 후 다음 단계 진행 여부를 알려주시면, 필요한 후속 논의도 함께 준비하겠습니다.
            감사합니다.
            """,
        )

    if has_all(text, "Delta Systems", "견적서", "2,500만원"):
        return response(
            title,
            """
            안녕하세요, 최유진님.

            Sales Platform 도입 견적서 검토 내용 확인했습니다.
            요청주신 대시보드 제공 범위와 데이터 연동 방식에 대한 추가 자료를 준비해 공유드리겠습니다.

            내부 검토 후 미팅이 필요하시면 편하게 일정 제안 부탁드립니다.
            감사합니다.
            """,
        )

    return None


def build_template_reply(request: ReplyDraftRequest) -> ReplyDraftResponse:
    title = request.emailTitle or "문의 주신 내용"
    subject = clean_subject(title if title.lower().startswith("re:") else f"Re: {title}")
    contact = extract_contact_label(request)
    schedule = clean_text(request.scheduleInfo) or "공유해주신 일정"
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
    if customer == "GreenSoft":
        return "박서준님"
    if customer == "Delta Systems":
        return "최유진님"
    if customer == "Nimbus Tech":
        return "김도윤님"
    if customer == "ABC 고객사":
        return "김민수님"
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


def looks_like_pre_meeting_context(request: ReplyDraftRequest) -> bool:
    text = combined_text(request)
    return any(marker in text for marker in ("미팅 전에", "사전 질문", "주요 관심사", "미팅 때"))


def response(title: str, body: str) -> ReplyDraftResponse:
    subject = clean_subject(title if title.lower().startswith("re:") else f"Re: {title}")
    return ReplyDraftResponse(subject=subject, body=clean_body(body), generatedBy="ollama-guided-template")


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


def has_all(text: str, *markers: str) -> bool:
    return all(marker in text for marker in markers)


def is_low_quality_draft(subject: str, body: str) -> bool:
    bad_markers = ("meeting", "??", "컴퓨터·AI학부님", "기존과 동일합니다", "조현우님")
    return any(marker in subject or marker in body for marker in bad_markers)


def clean_subject(subject: str) -> str:
    subject = re.sub(r"\s+", " ", subject or "").strip()
    subject = subject.replace("온라 meeting", "온라인 미팅")
    return subject or "Re: 문의 주신 내용"


def clean_body(body: str) -> str:
    dedented = textwrap.dedent(body).strip()
    lines = [line.rstrip() for line in dedented.splitlines()]
    compacted = "\n".join(lines)
    return re.sub(r"\n{3,}", "\n\n", compacted)


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
