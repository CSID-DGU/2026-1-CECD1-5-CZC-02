"""고정 규정의 전체 대장 비교. fit/calibration/tune/test 인물·역할을 분리한다."""
import argparse
import hashlib
import json
import math
import random
import time
from collections import Counter, defaultdict
from pathlib import Path

from app.identity.engine import _can_confirm, has_identity_clue
from app.identity.features import compare_people
from app.identity.ranking import rank_comparisons
from ml.identity.controlled_data import digest, make_corpus
from ml.identity.learned_comparison import FEATURES, features

PROTOCOL = Path(__file__).with_name("TRAINING_PROTOCOL.json")


def prepare(data):
    result = []
    for query in data["queries"]:
        start = time.perf_counter()
        comparisons = compare_people(query["mention"], data["persons"])
        elapsed = (time.perf_counter() - start) * 1000
        result.append({"query": query, "comparisons": comparisons, "x": [features(c) for c in comparisons], "featureMs": elapsed})
    return result


def training_rows(prepared, protocol, *, sampled=False, seed=42):
    rng = random.Random(seed)
    x, y, weights = [], [], []
    for row in prepared:
        expected = row["query"]["expected"]
        if expected["kind"] not in {"KNOWN", "NEW"}:
            continue
        targets = set(expected["targetPersonIds"])
        positive = [i for i, c in enumerate(row["comparisons"]) if c.person_id in targets]
        negative = [i for i, c in enumerate(row["comparisons"]) if c.person_id not in targets]
        if sampled:
            ordered = sorted(negative, key=lambda i: (
                row["comparisons"][i].email.status == "MATCH" or row["comparisons"][i].phone.status == "MATCH",
                row["comparisons"][i].name_similarity or 0), reverse=True)
            hard = ordered[:protocol["fitSampling"]["hardNegativesPerQuery"]]
            remaining = [i for i in negative if i not in hard]
            negative = hard + rng.sample(remaining, min(len(remaining), protocol["fitSampling"]["randomNegativesPerQuery"]))
        indices = positive + negative
        for index in indices:
            x.append(row["x"][index]); y.append(int(index in positive)); weights.append(1 / len(indices))
    if set(y) != {0, 1}:
        raise ValueError("학습/보정에 양성과 음성이 모두 필요합니다")
    return x, y, weights


def outcome(row, scores=None, threshold=0.5):
    """모든 방식이 같은 D-022 연락처 관문 사용. 아래 결과는 실험 내부 형식이다."""
    mention, comparisons = row["query"]["mention"], row["comparisons"]
    if not has_identity_clue(mention):
        return {"decision": "NEW", "personId": None, "ranked": []}
    contacts = [c for c in comparisons if "MATCH" in (c.email.status, c.phone.status)]
    if len(contacts) == 1 and _can_confirm(contacts[0], contacts, mention):
        return {"decision": "CONFIRMED", "personId": contacts[0].person_id,
                "ranked": [(contacts[0].person_id, (1.0,))]}
    if scores is None:
        ranked = [(r.person_id, r.priority) for r in rank_comparisons(comparisons, fuzzy_threshold=0.88)]
    else:
        if len(scores) != len(comparisons) or any(not math.isfinite(s) or not 0 <= s <= 1 for s in scores):
            raise ValueError("모델 점수 형식 오류")
        ranked = []
        for c, score in zip(comparisons, scores):
            contact = "MATCH" in (c.email.status, c.phone.status)
            exact = c.name.status == "MATCH"
            if contact or exact or score >= threshold:
                # 공유 연락처/동명이인 후보를 모델이 삭제하거나 점수만으로 확정하지 못하게 한다.
                ranked.append((c.person_id, (int(contact), int(exact), float(score))))
        ranked.sort(key=lambda item: (tuple(-v for v in item[1]), item[0]))
    return {"decision": "CANDIDATES" if ranked else "NEW", "personId": None, "ranked": ranked}


def worst_rank(ranked, target):
    score = next((score for person, score in ranked if person == target), None)
    return None if score is None else sum(other >= score for _, other in ranked)


def row_metrics(query, answer):
    kind, targets = query["expected"]["kind"], set(query["expected"]["targetPersonIds"])
    ids = {person for person, _ in answer["ranked"][:3]}
    known = kind == "KNOWN"
    confirmed = answer["decision"] == "CONFIRMED"
    ambiguous = kind in {"AMBIGUOUS", "CONFLICT"}
    return {"cluster": query["cluster"], "caseType": query["caseType"], "kind": kind,
            "known": int(known), "top3": int(known and bool(targets & ids)),
            "top3Guaranteed": int(known and all((worst_rank(answer["ranked"], t) or math.inf) <= 3 for t in targets)),
            "confirmed": int(confirmed), "confirmedCorrect": int(confirmed and known and answer["personId"] in targets),
            "ambiguous": int(ambiguous), "ambiguitySafe": int(ambiguous and answer["decision"] == "CANDIDATES" and targets <= ids),
            "new": int(kind == "NEW"), "newCorrect": int(kind == "NEW" and answer["decision"] == "NEW"),
            "insufficient": int(kind == "INSUFFICIENT"), "insufficientSafe": int(kind == "INSUFFICIENT" and not ids),
            "falseNew": int(known and answer["decision"] == "NEW"), "candidateCount": len(ids),
            "falseCandidates": len(ids - targets)}


def summarize(rows):
    total = len(rows)
    sums = {key: sum(r[key] for r in rows) for key in (
        "known", "top3", "top3Guaranteed", "confirmed", "confirmedCorrect", "ambiguous", "ambiguitySafe",
        "new", "newCorrect", "insufficient", "insufficientSafe", "falseNew", "candidateCount", "falseCandidates")}
    def ratio(a, b):
        return sums[a] / sums[b] if sums[b] else None
    return {"queries": total, "counts": sums, "top3Recall": ratio("top3", "known"),
            "top3GuaranteedRecall": ratio("top3Guaranteed", "known"), "confirmedPrecision": ratio("confirmedCorrect", "confirmed"),
            "ambiguityCoverage": ratio("ambiguitySafe", "ambiguous"), "newAccuracy": ratio("newCorrect", "new"),
            "insufficientSafety": ratio("insufficientSafe", "insufficient"), "falseNewRate": ratio("falseNew", "known"),
            "meanCandidates": sums["candidateCount"] / total if total else None,
            "meanFalseCandidates": sums["falseCandidates"] / total if total else None}


def evaluate(prepared, score_rows=None, threshold=0.5):
    rows = [row_metrics(row["query"], outcome(row, None if score_rows is None else score_rows[i], threshold))
            for i, row in enumerate(prepared)]
    return summarize(rows), rows


def safe_against(metrics, baseline, protocol):
    gate = protocol["gate"]
    return ((metrics["confirmedPrecision"] or 0) >= gate["minimumConfirmedPrecision"]
            and metrics["top3GuaranteedRecall"] >= max(gate["minimumTop3Recall"], baseline["top3GuaranteedRecall"] - gate["maximumTop3Drop"])
            and metrics["ambiguityCoverage"] >= baseline["ambiguityCoverage"] - gate["maximumAmbiguousCoverageDrop"]
            and metrics["newAccuracy"] >= baseline["newAccuracy"] - gate["maximumNewFalseCandidateIncrease"]
            and metrics["insufficientSafety"] == 1)


def choose_policy(prepared, score_rows, baseline, protocol):
    trials = []
    for threshold in protocol["candidateThresholds"]:
        metrics, _ = evaluate(prepared, score_rows, threshold)
        trials.append({"threshold": threshold, "metrics": metrics, "safetyPassed": safe_against(metrics, baseline, protocol)})
    selected = max(trials, key=lambda t: (t["safetyPassed"], t["metrics"]["top3GuaranteedRecall"],
                   t["metrics"]["newAccuracy"], -t["metrics"]["meanFalseCandidates"], -t["metrics"]["meanCandidates"], t["threshold"]))
    return selected, trials


def cluster_interval(baseline_rows, model_rows, protocol):
    """같은 인물 그룹의 모든 질문을 함께 재표집한다. 두 방식에 같은 표본을 쓴다."""
    if len(baseline_rows) != len(model_rows):
        raise ValueError("대응 평가 길이가 다릅니다")
    groups = defaultdict(list)
    for i, (base, model) in enumerate(zip(baseline_rows, model_rows)):
        if base["cluster"] != model["cluster"]:
            raise ValueError("대응 인물 그룹이 다릅니다")
        groups[base["cluster"]].append(i)
    rng = random.Random(protocol["bootstrap"]["seed"])
    keys = sorted(groups)
    values = {"top3Gain": [], "falseCandidatesReduction": []}
    for _ in range(protocol["bootstrap"]["replicates"]):
        indices = [i for key in rng.choices(keys, k=len(keys)) for i in groups[key]]
        base = summarize([baseline_rows[i] for i in indices]); model = summarize([model_rows[i] for i in indices])
        values["top3Gain"].append(model["top3GuaranteedRecall"] - base["top3GuaranteedRecall"])
        values["falseCandidatesReduction"].append(base["meanFalseCandidates"] - model["meanFalseCandidates"])
    alpha = (1 - protocol["bootstrap"]["interval"]) / 2
    return {name: {"lower": sorted(v)[int(alpha * (len(v) - 1))], "upper": sorted(v)[int((1 - alpha) * (len(v) - 1))]}
            for name, v in values.items()}


def gate_result(metrics, baseline, interval, p95, protocol):
    limits = protocol["gate"]
    top_gain = metrics["top3GuaranteedRecall"] - baseline["top3GuaranteedRecall"]
    false_gain = baseline["meanFalseCandidates"] - metrics["meanFalseCandidates"]
    improvement = ((top_gain >= limits["minimumTop3Gain"] and interval["top3Gain"]["lower"] > 0)
                   or (false_gain >= limits["minimumFalseCandidatesReduction"] and interval["falseCandidatesReduction"]["lower"] > 0))
    safety = safe_against(metrics, baseline, protocol)
    latency = p95 <= limits["p95RequestMillisecondsMaximum"]
    return {"safetyPassed": safety, "improvementPassed": improvement, "latencyPassed": latency,
            "passed": safety and improvement and latency, "top3Gain": top_gain, "falseCandidatesReduction": false_gain}


def _scores(model, calibration, prepared, np):
    sizes = [len(row["x"]) for row in prepared]
    x = [value for row in prepared for value in row["x"]]
    raw = model.decision_function(x)
    probabilities = calibration.predict_proba(np.asarray(raw).reshape(-1, 1))[:, 1]
    result = []; offset = 0
    for size in sizes:
        result.append([float(p) for p in probabilities[offset:offset + size]]); offset += size
    return result


def experiment(protocol, notify=print, freeze_data=None):
    # 선택 라이브러리는 명시적 실험 실행 시에만 로드한다.
    import numpy as np
    import sklearn
    from sklearn.ensemble import GradientBoostingClassifier
    from sklearn.linear_model import LogisticRegression
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import StandardScaler

    corpus = make_corpus(protocol)
    manifests = {name: {**data["manifest"], "sha256": digest(data),
                 "caseCounts": dict(Counter(q["caseType"] for q in data["queries"])),
                 "kindCounts": dict(Counter(q["expected"]["kind"] for q in data["queries"]))} for name, data in corpus.items()}
    # 최종 시험 자료 생성/해시는 학습 전에 고정한다. test의 특징/점수는 선택 완료 뒤에만 계산한다.
    if freeze_data is not None:
        freeze_data(manifests)
    notify("분할별 인물/질문과 자동 정답 해시 고정 완료", flush=True)
    prepared = {}
    for name in ("fit", "calibration", "tune"):
        prepared[name] = prepare(corpus[name]); notify(f"{name} 특징 준비: {len(prepared[name])}개 질문", flush=True)
    x_fit, y_fit, weights = training_rows(prepared["fit"], protocol, sampled=True)
    x_cal, y_cal, _ = training_rows(prepared["calibration"], protocol)
    baseline_tune, _ = evaluate(prepared["tune"])
    cal_pairs = len(y_cal)
    trained = []
    for seed in protocol["modelSeeds"]:
        for family, configs in (("logistic", protocol["logisticC"]), ("gradient", protocol["gradientDepth"])):
            trials, candidates = [], []
            for parameter in configs:
                if family == "logistic":
                    model = make_pipeline(StandardScaler(), LogisticRegression(C=parameter, solver="lbfgs", max_iter=2000, random_state=seed))
                    model.fit(x_fit, y_fit, standardscaler__sample_weight=weights, logisticregression__sample_weight=weights)
                else:
                    model = GradientBoostingClassifier(loss="log_loss", n_estimators=protocol["gradientEstimators"],
                            learning_rate=protocol["gradientLearningRate"], max_depth=parameter, random_state=seed)
                    model.fit(x_fit, y_fit, sample_weight=weights)
                cal_margin = model.decision_function(x_cal)
                calibration = LogisticRegression(C=1.0, solver="lbfgs", max_iter=2000, random_state=seed)
                calibration.fit(np.asarray(cal_margin).reshape(-1, 1), y_cal)
                scores = _scores(model, calibration, prepared["tune"], np)
                selected, thresholds = choose_policy(prepared["tune"], scores, baseline_tune, protocol)
                trial = {"parameter": parameter, "selectedPolicy": selected, "thresholdTrials": thresholds}
                trials.append(trial); candidates.append((model, calibration, trial))
            model, calibration, selected_trial = max(candidates, key=lambda item: (
                item[2]["selectedPolicy"]["safetyPassed"], item[2]["selectedPolicy"]["metrics"]["top3GuaranteedRecall"],
                item[2]["selectedPolicy"]["metrics"]["newAccuracy"], -item[2]["selectedPolicy"]["metrics"]["meanFalseCandidates"],
                -item[2]["selectedPolicy"]["metrics"]["meanCandidates"], -item[2]["parameter"]))
            trained.append({"family": family, "seed": seed, "model": model, "calibration": calibration,
                            "selection": selected_trial, "trials": trials})
            notify(f"{family} seed={seed} train/calibration/tune 완료", flush=True)
    # 여기부터 최종 시험 1회. 이 아래 결과로 재선택하지 않는다.
    test = prepare(corpus["test"])
    baseline, baseline_rows = evaluate(test)
    final = []
    for selected in trained:
        scores = []; request_times = []
        for row in test:
            start = time.perf_counter()
            score = _scores(selected["model"], selected["calibration"], [row], np)[0]
            outcome(row, score, selected["selection"]["selectedPolicy"]["threshold"])
            request_times.append(row["featureMs"] + (time.perf_counter() - start) * 1000)
            scores.append(score)
        metrics, rows = evaluate(test, scores, selected["selection"]["selectedPolicy"]["threshold"])
        intervals = cluster_interval(baseline_rows, rows, protocol)
        p95 = sorted(request_times)[int(0.95 * (len(request_times) - 1))]
        calibration_values = [(int(c.person_id in set(row["query"]["expected"]["targetPersonIds"])), score)
                              for row, query_scores in zip(test, scores) if row["query"]["expected"]["kind"] in {"KNOWN", "NEW"}
                              for c, score in zip(row["comparisons"], query_scores)]
        errors = [{"queryId": row["query"]["queryId"], "caseType": row["query"]["caseType"], "expected": row["query"]["expected"],
                   "decision": outcome(row, score, selected["selection"]["selectedPolicy"]["threshold"])["decision"],
                   "shownCandidateIds": [p for p, _ in outcome(row, score, selected["selection"]["selectedPolicy"]["threshold"])["ranked"][:3]]}
                  for row, score, metric in zip(test, scores, rows)
                  if (metric["known"] and not metric["top3Guaranteed"]) or (metric["ambiguous"] and not metric["ambiguitySafe"])
                  or (metric["new"] and not metric["newCorrect"])]
        final.append({"family": selected["family"], "seed": selected["seed"], "selection": selected["selection"],
                      "tuneTrials": selected["trials"], "test": metrics, "pairedCluster95CI": intervals,
                      "p95LocalRequestMs": p95, "pairBrierOnKnownAndNew": sum((y - p)**2 for y, p in calibration_values) / len(calibration_values),
                      "gate": gate_result(metrics, baseline, intervals, p95, protocol), "errors": errors,
                      "byCase": {case: summarize([r for r in rows if r["caseType"] == case]) for case in sorted({r["caseType"] for r in rows})}})
    all_pass = all(run["gate"]["passed"] for run in final)
    family_pass = {family: all(run["gate"]["passed"] for run in final if run["family"] == family) for family in ("logistic", "gradient")}
    return {"protocol": protocol, "protocolSha256": digest(protocol), "numpyVersion": np.__version__, "sklearnVersion": sklearn.__version__,
            "manifests": manifests, "fit": {"pairs": len(y_fit), "positive": sum(y_fit), "distinctFeatures": len({tuple(x) for x in x_fit}),
            "calibrationPairs": cal_pairs}, "baseline": {"tune": baseline_tune, "test": baseline}, "runs": final,
            "familyPassedAllSeeds": family_pass, "allModelsPassed": all_pass,
            "experimentConclusion": "candidate_model_met_prespecified_synthetic_gate" if any(family_pass.values()) else "no_model_met_prespecified_synthetic_gate",
            "humanReviewUsed": False, "productionChanged": False,
            "limitations": ["automatically_labeled_synthetic_scenarios_not_human_reviewed", "no_real_customer_performance_claim",
                            "synthetic_marker_and_generator_bias", "calibration_distribution_has_40_person_roster_test_has_80",
                            "D022_confirmation_shared_so_confirmation_precision_is_not_a_learned_improvement",
                            "latency_is_local_single_run_not_server_load_test", "test_consumed_do_not_retune_on_it"]}


def main():
    parser = argparse.ArgumentParser(description="고정 규정 ML-1 전체 대장 재학습 비교")
    parser.add_argument("--protocol", type=Path, default=PROTOCOL)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    if args.output_dir.exists():
        parser.error("기존 실험을 덮어쓰지 않습니다. 새 출력 폴더를 지정하세요")
    protocol = json.loads(args.protocol.read_text(encoding="utf-8"))
    args.output_dir.mkdir(parents=True)
    # 결과를 보기 전에 규정과 생성/평가 코드의 해시를 먼저 보존한다.
    files = [Path(__file__), Path(__file__).with_name("controlled_data.py"), Path(__file__).with_name("learned_comparison.py")]
    snapshot = {"protocol": protocol, "protocolSha256": digest(protocol),
                "sourceSha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in files}}
    (args.output_dir / "frozen_protocol.json").write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    def freeze_data(manifests):
        (args.output_dir / "frozen_data_manifest.json").write_text(
            json.dumps(manifests, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    report = experiment(protocol, freeze_data=freeze_data)
    report["sourceSha256"] = snapshot["sourceSha256"]
    (args.output_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({"output": str(args.output_dir), "conclusion": report["experimentConclusion"], "familyPassedAllSeeds": report["familyPassedAllSeeds"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
