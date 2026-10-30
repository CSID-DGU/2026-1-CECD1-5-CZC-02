"""기존 데이터의 검색 누락·모호함·불필요한 후보를 분석. 기준은 변경하지 않는다."""

import argparse
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path

from ml.identity.evaluate_candidates import DEFAULT_DATA, prepare_queries, rank_bounds
from ml.identity.generate_dataset import validate_dataset
from app.identity.ranking import rank_comparisons


def candidate_reason(candidate):
    supports = candidate.candidate.supporting_fields
    if "email" in supports or "phone" in supports:
        return "CONTACT_MATCH"
    if "name" in supports:
        if "company" in supports:
            return "EXACT_NAME_AND_COMPANY_OR_HISTORY"
        return "EXACT_NAME_WITHOUT_COMPANY_MATCH"
    return "SIMILAR_NAME_AND_COMPANY_OR_HISTORY"


def analyze(prepared, threshold, *, sample_limit=5):
    totals = Counter()
    ambiguous_queries = 0
    reasons, event_types, by_case = Counter(), Counter(), defaultdict(Counter)
    samples = defaultdict(list)
    unique_queries = set()
    for pair, comparisons in prepared:
        ranked = rank_comparisons(comparisons, fuzzy_threshold=threshold)
        if pair.get("expected", {}).get("kind") == "AMBIGUOUS":
            ambiguous_queries += 1
            continue
        target = pair["personIdB"]
        bounds = rank_bounds(ranked, target)
        row = {"queries": 1, "missed": int(bounds is None),
               "top1NotGuaranteed": int(bounds is None or bounds[1] > 1),
               "top3NotGuaranteed": int(bounds is None or bounds[1] > 3),
               "extraCandidates": sum(r.person_id != target for r in ranked),
               "queriesWithExtraCandidates": int(any(r.person_id != target for r in ranked))}
        totals.update(row)
        by_case[pair["caseType"]].update(row)
        if comparisons:
            c = comparisons[0]
            unique_queries.add((c.name.observed, c.company.observed, c.email.observed, c.phone.observed))
        events = []
        if bounds is None:
            events.append(("TARGET_MISSED", None))
        elif bounds[1] > 1:
            events.append(("TOP1_TIE_OR_LOWER_RANK", None))
        for candidate in ranked:
            if candidate.person_id != target:
                reason = candidate_reason(candidate)
                reasons[reason] += 1
                events.append((reason, candidate))
        for kind, candidate in events:
            event_types[kind] += 1
            if len(samples[kind]) < sample_limit:
                target_comparison = next(c for c in comparisons if c.person_id == target)
                sample = {"pairId": pair["pairId"], "caseType": pair["caseType"],
                          "mention": pair["mentionB"], "evidence": pair["evidence"]["mentionB"],
                          "targetPersonId": target, "targetRankBounds": bounds,
                          "targetNameStatus": target_comparison.name.status,
                          "targetNameSimilarity": target_comparison.name_similarity,
                          "targetCompanyStatus": target_comparison.company.status}
                if candidate is not None:
                    c = candidate.candidate.comparison
                    sample.update(extraPersonId=c.person_id,
                                  supportingFields=candidate.candidate.supporting_fields,
                                  nameSimilarity=c.name_similarity, warnings=c.warnings,
                                  registeredName=c.name.registered,
                                  companyMatchedPaths=c.company.matched_paths)
                samples[kind].append(sample)
    return {"excludedAmbiguous": ambiguous_queries, "counts": dict(totals), "uniqueObservedQueries": len(unique_queries),
            "extraCandidateReasons": dict(reasons), "eventCounts": dict(event_types),
            "byCase": {k: dict(v) for k, v in sorted(by_case.items())},
            "samples": dict(samples)}


def run(data_dir, threshold):
    paths = {name: data_dir / name for name in ("persons.json", "split.json", "pairs_train.json", "pairs_eval.json")}
    loaded = {name: json.loads(path.read_text(encoding="utf-8")) for name, path in paths.items()}
    dataset = {"persons": loaded["persons.json"], "split": loaded["split.json"],
               "trainPairs": loaded["pairs_train.json"], "evalPairs": loaded["pairs_eval.json"]}
    validate_dataset(dataset)
    report = {"version": "failure-analysis-0.2-reviewed-labels", "fixedThreshold": threshold, "freshHoldoutUsed": False,
              "inputSha256": {name: hashlib.sha256(path.read_bytes()).hexdigest() for name, path in paths.items()},
              "countingUnit": "unambiguous_pair_rows_including_repeats", "splits": {}}
    for split_name, pair_key, id_key in (("train", "trainPairs", "trainPersonIds"), ("eval", "evalPairs", "evalPersonIds")):
        people = [p for p in dataset["persons"] if p["personId"] in dataset["split"][id_key]]
        prepared, _ = prepare_queries(dataset[pair_key], people)
        report["splits"][split_name] = {"exactOnly": analyze(prepared, None), "fuzzyRules": analyze(prepared, threshold)}
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DEFAULT_DATA)
    parser.add_argument("--threshold", type=float, default=.88)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent / "results/failure_analysis.json")
    args = parser.parse_args()
    report = run(args.data_dir, args.threshold)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({key: value["fuzzyRules"]["counts"] for key, value in report["splits"].items()}, indent=2))


if __name__ == "__main__":
    main()
