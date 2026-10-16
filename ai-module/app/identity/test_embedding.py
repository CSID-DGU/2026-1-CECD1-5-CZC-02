"""모델 다운로드 없이 선택 임베딩 부품·규칙 보존·충돌 처리를 검사한다."""
import pytest

from app.identity.embedding import cosine, identity_text, person_texts, rank_with_embeddings
from app.identity.features import compare_people


def person(n, **updates):
    return {"personId": f"p_{n:03}", "name": f"가상인물_{n:04}",
            "company": "가상회사_001", "isSynthetic": True, **updates}


def test_text_does_not_contain_identity_metadata():
    text = identity_text(" 가상인물_0001 부장님 ", " 가상회사_001 ")
    assert text == "이름: 가상인물_0001\n회사: 가상회사_001"
    assert identity_text(None, "가상회사_001") is None
    p = person(1, emails=["synthetic@people.example"], nameVariants=["Synthetic Person 0001"],
               companyHistory=[{"company": "가상회사_002"}])
    texts = person_texts(p)
    assert len(texts) == 4
    assert all("p_001" not in t and "@" not in t for t in texts)
    assert any("가상회사_002" in t for t in texts)


def test_cosine_is_similarity_not_probability():
    assert cosine([1, 0], [1, 0]) == 1
    assert cosine([1, 0], [0, 1]) == 0
    assert cosine([1, 0], [-1, 0]) == -1
    with pytest.raises(ValueError):
        cosine([0, 0], [1, 0])


@pytest.mark.parametrize("weight,threshold", [(float('nan'), None), (-.1, None), (1.1, None), (0, 1.1)])
def test_invalid_parameters(weight, threshold):
    with pytest.raises(ValueError):
        rank_with_embeddings([], {}, rule_threshold=.88, weight=weight, embedding_threshold=threshold)


def test_optional_embedding_candidates_need_company_and_name():
    m = {"name": "가상표기_다른이름", "company": "가상회사_001", "channel": "MEETING"}
    comparisons = compare_people(m, [person(1), person(2, company="가상회사_002")])
    assert rank_with_embeddings(comparisons, {"p_001": .99, "p_002": .99}, rule_threshold=.88, weight=0) == []
    results = rank_with_embeddings(comparisons, {"p_001": .99, "p_002": .99}, rule_threshold=.88, weight=1, embedding_threshold=.9)
    assert [r.person_id for r in results] == ["p_001"]
    assert results[0].candidate.supporting_fields == ("embedding_similarity", "company")
    empty = compare_people(dict(m, name=None), [person(1)])
    assert rank_with_embeddings(empty, {"p_001": .99}, rule_threshold=.88, weight=1, embedding_threshold=.9) == []


def test_embedding_does_not_override_contact_or_warning():
    m = {"name": "가상인물_0002", "email": "synthetic@people.example", "channel": "EMAIL"}
    comparisons = compare_people(m, [person(1, emails=["synthetic@people.example"]), person(2)])
    ranked = rank_with_embeddings(comparisons, {"p_001": -.5, "p_002": 1}, rule_threshold=.88, weight=1)
    assert [r.person_id for r in ranked] == ["p_001", "p_002"]
    assert "CONTACT_MATCH_NAME_DIFFERS" in ranked[0].candidate.comparison.warnings
