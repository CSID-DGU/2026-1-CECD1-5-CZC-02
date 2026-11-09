from collections import Counter

from ml.llm_baseline.data import doc_example_sentences, load_c_texts, load_holdout, overlaps, similarity
from ml.task_type.experiment import LABELS


def test_holdout_covers_every_label_and_is_marked_unreviewed():
    holdout = load_holdout()
    items = holdout['items']
    counts = Counter(item['label'] for item in items)
    assert set(counts) == set(LABELS)
    assert all(counts[label] >= 3 for label in LABELS)
    assert 33 <= len(items) <= 36
    assert holdout['reviewStatus'].startswith('검수 전')
    assert holdout['frozenBeforeAnyModelRun'] is True
    assert len({item['id'] for item in items}) == len(items)
    assert len({item['text'] for item in items}) == len(items)


def test_holdout_does_not_overlap_c_data_or_prompt_examples():
    texts = [item['text'] for item in load_holdout()['items']]
    c_texts = load_c_texts()
    examples = doc_example_sentences()
    assert len(c_texts) > 700 and len(examples) > 10  # 비교 대상을 제대로 읽었는지
    assert not set(texts) & set(c_texts)
    assert not set(texts) & set(examples)
    # 표현만 바꾼 거의 같은 문장도 막는다 (C 학습·평가·추출 문장, 지시문에 들어가는 경계표 예시)
    assert overlaps(texts, c_texts + examples, threshold=0.6) == []


def test_similarity_catches_near_duplicates():
    assert similarity('계약서 초안을 송부한다', '계약서 초안을 송부 한다') == 1.0
    assert similarity('계약서 초안을 송부한다', '새 공급업체를 알아본다') < 0.2
