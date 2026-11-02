"""Ollama 요청에 seed가 들어가는지 (평가 재현용). 네트워크 없이 urlopen을 가짜로 바꾼다."""
import io
import json

from app.llm.base import LLMRequest
from app.llm import ollama_provider
from app.llm.ollama_provider import OllamaProvider


class FakeResponse(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


def capture_payload(monkeypatch, provider):
    sent = {}

    def fake_urlopen(request, timeout):
        sent.update(json.loads(request.data.decode("utf-8")))
        return FakeResponse(json.dumps({"response": "{}", "prompt_eval_count": 1, "eval_count": 1}).encode("utf-8"))

    monkeypatch.setattr(ollama_provider.urllib.request, "urlopen", fake_urlopen)
    provider.generate(LLMRequest(task="minutes", system="s", user="u", json_schema={"type": "object"}))
    return sent


def test_seed_is_sent_when_configured(monkeypatch):
    sent = capture_payload(monkeypatch, OllamaProvider(seed=42))
    assert sent["options"]["seed"] == 42


def test_seed_is_omitted_by_default(monkeypatch):
    sent = capture_payload(monkeypatch, OllamaProvider(seed=None))
    assert "seed" not in sent["options"]
