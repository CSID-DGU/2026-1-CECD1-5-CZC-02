"""ML-2 비교: 규칙 vs 임베딩+LR vs LLM 직접 판정 (같은 평가셋, 같은 지표).

평가셋 두 개:
- c55: C의 evaluation.json 55개 (규칙 개발에 쓰여 규칙에 유리함 — 독립 평가 아님)
- holdout: B가 모델 실행 전에 정답을 정한 새 문장 36개 (holdout.json, 검수 전)

실행 (ai-module에서, 로컬 Ollama가 켜져 있을 때만):
    OLLAMA_SEED=42 python -m ml.llm_baseline.compare --llm qwen2.5:7b
    OLLAMA_SEED=42 OLLAMA_THINK=false python -m ml.llm_baseline.compare --llm gemma4:e4b --output ml/llm_baseline/results-gemma4.json
    # 임베딩+LR을 C와 같은 조건으로 다시 학습해 holdout까지 재려면 (캐시된 모델만 사용)
    python -m ml.llm_baseline.compare --llm qwen2.5:7b --embedding-model sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2

CI는 이 파일을 실행하지 않는다. 함수는 가짜 분류기로 테스트한다.
"""
import argparse
import json
import platform
import time
from pathlib import Path

from app.tasks.classifier import classify_task
from ml.llm_baseline.classifier import LlmTaskTypeClassifier, build_system_prompt, prompt_sha256
from ml.llm_baseline.data import ROOT, TASK_TYPE_DIR, doc_example_sentences, load_c_evaluation, load_holdout, overlaps
from ml.task_type.experiment import LABELS, classification_metrics


def confusion_matrix(gold, predicted):
    """{정답: {예측: 개수}} — 0인 칸은 뺀다."""
    matrix = {}
    for g, p in zip(gold, predicted):
        row = matrix.setdefault(g, {})
        row[p] = row.get(p, 0) + 1
    return {label: matrix[label] for label in LABELS if label in matrix}


def summarize(gold, predicted, seconds=None):
    metrics = classification_metrics(gold, predicted)
    return {
        'macroF1': round(metrics['macroF1'], 4),
        'accuracy': round(sum(a == b for a, b in zip(gold, predicted)) / len(gold), 4),
        'noneRecall': round(metrics['noneRecall'], 4),
        'perClassF1': {label: round(v['f1'], 4) for label, v in metrics['perClass'].items()},
        'confusion': confusion_matrix(gold, predicted),
        'errorCount': len(metrics['errors']),
        **({'secondsPerItem': round(seconds, 4)} if seconds is not None else {}),
    }


def hybrid(rule_predictions, llm_predictions):
    """규칙이 부류를 냈으면 규칙, 규칙이 NONE일 때만 LLM 답을 쓴다 (LLM은 보조)."""
    return [rule if rule != 'NONE' else llm for rule, llm in zip(rule_predictions, llm_predictions)]


def run_rule(texts):
    started = time.perf_counter()
    predictions = [classify_task(text)[0] for text in texts]
    return predictions, (time.perf_counter() - started) / len(texts)


def run_llm(classifier, texts, progress=None):
    judgements = []
    for index, text in enumerate(texts):
        judgements.append(classifier.classify(text))
        if progress:
            progress(index + 1, len(texts))
    return judgements


def c_report_embedding_predictions(evaluation):
    """C report.json에 남은 임베딩+LR 오답 목록으로 55개 예측을 복원 (모델 없이 C 수치 재확인)."""
    report = json.loads((TASK_TYPE_DIR / 'report.json').read_text(encoding='utf-8'))
    section = report.get('embeddingLogisticRegression')
    if not section:
        return None
    predictions = [row['label'] for row in evaluation]
    for error in section['errors']:
        if evaluation[error['index']]['label'] != error['gold']:
            raise ValueError('C report.json의 오답 목록이 evaluation.json과 맞지 않습니다.')
        predictions[error['index']] = error['predicted']
    return {'model': section['model'], 'predictions': predictions, 'reportedMacroF1': section['macroF1']}


def run_embedding(model, texts_by_set):
    """C experiment.py와 같은 조건(정규화 임베딩, seed=42, max_iter=2000, class_weight=None)으로 C 학습 데이터에 다시 학습."""
    from importlib.metadata import version
    from sentence_transformers import SentenceTransformer
    from sklearn.linear_model import LogisticRegression

    train = [row for row in json.loads((TASK_TYPE_DIR / 'train.json').read_text(encoding='utf-8'))
             if row.get('trainingEligible', True)]
    encoder = SentenceTransformer(model, local_files_only=True)
    classifier = LogisticRegression(max_iter=2000, random_state=42)
    classifier.fit(encoder.encode([row['text'] for row in train], normalize_embeddings=True), [row['label'] for row in train])
    results = {}
    for name, texts in texts_by_set.items():
        started = time.perf_counter()
        predictions = classifier.predict(encoder.encode(texts, normalize_embeddings=True)).tolist()
        results[name] = (predictions, (time.perf_counter() - started) / len(texts))
    meta = {'model': model, 'train': len(train), 'seed': 42, 'maxIter': 2000, 'classWeight': None,
            'sentenceTransformers': version('sentence-transformers'), 'scikitLearn': version('scikit-learn')}
    return results, meta


def evaluate_set(rows, llm_judgements, rule_result, embedding_predictions=None, embedding_seconds=None):
    gold = [row['label'] for row in rows]
    rule_predictions, rule_seconds = rule_result
    llm_predictions = [j.label for j in llm_judgements]
    llm_seconds = sum(j.latency_ms for j in llm_judgements) / 1000 / len(rows)
    result = {
        'size': len(rows),
        'rule': summarize(gold, rule_predictions, rule_seconds),
        'llm': {**summarize(gold, llm_predictions, llm_seconds),
                'formatFailures': sum(j.failure is not None for j in llm_judgements)},
        'hybridRuleThenLlm': summarize(gold, hybrid(rule_predictions, llm_predictions)),
    }
    if embedding_predictions is not None:
        result['embeddingLR'] = summarize(gold, embedding_predictions, embedding_seconds)
    result['items'] = [
        {'id': row['id'], 'text': row['text'], 'gold': row['label'], 'rule': rule,
         **({'embeddingLR': embedding_predictions[i]} if embedding_predictions is not None else {}),
         'llm': j.label, 'llmReason': j.reason, 'llmFailure': j.failure, 'llmLatencyMs': j.latency_ms}
        for i, (row, rule, j) in enumerate(zip(rows, rule_predictions, llm_judgements))
    ]
    return result


def subset(result_items, keep_ids, method):
    items = [item for item in result_items if item['id'] in keep_ids]
    return summarize([item['gold'] for item in items], [item[method] for item in items])


def main():
    from app.llm.ollama_provider import OllamaProvider

    parser = argparse.ArgumentParser()
    parser.add_argument('--llm', default='qwen2.5:7b', help='로컬 Ollama 모델 이름')
    parser.add_argument('--embedding-model', help='캐시된 SentenceTransformer 모델 (없으면 holdout 임베딩 비교 생략)')
    parser.add_argument('--output', type=Path, default=ROOT / 'results-qwen2.5-7b.json')
    args = parser.parse_args()

    provider = OllamaProvider(model=args.llm)
    if provider.seed is None:
        raise SystemExit('재현을 위해 OLLAMA_SEED를 지정해 주세요 (예: OLLAMA_SEED=42).')
    prompt = build_system_prompt()
    classifier = LlmTaskTypeClassifier(provider, prompt)
    sets = {'c55': load_c_evaluation(), 'holdout': load_holdout()['items']}
    texts_by_set = {name: [row['text'] for row in rows] for name, rows in sets.items()}

    embedding = {}
    embedding_meta = None
    if args.embedding_model:
        embedding, embedding_meta = run_embedding(args.embedding_model, texts_by_set)
    c_embedding = c_report_embedding_predictions(sets['c55'])

    report = {
        'llm': {'provider': 'ollama', 'model': args.llm, 'seed': provider.seed, 'think': provider.think,
                'temperature': 0.0, 'promptSha256': prompt_sha256(prompt), 'promptSource': 'docs/project/task-types.md'},
        'embeddingRetrained': embedding_meta,
        'embeddingFromCReport': {k: v for k, v in (c_embedding or {}).items() if k != 'predictions'} or None,
        'python': platform.python_version(),
        'sets': {},
    }
    for name, rows in sets.items():
        print(f'[{name}] {len(rows)}개 LLM 판정 중…', flush=True)
        judgements = run_llm(classifier, texts_by_set[name],
                             progress=lambda done, total: print(f'  {done}/{total}', flush=True) if done % 10 == 0 else None)
        embedding_predictions, embedding_seconds = embedding.get(name, (None, None))
        if embedding_predictions is None and name == 'c55' and c_embedding:
            embedding_predictions = c_embedding['predictions']
        report['sets'][name] = evaluate_set(rows, judgements, run_rule(texts_by_set[name]),
                                            embedding_predictions, embedding_seconds)

    # 문서 경계표 예시(지시문에 들어감)와 거의 같은 C 평가 문장은 LLM에 정답 힌트가 된다 → 뺀 점수도 따로
    leaked = {text for text, _, _ in overlaps(texts_by_set['c55'], doc_example_sentences(), threshold=0.5)}
    keep = {row['id'] for row in sets['c55'] if row['text'] not in leaked}
    c55 = report['sets']['c55']
    c55['promptExampleOverlap'] = sorted(leaked)
    c55['withoutPromptOverlap'] = {method: subset(c55['items'], keep, method)
                                   for method in ('rule', 'llm', *(['embeddingLR'] if 'embeddingLR' in c55 else []))}

    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    for name, result in report['sets'].items():
        line = {method: result[method]['macroF1'] for method in ('rule', 'embeddingLR', 'llm', 'hybridRuleThenLlm') if method in result}
        print(name, json.dumps(line), 'LLM 형식 실패', result['llm']['formatFailures'])


if __name__ == '__main__':
    main()
