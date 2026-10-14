"""회의록 LLM 지시문·출력 스키마. 문항 나누는 규칙은 decisions.md D-018과 eval/minutes/README.md를 따른다.

출력 스키마는 OpenAI Structured Outputs(strict)에도 쓰이므로 모든 필드를 required로 두고, 없는 값은 빈 배열·null이다.
"""
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


def build_user_prompt(text: str, occurred_at, location, names, max_chars: int = 6000) -> str:
    meta = f"일시: {occurred_at or '모름'} / 장소: {location or '모름'} / 만난 사람: {', '.join(names) or '모름'}"
    return f"{meta}\n\n메모:\n{text[:max_chars]}"
