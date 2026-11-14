"""ML-1 오프라인 비교. sklearn은 명시적 CLI 실행에만 로드하며 운영 API는 바꾸지 않는다."""

import argparse
import hashlib
import json
import math
import random
from pathlib import Path

from app.identity.features import compare_people
from app.identity.normalization import normalize_name
from app.identity.ranking import rank_comparisons
from ml.identity.generate_dataset import validate_dataset

FEATURES = (
    "name_exact", "name_similarity", "name_unknown", "name_different",
    "email_exact", "email_unknown", "email_different", "phone_exact", "phone_unknown", "phone_different",
    "company_exact", "company_unknown", "company_different", "contact_warning",
)
THRESHOLDS = (0.50, 0.60, 0.70, 0.80, 0.90, 0.95)
DEFAULT_DATA = Path(__file__).resolve().parent / "data"


def features(comparison):
    """ID·번호 문자열·정답·caseType·sourceRef·evidence는 특징에 넣지 않는다."""
    values = [int(comparison.name.status == "MATCH"), comparison.name_similarity or 0.0,
              int(comparison.name.status == "UNKNOWN"), int(comparison.name.status == "DIFFERENT")]
    for field in ("email", "phone", "company"):
        status = getattr(comparison, field).status
        values.extend(int(status == value) for value in ("MATCH", "UNKNOWN", "DIFFERENT"))
    values.append(int(bool(comparison.warnings)))
    return values


def split_training_people(persons, train_ids, seed=42):
    """train内だけでfit/tuneを分離。同名・登録別名のつながる全員を同じ側に置く。"""
    selected = [p for p in persons if p["personId"] in set(train_ids)]
    if len(selected) != len(set(train_ids)):
        raise ValueError("학습용 인물 ID의 참조가 잘못됐습니다")
    parent = {p["personId"]: p["personId"] for p in selected}

    def root(key):
        while parent[key] != key:
            parent[key] = parent[parent[key]]
            key = parent[key]
        return key

    by_name = {}
    for person in selected:
        for raw in [person["name"], *person.get("nameVariants", [])]:
            name = normalize_name(raw)
            if name is not None:
                if name in by_name:
                    parent[root(person["personId"])] = root(by_name[name])
                by_name[name] = person["personId"]
    groups = {}
    for person in selected:
        groups.setdefault(root(person["personId"]), []).append(person["personId"])
    groups = sorted(groups.values(), key=lambda group: min(group))
    random.Random(seed).shuffle(groups)
    target = max(1, round(len(selected) * 0.2))
    tune = set()
    for group in groups:
        if len(tune) >= target:
            break
        tune.update(group)
    fit = set(train_ids) - tune
    if not fit or not tune:
        raise ValueError("인물 그룹을 fit/tune 양쪽으로 나눌 수 없습니다")
    return fit, tune


def partition_pairs(pairs, fit_ids, tune_ids):
    fit, tune, dropped, ambiguous = [], [], 0, 0
    for pair in pairs:
        if pair["label"] is None:
            ambiguous += 1
            continue
        if pair["label"] not in (0, 1):
            raise ValueError("0/1/null 정답만 허용합니다")
        ids = {pair["personIdA"], pair["personIdB"]}
        if ids <= fit_ids:
            fit.append(pair)
        elif ids <= tune_ids:
            tune.append(pair)
        else:
            dropped += 1
    return fit, tune, {"excludedAmbiguous": ambiguous, "excludedCrossPartitionPairs": dropped}


def examples(pairs, roster):
    """조회한 대장 후보 A와 언급 B 비교. 정답 ID는 비교 대상 조회/채점에만 사용한다."""
    cache = {}
    matrix, labels, rule = [], [], []
    for pair in pairs:
        if pair["label"] is None:
            raise ValueError("애매한 쌍은 이진 학습·평가에 넣을 수 없습니다")
        # 원문 추적 ID나 채널은 모델 입력이 아니다.
        mention = {key: pair["mentionB"].get(key) for key in ("name", "company", "email", "phone")}
        key = tuple(mention.values())
        if key not in cache:
            comparisons = compare_people(mention, roster)
            cache[key] = ({c.person_id: c for c in comparisons},
                          {r.person_id for r in rank_comparisons(comparisons, fuzzy_threshold=0.88)})
        comparisons, candidates = cache[key]
        candidate_id = pair["personIdA"]
        if candidate_id not in comparisons:
            raise ValueError("분할 밖의 후보 인물이 입력에 포함됐습니다")
        matrix.append(features(comparisons[candidate_id]))
        labels.append(pair["label"])
        rule.append(float(candidate_id in candidates))
    return matrix, labels, rule


def metrics(labels, scores, threshold=0.5):
    if len(labels) != len(scores) or not labels:
        raise ValueError("정답과 점수의 비어 있지 않은 동일 길이 배열이 필요합니다")
    if any(y not in (0, 1) for y in labels) or any(not math.isfinite(p) or not 0 <= p <= 1 for p in scores):
        raise ValueError("지원하지 않는 정답/점수입니다")
    tp = fp = tn = fn = 0
    for y, p in zip(labels, scores):
        if p >= threshold:
            tp += y == 1
            fp += y == 0
        else:
            tn += y == 0
            fn += y == 1
    precision = tp / (tp + fp) if tp + fp else None
    recall = tp / (tp + fn) if tp + fn else None
    positive = [p for y, p in zip(labels, scores) if y == 1]
    negative = [p for y, p in zip(labels, scores) if y == 0]
    auc = (sum((a > b) + 0.5 * (a == b) for a in positive for b in negative) / (len(positive) * len(negative))
           if positive and negative else None)
    return {"count": len(labels), "tp": tp, "fp": fp, "tn": tn, "fn": fn,
            "precision": precision, "recall": recall, "f1": 2 * tp / (2 * tp + fp + fn) if 2 * tp + fp + fn else 0,
            "accuracy": (tp + tn) / len(labels), "rocAuc": auc,
            "brier": sum((p - y) ** 2 for y, p in zip(labels, scores)) / len(labels)}


def select_threshold(labels, scores):
    trials = [{"threshold": threshold, "metrics": metrics(labels, scores, threshold)} for threshold in THRESHOLDS]
    # 95% 정밀도는 이진 쌍 검사의 탐색 기준일 뿐 CONFIRMED 정책을 대체하지 않는다.
    selected = max(trials, key=lambda t: (
        t["metrics"]["precision"] is not None and t["metrics"]["precision"] >= 0.95,
        t["metrics"]["f1"], t["metrics"]["precision"] or 0, t["threshold"]))
    return selected, trials


def run(data_dir=DEFAULT_DATA, seed=42):
    # 기본 CI/운영 앱은 sklearn을 import하지 않는다. 명시적 실험에만 필요하다.
    try:
        import sklearn
        from sklearn.ensemble import GradientBoostingClassifier
        from sklearn.linear_model import LogisticRegression
        from sklearn.pipeline import make_pipeline
        from sklearn.preprocessing import StandardScaler
    except ImportError as error:
        raise RuntimeError("ML-1 선택 실험에는 scikit-learn이 설치된 환경이 필요합니다") from error
    paths = [Path(data_dir) / name for name in ("persons.json", "split.json", "pairs_train.json", "pairs_eval.json")]
    persons, split, train, evaluation = [json.loads(path.read_text(encoding="utf-8")) for path in paths]
    validate_dataset({"persons": persons, "split": split, "trainPairs": train, "evalPairs": evaluation})
    train_ids, eval_ids = set(split["trainPersonIds"]), set(split["evalPersonIds"])
    if train_ids & eval_ids:
        raise ValueError("학습·평가 인물이 겹칩니다")
    fit_ids, tune_ids = split_training_people(persons, train_ids, seed)
    fit_pairs, tune_pairs, exclusions = partition_pairs(train, fit_ids, tune_ids)
    x_fit, y_fit, _ = examples(fit_pairs, [p for p in persons if p["personId"] in fit_ids])
    x_tune, y_tune, tune_rule = examples(tune_pairs, [p for p in persons if p["personId"] in tune_ids])
    if set(y_fit) != {0, 1} or set(y_tune) != {0, 1}:
        raise ValueError("fit/tune 양쪽에 같은/다른 사람 정답이 모두 필요합니다")
    models = {
        "logistic_regression": make_pipeline(StandardScaler(), LogisticRegression(C=1.0, solver="lbfgs", max_iter=2000, random_state=seed)),
        "gradient_boosting": GradientBoostingClassifier(loss="log_loss", n_estimators=100, learning_rate=0.1, max_depth=2, subsample=1.0, random_state=seed),
    }
    trained = {}
    for name, model in models.items():
        model.fit(x_fit, y_fit)
        scores = [float(v) for v in model.predict_proba(x_tune)[:, 1]]
        selected, trials = select_threshold(y_tune, scores)
        trained[name] = {"threshold": selected["threshold"], "tuneMetrics": selected["metrics"], "tuneTrials": trials}
        if name == "logistic_regression":
            classifier = model.steps[-1][1]
            trained[name]["parameters"] = {"standardScaler": True, "C": 1.0, "solver": "lbfgs", "maxIter": 2000}
            trained[name]["standardizedFeatureCoefficients"] = dict(zip(FEATURES, [float(v) for v in classifier.coef_[0]]))
            trained[name]["intercept"] = float(classifier.intercept_[0])
        else:
            trained[name]["parameters"] = {"nEstimators": 100, "learningRate": 0.1, "maxDepth": 2, "subsample": 1.0, "loss": "log_loss"}
            trained[name]["featureImportance"] = dict(zip(FEATURES, [float(v) for v in model.feature_importances_]))
    # 모델/기준은 tune까지만 보고 고정. eval로 재학습·재선택하지 않는다.
    selected_model = max(trained, key=lambda name: (
        (trained[name]["tuneMetrics"]["precision"] or 0) >= 0.95,
        trained[name]["tuneMetrics"]["f1"], name == "logistic_regression"))
    eval_pairs = [p for p in evaluation if p["label"] is not None]
    x_eval, y_eval, eval_rule = examples(eval_pairs, [p for p in persons if p["personId"] in eval_ids])
    for name, model in models.items():
        scores = [float(v) for v in model.predict_proba(x_eval)[:, 1]]
        trained[name]["evalMetrics"] = metrics(y_eval, scores, trained[name]["threshold"])
        trained[name]["evalErrors"] = [{"pairId": p["pairId"], "caseType": p["caseType"], "label": y,
                                      "uncalibratedModelScore": score}
                                     for p, y, score in zip(eval_pairs, y_eval, scores)
                                     if (score >= trained[name]["threshold"]) != bool(y)]
        trained[name]["evalByCase"] = {case: metrics(
            [y for p, y in zip(eval_pairs, y_eval) if p["caseType"] == case],
            [s for p, s in zip(eval_pairs, scores) if p["caseType"] == case], trained[name]["threshold"])
            for case in sorted({p["caseType"] for p in eval_pairs})}
    return {"version": "ml1-pair-experiment-1", "seed": seed, "sklearnVersion": sklearn.__version__,
            "task": "binary_pair_match_not_top3_retrieval_or_api_decision", "features": FEATURES,
            "inputSha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in paths},
            "split": {"fitPersonIds": sorted(fit_ids), "tunePersonIds": sorted(tune_ids), "evalPersons": len(eval_ids),
                      "fitPairs": len(fit_pairs), "tunePairs": len(tune_pairs), "evalPairs": len(eval_pairs), **exclusions,
                      "fitPositive": sum(y_fit), "fitNegative": len(y_fit) - sum(y_fit),
                      "tunePositive": sum(y_tune), "tuneNegative": len(y_tune) - sum(y_tune),
                      "fitDistinctFeatureVectors": len({tuple(row) for row in x_fit}),
                      "excludedEvalAmbiguous": len(evaluation) - len(eval_pairs)},
            "baseline": {"name": "current_fuzzy_candidate_inclusion_as_binary_pair_prediction",
                         "tune": metrics(y_tune, tune_rule), "eval": metrics(y_eval, eval_rule)},
            "selectedOnTune": selected_model, "models": trained,
            "productionRecommendation": "keep_rules_pending_fresh_reviewed_validation",
            "limitations": ["synthetic_numbered_people_and_repeated_examples", "existing_eval_already_observed",
                            "uncalibrated_predict_proba_not_verified_identity_probability", "no_new_person_or_full_roster_ranking_evaluation",
                            "ambiguous_pairs_excluded_not_converted_to_zero", "no_holdout_v1_read_or_production_changes",
                            "binary_pair_precision_is_not_CONFIRMED_precision", "baseline_scores_are_hard_candidate_membership_not_probabilities", "fit_tune_cross_pairs_dropped",
                            "fixed_hyperparameters_thresholds_selected_on_tune_only"]}


def main():
    parser = argparse.ArgumentParser(description="A ML-1 규칙/LR/그래디언트 부스팅 오프라인 비교")
    parser.add_argument("--data-dir", type=Path, default=DEFAULT_DATA)
    parser.add_argument("--output", type=Path, required=True, help="기존 보고서 보호를 위해 새 출력 경로 지정")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    if args.output.exists():
        parser.error("기존 보고서를 덮어쓰지 않습니다. 새 --output 경로를 지정하세요")
    report = run(args.data_dir, args.seed)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n")
    print(json.dumps({"report": str(args.output), "selectedOnTune": report["selectedOnTune"],
                      "recommendation": report["productionRecommendation"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
