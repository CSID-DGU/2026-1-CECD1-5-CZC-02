"""로컬 Ollama 제공자 (무료, 오프라인)."""
import json
import time
import urllib.error
import urllib.request

from app.config import OLLAMA_BASE_URL, OLLAMA_MODEL, OLLAMA_TIMEOUT_SECONDS
from app.llm.base import LLMError, LLMRequest, LLMResponse, parse_json_object


class OllamaProvider:
    name = "ollama"

    def __init__(self, base_url: str = OLLAMA_BASE_URL, model: str = OLLAMA_MODEL,
                 timeout_seconds: float = OLLAMA_TIMEOUT_SECONDS):
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.timeout_seconds = timeout_seconds

    def model_for(self, task: str) -> str:
        return self.model

    def generate(self, request: LLMRequest) -> LLMResponse:
        # Ollama는 스키마 강제가 약하므로 JSON 모드로 받고 파싱·검증은 호출부에서 한다.
        payload = {
            "model": self.model,
            "prompt": f"{request.system}\n\n{request.user}",
            "stream": False,
            "options": {
                "temperature": request.temperature,
                "num_predict": request.max_output_tokens,
            },
        }
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
            model=self.model,
            input_tokens=int(envelope.get("prompt_eval_count") or 0),
            output_tokens=int(envelope.get("eval_count") or 0),
            latency_ms=int((time.perf_counter() - started) * 1000),
            estimated_cost_usd=0.0,
        )
