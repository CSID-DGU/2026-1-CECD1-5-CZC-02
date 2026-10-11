"""작업별 LLM 제공자 선택 + 폴백 체인 + 서킷브레이커 + 월 예산 상한.

폴백 순서: OpenAI → Ollama → (호출부) 규칙/템플릿
OpenAI는 LLM_PROVIDER=openai 이고 OPENAI_API_KEY가 있을 때만 사용한다.
"""
import threading
import time
from datetime import datetime
from typing import Callable, Dict, List, Optional

from app.config import LLM_PROVIDER, OPENAI_API_KEY, OPENAI_MONTHLY_BUDGET_USD
from app.llm.base import LLMError, LLMProvider, LLMRequest, LLMResponse, LLMUnavailable

FAILURE_THRESHOLD = 2
COOLDOWN_SECONDS = 60


class CircuitBreaker:
    """연속 실패가 쌓이면 일정 시간 해당 제공자를 건너뛴다(장애 시 타임아웃 대기 누적 방지)."""

    def __init__(self, threshold: int = FAILURE_THRESHOLD, cooldown_seconds: float = COOLDOWN_SECONDS,
                 clock: Callable[[], float] = time.monotonic):
        self.threshold = threshold
        self.cooldown_seconds = cooldown_seconds
        self.clock = clock
        self._failures: Dict[str, int] = {}
        self._opened_at: Dict[str, float] = {}

    def is_open(self, name: str) -> bool:
        opened_at = self._opened_at.get(name)
        if opened_at is None:
            return False
        if self.clock() - opened_at >= self.cooldown_seconds:
            self._opened_at.pop(name, None)
            self._failures[name] = 0
            return False
        return True

    def record_success(self, name: str) -> None:
        self._failures[name] = 0
        self._opened_at.pop(name, None)

    def record_failure(self, name: str) -> None:
        self._failures[name] = self._failures.get(name, 0) + 1
        if self._failures[name] >= self.threshold:
            self._opened_at[name] = self.clock()


class MonthlyBudget:
    """서버 프로세스 기준 월 누적 예상 비용. 재시작하면 0부터 다시 센다."""

    def __init__(self, limit_usd: float = OPENAI_MONTHLY_BUDGET_USD):
        self.limit_usd = limit_usd
        self._month = datetime.now().strftime("%Y-%m")
        self._spent = 0.0
        self._lock = threading.Lock()

    def _roll_month(self) -> None:
        month = datetime.now().strftime("%Y-%m")
        if month != self._month:
            self._month, self._spent = month, 0.0

    def exceeded(self) -> bool:
        with self._lock:
            self._roll_month()
            return self._spent >= self.limit_usd

    def add(self, cost_usd: float) -> None:
        with self._lock:
            self._roll_month()
            self._spent += cost_usd

    @property
    def spent_usd(self) -> float:
        return self._spent


class LLMRouter:
    def __init__(self, providers: List[LLMProvider], breaker: Optional[CircuitBreaker] = None,
                 budget: Optional[MonthlyBudget] = None, setup_note: Optional[str] = None):
        self.providers = providers
        self.breaker = breaker or CircuitBreaker()
        self.budget = budget or MonthlyBudget()
        # 설정 때문에 OpenAI를 못 쓰는 경우의 사유 (예: 키 없음). 응답의 fallback_reason에 남긴다.
        self.setup_note = setup_note

    def generate(self, request: LLMRequest) -> LLMResponse:
        reasons: List[str] = [self.setup_note] if self.setup_note else []

        for provider in self.providers:
            if self.breaker.is_open(provider.name):
                reasons.append(f"{provider.name}: 연속 실패로 {COOLDOWN_SECONDS}초간 건너뜀")
                continue
            if provider.name == "openai" and self.budget.exceeded():
                reasons.append(f"openai: 월 예산 ${self.budget.limit_usd:.2f} 초과")
                continue

            try:
                response = provider.generate(request)
            except LLMError as exception:
                self.breaker.record_failure(provider.name)
                reasons.append(f"{provider.name}: {exception}")
                continue

            self.breaker.record_success(provider.name)
            if provider.name == "openai":
                self.budget.add(response.estimated_cost_usd)
            response.fallback_reason = " / ".join(reasons) or None
            log_usage(request, response)
            return response

        reason = " / ".join(reasons) or "사용 가능한 LLM 제공자가 없습니다."
        print(f"[LLM] task={request.task} all providers failed. reason={reason}")
        raise LLMUnavailable(reason)


def log_usage(request: LLMRequest, response: LLMResponse) -> None:
    print(
        f"[LLM] task={request.task} provider={response.provider} model={response.model} "
        f"in={response.input_tokens}(cached {response.cached_input_tokens}) out={response.output_tokens} "
        f"latency={response.latency_ms}ms cost=${response.estimated_cost_usd:.5f}"
        + (f" fallback={response.fallback_reason}" if response.fallback_reason else "")
    )


def build_default_router() -> LLMRouter:
    from app.llm.ollama_provider import OllamaProvider

    providers: List[LLMProvider] = []
    setup_note = None

    if LLM_PROVIDER == "openai":
        if OPENAI_API_KEY:
            from app.llm.openai_provider import OpenAIProvider

            providers.append(OpenAIProvider())
        else:
            setup_note = "openai: OPENAI_API_KEY가 없어 사용하지 않음"
            print("[LLM] LLM_PROVIDER=openai 이지만 OPENAI_API_KEY가 없어 Ollama만 사용합니다.")

    providers.append(OllamaProvider())
    return LLMRouter(providers, setup_note=setup_note)


_router: Optional[LLMRouter] = None
_router_lock = threading.Lock()


def get_router() -> LLMRouter:
    global _router
    with _router_lock:
        if _router is None:
            _router = build_default_router()
        return _router


def generate(request: LLMRequest) -> LLMResponse:
    return get_router().generate(request)
