"""회의록 엔진을 평가셋 10건에 돌리고 채점한다. 로컬 Ollama가 필요하다 (CI에서는 돌리지 않음).

사용법 (ai-module 폴더에서):
    python -m eval.minutes.run                                   # 기본 OLLAMA_MODEL
    OLLAMA_MODEL=qwen2.5:7b python -m eval.minutes.run --verbose
    python -m eval.minutes.run --output eval/results/minutes_7b.json

결과 JSON과 같은 이름의 폴더에 케이스별 예측(<caseId>.json)을 남긴다 → score.py --pred로 다시 채점할 수 있다.
인자(--engine/--limit/--output/--allow-paid)와 결과 형식(settings·summary·runtime·usage)은 eval.run_eval과 같아서
eval/bench.py의 TASKS에 추가하면 모델 비교표로 모을 수 있다.
"""
import argparse
import json
import time
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List

from app import config
from app.minutes import build_meeting_note
from app.minutes.schemas import MinutesRequest
from eval.minutes.score import load_cases, print_report, score
from eval.run_eval import UsageRecorder, paid_provider_active, peak_memory_mb, runtime_settings

RESULTS_DIR = Path(__file__).resolve().parents[1] / "results"


def case_request(case: Dict[str, Any]) -> MinutesRequest:
    given = case["input"]
    return MinutesRequest(
        noteId=f"mn_eval_{case['caseId']}",
        sourceRefs=[{"sourceId": f"src_eval_{case['caseId']}", "sourceType": given["sourceType"]}],
        text=case["text"],
        occurredAt=given["occurredAt"],
        location=given["location"],
        attendees=given["attendees"],
    )


def evaluate(cases: List[Dict[str, Any]], engine: str, pred_dir: Path) -> Dict[str, Any]:
    pred_dir.mkdir(parents=True, exist_ok=True)
    predictions, rows, latencies = {}, [], []
    started = time.perf_counter()
    with UsageRecorder().attached() as usage:
        for case in cases:
            case_started = time.perf_counter()
            result = build_meeting_note(case_request(case))
            latency_ms = (time.perf_counter() - case_started) * 1000
            predictions[case["caseId"]] = result.note
            (pred_dir / f"{case['caseId']}.json").write_text(
                json.dumps(result.note, ensure_ascii=False, indent=2), encoding="utf-8")
            rows.append({
                "caseId": case["caseId"],
                "llmProvider": result.llm_provider,
                "llmFallbackReason": result.fallback_reason,
                "droppedItems": result.dropped_items,
                "latencyMs": round(latency_ms, 1),
            })
            latencies.append(latency_ms)
            print(f"  {case['caseId']} {latency_ms / 1000:.1f}s provider={result.llm_provider}"
                  f" 버린 항목={len(result.dropped_items)}", flush=True)
    elapsed = time.perf_counter() - started

    report = score(cases, predictions)
    llm_used = sum(1 for row in rows if row["llmProvider"] != "rule")
    latencies.sort()
    summary = {
        "overallF1": report["overall"]["f1"],
        "overallPrecision": report["overall"]["precision"],
        "overallRecall": report["overall"]["recall"],
        **{f"{key}F1": value["f1"] for key, value in report["fields"].items()},
        "evidenceInSource": report["evidenceInSourceRate"],
        "missingFieldsSlotAccuracy": report["missingFields"]["slotAccuracy"],
        "missingFieldsExactMatch": report["missingFields"]["exactMatchRate"],
        "schemaValid": report["schemaValidRate"],
        "modeAccuracy": report["modeAccuracy"],
    }
    return {
        "task": "minutes",
        "engine": engine,
        "settings": runtime_settings(engine),
        "summary": {key: {"score": value, "n": len(cases)} for key, value in summary.items()},
        "report": {key: value for key, value in report.items() if key != "perCase"},
        "runtime": {
            "items": len(rows),
            "elapsedSeconds": round(elapsed, 2),
            "msPerItem": round(elapsed * 1000 / len(rows), 1) if rows else None,
            "p50Ms": round(latencies[len(latencies) // 2], 1) if latencies else None,
            "maxMs": round(latencies[-1], 1) if latencies else None,
            "llmAttempted": len(rows),
            # 1.0이 아니면 LLM 실패로 빈 회의록이 섞인 것 → 점수를 LLM 성능으로 보지 말 것
            "llmCoverage": llm_used / len(rows) if rows else None,
            "droppedItems": sum(len(row["droppedItems"]) for row in rows),
            "peakMemoryMb": peak_memory_mb(),
        },
        "usage": usage.to_dict(),
        "items": rows,
        "predictionsDir": str(pred_dir),
        "_report": report,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="회의록 엔진 평가 (로컬 LLM 필요)")
    parser.add_argument("--engine", default="ollama", choices=["ollama"])
    parser.add_argument("--limit", type=int, default=None, help="앞에서부터 N건만 실행 (유료 API 시험용)")
    parser.add_argument("--cases", default=None, help="쉼표로 고른 caseId만 실행 (예: e01,e02,e03 — 메일 케이스만)")
    parser.add_argument("--allow-paid", action="store_true",
                        help="LLM_PROVIDER=openai일 때 유료 API 호출을 허용 (--limit 필수)")
    parser.add_argument("--output", default=None, help="결과 JSON 경로 (기본: eval/results/minutes_<시각>.json)")
    parser.add_argument("--verbose", action="store_true", help="케이스별 놓친·초과 항목 출력")
    args = parser.parse_args()

    if paid_provider_active(args.engine):
        if not args.allow_paid or args.limit is None:
            parser.error("LLM_PROVIDER=openai 설정이라 유료 API가 호출됩니다. --allow-paid와 --limit N을 함께 주세요.")
        print(f"[유료] OpenAI {config.OPENAI_MODEL_ANALYSIS}로 최대 {args.limit}건 실행 "
              f"(프로세스 예산 상한 ${config.OPENAI_MONTHLY_BUDGET_USD:.2f})")

    output = Path(args.output) if args.output else RESULTS_DIR / f"minutes_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    cases = load_cases()
    if args.cases:
        wanted = {case_id.strip() for case_id in args.cases.split(",") if case_id.strip()}
        cases = [case for case in cases if case["caseId"] in wanted]
    cases = cases[: args.limit]

    print(f"회의록 평가 {len(cases)}건 · 모델 {config.OLLAMA_MODEL if config.LLM_PROVIDER != 'openai' else config.OPENAI_MODEL_ANALYSIS}")
    payload = evaluate(cases, args.engine, output.with_suffix(""))
    payload["finishedAt"] = datetime.now().isoformat(timespec="seconds")
    report = payload.pop("_report")

    print()
    print_report(report, verbose=args.verbose)
    runtime = payload["runtime"]
    print(f"건당 시간            {runtime['msPerItem'] / 1000:.1f}s (중앙 {runtime['p50Ms'] / 1000:.1f}s)"
          f" · 규칙 검증에서 버린 항목 {runtime['droppedItems']}개")
    if runtime["llmCoverage"] is not None and runtime["llmCoverage"] < 1.0:
        print(f"\n[주의] LLM이 실제로 쓰인 비율 {runtime['llmCoverage'] * 100:.0f}% — 나머지는 빈 회의록입니다.")

    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n결과 저장: {output}")


if __name__ == "__main__":
    main()
