"""다른 역할이 작성한 교차 검수 CSV를 검증하고 부류별 불일치율을 계산한다."""
import argparse
import csv
import json
from collections import Counter
from pathlib import Path
from .experiment import LABELS, load
from app.tasks.classifier import classify_task


def evaluate_review(rows, votes):
    by_id = {row['id']: row for row in rows}
    seen = set()
    disagreement, totals = Counter(), Counter()
    for vote in votes:
        key = vote['id']
        if key not in by_id or key in seen:
            raise ValueError('알 수 없거나 중복된 검수 ID')
        if vote['reviewerRole'] not in ['A', 'B', 'D']:
            raise ValueError('C가 만든 데이터는 A/B/D가 검수해야 합니다.')
        if vote['label'] not in LABELS:
            raise ValueError('유효한 11부류 라벨이 필요합니다.')
        seen.add(key)
        original = by_id[key]['label']
        totals[original] += 1
        disagreement[original] += vote['label'] != original
    rates = {label: disagreement[label] / totals[label] for label in totals}
    return {'reviewed': len(seen), 'total': len(rows), 'complete': len(seen) == len(rows),
            'disagreementByClass': rates,
            'definitionReviewRequired': [label for label, rate in rates.items() if rate >= 0.2],
            'promotionAllowed': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--data', default='train.json')
    parser.add_argument('--export', type=Path)
    parser.add_argument('--votes', type=Path)
    args = parser.parse_args()
    rows = load(args.data)
    if args.export:
        with args.export.open('w', encoding='utf-8', newline='') as handle:
            writer = csv.DictWriter(handle, fieldnames=['id', 'text', 'proposedLabel', 'trainingEligible', 'ruleLabel', 'ruleDisagrees', 'reviewerRole', 'label'])
            writer.writeheader()
            for row in rows:
                writer.writerow({'id': row['id'], 'text': row['text'], 'proposedLabel': row['label'], 'trainingEligible': row.get('trainingEligible', True), 'ruleLabel': classify_task(row['text'])[0], 'ruleDisagrees': classify_task(row['text'])[0] != row['label'], 'reviewerRole': '', 'label': ''})
    if args.votes:
        with args.votes.open(encoding='utf-8', newline='') as handle:
            print(json.dumps(evaluate_review(rows, list(csv.DictReader(handle))), ensure_ascii=False, indent=2))
