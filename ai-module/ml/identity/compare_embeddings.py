"""로컬 BGE-M3 선택 실험. 기존 train에서만 선택, 기존 eval에서 비교. 새 holdout 미사용."""

import argparse
import hashlib
import json
import time
from importlib.metadata import version
from pathlib import Path

from app.identity.embedding import LocalEncoder, cosine, identity_text, person_texts, rank_with_embeddings
from ml.identity.evaluate_candidates import DEFAULT_DATA, measure, prepare_queries, select_threshold
from ml.identity.generate_dataset import validate_dataset


def embedding_scores(prepared, persons, encoder):
    documents = {p["personId"]: person_texts(p) for p in persons}
    query_texts = {identity_text(cs[0].name.observed, cs[0].company.observed)
                   for _, cs in prepared if cs}
    texts = set(t for values in documents.values() for t in values) | (query_texts - {None})
    vectors = encoder.encode(texts)
    scores = {}
    for query in query_texts:
        scores[query] = {} if query is None else {
            pid: max(cosine(vectors[query], vectors[text]) for text in values)
            for pid, values in documents.items() if values
        }
    return scores


def run_metrics(prepared, scores, rule_threshold, config):
    def ranker(comparisons):
        key = identity_text(comparisons[0].name.observed, comparisons[0].company.observed) if comparisons else None
        return rank_with_embeddings(comparisons, scores.get(key, {}), rule_threshold=rule_threshold, **config)
    return measure(prepared, rule_threshold, ranker=ranker)


def choose_config(trials):
    # 학습 top-3 → top-1 → 거짓 후보 적음 → 평균 후보 수 적음 → 기본 규칙 선호.
    return max(trials, key=lambda t: (
        t["metrics"]["retrieval"]["top3Guaranteed"], t["metrics"]["retrieval"]["top1Guaranteed"],
        -t["metrics"]["pairCandidateInclusion"]["fp"], -t["metrics"]["retrieval"]["candidateCount"],
        int(t["config"]["embedding_threshold"] is None), -t["config"]["weight"],
    ))



def recommendation(report):
    if report["selectedConfig"] == {"weight": 0.0, "embedding_threshold": None}:
        return "keep_rules"
    def quality(metrics):
        return (metrics["retrieval"]["top3Guaranteed"], metrics["retrieval"]["top1Guaranteed"],
                -metrics["pairCandidateInclusion"]["fp"], -metrics["retrieval"]["candidateCount"])
    selected, baseline = quality(report["evalSelected"]), quality(report["evalRules"])
    if selected == baseline:
        return "keep_rules_no_eval_gain"
    return "review_on_fresh_holdout_before_adoption" if selected > baseline else "keep_rules_eval_regression"


def model_metadata(model_path):
    path = Path(model_path)
    files = {}
    for name in ("config.json", "modules.json", "tokenizer_config.json", "pytorch_model.bin", "model.safetensors"):
        target = path / name
        if target.is_file():
            with target.open("rb") as stream:
                files[name] = hashlib.file_digest(stream, "sha256").hexdigest()
    return {"fileSha256": files, "packages": {name: version(name) for name in ("sentence-transformers", "torch", "transformers")}}

def compare(data_dir, model_path, encoder_factory=LocalEncoder):
    start = time.perf_counter()
    paths = {name: data_dir / name for name in ("persons.json", "split.json", "pairs_train.json", "pairs_eval.json")}
    data = {name: json.loads(path.read_text()) for name, path in paths.items()}
    dataset = {"persons": data["persons.json"], "split": data["split.json"],
               "trainPairs": data["pairs_train.json"], "evalPairs": data["pairs_eval.json"]}
    validate_dataset(dataset)
    split = dataset["split"]
    train_persons = [p for p in dataset["persons"] if p["personId"] in split["trainPersonIds"]]
    eval_persons = [p for p in dataset["persons"] if p["personId"] in split["evalPersonIds"]]
    train, _ = prepare_queries(dataset["trainPairs"], train_persons)
    rule_threshold, _ = select_threshold(train)
    baseline_config = {"weight": 0.0, "embedding_threshold": None}
    baseline = measure(train, rule_threshold)
    report = {"version": "bge-comparison-0.1", "status": "rules_only", "ruleThreshold": rule_threshold,
              "inputSha256": {name: hashlib.sha256(path.read_bytes()).hexdigest() for name, path in paths.items()},
              "selectionData": "train_only", "freshHoldoutUsed": False, "modelPath": str(model_path),
              "trainRules": baseline, "selectedConfig": baseline_config}
    # 로컬 모델/선택 라이브러리 부재 시 기존 규칙 보고서를 제공한다. 실패를 성공으로 기록하지 않는다.
    try:
        encoder = encoder_factory(model_path)
    except (FileNotFoundError, ImportError, OSError) as exc:
        report.update(fallbackReason=f"{type(exc).__name__}: {exc}", recommendation="keep_rules_model_unavailable")
        evaluation, _ = prepare_queries(dataset["evalPairs"], eval_persons)
        report["evalRules"] = measure(evaluation, rule_threshold)
        return report
    train_scores = embedding_scores(train, train_persons, encoder)
    trials = []
    for threshold in (None, 0.80, 0.90, 0.95):
        for weight in (0.0, 0.25, 1.0):
            config = {"weight": weight, "embedding_threshold": threshold}
            trials.append({"config": config, "metrics": run_metrics(train, train_scores, rule_threshold, config)})
    chosen = choose_config(trials)
    # 기존 eval 계산은 설정 선택 후에 한다. 새 holdout 경로는 참조하지 않는다.
    evaluation, _ = prepare_queries(dataset["evalPairs"], eval_persons)
    eval_scores = embedding_scores(evaluation, eval_persons, encoder)
    embedding_only_choice = choose_config([t for t in trials if t["config"]["weight"] > 0 or t["config"]["embedding_threshold"] is not None])
    report.update(status="completed", fallbackReason=None, trainTrials=trials,
                  selectedConfig=chosen["config"], bestEmbeddingConfig=embedding_only_choice["config"],
                  evalRules=measure(evaluation, rule_threshold),
                  evalSelected=run_metrics(evaluation, eval_scores, rule_threshold, chosen["config"]),
                  evalBestEmbedding=run_metrics(evaluation, eval_scores, rule_threshold, embedding_only_choice["config"]),
                  recommendation="keep_rules" if chosen["config"] == baseline_config else "review_on_fresh_holdout_before_adoption",
                  elapsedSeconds=round(time.perf_counter() - start, 3),
                  encoder={"device": "cpu", "maxSequenceLength": 64, "batchSize": 8, "denseOnly": True},
                  limitations=["previously_seen_eval", "synthetic_numbered_names", "known_people_only",
                               "repeated_queries", "no_probability_or_auto_confirmation", "train_selection_not_cross_validation"])
    report["recommendation"] = recommendation(report)
    report["modelMetadata"] = model_metadata(model_path)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, default=Path.home() / "Models/bge-m3")
    parser.add_argument("--data-dir", type=Path, default=DEFAULT_DATA)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent / "results/embedding_comparison.json")
    args = parser.parse_args()
    report = compare(args.data_dir, args.model)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({key: report.get(key) for key in ("status", "fallbackReason", "selectedConfig", "bestEmbeddingConfig", "recommendation", "elapsedSeconds")}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
