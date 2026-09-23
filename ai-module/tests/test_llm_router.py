"""LLM 라우터·제공자 테스트. 실제 네트워크 호출은 하지 않는다(유료 API 과금 없음)."""
import asyncio
from types import SimpleNamespace

import pytest

from app.llm import router as router_module
from app.llm.base import LLMError, LLMRequest, LLMResponse, LLMUnavailable
from app.llm.openai_provider import OpenAIProvider, estimate_cost_usd
from app.llm.router import CircuitBreaker, LLMRouter, MonthlyBudget

REQUEST = LLMRequest(task="analysis", system="sys", user="user", json_schema={"type": "object"})


class FakeProvider:
    def __init__(self, name, fail=False, cost=0.0):
        self.name = name
        self.fail = fail
        self.cost = cost
        self.calls = 0

    def model_for(self, task):
        return f"{self.name}-model"

    def generate(self, request):
        self.calls += 1
        if self.fail:
            raise LLMError("boom")
        return LLMResponse(data={"ok": True}, provider=self.name, model=self.model_for(request.task),
                           estimated_cost_usd=self.cost)


def test_falls_back_to_next_provider_and_records_reason():
    openai, ollama = FakeProvider("openai", fail=True), FakeProvider("ollama")
    response = LLMRouter([openai, ollama]).generate(REQUEST)

    assert response.provider == "ollama"
    assert "openai: boom" in response.fallback_reason


def test_all_providers_failing_raises_unavailable():
    with pytest.raises(LLMUnavailable):
        LLMRouter([FakeProvider("ollama", fail=True)]).generate(REQUEST)


def test_circuit_breaker_skips_provider_after_repeated_failures():
    now = [0.0]
    breaker = CircuitBreaker(threshold=2, cooldown_seconds=60, clock=lambda: now[0])
    openai, ollama = FakeProvider("openai", fail=True), FakeProvider("ollama")
    router = LLMRouter([openai, ollama], breaker=breaker)

    for _ in range(3):
        router.generate(REQUEST)
    assert openai.calls == 2  # 세 번째는 차단되어 호출하지 않음

    now[0] = 61
    router.generate(REQUEST)
    assert openai.calls == 3  # 쿨다운 후 재시도


def test_monthly_budget_blocks_openai():
    budget = MonthlyBudget(limit_usd=0.01)
    openai, ollama = FakeProvider("openai", cost=0.02), FakeProvider("ollama")
    router = LLMRouter([openai, ollama], budget=budget)

    assert router.generate(REQUEST).provider == "openai"
    second = router.generate(REQUEST)
    assert second.provider == "ollama"
    assert "월 예산" in second.fallback_reason
    assert openai.calls == 1


def test_openai_without_api_key_uses_only_ollama(monkeypatch):
    monkeypatch.setattr(router_module, "LLM_PROVIDER", "openai")
    monkeypatch.setattr(router_module, "OPENAI_API_KEY", "")

    router = router_module.build_default_router()

    assert [provider.name for provider in router.providers] == ["ollama"]
    assert "OPENAI_API_KEY" in router.setup_note


def test_default_provider_is_ollama_only():
    assert [provider.name for provider in router_module.build_default_router().providers] == ["ollama"]


class FakeResponses:
    def __init__(self):
        self.params = None

    def create(self, **params):
        self.params = params
        usage = SimpleNamespace(input_tokens=1500, output_tokens=120,
                                input_tokens_details=SimpleNamespace(cached_tokens=1000))
        return SimpleNamespace(output_text='{"customerName": "대성물류"}', usage=usage)


def test_openai_provider_uses_strict_structured_output_and_counts_cost():
    responses = FakeResponses()
    provider = OpenAIProvider(client=SimpleNamespace(responses=responses),
                              models={"analysis": "gpt-5.6-luna", "reply": "gpt-5.6-terra"})

    response = provider.generate(LLMRequest(task="analysis", system="지시문", user="메일",
                                            json_schema={"type": "object"}, schema_name="patch"))

    text_format = responses.params["text"]["format"]
    assert text_format["type"] == "json_schema" and text_format["strict"] is True
    assert responses.params["model"] == "gpt-5.6-luna"
    assert responses.params["instructions"] == "지시문"
    assert response.data == {"customerName": "대성물류"}
    assert response.cached_input_tokens == 1000
    assert response.estimated_cost_usd == pytest.approx(estimate_cost_usd("gpt-5.6-luna", 1500, 1000, 120))


def test_cost_estimate_applies_cached_discount():
    # 미캐시 500 × $0.20 + 캐시 1000 × $0.02 + 출력 100 × $1.20 (1M당)
    assert estimate_cost_usd("gpt-5.6-luna", 1500, 1000, 100) == pytest.approx((100 + 20 + 120) / 1_000_000)
    assert estimate_cost_usd("unknown-model", 1000, 0, 100) == 0.0


def test_analysis_falls_back_to_rules_when_llm_unavailable(monkeypatch):
    from app.services import analyzer, llm_analysis

    def unavailable(request):
        raise LLMUnavailable("ollama: connection refused")

    monkeypatch.setattr(llm_analysis, "generate", unavailable)
    body = "안녕하세요, 대성물류 김현우 부장입니다. 다음 주 화요일 오전 10시에 미팅 가능하실까요?"

    result = asyncio.run(analyzer.analyze_message(body, analysis_mode="ollama"))

    assert result.llm_provider == "rule"
    assert "connection refused" in result.llm_fallback_reason
    assert result.action_type == "CREATE"


def test_analysis_records_llm_provider(monkeypatch):
    from app.services import analyzer, llm_analysis

    def fake_generate(request):
        return LLMResponse(data={"customerName": "대성물류", "contactName": "김현우", "productName": None,
                                 "attendees": ["김현우"], "scheduleTitle": None, "summary": "요약"},
                           provider="openai", model="gpt-5.6-luna")

    monkeypatch.setattr(llm_analysis, "generate", fake_generate)
    result = asyncio.run(analyzer.analyze_message("대성물류 김현우입니다. 미팅 요청드립니다.", analysis_mode="ollama"))

    assert (result.llm_provider, result.llm_model) == ("openai", "gpt-5.6-luna")
    assert result.customer_name == "대성물류"


def test_reply_draft_uses_template_when_llm_unavailable(monkeypatch):
    from app.schemas.request import ReplyDraftRequest
    from app.services import reply_draft

    def unavailable(request):
        raise LLMUnavailable("down")

    monkeypatch.setattr(reply_draft, "generate", unavailable)
    draft = reply_draft.generate_reply_draft(ReplyDraftRequest(emailTitle="견적 문의", emailContent="견적 부탁드립니다",
                                                               customerName="대성물류", actionType="UNKNOWN"))

    assert draft.generatedBy == "hybrid-template"
    assert "대성물류 담당자님" in draft.body
