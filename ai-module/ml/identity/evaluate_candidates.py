"""학습에서만 임계값을 고르고 고정된 후보 검색을 평가한다."""

import argparse
import hashlib
import json
from collections import defaultdict
from pathlib import Path

from app.identity.features import compare_people
from app.identity.ranking import rank_comparisons

THRESHOLDS = (0.80, 0.85, 0.88, 0.90, 0.95)
DEFAULT_DATA = Path(__file__).resolve().parent / "data"


def prepare_queries(pairs, persons):
    """정답·사례 유형·원본 ID는 모델 입력에 넘기지 않는다. 같은 단서는 계산을 재사용한다."""
    cache = {}
    prepared = []
    for pair in pairs:
        raw = pair["mentionB"]
        mention = {key: raw.get(key) for key in ("name", "company", "email", "phone")}
        mention["channel"] = raw["channel"]
        key = tuple(mention[k] for k in ("name", "company", "email", "phone"))
        if key not in cache:
            cache[key] = compare_people(mention, persons)
        prepared.append((pair, cache[key]))
    return prepared, len(cache)


def rank_bounds(ranked, target):
    """동점 내 최선·최악 순위. ID 표시 순서로 성능이 올라가지 않게 한다."""
    found = next((item for item in ranked if item.person_id == target), None)
    if found is None:
        return None
    better = sum(item.priority > found.priority for item in ranked)
    equal = sum(item.priority == found.priority for item in ranked)
    return better + 1, better + equal


def measure(prepared, threshold, *, ranker=None):
    rows = []
    tp = fp = fn = tn = 0
    for pair, comparisons in prepared:
        ranked = ranker(comparisons) if ranker is not None else rank_comparisons(comparisons, fuzzy_threshold=threshold)
        # 검색의 정답은 label과 무관하게 mentionB의 실제 사람이다.
        bounds = rank_bounds(ranked, pair["personIdB"])
        row = {"caseType": pair["caseType"], "included": bounds is not None,
               "top1Guaranteed": bounds is not None and bounds[1] <= 1,
               "top3Guaranteed": bounds is not None and bounds[1] <= 3,
               "top3Possible": bounds is not None and bounds[0] <= 3,
               "candidateCount": len(ranked)}
        rows.append(row)
        # 쌍별 검사는 B 언급이 A 인물을 후보에 넣었는지다. 확정 판정 정확도가 아니다.
        predicted = any(item.person_id == pair["personIdA"] for item in ranked)
        if pair["label"] == 1:
            tp += predicted
            fn += not predicted
        else:
            fp += predicted
            tn += not predicted
    def summarize(group):
        n = len(group)
        return {"queries": n, **{
            key: sum(row[key] for row in group) / n if n else None
            for key in ("included", "top1Guaranteed", "top3Guaranteed", "top3Possible", "candidateCount")
        }}
    groups = defaultdict(list)
    for row in rows:
        groups[row["caseType"]].append(row)
    return {"retrieval": summarize(rows),
            "byCase": {key: summarize(value) for key, value in sorted(groups.items())},
            "pairCandidateInclusion": {"tp": tp, "fp": fp, "fn": fn, "tn": tn,
                "precision": tp / (tp + fp) if tp + fp else None,
                "recall": tp / (tp + fn) if tp + fn else None}}


def select_threshold(train_prepared):
    trials = [{"threshold": threshold, "metrics": measure(train_prepared, threshold)} for threshold in THRESHOLDS]
    # 동점 최악 순위 기준 top-3 → 후보 수 적음 → 더 엄격한 임계값 순으로 선택한다.
    selected = max(trials, key=lambda t: (
        t["metrics"]["retrieval"]["top3Guaranteed"],
        -t["metrics"]["retrieval"]["candidateCount"], t["threshold"],
    ))
    return selected["threshold"], trials


def evaluate(data_dir):
    paths = [data_dir / filename for filename in ("persons.json", "split.json", "pairs_train.json", "pairs_eval.json")]
    persons, split, train, evaluation = [json.loads(path.read_text()) for path in paths]
    train_ids, eval_ids = set(split["trainPersonIds"]), set(split["evalPersonIds"])
    ids = [p["personId"] for p in persons]
    if train_ids & eval_ids or train_ids | eval_ids != set(ids) or len(ids) != len(set(ids)):
        raise ValueError("인물 분할이 유효하지 않습니다")
    for pairs, allowed in ((train, train_ids), (evaluation, eval_ids)):
        if not pairs or any(p["personIdA"] not in allowed or p["personIdB"] not in allowed
                            or p["label"] != int(p["personIdA"] == p["personIdB"]) for p in pairs):
            raise ValueError("비교 쌍의 분할 또는 정답이 유효하지 않습니다")
    train_queries, train_unique = prepare_queries(train, [p for p in persons if p["personId"] in train_ids])
    threshold, trials = select_threshold(train_queries)
    # 임계값을 선택한 뒤 평가 언급을 처음 계산한다. 평가로 재선택하지 않는다.
    eval_queries, eval_unique = prepare_queries(evaluation, [p for p in persons if p["personId"] in eval_ids])
    return {
        "version": "candidate-ranking-0.1", "selectedThreshold": threshold,
        "selectionData": "train_only", "trainTrials": trials,
        "train": {"persons": len(train_ids), "uniqueQueries": train_unique},
        "eval": {"persons": len(eval_ids), "uniqueQueries": eval_unique,
                 "exactBaseline": measure(eval_queries, None), "fuzzyRanking": measure(eval_queries, threshold)},
        "inputSha256": {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in paths},
        "limitations": ["synthetic_numbered_names", "repeated_queries", "known_people_only",
                        "split_specific_rosters_240_and_60", "no_probability_or_final_decision",
                        "top3_guaranteed_uses_worst_rank_within_ties", "threshold_selection_is_train_only_not_cross_validation"],
    }


def main():
    parser = argparse.ArgumentParser(description="A 내부 후보 검색 비교 평가")
    parser.add_argument("--data-dir", type=Path, default=DEFAULT_DATA)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent / "results/candidate_evaluation.json")
    args = parser.parse_args()
    report = evaluate(args.data_dir)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"report": str(args.output), "threshold": report["selectedThreshold"],
                      "evalExact": report["eval"]["exactBaseline"]["retrieval"],
                      "evalFuzzy": report["eval"]["fuzzyRanking"]["retrieval"]}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
