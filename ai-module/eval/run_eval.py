"""정답 라벨 평가셋으로 분석 엔진 정확도를 측정한다.

사용법 (ai-module 폴더에서):
    python -m eval.run_eval                  # 규칙 엔진
    python -m eval.run_eval --engine ollama  # 규칙 + 로컬 LLM
    python -m eval.run_eval --verbose        # 틀린 항목 상세 출력

같은 데이터셋·같은 기준 시각(메일 발송 시각)으로 돌리므로 실행 날짜와 무관하게 결과가 재현된다.
설정 조합을 바꿔 가며 여러 번 돌리고 표로 모으려면 eval/bench.py를 쓴다.

LLM_PROVIDER=openai + OPENAI_API_KEY 상태에서 --engine ollama를 돌리면 유료 API가 호출되므로
--allow-paid와 --limit을 함께 줘야만 실행된다 (AGENTS.md 규칙 7).
"""
import argparse
import asyncio
import inspect
import json
import re
import sys
import time
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

EVAL_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL_DIR.parent))

from app import config  # noqa: E402
from app.llm import router as llm_router  # noqa: E402
from app.schemas.request import HistoricalAnalysisInfo, MessageItem  # noqa: E402
from app.services import analyzer  # noqa: E402

REQUESTER_NAME = "테스트사용자"
COMPANY_SUFFIXES = ("주식회사", "(주)", "㈜", "고객사", "기업")


def load_dataset(path: Path) -> List[Dict[str, Any]]:
    with path.open(encoding="utf-8") as file:
        return [json.loads(line) for line in file if line.strip()]


@contextmanager
def frozen_now(reference: datetime):
    """reference_time 인자를 지원하지 않는 구버전 analyzer를 위해 현재 시각을 고정한다."""
    original = analyzer.datetime

    class FrozenDatetime(datetime):
        @classmethod
        def now(cls, tz=None):
            return reference

    analyzer.datetime = FrozenDatetime
    try:
        yield
    finally:
        analyzer.datetime = original


async def analyze_item(item: Dict[str, Any], engine: str):
    sent_at = datetime.fromisoformat(item["sent_at"])
    message = MessageItem(
        direction="RECEIVED",
        senderName=item["sender_name"],
        senderEmail=item["sender_email"],
        receiverNames=[REQUESTER_NAME],
        sentAt=sent_at,
        content=item["body"],
    )
    memory = [HistoricalAnalysisInfo(**entry) for entry in item.get("recent_sender_analyses", [])]
    text = f"{item['subject']} {item['body']}"
    kwargs = dict(
        messages=[message],
        requester_name=REQUESTER_NAME,
        existing_schedules=[],
        analysis_mode=engine,
        recent_sender_analyses=memory,
    )

    if "reference_time" in inspect.signature(analyzer.analyze_message).parameters:
        return await analyzer.analyze_message(text, reference_time=sent_at, **kwargs)
    with frozen_now(sent_at):
        return await analyzer.analyze_message(text, **kwargs)


def normalize_entity(value: Optional[str]) -> str:
    if not value:
        return ""
    cleaned = value
    for suffix in COMPANY_SUFFIXES:
        cleaned = cleaned.replace(suffix, "")
    return re.sub(r"\s+", "", cleaned).lower()


def attendee_f1(gold: Iterable[str], predicted: Iterable[str]) -> float:
    gold_names = [name for name in gold if name]
    predicted_names = [name for name in predicted if name]
    if not gold_names and not predicted_names:
        return 1.0
    if not gold_names or not predicted_names:
        return 0.0
    # 예측값에 직함·부서가 붙어도("IT팀 최지훈 대리") 이름이 포함돼 있으면 맞은 것으로 본다.
    recall_hits = sum(1 for name in gold_names if any(name in candidate for candidate in predicted_names))
    precision_hits = sum(1 for candidate in predicted_names if any(name in candidate for name in gold_names))
    recall = recall_hits / len(gold_names)
    precision = precision_hits / len(predicted_names)
    return 0.0 if recall + precision == 0 else 2 * precision * recall / (precision + recall)


def score_item(item: Dict[str, Any], result) -> Dict[str, Any]:
    gold = item["gold"]
    schedule = result.schedule
    predicted = {
        "businessType": result.business_type,
        "actionType": result.action_type,
        "customerName": result.customer_name,
        "productName": result.product_name,
        "contactName": result.contact_person,
        "attendees": result.attendees or [],
        "amount": result.amount,
        "scheduleDate": schedule.date if schedule and schedule.date else None,
        "scheduleTime": schedule.time if schedule and schedule.time else None,
    }

    checks: Dict[str, Optional[float]] = {"businessType": float(predicted["businessType"] == gold["businessType"])}
    if gold["businessType"] == "SALES_ACTIVITY":
        checks["actionType"] = float(predicted["actionType"] == gold["actionType"])
        for field in ("customerName", "productName", "contactName"):
            checks[field] = float(normalize_entity(predicted[field]) == normalize_entity(gold[field]))
        checks["amount"] = float(predicted["amount"] == gold.get("amount"))
        if gold.get("attendees"):
            checks["attendees"] = attendee_f1(gold["attendees"], predicted["attendees"])
        if gold.get("scheduleDate"):
            checks["scheduleDate"] = float(predicted["scheduleDate"] == gold["scheduleDate"])
            checks["scheduleTime"] = float(predicted["scheduleTime"] == gold["scheduleTime"])

    return {
        "id": item["id"],
        "tags": item.get("tags", []),
        "llmProvider": getattr(result, "llm_provider", None),
        "llmModel": getattr(result, "llm_model", None),
        "llmFallbackReason": getattr(result, "llm_fallback_reason", None),
        "checks": checks,
        "all_correct": all(value == 1.0 for value in checks.values()),
        "gold": gold,
        "predicted": predicted,
    }


def summarize(scored: List[Dict[str, Any]]) -> Dict[str, Dict[str, float]]:
    fields: Dict[str, List[float]] = {}
    for row in scored:
        for field, value in row["checks"].items():
            fields.setdefault(field, []).append(value)
    summary = {field: {"score": sum(values) / len(values), "n": len(values)} for field, values in fields.items()}
    summary["allFieldsCorrect"] = {
        "score": sum(row["all_correct"] for row in scored) / len(scored),
        "n": len(scored),
    }
    return summary


def print_report(summary, scored, engine: str, elapsed: float, verbose: bool) -> None:
    labels = {
        "businessType": "영업/비영업 분류",
        "actionType": "처리 유형",
        "customerName": "고객사",
        "productName": "제품",
        "contactName": "담당자",
        "attendees": "참석자(F1)",
        "amount": "금액",
        "scheduleDate": "일정 날짜",
        "scheduleTime": "일정 시간",
        "allFieldsCorrect": "전 필드 정답(무수정 통과)",
    }
    print(f"\n=== 평가 결과 · engine={engine} · {len(scored)}건 · {elapsed:.1f}초 ===")
    for field, label in labels.items():
        if field in summary:
            stat = summary[field]
            print(f"  {label:<22} {stat['score'] * 100:6.1f}%  (n={stat['n']})")

    failures = [row for row in scored if not row["all_correct"]]
    print(f"\n틀린 항목 {len(failures)}건: {', '.join(row['id'] for row in failures) or '없음'}")
    if verbose:
        for row in failures:
            print(f"\n- {row['id']} {row['tags']}")
            for field, value in row["checks"].items():
                if value < 1.0:
                    print(f"    {field}: 정답={row['gold'].get(field)!r} / 예측={row['predicted'].get(field)!r}")


def paid_provider_active(engine: str) -> bool:
    """이 설정으로 돌리면 유료 API(OpenAI)가 호출되는지. router.build_default_router와 같은 조건."""
    return engine != "rule" and config.LLM_PROVIDER == "openai" and bool(config.OPENAI_API_KEY)


def runtime_settings(engine: str) -> Dict[str, Any]:
    """결과 파일에 남길 모델 설정. 키 같은 비밀값은 넣지 않는다."""
    llm = engine != "rule"
    paid = paid_provider_active(engine)
    return {
        "engine": engine,
        "llmProvider": config.LLM_PROVIDER if llm else None,
        "ollamaModel": config.OLLAMA_MODEL if llm else None,
        "openaiModelAnalysis": config.OPENAI_MODEL_ANALYSIS if paid else None,
        "openaiReasoningEffort": (config.OPENAI_REASONING_EFFORT or None) if paid else None,
        "embeddingEnabled": config.EMBEDDING_CLASSIFIER_ENABLED,
        "embeddingModel": config.EMBEDDING_MODEL_NAME if config.EMBEDDING_CLASSIFIER_ENABLED else None,
        "glinerEnabled": config.GLINER_ENABLED,
        "glinerModel": config.GLINER_MODEL_NAME if config.GLINER_ENABLED else None,
        "glinerThreshold": config.GLINER_THRESHOLD if config.GLINER_ENABLED else None,
    }


class UsageRecorder:
    """router.log_usage를 감싸 LLM 호출의 토큰·비용·제공자를 모은다 (app/llm 코드는 건드리지 않는다)."""

    def __init__(self):
        self.calls = 0
        self.input_tokens = 0
        self.cached_input_tokens = 0
        self.output_tokens = 0
        self.cost_usd = 0.0
        self.providers: Dict[str, int] = {}

    def record(self, request, response) -> None:
        self.calls += 1
        self.input_tokens += response.input_tokens
        self.cached_input_tokens += response.cached_input_tokens
        self.output_tokens += response.output_tokens
        self.cost_usd += response.estimated_cost_usd
        key = f"{response.provider}:{response.model}"
        self.providers[key] = self.providers.get(key, 0) + 1

    @contextmanager
    def attached(self):
        original = llm_router.log_usage

        def wrapped(request, response):
            self.record(request, response)
            original(request, response)

        llm_router.log_usage = wrapped
        try:
            yield self
        finally:
            llm_router.log_usage = original

    def to_dict(self) -> Dict[str, Any]:
        return {
            "llmCalls": self.calls,
            "inputTokens": self.input_tokens,
            "cachedInputTokens": self.cached_input_tokens,
            "outputTokens": self.output_tokens,
            "estimatedCostUsd": round(self.cost_usd, 6),
            "providers": self.providers,
            "sentToExternalApi": any(key.startswith("openai:") for key in self.providers),
        }


def peak_memory_mb() -> Optional[float]:
    """이 프로세스의 최대 메모리(MB). 로컬 Ollama 모델 메모리는 Ollama 서버 쪽이라 포함되지 않는다."""
    try:
        import resource  # Unix

        peak = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
        return round(peak / 1024 / (1024 if sys.platform == "darwin" else 1), 1)
    except ImportError:
        pass
    try:
        import psutil  # Windows (선택 설치)

        info = psutil.Process().memory_info()
        return round(getattr(info, "peak_wset", info.rss) / 1024 / 1024, 1)
    except Exception:
        return None


async def evaluate(items: List[Dict[str, Any]], engine: str) -> Dict[str, Any]:
    """평가셋을 돌려 채점·시간·LLM 사용량을 한 덩어리로 돌려준다 (bench.py가 이 결과 파일을 모은다)."""
    scored: List[Dict[str, Any]] = []
    latencies: List[float] = []
    started = time.perf_counter()
    with UsageRecorder().attached() as usage:
        for item in items:
            item_started = time.perf_counter()
            result = await analyze_item(item, engine)
            latency_ms = (time.perf_counter() - item_started) * 1000
            row = score_item(item, result)
            row["latencyMs"] = round(latency_ms, 1)
            scored.append(row)
            latencies.append(latency_ms)
    elapsed = time.perf_counter() - started

    # 규칙이 비영업으로 판정한 메일은 analyzer가 일부러 LLM을 건너뛴다(사유 없음). 사유가 있는 rule만 LLM 실패.
    llm_used = sum(1 for row in scored if row["llmProvider"] not in (None, "rule"))
    llm_failed = sum(1 for row in scored if row["llmProvider"] == "rule" and row["llmFallbackReason"])
    llm_attempted = llm_used + llm_failed
    latencies.sort()
    return {
        "task": "mail",
        "engine": engine,
        "settings": runtime_settings(engine),
        "summary": summarize(scored),
        "runtime": {
            "items": len(scored),
            "elapsedSeconds": round(elapsed, 2),
            "msPerItem": round(elapsed * 1000 / len(scored), 1) if scored else None,
            "p50Ms": round(latencies[len(latencies) // 2], 1) if latencies else None,
            "maxMs": round(latencies[-1], 1) if latencies else None,
            # LLM을 시도한 건 중 실제로 LLM 결과가 쓰인 비율. 실패해 규칙으로 폴백한 건은 LLM 성능이 아니므로
            # 1.0이 아니면 결과를 믿지 말 것 (Ollama 꺼짐·타임아웃 등).
            "llmAttempted": None if engine == "rule" else llm_attempted,
            "llmCoverage": None if engine == "rule" else (llm_used / llm_attempted if llm_attempted else None),
            "peakMemoryMb": peak_memory_mb(),
        },
        "usage": usage.to_dict(),
        "items": scored,
    }


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--engine", default="rule", choices=["rule", "ollama"])
    parser.add_argument("--dataset", default=str(EVAL_DIR / "dataset.jsonl"))
    parser.add_argument("--limit", type=int, default=None, help="앞에서부터 N건만 실행 (유료 API 시험용)")
    parser.add_argument("--allow-paid", action="store_true",
                        help="LLM_PROVIDER=openai일 때 유료 API 호출을 허용 (--limit 필수)")
    parser.add_argument("--output", default=None, help="결과 JSON 경로 (기본: eval/results/<engine>_<시각>.json)")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    if paid_provider_active(args.engine):
        if not args.allow_paid or args.limit is None:
            parser.error("LLM_PROVIDER=openai 설정이라 유료 API가 호출됩니다. --allow-paid와 --limit N을 함께 주세요.")
        print(f"[유료] OpenAI {config.OPENAI_MODEL_ANALYSIS}로 최대 {args.limit}건 실행 "
              f"(프로세스 예산 상한 ${config.OPENAI_MONTHLY_BUDGET_USD:.2f})")

    items = load_dataset(Path(args.dataset))[: args.limit]
    payload = await evaluate(items, args.engine)
    payload["dataset"] = Path(args.dataset).name
    payload["finishedAt"] = datetime.now().isoformat(timespec="seconds")

    print_report(payload["summary"], payload["items"], args.engine, payload["runtime"]["elapsedSeconds"], args.verbose)
    coverage = payload["runtime"]["llmCoverage"]
    if coverage is not None and coverage < 1.0:
        print(f"\n[주의] LLM이 실제로 쓰인 비율 {coverage * 100:.0f}% — 나머지는 규칙 결과로 폴백했습니다.")

    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
    else:
        results_dir = EVAL_DIR / "results"
        results_dir.mkdir(exist_ok=True)
        output = results_dir / f"{args.engine}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n결과 저장: {output}")


if __name__ == "__main__":
    asyncio.run(main())
