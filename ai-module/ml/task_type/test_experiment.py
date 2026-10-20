from collections import Counter

from ml.task_type.experiment import classification_metrics, extraction_metrics, load, validate_splits


def test_dataset_sources_groups_and_labels():
    train, evaluation = load('train.json'), load('evaluation.json')
    validate_splits(train, evaluation)
    active = [row for row in train if row.get('trainingEligible', True)]
    assert all(Counter(row['label'] for row in active).values())
    assert len(active) < len(train)  # 모호한 후보를 정답으로 강제하지 않는다.
    assert set(Counter(r['label'] for r in evaluation).values()) == {5}
    assert all(r['reviewStatus'] == 'PENDING' for r in train + evaluation)


def test_metric_penalizes_missing_extra_and_wrong_labels():
    metrics = classification_metrics(['NONE', 'MEETING_PREP'], ['MEETING_PREP', 'MEETING_PREP'])
    assert metrics['noneRecall'] == 0
    assert metrics['perClass']['MEETING_PREP']['precision'] == 0.5
    assert len(metrics['errors']) == 1


def test_extraction_regression_gold():
    report = extraction_metrics(load('extraction.json'))
    assert report['recall'] == 1
    assert report['precision'] == 1
    assert report['dateAccuracyOnMatched'] == 1
    assert report['typeAccuracyOnMatched'] == 1
    assert report['kindAccuracyOnMatched'] == 1


def test_review_requires_other_role_and_flags_boundary_disagreement():
    import pytest
    from ml.task_type.review import evaluate_review
    rows = [{'id': str(i), 'label': 'NONE'} for i in range(5)]
    votes = [{'id': str(i), 'reviewerRole': 'B', 'label': 'NONE' if i else 'MEETING_PREP'} for i in range(5)]
    report = evaluate_review(rows, votes)
    assert report['complete']
    assert report['definitionReviewRequired'] == ['NONE']
    votes[0]['reviewerRole'] = 'C'
    with pytest.raises(ValueError):
        evaluate_review(rows, votes)
