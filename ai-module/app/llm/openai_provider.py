"""OpenAI 제공자 (유료). Responses API + Structured Outputs로 JSON 스키마를 강제한다."""
import json
import time
from typing import Any, Dict, Optional, Tuple

from app.config import (
    OPENAI_API_KEY,
    OPENAI_MODEL_ANALYSIS,
    OPENAI_MODEL_REPLY,
    OPENAI_REASONING_EFFORT,
    OPENAI_TIMEOUT_SECONDS,
)
from app.llm.base import LLMError, LLMRequest, LLMResponse

# 1M 토큰당 USD (입력, 캐시 입력, 출력). 2026-09 developers.openai.com/api/docs/pricing 기준 — 바뀌면 갱신.
PRICES_PER_MILLION: Dict[str, Tuple[float, float, float]] = {
    "gpt-5.6-sol": (4.00, 0.40, 20.00),
    "gpt-5.6-terra": (2.00, 0.20, 12.00),
    "gpt-5.6-luna": (0.20, 0.02, 1.20),
    "gpt-5.4-mini": (0.75, 0.075, 4.50),
    "gpt-5.4-nano": (0.20, 0.02, 1.25),
    "gpt-5-nano": (0.05, 0.005, 0.40),
}


def estimate_cost_usd(model: str, input_tokens: int, cached_input_tokens: int, output_tokens: int) -> float:
    prices = PRICES_PER_MILLION.get(model)
    if prices is None:
        return 0.0
    input_price, cached_price, output_price = prices
    uncached = max(input_tokens - cached_input_tokens, 0)
    return (uncached * input_price + cached_input_tokens * cached_price + output_tokens * output_price) / 1_000_000


class OpenAIProvider:
    name = "openai"

    def __init__(self, api_key: str = OPENAI_API_KEY, client: Optional[Any] = None,
                 models: Optional[Dict[str, str]] = None, timeout_seconds: float = OPENAI_TIMEOUT_SECONDS,
                 reasoning_effort: str = OPENAI_REASONING_EFFORT):
        if client is None:
            if not api_key:
                raise LLMError("OPENAI_API_KEY is not set.")
            from openai import OpenAI

            client = OpenAI(api_key=api_key, timeout=timeout_seconds, max_retries=2)
        self.client = client
        self.models = models or {"analysis": OPENAI_MODEL_ANALYSIS, "reply": OPENAI_MODEL_REPLY}
        self.reasoning_effort = reasoning_effort

    def model_for(self, task: str) -> str:
        return self.models.get(task) or self.models["analysis"]

    def generate(self, request: LLMRequest) -> LLMResponse:
        model = self.model_for(request.task)
        params: Dict[str, Any] = {
            "model": model,
            "instructions": request.system,
            "input": request.user,
            "max_output_tokens": request.max_output_tokens,
        }
        if request.json_schema is not None:
            params["text"] = {
                "format": {
                    "type": "json_schema",
                    "name": request.schema_name,
                    "schema": request.json_schema,
                    "strict": True,
                }
            }
        if self.reasoning_effort:
            params["reasoning"] = {"effort": self.reasoning_effort}

        started = time.perf_counter()
        try:
            response = self.client.responses.create(**params)
        except Exception as exception:  # SDK 예외 종류와 무관하게 폴백 대상으로 처리
            raise LLMError(f"OpenAI request failed: {type(exception).__name__}: {exception}") from exception

        text = getattr(response, "output_text", None)
        if not isinstance(text, str) or not text.strip():
            raise LLMError("OpenAI response has no output text.")

        if request.json_schema is not None:
            try:
                data = json.loads(text)
            except json.JSONDecodeError as exception:
                raise LLMError("OpenAI structured output is not valid JSON.") from exception
        else:
            data = text.strip()

        usage = getattr(response, "usage", None)
        input_tokens = int(getattr(usage, "input_tokens", 0) or 0)
        output_tokens = int(getattr(usage, "output_tokens", 0) or 0)
        details = getattr(usage, "input_tokens_details", None)
        cached_tokens = int(getattr(details, "cached_tokens", 0) or 0)

        return LLMResponse(
            data=data,
            provider=self.name,
            model=model,
            input_tokens=input_tokens,
            cached_input_tokens=cached_tokens,
            output_tokens=output_tokens,
            latency_ms=int((time.perf_counter() - started) * 1000),
            estimated_cost_usd=estimate_cost_usd(model, input_tokens, cached_tokens, output_tokens),
        )
