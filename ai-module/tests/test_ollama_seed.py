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


def capture_payload(monkeypatch, provider, task="analysis"):
    sent = {}

    def fake_urlopen(request, timeout):
        sent.update(json.loads(request.data.decode("utf-8")))
        return FakeResponse(json.dumps({"response": "{}", "prompt_eval_count": 1, "eval_count": 1}).encode("utf-8"))

    monkeypatch.setattr(ollama_provider.urllib.request, "urlopen", fake_urlopen)
    provider.generate(LLMRequest(task=task, system="s", user="u", json_schema={"type": "object"}))
    return sent


def test_seed_is_sent_when_configured(monkeypatch):
    sent = capture_payload(monkeypatch, OllamaProvider(seed=42))
    assert sent["options"]["seed"] == 42


def test_seed_is_omitted_by_default(monkeypatch):
    sent = capture_payload(monkeypatch, OllamaProvider(seed=None))
    assert "seed" not in sent["options"]


def test_think_is_sent_only_when_configured(monkeypatch):
    # 생각 모드 모델(qwen3.5·gemma4)은 OLLAMA_THINK=false로 끈다. 비우면 보내지 않아 qwen2.5 등은 지금 동작 그대로
    assert capture_payload(monkeypatch, OllamaProvider(think=False))["think"] is False
    assert "think" not in capture_payload(monkeypatch, OllamaProvider(think=None))


def test_minutes_uses_its_own_model_and_think_setting(monkeypatch):
    # D-029: 회의록만 gemma4:e4b(생각 모드 끔), 메일 분석 등은 기본 모델 그대로
    provider = OllamaProvider(model="qwen2.5:7b", think=None, task_models={"minutes": "gemma4:e4b"}, task_think={"minutes": False})
    minutes = capture_payload(monkeypatch, provider, task="minutes")
    analysis = capture_payload(monkeypatch, provider, task="analysis")
    assert minutes["model"] == "gemma4:e4b" and minutes["think"] is False
    assert analysis["model"] == "qwen2.5:7b" and "think" not in analysis


def test_minutes_model_follows_default_when_not_configured(monkeypatch):
    provider = OllamaProvider(model="qwen2.5:7b", think=None, task_models={}, task_think={})
    sent = capture_payload(monkeypatch, provider, task="minutes")
    assert sent["model"] == "qwen2.5:7b" and "think" not in sent


def test_response_reports_the_model_actually_called(monkeypatch):
    def fake_urlopen(request, timeout):
        return FakeResponse(json.dumps({"response": "{}"}).encode("utf-8"))

    monkeypatch.setattr(ollama_provider.urllib.request, "urlopen", fake_urlopen)
    provider = OllamaProvider(model="qwen2.5:7b", task_models={"minutes": "gemma4:e4b"})
    response = provider.generate(LLMRequest(task="minutes", system="s", user="u", json_schema={"type": "object"}))
    assert response.model == "gemma4:e4b"


def test_only_minutes_has_its_own_model():
    # 과제별 모델은 회의록 하나뿐 (D-029). 값은 로컬 .env로 바꿀 수 있어 키만 본다
    assert ollama_provider.default_task_models().keys() <= {"minutes"}
