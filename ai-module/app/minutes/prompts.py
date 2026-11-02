"""회의록 LLM 지시문·출력 스키마. 문항 나누는 규칙은 decisions.md D-018과 eval/minutes/README.md를 따른다.

출력 스키마는 OpenAI Structured Outputs(strict)에도 쓰이므로 모든 필드를 required로 두고, 없는 값은 빈 배열·null이다.
"""
import json
from typing import Any, Dict

FIELD_NAMES = ("discussion", "customerRequests", "concerns", "commitments", "nextContact")
PER_PERSON_FIELDS = ("status", "request", "followUp")

ITEM_SCHEMA: Dict[str, Any] = {
    "type": "object",
    "additionalProperties": False,
    "required": ["text", "evidence"],
    "properties": {"text": {"type": "string"}, "evidence": {"type": "string"}},
}

FULL_OUTPUT_SCHEMA: Dict[str, Any] = {
    "type": "object",
    "additionalProperties": False,
    "required": list(FIELD_NAMES),
    "properties": {name: {"type": "array", "items": ITEM_SCHEMA} for name in FIELD_NAMES},
}

LIGHT_OUTPUT_SCHEMA: Dict[str, Any] = {
    "type": "object",
    "additionalProperties": False,
    "required": ["perPerson"],
    "properties": {
        "perPerson": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": ["displayName", *PER_PERSON_FIELDS],
                "properties": {
                    "displayName": {"type": "string"},
                    **{name: {"anyOf": [ITEM_SCHEMA, {"type": "null"}]} for name in PER_PERSON_FIELDS},
                },
            },
        }
    },
}

EVIDENCE_RULES = """- evidence에는 근거가 된 메모 원문 일부를 글자 그대로 복사한다. 고치거나 요약하거나 이어 붙이지 않는다.
- 메모에 없는 내용은 만들지 않는다. 추측하지 않는다.
- 회의와 무관한 잡담(취미·가족·식사·날씨·회의 진행 문제 등)은 넣지 않는다.
- 기한 표현은 원문 그대로 둔다. 예: "이번 주 금요일", "다음 달 초"
- JSON 객체만 출력한다. 설명·마크다운·코드블록 금지."""

# 고정 지시문 + 예시. 매 요청 동일하므로 프롬프트 캐시 대상이 되도록 system에 둔다. 예시는 평가셋과 겹치지 않는 가짜 메모.
FULL_SYSTEM_PROMPT = f"""너는 한국어 B2B 영업 미팅 메모를 표준 회의록으로 정리하는 보조자다.
메모를 읽고 아래 5개 문항을 JSON으로 반환한다. 항목 하나 = 사실·요청·약속 하나이고, 각 항목은 text(짧은 요약)와 evidence(원문 인용)를 가진다.

문항:
- discussion: 오간 내용 중 아래 4개 문항에 속하지 않는 것
- customerRequests: 고객이 원하거나 요청·문의한 것
- concerns: 고객의 걱정·불만·위험 요소
- commitments: 결과물이 있는 약속. 고객이 하기로 한 일은 text 끝에 "(고객 측)"
- nextContact: 다음 만남·연락 계획. 같은 내용을 commitments에 겹쳐 넣지 않는다
- 고객이 내용을 알려주기로 하면 commitments(고객 측), 연락만 주기로 하면 nextContact

규칙:
- 해당 내용이 없는 문항은 빈 배열 []
{EVIDENCE_RULES}

예시)
메모:
가상상사 홍길동 차장 미팅
- 신규 서버 도입 예산 확보됐다고 함
- 설치 일정이 연말 휴가와 겹칠까 걱정하심
- 설치 매뉴얼 미리 받아볼 수 있는지 물어보심
- 견적서는 내일까지 보내드리기로 함
- 차장님이 담당 엔지니어 연락처 알려주신다고 함
- 다음 달 초에 다시 방문하기로
출력:
{{"discussion": [{{"text": "신규 서버 도입 예산 확보", "evidence": "신규 서버 도입 예산 확보됐다고 함"}}], "customerRequests": [{{"text": "설치 매뉴얼 사전 제공 문의", "evidence": "설치 매뉴얼 미리 받아볼 수 있는지 물어보심"}}], "concerns": [{{"text": "설치 일정이 연말 휴가와 겹칠 우려", "evidence": "설치 일정이 연말 휴가와 겹칠까 걱정하심"}}], "commitments": [{{"text": "견적서 내일까지 송부", "evidence": "견적서는 내일까지 보내드리기로 함"}}, {{"text": "담당 엔지니어 연락처 공유 (고객 측)", "evidence": "차장님이 담당 엔지니어 연락처 알려주신다고 함"}}], "nextContact": [{{"text": "다음 달 초 재방문", "evidence": "다음 달 초에 다시 방문하기로"}}]}}"""

LIGHT_SYSTEM_PROMPT = f"""너는 한국어 B2B 영업 메모를 정리하는 보조자다. 이 메모는 여러 사람을 짧게 차례로 만난 기록이다.
만난 사람마다 아래 3칸을 JSON으로 반환한다. 각 칸은 text(짧은 요약)와 evidence(원문 인용)를 가진 객체이거나, 내용이 없으면 null이다.

칸:
- status: 그 사람의 근황·상황
- request: 그 사람이 요청·문의한 것
- followUp: 우리 또는 그 사람이 하기로 한 후속 조치

규칙:
- displayName은 "만난 사람" 목록의 이름을 그대로 쓴다. 목록이 없으면 메모에 적힌 이름을 그대로 쓴다.
- 한 사람의 내용을 다른 사람 칸에 넣지 않는다.
{EVIDENCE_RULES}

예시)
만난 사람: 홍길동 차장, 김가상 과장
메모:
가상상사 층별 인사
홍길동 차장: 신규 서버 예산 확보됨. 설치 매뉴얼 미리 달라고 하심
김가상 과장: 휴가 복귀 인사만 드림
출력:
{{"perPerson": [{{"displayName": "홍길동 차장", "status": {{"text": "신규 서버 예산 확보", "evidence": "신규 서버 예산 확보됨."}}, "request": {{"text": "설치 매뉴얼 사전 제공 요청", "evidence": "설치 매뉴얼 미리 달라고 하심"}}, "followUp": null}}, {{"displayName": "김가상 과장", "status": {{"text": "휴가 복귀 인사", "evidence": "휴가 복귀 인사만 드림"}}, "request": null, "followUp": null}}]}}"""


# ---------- 메일 (B-5 이후) ----------
# 메모 지시문을 그대로 쓰면 메일에서 놓치는 것이 많았다 (개발용 메일 eval/minutes/dev로 확인):
# 누가 쓴 메일인지 몰라 고객 약속을 우리 일·다음 연락으로 넣고, 상황 공유(조직·예산)와 공손한 질문을 빠뜨린다.
# 그래서 메일일 때만 아래 지시문·예시를 쓴다. 메모 지시문은 그대로 둔다.
# 예시는 개발용·평가용 메일과 겹치지 않는 가짜 메일이다 (tests/test_minutes_engine.py에서 검사).

MAIL_EXAMPLE_COUNTERPARTS = ["마예제 대리"]
MAIL_EXAMPLE_TEXT = """------ 메일 1/2 · 2026-09-01 10:00 · 마예제 ------
안녕하세요, 예제산업 마예제입니다.
다음 달부터 물류팀이 본사로 옮기는데 제가 계속 담당합니다.
라벨 프린터 연동이 되는지, 그리고 교육을 현장에서 해주실 수 있는지 궁금합니다.
보안 서약서 양식은 제가 이번 주 안에 보내드리겠습니다.
------ 메일 2/2 · 2026-09-01 15:00 · 노가상 ------
대리님, 라벨 프린터는 기본으로 연동됩니다.
현장 교육 일정안은 다음 주 수요일까지 보내드리겠습니다.
양식 받으면 확인하고 다음 주 금요일에 전화드리겠습니다."""
MAIL_EXAMPLE_OUTPUT = {
    "discussion": [
        {"text": "물류팀 본사 이전, 마예제 대리가 계속 담당", "evidence": "다음 달부터 물류팀이 본사로 옮기는데 제가 계속 담당합니다."},
        {"text": "라벨 프린터 기본 연동 안내", "evidence": "대리님, 라벨 프린터는 기본으로 연동됩니다."},
    ],
    "customerRequests": [
        {"text": "라벨 프린터 연동 여부 문의", "evidence": "라벨 프린터 연동이 되는지"},
        {"text": "현장 교육 가능 여부 문의", "evidence": "교육을 현장에서 해주실 수 있는지 궁금합니다."},
    ],
    "concerns": [],
    "commitments": [
        {"text": "보안 서약서 양식 이번 주 안에 송부 (고객 측)", "evidence": "보안 서약서 양식은 제가 이번 주 안에 보내드리겠습니다."},
        {"text": "현장 교육 일정안 다음 주 수요일까지 송부", "evidence": "현장 교육 일정안은 다음 주 수요일까지 보내드리겠습니다."},
    ],
    "nextContact": [
        {"text": "양식 확인 후 다음 주 금요일 전화", "evidence": "양식 받으면 확인하고 다음 주 금요일에 전화드리겠습니다."},
    ],
}

MAIL_FULL_SYSTEM_PROMPT = f"""너는 한국어 B2B 영업 메일(한 통 또는 주고받은 여러 통)을 표준 회의록으로 정리하는 보조자다.
메일을 읽고 아래 5개 문항을 JSON으로 반환한다. 항목 하나 = 사실·요청·약속 하나이고, 각 항목은 text(짧은 요약)와 evidence(원문 인용)를 가진다.

문항:
- discussion: 오간 내용 중 아래 4개 문항에 속하지 않는 것. 고객이 알려준 상황(조직·담당자 변경, 예산·검토 진행 상황, 현재 쓰는 방식)과 우리가 답한 설명도 여기에 넣는다
- customerRequests: 고객이 원하거나 요청·문의한 것. "~인지 궁금합니다", "~알려주시면", "~확인 부탁드립니다", "~검토 부탁드립니다"처럼 공손하게 물은 것도 요청이다. 한 문장에 요청이 여러 개면 각각 따로 넣는다
- concerns: 고객의 걱정·불만·위험 요소
- commitments: 결과물이 있는 약속. 고객이 하기로 한 일은 text 끝에 "(고객 측)"
- nextContact: 다음 만남·연락 계획. 같은 내용을 commitments에 겹쳐 넣지 않는다
- 고객이 내용을 보내거나 알려주기로 하면 commitments(고객 측), 연락만 주기로 하면 nextContact

누가 쓴 말인지:
- "------ 메일 2/3 · 시각 · 보낸 사람 ------" 줄 아래는 그 사람이 쓴 메일이다
- "상대(고객 측)" 목록에 있는 사람이 쓴 메일은 고객이 쓴 것이다. 그 메일의 "제가/저희가 ~하겠습니다"는 고객의 약속이다
- 목록에 없는 보낸 사람(우리 영업 담당)이 쓴 "~드리겠습니다"는 우리의 약속이다
- 구분 줄이 없으면 메일 한 통이다. 인사말("~입니다")과 내용으로 보낸 사람을 판단한다

규칙:
- 해당 내용이 없는 문항은 빈 배열 []
- 인사·감사·안부·날씨 같은 말은 넣지 않는다
{EVIDENCE_RULES}

예시)
상대(고객 측): {", ".join(MAIL_EXAMPLE_COUNTERPARTS)}
메일:
{MAIL_EXAMPLE_TEXT}
출력:
{json.dumps(MAIL_EXAMPLE_OUTPUT, ensure_ascii=False)}"""


def build_user_prompt(text: str, occurred_at, location, names, max_chars: int = 6000, is_email: bool = False) -> str:
    if is_email:
        meta = f"일시(마지막 메일): {occurred_at or '모름'} / 상대(고객 측): {', '.join(names) or '모름'}"
        return f"{meta}\n\n메일:\n{text[:max_chars]}"
    meta = f"일시: {occurred_at or '모름'} / 장소: {location or '모름'} / 만난 사람: {', '.join(names) or '모름'}"
    return f"{meta}\n\n메모:\n{text[:max_chars]}"
