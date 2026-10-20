"""ML-2 비교. 기본은 규칙만; 학습/모델 다운로드는 명시적으로 선택한다."""
import argparse
import json
from collections import Counter
from pathlib import Path

from app.tasks.classifier import RULES, classify_task
from app.tasks.engine import extract_tasks

ROOT = Path(__file__).resolve().parent
LABELS = [*RULES, 'NONE']


def load(name):
    return json.loads((ROOT / name).read_text(encoding='utf-8'))


def classification_metrics(gold, predicted):
    if len(gold) != len(predicted):
        raise ValueError('정답과 예측 개수가 다릅니다.')
    per_class = {}
    for label in LABELS:
        tp = sum(a == label and b == label for a, b in zip(gold, predicted))
        fp = sum(a != label and b == label for a, b in zip(gold, predicted))
        fn = sum(a == label and b != label for a, b in zip(gold, predicted))
        precision = tp / (tp + fp) if tp + fp else 0.0
        recall = tp / (tp + fn) if tp + fn else 0.0
        per_class[label] = {'precision': precision, 'recall': recall,
                            'f1': 2 * tp / (2 * tp + fp + fn) if 2 * tp + fp + fn else 0.0,
                            'support': tp + fn}
    return {'macroF1': sum(v['f1'] for v in per_class.values()) / len(LABELS),
            'noneRecall': per_class['NONE']['recall'], 'perClass': per_class,
            'errors': [{'gold': a, 'predicted': b, 'index': i}
                       for i, (a, b) in enumerate(zip(gold, predicted)) if a != b]}


def extraction_metrics(cases):
    tp = fp = fn = date_correct = type_correct = kind_correct = schema_valid = 0
    explicit_expected = explicit_matched = links_correct = 0
    for case in cases:
        tasks = extract_tasks(case['note'])
        remaining = list(case['expected'])
        explicit_expected += sum(item['commitmentKind'] == 'EXPLICIT' for item in remaining)
        schema_valid += len(tasks)
        for actual in tasks:
            match = next((i for i, expected in enumerate(remaining)
                          if (expected['content'], expected['evidence']) == (actual['content'], actual['evidence'])), None)
            if match is None:
                fp += 1
                continue
            expected = remaining.pop(match)
            tp += 1
            explicit_matched += expected['commitmentKind'] == 'EXPLICIT'
            date_correct += actual['dueDate'] == expected['dueDate']
            type_correct += actual['taskType'] == expected['taskType']
            kind_correct += actual['commitmentKind'] == expected['commitmentKind']
            links_correct += actual['counterpartPersonId'] == expected.get('counterpartPersonId')
        fn += len(remaining)
    return {'cases': len(cases), 'truePositives': tp, 'falsePositives': fp, 'falseNegatives': fn,
            'precision': tp / (tp + fp) if tp + fp else 0.0,
            'recall': tp / (tp + fn) if tp + fn else 0.0,
            'dateAccuracyOnMatched': date_correct / tp if tp else 0.0,
            'typeAccuracyOnMatched': type_correct / tp if tp else 0.0,
            'kindAccuracyOnMatched': kind_correct / tp if tp else 0.0,
            'counterpartAccuracyOnMatched': links_correct / tp if tp else 0.0,
            'schemaValidCount': schema_valid,
            'explicitRecall': explicit_matched / explicit_expected if explicit_expected else 0.0}


def validate_splits(train, evaluation):
    if {r['text'] for r in train} & {r['text'] for r in evaluation}:
        raise ValueError('학습/평가 문장 중복')
    if {r['source'] for r in train} & {r['source'] for r in evaluation}:
        raise ValueError('학습/평가 출처 중복')
    if {r['group'] for r in train} & {r['group'] for r in evaluation}:
        raise ValueError('학습/평가 그룹 중복')
    for rows in (train, evaluation):
        if any('trainingEligible' in row and not isinstance(row['trainingEligible'], bool) for row in rows):
            raise ValueError('trainingEligible은 boolean이어야 합니다.')
        if len({row['id'] for row in rows}) != len(rows) or len({row['text'] for row in rows}) != len(rows):
            raise ValueError('데이터 내부 ID/문장 중복')
        if {r['label'] for r in rows} != set(LABELS):
            raise ValueError('11부류가 모두 필요합니다.')


def run(model=None, allow_download=False, train_file='train.json', compare_balanced=False):
    train, evaluation = load(train_file), load('evaluation.json')
    validate_splits(train, evaluation)
    candidates = train
    train = [row for row in candidates if row.get('trainingEligible', True)]
    if {row['label'] for row in train} != set(LABELS):
        raise ValueError('학습 가능한 데이터에 11부류가 모두 필요합니다.')
    review = load('training-review.json') if (ROOT / 'training-review.json').exists() else {}
    texts = [r['text'] for r in evaluation]
    gold = [r['label'] for r in evaluation]
    report = {'data': {'train': len(train), 'trainingCandidates': len(candidates), 'excludedFromTraining': len(candidates) - len(train), 'trainingClassCounts': dict(Counter(row['label'] for row in train)), 'trainFile': train_file, 'trainSources': sorted({r['source'] for r in train}), 'evaluation': len(evaluation),
                       'reviewStatus': 'TRAINING_PENDING', 'evaluationLabelsReviewed': review.get('evaluationLabelsReviewed', False), 'independentReviewCompleted': False},
              'extraction': extraction_metrics(load('extraction.json')),
              'rule': classification_metrics(gold, [classify_task(t)[0] for t in texts]),
              'productionClassifier': 'RULE', 'promotionAllowed': False,
              'limitations': ['학습 후보의 C 수정/재생성은 독립 교차 검수 전이며, 평가 라벨 검수와 구분함',
                              '이 결과만으로 실제 발화 성능 또는 90% 재현율 달성을 주장하지 않음',
                              '학습 모델은 운영에 자동 적용하지 않음',
                              '자동 생성 후보에는 부류 경계와 라벨 오류가 포함될 수 있어 교차 검수가 필수',
                              '평가 55개는 개발 피드백에 사용되어 독립 최종 평가셋이 아니며 별도 최종 평가가 필요']}
    if model:
        import numpy as np
        import platform
        from importlib.metadata import version
        from sklearn.linear_model import LogisticRegression
        from sentence_transformers import SentenceTransformer
        report['environment'] = {'python': platform.python_version(), 'sentenceTransformers': version('sentence-transformers'), 'scikitLearn': version('scikit-learn'), 'numpy': version('numpy')}
        encoder = SentenceTransformer(model, local_files_only=not allow_download)
        train_vectors = encoder.encode([r['text'] for r in train], normalize_embeddings=True, show_progress_bar=True)
        test_vectors = encoder.encode(texts, normalize_embeddings=True, show_progress_bar=True)
        variants = [('embeddingLogisticRegression', None, 'local_model.npz')]
        if compare_balanced:
            variants.append(('balancedEmbeddingLogisticRegression', 'balanced', 'balanced_model.npz'))
        for key, class_weight, filename in variants:
            classifier = LogisticRegression(max_iter=2000, random_state=42, class_weight=class_weight)
            classifier.fit(train_vectors, [r['label'] for r in train])
            predictions = classifier.predict(test_vectors).tolist()
            training_predictions = classifier.predict(train_vectors).tolist()
            report[key] = {
                'model': model, 'seed': 42, 'maxIter': 2000, 'classWeight': class_weight, 'normalizedEmbeddings': True,
                'trainingAccuracy': sum(a == b for a, b in zip([r['label'] for r in train], training_predictions)) / len(train),
                'trainingMacroF1': classification_metrics([r['label'] for r in train], training_predictions)['macroF1'],
                **classification_metrics(gold, predictions)}
            np.savez(ROOT / filename, weights=classifier.coef_, bias=classifier.intercept_, classes=classifier.classes_)
            with np.load(ROOT / filename, allow_pickle=False) as artifact:
                replay = artifact['classes'][(test_vectors @ artifact['weights'].T + artifact['bias']).argmax(axis=1)].tolist()
            if replay != predictions:
                raise ValueError('저장 아티팩트 재현 결과가 다릅니다.')
        report['artifactReadbackVerified'] = True
        report['modelBeatsRuleOnThisSet'] = report['embeddingLogisticRegression']['macroF1'] > report['rule']['macroF1']
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--compare-balanced', action='store_true', help='동일 임베딩에서 학습 부류 불균형 보정 비교 추가')
    parser.add_argument('--train', default='train.json', help='task_type 폴더 안 학습 데이터 파일')
    parser.add_argument('--model', help='로컬 SentenceTransformer 경로 또는 모델 ID')
    parser.add_argument('--allow-download', action='store_true')
    parser.add_argument('--output', type=Path, default=ROOT / 'report.json')
    args = parser.parse_args()
    report = run(args.model, args.allow_download, args.train, args.compare_balanced)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k in ['extraction', 'productionClassifier', 'modelBeatsRuleOnThisSet']}, ensure_ascii=False))
