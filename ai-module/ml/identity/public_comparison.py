"""공개 RULE/hybrid 함수의 합성 비교. 학습·설정 선택·실제 자료 입력은 없다."""
import argparse
import json
import logging
import math
import os
import platform
import random
from collections import Counter, defaultdict
from copy import deepcopy
from pathlib import Path

from app.identity import engine, hybrid, logistic
from app.identity.features import compare_people
from app.identity.normalization import normalize_email, normalize_name, normalize_phone, normalize_text
from ml.identity.controlled_data import digest
from ml.identity.fresh_validation import make_fresh, validate_data

ROOT = Path(__file__).resolve().parents[2]
SEED = 20261123


def make_splits(seed=SEED, groups=20):
    """정답·공유 연락처·동명이인 집단을 통째로 분리한다. 새 학습 자료가 아니다."""
    if groups < 2 or groups % 2:
        raise ValueError("groups는 2 이상의 짝수여야 합니다")
    data = make_fresh(seed=seed, groups=groups)
    clusters = sorted({q["cluster"] for q in data["queries"]})
    random.Random(seed + 1).shuffle(clusters)
    result = {}
    for name, selected in (("validation", clusters[:groups//2]), ("test", clusters[groups//2:])):
        group_numbers = {int(c.rsplit("_", 1)[1]) for c in selected}
        result[name] = {
            "persons": [p for i, p in enumerate(data["persons"]) if i//6 in group_numbers],
            "queries": [q for q in data["queries"] if q["cluster"] in selected],
        }
        validate_data(result[name])
    validate_splits(result)
    return result


def validate_splits(splits):
    def clues(data):
        people = data["persons"]
        return (
            {p["personId"] for p in people},
            {normalize_name(n) for p in people for n in (p["name"], *p["nameVariants"])},
            {normalize_email(v) for p in people for v in p["emails"]},
            {normalize_phone(v) for p in people for v in p["phones"]},
            {v for p in people for v in (p["company"], *(h["company"] for h in p["companyHistory"]))},
            {q["cluster"] for q in data["queries"]},
        )
    for left, right in zip(clues(splits["validation"]), clues(splits["test"])):
        if (left & right) - {None}:
            raise ValueError("검증용·시험용 사이에 인물 또는 단서가 겹칩니다")


def measure(query, result):
    """미정 정답은 점수화하지 않는다. 애매함은 한 명 포함과 전원 포함을 나눠 센다."""
    expected = query["expected"]
    kind, targets = expected["kind"], set(expected["targetPersonIds"])
    if kind not in {"KNOWN", "AMBIGUOUS", "CONFLICT", "NEW", "INSUFFICIENT"}:
        raise ValueError("검수되지 않은 정답은 정확도 분모에 넣지 않습니다")
    if kind == "KNOWN" and len(targets) != 1:
        raise ValueError("KNOWN은 단일 정답이 필요합니다")
    if kind in {"AMBIGUOUS", "CONFLICT"} and len(targets) < 2:
        raise ValueError("애매함·충돌은 가능한 인물 집합이 필요합니다")
    if kind in {"NEW", "INSUFFICIENT"} and targets:
        raise ValueError("신규·정보 부족에 정답 인물을 추측해 넣지 않습니다")
    selected = ([result["personId"]] if result["decision"] == "CONFIRMED"
                else [c["personId"] for c in result["candidates"]])
    found = targets & set(selected)
    return {"queryId": query["queryId"], "cluster": query["cluster"], "caseType": query["caseType"],
            "kind": kind, "decision": result["decision"], "selectedIds": selected,
            "targetCount": len(targets), "validTargetCount": len(found),
            "knownHit": bool(found) if kind == "KNOWN" else None,
            "wrongConfirmation": result["decision"] == "CONFIRMED" and
                (kind != "KNOWN" or result["personId"] not in targets),
            "ambiguityHandled": result["decision"] == "CANDIDATES" and bool(found)
                if kind in {"AMBIGUOUS", "CONFLICT"} else None,
            "allTargetsIncluded": targets <= set(selected) if targets else None,
            "falseSuggestedIds": len(set(selected) - targets),
            "newCorrect": result["decision"] == "NEW" and not selected if kind == "NEW" else None,
            "insufficientSafe": result["decision"] == "NEW" and not selected if kind == "INSUFFICIENT" else None}


def summarize(rows):
    known = [r for r in rows if r["kind"] == "KNOWN"]
    ambiguity = [r for r in rows if r["kind"] in {"AMBIGUOUS", "CONFLICT"}]
    feasible = [r for r in ambiguity if r["targetCount"] <= 3]
    overflow = [r for r in ambiguity if r["targetCount"] > 3]
    suggested = sum(len(r["selectedIds"]) for r in rows)
    extra = sum(r["falseSuggestedIds"] for r in rows)
    def ratio(a, b):
        return a/b if b else None
    return {"queries": len(rows), "kindCounts": dict(Counter(r["kind"] for r in rows)),
            "knownQueries": len(known), "knownHits": sum(r["knownHit"] for r in known),
            "knownTop3Recall": ratio(sum(r["knownHit"] for r in known), len(known)),
            "confirmationCount": sum(r["decision"] == "CONFIRMED" for r in rows),
            "wrongConfirmations": sum(r["wrongConfirmation"] for r in rows),
            "ambiguousConflictQueries": len(ambiguity),
            "ambiguousConflictHandled": sum(r["ambiguityHandled"] for r in ambiguity),
            "ambiguousFeasibleQueries": len(feasible),
            "ambiguousFeasibleAllTargetsIncluded": sum(r["allTargetsIncluded"] for r in feasible),
            "overflowQueries": len(overflow),
            "overflowValidTargetCoverage": ratio(sum(r["validTargetCount"] for r in overflow),
                                                sum(r["targetCount"] for r in overflow)),
            "newQueries": sum(r["kind"] == "NEW" for r in rows),
            "newCorrect": sum(bool(r["newCorrect"]) for r in rows),
            "insufficientQueries": sum(r["kind"] == "INSUFFICIENT" for r in rows),
            "insufficientSafe": sum(bool(r["insufficientSafe"]) for r in rows),
            "totalSuggestedIds": suggested, "falseSuggestedIds": extra,
            "falseSuggestionRate": ratio(extra, suggested)}


def paired_interval(rule, mixed, seed=SEED, resamples=5000):
    groups = defaultdict(list)
    if len(rule) != len(mixed):
        raise ValueError("같은 질문에 대한 대응 결과가 필요합니다")
    for a, b in zip(rule, mixed):
        if (a["queryId"], a["cluster"], a["kind"]) != (b["queryId"], b["cluster"], b["kind"]):
            raise ValueError("대응 질문이 다릅니다")
        if a["kind"] == "KNOWN":
            groups[a["cluster"]].append(int(b["knownHit"]) - int(a["knownHit"]))
    if not groups:
        return {"clusters": 0, "delta": None, "percentile95CI": None}
    rng, clusters = random.Random(seed + 99), sorted(groups)
    samples = []
    for _ in range(resamples):
        values = [v for c in rng.choices(clusters, k=len(clusters)) for v in groups[c]]
        samples.append(sum(values)/len(values))
    samples.sort()
    def percentile(p):
        x = (len(samples)-1)*p
        lo, hi = math.floor(x), math.ceil(x)
        return samples[lo] + (samples[hi]-samples[lo])*(x-lo)
    values = [v for c in clusters for v in groups[c]]
    return {"clusters": len(clusters), "resamples": resamples, "delta": sum(values)/len(values),
            "percentile95CI": [percentile(0.025), percentile(0.975)]}


def check_confirmation(mention, persons, result):
    if result["decision"] != "CONFIRMED":
        return
    if result["decidedBy"] != "RULE":
        raise ValueError("MODEL 자동 확정은 허용하지 않습니다")
    contacts = [c for c in compare_people(mention, persons) if "MATCH" in (c.email.status, c.phone.status)]
    if len(contacts) != 1 or contacts[0].person_id != result["personId"]:
        raise ValueError("한 사람의 정확한 연락처 일치가 필요합니다")
    comparison = contacts[0]
    if comparison.warnings or comparison.name.status == "DIFFERENT" or comparison.company.status == "DIFFERENT":
        raise ValueError("충돌·공용 연락처가 있으면 확정할 수 없습니다")
    for field, normalizer in (("email", normalize_email), ("phone", normalize_phone)):
        if normalize_text(mention.get(field)) is not None and (
            normalizer(mention[field]) is None or getattr(comparison, field).status == "DIFFERENT"
        ):
            raise ValueError("추가 연락처의 불일치를 무시할 수 없습니다")


def source_hashes():
    import hashlib
    paths = list((ROOT/"app/identity").glob("*.py")) + [logistic.ARTIFACT]
    paths += [ROOT/"ml/identity"/name for name in (
        "public_comparison.py", "fresh_validation.py", "controlled_data.py", "controlled_experiment.py", "learned_comparison.py")]
    paths += [ROOT.parent/"contracts"/name for name in ("person.schema.json", "identity_resolution.schema.json")]
    return {p.relative_to(ROOT.parent).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(paths)}


def write_json(path, value):
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n")


def run(output):
    """기존 출력은 덮어쓰지 않으며, 모든 자료·모델을 예측 전에 고정한다."""
    model, splits = logistic.load_model(), make_splits()
    frozen = {"seed": SEED, "groups": 20, "splitShuffleSeed": SEED + 1,
              "datasetSha256": {name: digest(data) for name, data in splits.items()},
              "codeSha256": source_hashes(), "ruleVersion": engine.MODEL_VERSION,
              "ruleFuzzyThreshold": engine.FUZZY_THRESHOLD,
              "logisticVersion": model.version, "logisticThreshold": model.threshold,
              "pythonVersion": platform.python_version(), "training": False, "thresholdSelection": False}
    output.mkdir()  # exists -> stop, including partially finished earlier runs
    write_json(output/"frozen_protocol.json", frozen)
    for name, data in splits.items():
        write_json(output/f"{name}_dataset.json", data)
    logger_warnings = []
    class Capture(logging.Handler):
        def emit(self, record):
            logger_warnings.append(record.getMessage())
    handler = Capture(logging.WARNING)
    old_mode = os.environ.get("IDENTITY_CANDIDATE_MODE")
    os.environ["IDENTITY_CANDIDATE_MODE"] = "hybrid"
    hybrid.logger.addHandler(handler)
    reports = {}
    try:
        for name, data in splits.items():
            before, rows, predictions = deepcopy(data), {"rule": [], "hybrid": []}, []
            for query in data["queries"]:
                for mode, resolver in (("rule", engine.resolve_identities), ("hybrid", hybrid.resolve_identities)):
                    result = resolver([query["mention"]], data["persons"])[0]
                    engine.validators()[2].validate(result)
                    if any(not c["reasons"] for c in result["candidates"]):
                        raise ValueError("후보 이유가 비어 있습니다")
                    check_confirmation(query["mention"], data["persons"], result)
                    rows[mode].append(measure(query, result))
                    predictions.append({"mode": mode, "queryId": query["queryId"],
                                        "result": {k: v for k, v in result.items() if k != "resolutionId"}})
            if data != before or logger_warnings:
                raise ValueError("입력 변경 또는 hybrid 경고·폴백이 발생했습니다")
            reports[name] = {mode: {"summary": summarize(values), "byCase": {
                case: summarize([r for r in values if r["caseType"] == case])
                for case in sorted({r["caseType"] for r in values})}} for mode, values in rows.items()}
            reports[name]["pairedKnownTop3Comparison"] = paired_interval(rows["rule"], rows["hybrid"])
            write_json(output/f"{name}_predictions.json", predictions)
    finally:
        hybrid.logger.removeHandler(handler)
        if old_mode is None:
            os.environ.pop("IDENTITY_CANDIDATE_MODE", None)
        else:
            os.environ["IDENTITY_CANDIDATE_MODE"] = old_mode
    if source_hashes() != frozen["codeSha256"] or any(digest(data) != frozen["datasetSha256"][name] for name, data in splits.items()):
        raise ValueError("실행 중 모델·코드·입력 자료가 바뀌었습니다")
    report = {"format": "identity-public-comparison-v1", "frozen": frozen, "reports": reports,
              "responsesSchemaValidated": sum(len(d["queries"])*2 for d in splits.values()),
              "confirmationBoundaryValidated": True, "hybridWarningsOrFallbacks": 0,
              "productionChanged": False, "realDerivedDataUsed": False,
              "limitations": ["same_synthetic_generator_family_not_real_company_accuracy",
                              "synthetic_gold_not_human_reviewed", "extra_candidates_are_not_wrong_confirmations",
                              "test_is_observed_after_run_do_not_reuse_for_unseen_claims",
                              "bootstrap_describes_only_ten_synthetic_clusters_per_split"]}
    write_json(output/"report.json", report)
    return report


def main():
    parser = argparse.ArgumentParser(description="완전 합성 자료에서 현재 RULE/hybrid 공개 함수 비교 (재학습 없음)")
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    try:
        report = run(args.output_dir)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    print(json.dumps({"output": str(args.output_dir), "test": {
        mode: report["reports"]["test"][mode]["summary"] for mode in ("rule", "hybrid")}}, ensure_ascii=False))


if __name__ == "__main__":
    main()
