"""정답 라벨 평가셋으로 분석 엔진 정확도를 측정한다.

사용법 (ai-module 폴더에서):
    python -m eval.run_eval                  # 규칙 엔진
    python -m eval.run_eval --engine ollama  # 규칙 + 로컬 LLM
    python -m eval.run_eval --verbose        # 틀린 항목 상세 출력

같은 데이터셋·같은 기준 시각(메일 발송 시각)으로 돌리므로 실행 날짜와 무관하게 결과가 재현된다.
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

EVAL_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL_DIR.parent))

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


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--engine", default="rule", choices=["rule", "ollama"])
    parser.add_argument("--dataset", default=str(EVAL_DIR / "dataset.jsonl"))
    parser.add_argument("--limit", type=int, default=None, help="앞에서부터 N건만 실행 (유료 API 시험용)")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    items = load_dataset(Path(args.dataset))[: args.limit]
    started = time.perf_counter()
    scored = [score_item(item, await analyze_item(item, args.engine)) for item in items]
    elapsed = time.perf_counter() - started

    summary = summarize(scored)
    print_report(summary, scored, args.engine, elapsed, args.verbose)

    results_dir = EVAL_DIR / "results"
    results_dir.mkdir(exist_ok=True)
    output = results_dir / f"{args.engine}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    output.write_text(
        json.dumps({"engine": args.engine, "summary": summary, "items": scored}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"\n결과 저장: {output}")


if __name__ == "__main__":
    asyncio.run(main())
