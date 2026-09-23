"""LLM 제공자 공통 인터페이스.

분석·답장 코드는 이 인터페이스만 알고, 실제로 Ollama를 쓸지 OpenAI를 쓸지는 router가 정한다.
"""
import json
import re
from dataclasses import dataclass, field
from typing import Any, Dict, Optional, Protocol


@dataclass(frozen=True)
class LLMRequest:
    task: str                                   # "analysis" | "reply"
    system: str                                 # 고정 지시문 + 예시 (프롬프트 캐시 대상이므로 앞에 둔다)
    user: str                                   # 매번 바뀌는 컨텍스트 + 본문
    json_schema: Optional[Dict[str, Any]] = None
    schema_name: str = "result"
    max_output_tokens: int = 700
    temperature: float = 0.1


@dataclass
class LLMResponse:
    data: Any                                   # json_schema가 있으면 dict, 없으면 str
    provider: str
    model: str
    input_tokens: int = 0
    cached_input_tokens: int = 0
    output_tokens: int = 0
    latency_ms: int = 0
    estimated_cost_usd: float = 0.0
    fallback_reason: Optional[str] = None
    extra: Dict[str, Any] = field(default_factory=dict)


class LLMError(RuntimeError):
    """제공자 호출 실패. router가 잡아서 다음 제공자로 넘어간다."""


class LLMUnavailable(RuntimeError):
    """모든 제공자가 실패. 호출부는 규칙/템플릿 결과로 폴백한다."""


class LLMProvider(Protocol):
    name: str

    def model_for(self, task: str) -> str: ...

    def generate(self, request: LLMRequest) -> LLMResponse: ...


def parse_json_object(value: str) -> Dict[str, Any]:
    """모델 출력에서 JSON 객체를 꺼낸다. 앞뒤에 설명이 붙은 경우도 처리한다."""
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", value, re.DOTALL)
        if not match:
            raise LLMError("LLM response does not contain a JSON object.")
        try:
            parsed = json.loads(match.group(0))
        except json.JSONDecodeError as exception:
            raise LLMError("LLM response JSON is malformed.") from exception

    if not isinstance(parsed, dict):
        raise LLMError("LLM response JSON is not an object.")
    return parsed
