"""원 fit/calibration만 재현해 JSON 계수로 내보낸다. 새 검증 자료로 재학습하지 않는다."""
import argparse
import hashlib
import json
from pathlib import Path

from app.identity.logistic import FEATURES, LogisticModel, fingerprint
from ml.identity.controlled_data import digest, make_corpus
from ml.identity.controlled_experiment import _scores, prepare, evaluate
from ml.identity.overfit_audit import reproduce_models

ROOT = Path(__file__).resolve().parent


def export(output):
    import numpy as np
    import sklearn
    report = json.loads((ROOT/"results/controlled_v1/report.json").read_text(encoding="utf-8"))
    if report["sklearnVersion"] != sklearn.__version__:
        raise ValueError("sklearn 버전 불일치")
    for name, sha in report["sourceSha256"].items():
        assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest() == sha
    corpus = make_corpus(report["protocol"])
    assert all(digest(data) == report["manifests"][name]["sha256"] for name, data in corpus.items())
    original = {name: prepare(corpus[name]) for name in ("fit", "calibration")}
    chosen = next(m for m in reproduce_models(report, original, {"modelSeed": 42}) if m["family"] == "logistic")
    original.clear()
    scaler, classifier = chosen["model"].steps[0][1], chosen["model"].steps[1][1]
    p = {"format": "identity-logistic-json-v1", "version": "identity-hybrid-logistic-1.0", "features": list(FEATURES),
         "mean": scaler.mean_.tolist(), "scale": scaler.scale_.tolist(), "coefficients": classifier.coef_[0].tolist(),
         "intercept": float(classifier.intercept_[0]), "calibrationCoefficient": float(chosen["calibration"].coef_[0][0]),
         "calibrationIntercept": float(chosen["calibration"].intercept_[0]), "threshold": chosen["threshold"],
         "sourceReportSha256": digest(report), "seed": 42, "C": chosen["frozen"]["selection"]["parameter"],
         "training": "original_fit_only_sigmoid_original_calibration_only", "calibrationScope": "synthetic_full_roster_pairs"}
    bundle = {"payload": p, "sha256": fingerprint(p)}
    runtime = LogisticModel(bundle)
    prepared = prepare(corpus["test"])
    expected_scores = _scores(chosen["model"], chosen["calibration"], prepared, np)
    actual_scores = [[runtime.score(c) for c in row["comparisons"]] for row in prepared]
    max_error = max(abs(a-b) for aa, bb in zip(expected_scores, actual_scores) for a, b in zip(aa, bb))
    assert max_error < 1e-12
    assert evaluate(prepared, actual_scores, runtime.threshold)[0] == chosen["frozen"]["test"]
    with output.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(bundle, ensure_ascii=False, indent=2, allow_nan=False)+"\n")
    print(json.dumps({"output": str(output), "maxProbabilityErrorVsSklearn": max_error, "oldTestReproduced": True}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    export(parser.parse_args().output)
