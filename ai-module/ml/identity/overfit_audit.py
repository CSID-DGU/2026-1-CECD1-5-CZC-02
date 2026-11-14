"""고정 모델의 과적합/분포 변화 진단. 새 시험으로 모델·임계값을 다시 고르지 않는다."""
import argparse
import hashlib
import json
import time
from pathlib import Path

from ml.identity.controlled_data import digest, make_corpus
from ml.identity.controlled_experiment import _scores, evaluate, prepare, training_rows, summarize
from ml.identity.stress_data import make_stress

ROOT = Path(__file__).resolve().parent
AUDIT_PROTOCOL = ROOT / "OVERFIT_AUDIT_PROTOCOL.json"


def diagnose(fit, tune, original_test, current, baseline, policy):
    fit_tune = fit["top3GuaranteedRecall"] - tune["top3GuaranteedRecall"]
    fit_test = fit["top3GuaranteedRecall"] - original_test["top3GuaranteedRecall"]
    shifted = original_test["top3GuaranteedRecall"] - current["top3GuaranteedRecall"]
    new_loss = original_test["newAccuracy"] - current["newAccuracy"]
    extra = current["meanFalseCandidates"] - original_test["meanFalseCandidates"]
    ambiguity_loss = original_test["ambiguityCoverage"] - current["ambiguityCoverage"]
    flags = []
    if ambiguity_loss >= policy["sharpDrop"]:
        flags.append("SHARP_AMBIGUITY_COVERAGE_DROP")
    elif ambiguity_loss >= policy["warningDrop"]:
        flags.append("AMBIGUITY_COVERAGE_WARNING")
    if max(fit_tune, fit_test) >= policy["sharpDrop"]:
        flags.append("SHARP_SAME_GENERATOR_GENERALIZATION_GAP")
    elif max(fit_tune, fit_test) >= policy["warningDrop"]:
        flags.append("SAME_GENERATOR_GENERALIZATION_GAP_WARNING")
    if shifted >= policy["sharpDrop"]:
        flags.append("SHARP_SHIFT_SENSITIVITY_NOT_PROOF_OF_OVERFIT")
    elif shifted >= policy["warningDrop"]:
        flags.append("SHIFT_WARNING")
    if current["top3GuaranteedRecall"] < policy["minimumKnownTop3"]:
        flags.append("BELOW_TOP3_TARGET")
    if new_loss > policy["maximumNewFalseCandidateIncrease"]:
        flags.append("NEW_PERSON_FALSE_CANDIDATE_SURGE")
    if extra > policy["maximumMeanFalseCandidateIncrease"]:
        flags.append("FALSE_CANDIDATE_LOAD_SURGE")
    if flags:
        response = ("baseline_fallback_is_better_here" if (baseline["top3GuaranteedRecall"] > current["top3GuaranteedRecall"] or
                        baseline["ambiguityCoverage"] > current["ambiguityCoverage"])
                    else "baseline_is_not_a_recall_fix_keep_contact_safety_and_request_more_clues_or_manual_search")
    else:
        response = "no_sharp_drop_detected_in_this_profile_not_a_universal_guarantee"
    return {"fitMinusTune": fit_tune, "fitMinusOriginalTest": fit_test, "originalTestMinusProfile": shifted,
            "ambiguityCoverageDrop": ambiguity_loss, "newAccuracyDrop": new_loss, "falseCandidateIncrease": extra, "flags": flags, "response": response}


def reproduce_models(report, prepared, protocol):
    import numpy as np
    from sklearn.ensemble import GradientBoostingClassifier
    from sklearn.linear_model import LogisticRegression
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import StandardScaler
    seed = protocol["modelSeed"]
    training = report["protocol"]
    x, y, weights = training_rows(prepared["fit"], training, sampled=True)
    x_cal, y_cal, _ = training_rows(prepared["calibration"], training)
    models = []
    for family in ("logistic", "gradient"):
        frozen = next(run for run in report["runs"] if run["family"] == family and run["seed"] == seed)
        parameter = frozen["selection"]["parameter"]
        if family == "logistic":
            model = make_pipeline(StandardScaler(), LogisticRegression(C=parameter, solver="lbfgs", max_iter=2000, random_state=seed))
            model.fit(x, y, standardscaler__sample_weight=weights, logisticregression__sample_weight=weights)
        else:
            model = GradientBoostingClassifier(loss="log_loss", n_estimators=training["gradientEstimators"],
                    learning_rate=training["gradientLearningRate"], max_depth=parameter, random_state=seed)
            model.fit(x, y, sample_weight=weights)
        calibration = LogisticRegression(C=1.0, solver="lbfgs", max_iter=2000, random_state=seed)
        calibration.fit(np.asarray(model.decision_function(x_cal)).reshape(-1, 1), y_cal)
        models.append({"family": family, "model": model, "calibration": calibration,
                       "threshold": frozen["selection"]["selectedPolicy"]["threshold"], "frozen": frozen})
    return models


def audit(report, policy, output):
    import numpy as np
    import sklearn
    if sklearn.__version__ != report["sklearnVersion"]:
        raise ValueError("원 실험과 같은 sklearn 버전으로 재현해야 합니다")
    corpus = make_corpus(report["protocol"])
    if any(digest(data) != report["manifests"][name]["sha256"] for name, data in corpus.items()):
        raise ValueError("원 실험 자료 해시가 일치하지 않습니다")
    datasets = {
        "independent_clean": make_stress(policy["independentGeneratorSeed"]),
        "independent_heavy_noise": make_stress(policy["independentGeneratorSeed"], heavy=True),
        "independent_clean_roster_240": make_stress(policy["independentGeneratorSeed"], roster_size=240),
        "independent_clean_roster_800": make_stress(policy["independentGeneratorSeed"], roster_size=800),
    }
    old_ids = {p["personId"] for data in corpus.values() for p in data["persons"]}
    for data in datasets.values():
        if old_ids & {p["personId"] for p in data["persons"]}:
            raise ValueError("추가 시험 인물이 학습/원 시험에 겹칩니다")
    frozen = {name: {"sha256": digest(data), **data["manifest"]} for name, data in datasets.items()}
    (output / "frozen_stress_manifest.json").write_text(json.dumps(frozen, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    prepared = {}
    for name in ("fit", "calibration"):
        prepared[name] = prepare(corpus[name]); print(f"원 {name} 재현 완료", flush=True)
    models = reproduce_models(report, prepared, policy)
    runs = {m["family"]: {"threshold": m["threshold"], "sameGenerator": {}, "stress": {}} for m in models}
    for partition in ("fit", "tune", "test"):
        current = prepared.get(partition)
        if current is None:
            current = prepare(corpus[partition])
        for selected in models:
            scores = _scores(selected["model"], selected["calibration"], current, np)
            metrics, _ = evaluate(current, scores, selected["threshold"])
            runs[selected["family"]]["sameGenerator"][partition] = metrics
            if partition == "test" and metrics != selected["frozen"]["test"]:
                raise ValueError("고정 모델/기준 재현 결과가 원 시험과 다릅니다")
        print(f"동일 생성기 {partition} 비교 완료", flush=True)
    # 큰 비교 객체를 누적하지 않는다. 이후 스트레스 자료는 한 번에 하나씩만 특징화한다.
    prepared.clear(); current = None
    for name, data in datasets.items():
        start = time.perf_counter()
        current = prepare(data)
        baseline, _ = evaluate(current)
        for selected in models:
            scores = _scores(selected["model"], selected["calibration"], current, np)
            metrics, rows = evaluate(current, scores, selected["threshold"])
            same = runs[selected["family"]]["sameGenerator"]
            runs[selected["family"]]["stress"][name] = {"metrics": metrics, "baseline": baseline,
                "diagnosis": diagnose(same["fit"], same["tune"], same["test"], metrics, baseline, policy),
                "byCase": {case: summarize(
                    [row for row in rows if row["caseType"] == case]) for case in sorted({row["caseType"] for row in rows})}}
        print(f"{name}: {len(data['persons'])}명·{len(data['queries'])}질문 진단 완료 ({time.perf_counter()-start:.1f}s)", flush=True)
        current = None
    return {"policy": policy, "sourceReportSha256": digest(report), "sklearnVersion": sklearn.__version__,
            "stressManifests": frozen, "runs": runs, "productionChanged": False, "modelsRetuned": False,
            "interpretation": "separate_same_generator_generalization_gap_from_shift_or_information_loss",
            "limitations": ["automatic_synthetic_gold", "stress_results_are_now_observed_do_not_tune_and_retest_on_them",
                            "heavy_noise_can_destroy_identifying_information_not_necessarily_overfit",
                            "shared_D022_contact_confirmation_is_not_a_model_accuracy_gain"]}


def main():
    parser = argparse.ArgumentParser(description="고정 ML-1 과적합·분포 변화·대장 확장 진단")
    parser.add_argument("--report", type=Path, default=ROOT / "results/controlled_v1/report.json")
    parser.add_argument("--policy", type=Path, default=AUDIT_PROTOCOL)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    if args.output_dir.exists():
        parser.error("기존 진단 폴더는 덮어쓰지 않습니다")
    source = json.loads(args.report.read_text(encoding="utf-8"))
    policy = json.loads(args.policy.read_text(encoding="utf-8"))
    for name, sha in source["sourceSha256"].items():
        if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != sha:
            parser.error("원 실험 코드가 변경돼 재현을 중단합니다")
    args.output_dir.mkdir(parents=True)
    snapshot = {"policy": policy, "reportSha256": hashlib.sha256(args.report.read_bytes()).hexdigest(),
                "auditCodeSha256": {name: hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in ("overfit_audit.py", "stress_data.py")}}
    (args.output_dir / "frozen_audit.json").write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    result = audit(source, policy, args.output_dir)
    (args.output_dir / "report.json").write_text(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({"output": str(args.output_dir), "modelsRetuned": False, "productionChanged": False}))


if __name__ == "__main__":
    main()
