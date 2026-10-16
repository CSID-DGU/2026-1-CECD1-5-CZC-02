"""규칙 검색의 단일 프로세스 대장 크기별 시간 측정. 품질 평가·서비스 부하 시험은 아니다."""

import argparse
import cProfile
import hashlib
import json
import math
import os
import platform
import pstats
import statistics
import time
from copy import deepcopy
from pathlib import Path

from app.identity.ranking import rank_candidates
from app.identity.similarity import _edit_distance
from ml.identity.evaluate_candidates import DEFAULT_DATA
from ml.identity.generate_dataset import CONTRACTS_DIR
from jsonschema import Draft202012Validator


def make_roster(baseline, size):
    if size < 1 or not baseline:
        raise ValueError("대장 크기와 원본 대장이 필요합니다")
    people = deepcopy(baseline[:size])
    for index in range(len(people), size):
        people.append({"personId": f"p_load_{index:05}", "name": f"가상부하인물_{index:05}",
                       "nameVariants": [f"Synthetic Load Person {index:05}"],
                       "company": f"가상부하회사_{index % 20:03}", "title": None,
                       "emails": [f"synthetic-load-{index}@load.example"], "phones": [f"000-9999-{index:05}"],
                       "companyHistory": [{"company": f"가상부하이전회사_{index % 10:03}", "until": None}],
                       "isSynthetic": True})
    validator = Draft202012Validator(json.loads((CONTRACTS_DIR / "person.schema.json").read_text(encoding="utf-8")))
    for person in people:
        validator.validate(person)
    return people


def timing_summary(values):
    if not values:
        raise ValueError("시간 표본이 없습니다")
    ordered = sorted(values)
    return {"samples": len(values), "medianMs": statistics.median(ordered),
            "p95Ms": ordered[math.ceil(.95 * len(ordered)) - 1],
            "minMs": ordered[0], "maxMs": ordered[-1]}


def select_queries(pairs):
    # 학습의 유형별 서로 다른 입력 1건. 평가셋·새 holdout을 속도 시험에 소비하지 않는다.
    selected = {}
    for pair in pairs:
        selected.setdefault(pair["caseType"], dict(pair["mentionB"]))
    return [selected[k] for k in sorted(selected)]


def benchmark(baseline, queries, *, sizes=(60, 120, 300, 1000), repeats=3, threshold=.88):
    if repeats < 1 or not queries:
        raise ValueError("반복 수와 입력이 필요합니다")
    results = []
    for size in sizes:
        people = make_roster(baseline, size)
        modes = {}
        for mode in ("distance_cache_cold", "distance_cache_warm"):
            latencies = []
            for query in queries:
                _edit_distance.cache_clear()
                if mode == "distance_cache_warm":
                    rank_candidates(query, people, fuzzy_threshold=threshold)
                for _ in range(repeats):
                    if mode == "distance_cache_cold":
                        _edit_distance.cache_clear()
                    start = time.perf_counter_ns()
                    rank_candidates(query, people, fuzzy_threshold=threshold)
                    latencies.append((time.perf_counter_ns() - start) / 1_000_000)
            modes[mode] = timing_summary(latencies)
        results.append({"persons": size, **modes})
        print(f"대장 {size}명 측정 완료", flush=True)
    # 별도 300명 프로파일: 측정 표본에는 profiler 비용을 섞지 않는다.
    people = make_roster(baseline, 300)
    _edit_distance.cache_clear()
    profiler = cProfile.Profile()
    profiler.enable()
    for query in queries:
        rank_candidates(query, people, fuzzy_threshold=threshold)
    profiler.disable()
    stats = pstats.Stats(profiler).stats
    functions = []
    for (filename, line, name), (primitive, calls, self_time, cumulative, _) in stats.items():
        if "/identity/" in filename and not filename.endswith("benchmark_candidates.py"):
            functions.append({"function": f"{Path(filename).name}:{line}:{name}", "calls": calls,
                              "selfSeconds": self_time, "cumulativeSeconds": cumulative})
    functions.sort(key=lambda x: x["cumulativeSeconds"], reverse=True)
    _edit_distance.cache_clear()
    return {"version": "rule-latency-0.1", "fixedThreshold": threshold, "freshHoldoutUsed": False,
            "modelLoaded": False, "queriesPerSize": len(queries), "repeats": repeats,
            "environment": {"python": platform.python_version(), "system": platform.system(),
                            "release": platform.release(), "architecture": platform.machine(), "logicalCpus": os.cpu_count()},
            "scope": "single_process_sequential_rule_search_only",
            "excluded": ["file_io", "roster_generation", "schema_validation", "cache_clear", "warmup", "model_inference", "http", "database"],
            "sizeResults": results, "profile300Cold": functions[:10],
            "limitations": ["nine_training_queries", "three_repetitions_default", "synthetic_load_records_above_300",
                            "subset_rosters_may_not_contain_target", "no_quality_metrics", "no_service_sla", "machine_load_sensitive"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DEFAULT_DATA)
    parser.add_argument("--sizes", type=int, nargs="+", default=[60, 120, 300, 1000])
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent / "results/candidate_benchmark.json")
    args = parser.parse_args()
    people = json.loads((args.data_dir / "persons.json").read_text(encoding="utf-8"))
    queries = select_queries(json.loads((args.data_dir / "pairs_train.json").read_text(encoding="utf-8")))
    report = benchmark(people, queries, sizes=args.sizes, repeats=args.repeats)
    report["inputSha256"] = {name: hashlib.sha256((args.data_dir / name).read_bytes()).hexdigest()
                             for name in ("persons.json", "pairs_train.json")}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"저장: {args.output}")


if __name__ == "__main__":
    main()
