"""학습 선택·선택 의존성 부재를 네트워크/실제 모델 없이 검사한다."""
from ml.identity.compare_embeddings import choose_config, compare
from pathlib import Path


def trial(weight, threshold, *, top3=1, fp=0):
    return {"config": {"weight": weight, "embedding_threshold": threshold}, "metrics": {
        "retrieval": {"top3Guaranteed": top3, "top1Guaranteed": 1, "candidateCount": 1},
        "pairCandidateInclusion": {"fp": fp}}}


def test_train_selection_prefers_rules_when_tied():
    baseline = trial(0, None)
    assert choose_config([trial(1, .9), baseline, trial(.25, None)]) == baseline
    better = trial(.25, .9, fp=0)
    assert choose_config([trial(0, None, top3=.9), better]) == better


def test_unavailable_model_runs_rules_only_without_downloading():
    def missing(_):
        raise FileNotFoundError("가짜 로컬 모델 없음")
    data = Path(__file__).resolve().parent / "data"
    report = compare(data, Path('/tmp/synthetic-model-does-not-exist'), encoder_factory=missing)
    assert report["status"] == "rules_only"
    assert report["freshHoldoutUsed"] is False
    assert report["recommendation"] == "keep_rules_model_unavailable"
    assert report["evalRules"]["retrieval"]["queries"] == 600


def test_vector_lookup_uses_only_names_companies_and_best_registered_text():
    from app.identity.features import compare_people
    from ml.identity.compare_embeddings import embedding_scores
    people = [{"personId": "p_001", "name": "가상인물_0001", "company": "가상회사_001",
               "nameVariants": ["Synthetic Person 0001"], "isSynthetic": True},
              {"personId": "p_002", "name": "가상인물_0002", "company": "가상회사_001", "isSynthetic": True}]
    mention = {"name": "가상인물_0001", "company": "가상회사_001", "channel": "MEETING"}
    class FakeEncoder:
        def encode(self, texts):
            assert all("p_001" not in text and "p_002" not in text for text in texts)
            return {text: [1, 0] if "가상인물_0001" in text else [0, 1] for text in texts}
    scores = embedding_scores([({"label": 999}, compare_people(mention, people))], people, FakeEncoder())
    result, = scores.values()
    assert result == {"p_001": 1, "p_002": 0}


def test_eval_gain_is_required_for_adoption_review():
    from ml.identity.compare_embeddings import recommendation
    report = {"selectedConfig": {"weight": .25, "embedding_threshold": None},
              "evalRules": trial(0, None)["metrics"], "evalSelected": trial(.25, None)["metrics"]}
    assert recommendation(report) == 'keep_rules_no_eval_gain'
    report['evalSelected'] = trial(.25, None, top3=.9)['metrics']
    assert recommendation(report) == 'keep_rules_eval_regression'
    report['evalRules'] = trial(0, None, top3=.8)['metrics']
    assert recommendation(report) == 'review_on_fresh_holdout_before_adoption'
