"""로컬 Ollama 제공자 (무료, 오프라인)."""
import json
import time
import urllib.error
import urllib.request
from typing import Dict, Optional

from app.config import (OLLAMA_BASE_URL, OLLAMA_MODEL, OLLAMA_MODEL_MINUTES, OLLAMA_SEED, OLLAMA_THINK,
                        OLLAMA_THINK_MINUTES, OLLAMA_TIMEOUT_SECONDS)
from app.llm.base import LLMError, LLMRequest, LLMResponse, parse_json_object


def default_task_models() -> Dict[str, str]:
    """과제별 모델 (D-029). 비어 있는 과제는 기본 모델을 쓴다."""
    return {"minutes": OLLAMA_MODEL_MINUTES} if OLLAMA_MODEL_MINUTES else {}


def default_task_think() -> Dict[str, bool]:
    return {"minutes": OLLAMA_THINK_MINUTES} if OLLAMA_MODEL_MINUTES and OLLAMA_THINK_MINUTES is not None else {}


class OllamaProvider:
    name = "ollama"

    def __init__(self, base_url: str = OLLAMA_BASE_URL, model: str = OLLAMA_MODEL,
                 timeout_seconds: float = OLLAMA_TIMEOUT_SECONDS, seed: Optional[int] = OLLAMA_SEED,
                 think: Optional[bool] = OLLAMA_THINK, task_models: Optional[Dict[str, str]] = None,
                 task_think: Optional[Dict[str, bool]] = None):
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.timeout_seconds = timeout_seconds
        self.seed = seed
        self.think = think
        # None이면 설정값(D-029: 회의록만 gemma4). 빈 dict를 넘기면 모든 과제가 model 하나를 쓴다
        self.task_models = default_task_models() if task_models is None else dict(task_models)
        self.task_think = default_task_think() if task_think is None else dict(task_think)

    def model_for(self, task: str) -> str:
        return self.task_models.get(task) or self.model

    def think_for(self, task: str) -> Optional[bool]:
        # 과제별 모델을 쓰는 과제는 그 모델의 생각 설정을, 아니면 공통 설정을 따른다
        if task in self.task_models and task in self.task_think:
            return self.task_think[task]
        return self.think

    def generate(self, request: LLMRequest) -> LLMResponse:
        # Ollama는 스키마 강제가 약하므로 JSON 모드로 받고 파싱·검증은 호출부에서 한다.
        model = self.model_for(request.task)
        think = self.think_for(request.task)
        payload = {
            "model": model,
            "prompt": f"{request.system}\n\n{request.user}",
            "stream": False,
            "options": {
                "temperature": request.temperature,
                "num_predict": request.max_output_tokens,
            },
        }
        if self.seed is not None:
            # 같은 입력 → 같은 답 (평가 재현용). 서버 기본은 None이라 넣지 않는다
            payload["options"]["seed"] = self.seed
        if think is not None:
            # 생각 모드 모델만 설정한다. None이면 보내지 않아 생각 기능이 없는 모델도 그대로 동작
            payload["think"] = think
        if request.json_schema is not None:
            payload["format"] = "json"

        http_request = urllib.request.Request(
            f"{self.base_url}/api/generate",
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )

        started = time.perf_counter()
        try:
            with urllib.request.urlopen(http_request, timeout=self.timeout_seconds) as response:
                raw_body = response.read().decode("utf-8")
        except (urllib.error.URLError, TimeoutError, OSError) as exception:
            raise LLMError(f"Ollama request failed: {exception}") from exception

        try:
            envelope = json.loads(raw_body)
        except json.JSONDecodeError as exception:
            raise LLMError("Ollama response envelope is not valid JSON.") from exception

        text = envelope.get("response")
        if not isinstance(text, str) or not text.strip():
            raise LLMError("Ollama response field is empty.")

        return LLMResponse(
            data=parse_json_object(text) if request.json_schema is not None else text.strip(),
            provider=self.name,
            model=model,
            input_tokens=int(envelope.get("prompt_eval_count") or 0),
            output_tokens=int(envelope.get("eval_count") or 0),
            latency_ms=int((time.perf_counter() - started) * 1000),
            estimated_cost_usd=0.0,
        )
