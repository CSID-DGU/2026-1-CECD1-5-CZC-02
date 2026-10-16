"""이름 유사도 계산·오타·잘못된 확정 방지 검사."""

import json
import unicodedata
from copy import deepcopy
from pathlib import Path

import pytest

from app.identity.candidates import find_candidates
from app.identity.features import compare_person
from app.identity.similarity import compare_name_similarity


@pytest.mark.parametrize("left,right,distance,similarity", [
    ("가상인물_0001", "가상인물_0001", 0, 1),
    ("가상인믈_0001", "가상인물_0001", 1, 8 / 9),
    ("가상인물_0001x", "가상인물_0001", 1, 0.9),
    ("가상인물_001", "가상인물_0001", 1, 8 / 9),
    ("가상", "테스트", 3, 0),
    ("가상인물_0001 부장님", "가상인물_0001", 0, 1),
    (" SYNTHETIC  PERSON 0001 ", "Synthetic Person 0001", 0, 1),
    (unicodedata.normalize("NFD", "가상인물_0001"), "가상인물_0001", 0, 1),
])
def test_distance_and_similarity(left, right, distance, similarity):
    result, = compare_name_similarity(left, [("name", right)])
    assert result.edit_distance == distance
    assert result.similarity == pytest.approx(similarity)
    assert result.observed == left
    assert result.registered == right
    reverse, = compare_name_similarity(right, [("name", left)])
    assert reverse.similarity == result.similarity
    assert reverse.edit_distance == distance


@pytest.mark.parametrize("left,right", [
    (None, None), (None, "가상인물_0001"), ("", ""),
    ("  ", "가상인물_0001"), ("가상인물_0001", None),
    ("가상인물_0001", "  "), ("부장님", "부장님"),
])
def test_missing_names_have_no_numeric_similarity(left, right):
    assert compare_name_similarity(left, [("name", right)]) == ()


def test_alias_paths_and_best_value_preserved():
    p = {"personId": "p_001", "name": "가상인물_0001", "isSynthetic": True,
         "nameVariants": ["Synthetic Person 0001", " "]}
    mention = {"name": "Synthetic Person 0001", "channel": "MEETING"}
    original = deepcopy((mention, p))
    result = compare_person(mention, p)
    assert result.name_similarity == 1
    assert [s.path for s in result.name_similarities] == ["name", "nameVariants[0]"]
    assert result.name_similarities[1].edit_distance == 0
    assert (mention, p) == original
    unknown = compare_person(dict(mention, name=None), p)
    assert unknown.name_similarity is None
    assert unknown.name_similarities == ()


def test_high_similarity_does_not_change_exact_match_or_candidates():
    p = {"personId": "p_001", "name": "가상인물_0001", "isSynthetic": True,
         "company": "가상회사_001"}
    typo = {"name": "가상인믈_0001", "company": "가상회사_001", "channel": "MEETING"}
    other_person = dict(typo, name="가상인물_0002")
    for mention in (typo, other_person):
        result = compare_person(mention, p)
        assert result.name_similarity == pytest.approx(8 / 9)
        assert result.name.status == "DIFFERENT"
        assert find_candidates(mention, [p]) == []
    # 번호 차이와 오타가 같은 유사도여도 같은 사람이라고 해석할 수 없다.


def test_all_generated_typo_cases_have_distance_evidence_without_exact_match():
    root = Path(__file__).resolve().parents[2]
    persons = {p["personId"]: p for p in json.loads((root / "ml/identity/data/persons.json").read_text())}
    counts = []
    for filename in ("pairs_train.json", "pairs_eval.json"):
        count = 0
        for pair in json.loads((root / "ml/identity/data" / filename).read_text()):
            if pair["caseType"] != "typo":
                continue
            result = compare_person(pair["mentionB"], persons[pair["personIdA"]])
            assert result.name.status == "DIFFERENT"
            primary, = (s for s in result.name_similarities if s.path == "name")
            assert primary.edit_distance == 1
            assert 0 < primary.similarity < 1
            count += 1
        counts.append(count)
    assert counts == [200, 50]
