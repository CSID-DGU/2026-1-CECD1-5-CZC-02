"""검색 평가의 정답·동점 처리·학습 전용 임계값 선택 검사."""
from app.identity.features import compare_people
from ml.identity.evaluate_candidates import measure, prepare_queries, rank_bounds, select_threshold
from app.identity.ranking import rank_candidates


def person(n):
    return {"personId": f"p_{n:03}", "name": "가상인물_0001", "company": "가상회사_001", "isSynthetic": True}


def test_tie_metrics_do_not_credit_id_order():
    people = [person(n) for n in range(1, 5)]
    mention = {"name": "가상인물_0001", "channel": "MEETING"}
    ranked = rank_candidates(mention, people)
    assert rank_bounds(ranked, "p_001") == (1, 4)
    assert rank_bounds(ranked, "p_999") is None
    pair = {"personIdA": "p_002", "personIdB": "p_001", "label": 0, "caseType": "same_name", "mentionB": mention}
    metrics = measure([(pair, compare_people(mention, people))], None)
    assert metrics["retrieval"]["included"] == 1
    assert metrics["retrieval"]["top3Guaranteed"] == 0
    assert metrics["retrieval"]["top3Possible"] == 1
    assert metrics["pairCandidateInclusion"]["fp"] == 1
    assert metrics["pairCandidateInclusion"]["tp"] == 0


def test_metadata_and_negative_labels_are_not_search_inputs():
    m = {"name": "가상인믈_0001", "company": "가상회사_001", "channel": "MEETING"}
    pair = {"personIdA": "p_002", "personIdB": "p_001", "label": 0, "caseType": "random", "mentionB": m}
    queries, unique = prepare_queries([pair, dict(pair, label=1, personIdA="p_001", caseType="typo")], [person(1)])
    assert unique == 1
    assert queries[0][1] is queries[1][1]
    threshold, trials = select_threshold(queries)
    assert threshold in [t["threshold"] for t in trials]
    assert all(t["metrics"]["retrieval"]["queries"] == 2 for t in trials)
