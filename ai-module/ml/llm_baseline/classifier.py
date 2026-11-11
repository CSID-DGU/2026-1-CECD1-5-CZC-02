"""ML-2 비교용 LLM 직접 판정. 할 일 문장 하나 → 부류 하나.

지시문은 docs/project/task-types.md의 정의표·경계표에서 만든다 (문서가 바뀌면 지시문도 바뀐다).
평가 문장은 지시문 예시로 넣지 않는다. 운영 분류기는 계속 RULE이다 — 이 모듈은 비교 실험 전용.
"""
import hashlib
import re
import time
from dataclasses import dataclass
from typing import Optional

from app.llm.base import LLMError, LLMRequest
from ml.llm_baseline.data import TASK_TYPES_DOC
from ml.task_type.experiment import LABELS


def _table_rows(section):
    rows = []
    for line in section.splitlines():
        if not line.startswith('|') or set(line) <= set('|- '):
            continue
        rows.append([cell.strip() for cell in line.strip().strip('|').split('|')])
    return rows[1:]  # 머리글 제외


def parse_task_types(doc_text):
    """정의표 → [(코드, 서비스, 언제)], 경계표 → [(쌍, 기준, 예시)]."""
    definitions_part, _, boundary_part = doc_text.partition('## 헷갈리기 쉬운 경계')
    boundary_part = boundary_part.split('\n## ', 1)[0]
    definitions = []
    for cells in _table_rows(definitions_part):
        code = cells[0].strip('`')
        if code in LABELS:
            definitions.append((code, cells[1], cells[2]))
    boundaries = [(cells[0].replace('`', ''), cells[1], cells[2]) for cells in _table_rows(boundary_part) if len(cells) >= 3]
    return definitions, boundaries


def build_system_prompt(doc_text=None):
    doc_text = doc_text if doc_text is not None else TASK_TYPES_DOC.read_text(encoding='utf-8')
    definitions, boundaries = parse_task_types(doc_text)
    codes = [code for code, _, _ in definitions]
    if sorted(codes) != sorted(LABELS):
        raise ValueError(f'task-types.md 정의표에서 부류를 모두 찾지 못했습니다: {codes}')
    lines = [
        '너는 영업 담당자의 할 일 한 줄을 아래 부류 중 정확히 하나로 분류한다.',
        '',
        '## 부류 (코드 — 서비스: 언제 이 부류인가)',
        *[f'- {code} — {service}: {when}' for code, service, when in definitions],
        '',
        '## 헷갈리기 쉬운 경계 (기준 / 예시 → 정답)',
        *[f'- {pair}: {rule} / {example}' for pair, rule, example in boundaries],
        '',
        '## 판단 규칙',
        '- 문장에 적힌 내용만 보고 판단한다. 고객인지·내부인지·이동이 있는지 모르면 추측하지 말고 NONE.',
        '- 서로 다른 행위가 나뉘지 않고 섞여 있으면 NONE.',
        '- 어디에도 확실히 맞지 않으면 억지로 넣지 말고 NONE.',
        '',
        '## 출력',
        'JSON 객체 하나만 출력한다: {"taskType": "<위 코드 중 하나>", "reason": "<근거 한 줄>"}',
    ]
    return '\n'.join(lines)


def prompt_sha256(prompt):
    return hashlib.sha256(prompt.encode('utf-8')).hexdigest()[:12]


@dataclass
class LlmJudgement:
    label: str
    reason: Optional[str]
    failure: Optional[str]      # 형식 오류·호출 실패면 이유, 정상이면 None
    latency_ms: int


class LlmTaskTypeClassifier:
    """provider는 app.llm의 LLMProvider (generate(LLMRequest) -> LLMResponse). 테스트는 가짜를 넣는다."""

    def __init__(self, provider, system_prompt=None, max_output_tokens=150):
        self.provider = provider
        self.system_prompt = system_prompt or build_system_prompt()
        self.max_output_tokens = max_output_tokens

    def classify(self, text) -> LlmJudgement:
        request = LLMRequest(
            task='task_type', system=self.system_prompt, user=f'할 일: {text}',
            json_schema={'type': 'object'}, schema_name='task_type',
            max_output_tokens=self.max_output_tokens, temperature=0.0,
        )
        started = time.perf_counter()
        try:
            data = self.provider.generate(request).data
        except LLMError as exception:
            return LlmJudgement('NONE', None, f'호출·JSON 실패: {exception}', _elapsed(started))
        label = data.get('taskType') if isinstance(data, dict) else None
        reason = data.get('reason') if isinstance(data, dict) else None
        if isinstance(label, str):
            label = re.sub(r'[`"\s]', '', label).upper()
        if label not in LABELS:
            # 형식이 틀리면 추측하지 않고 NONE으로 두고 실패로 센다
            return LlmJudgement('NONE', reason if isinstance(reason, str) else None, f'부류 값 형식 오류: {data!r}'[:200], _elapsed(started))
        return LlmJudgement(label, reason if isinstance(reason, str) else None, None, _elapsed(started))


def _elapsed(started):
    return int((time.perf_counter() - started) * 1000)
