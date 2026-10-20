"""사용자 검수 피드백 회귀. 네트워크·유료 API·무거운 모델 import 없음."""
import io
import json

import pytest

from ml.task_type import generate as generation
from ml.task_type.experiment import load, run


def test_user_requested_relabels_keep_original_ids():
    labels = {row['id'].removeprefix('ollama_'): row['label'] for row in load('train.json')}
    expected = {
        'sourcing_start_32': 'NONE', 'sourcing_start_13': 'NONE', 'sourcing_start_29': 'NONE',
        'end_evaluation_52': 'PROPOSAL_SUBMISSION', 'end_evaluation_54': 'DOC_COLLECTION',
        'end_evaluation_6': 'NONE', 'doc_collection_38': 'CONTRACT_PREP',
        'doc_collection_34': 'NONE', 'doc_collection_25': 'NONE', 'rejection_notice_6': 'NONE',
        'none_13': 'CONTRACT_PREP',
    }
    assert {key: labels[key] for key in expected} == expected


def test_human_sourcing_replaces_supplier_sourcing_without_erasing_negatives():
    rows = load('train.json')
    old = [row for row in rows if row['id'].startswith('ollama_sourcing_start_') and '_v2_' not in row['id']]
    fresh = [row for row in rows if row['id'].startswith('ollama_sourcing_start_v2_')]
    assert len(old) == len(fresh) == 60
    assert all(row['label'] == 'NONE' for row in old)
    assert all(row['label'] == 'SOURCING_START' and generation.acceptable_candidate(row['text'], row['label']) for row in fresh)


def test_ambiguous_boundaries_are_excluded_from_training():
    by_id = {row['id'].removeprefix('ollama_'): row for row in load('train.json')}
    for key in ['visit_prep_7', 'visit_prep_49', 'meeting_prep_20', 'meeting_prep_54', 'end_evaluation_33', 'doc_collection_45']:
        assert by_id[key]['trainingEligible'] is False
    assert by_id['meeting_prep_49']['label'] == by_id['visit_prep_47']['label'] == 'MEETING_PREP'
    assert by_id['none_29']['label'] == 'NONE'


def test_report_distinguishes_candidates_and_training_and_review_scopes():
    report = run()
    candidates = load('train.json')
    assert report['data']['train'] == sum(row.get('trainingEligible', True) for row in candidates)
    assert report['data']['trainingCandidates'] == len(candidates)
    assert report['data']['excludedFromTraining'] > 0
    assert report['data']['evaluationLabelsReviewed'] is True
    assert report['data']['independentReviewCompleted'] is False
    assert report['productionClassifier'] == 'RULE'
    assert report['promotionAllowed'] is False


def test_prompt_uses_domain_definitions_not_bad_train_hints_or_evaluation():
    prompt = generation.build_prompt('SOURCING_START', 10, 0)
    assert all(word in prompt for word in ['인력', '후보', '포지션', '공급업체'])
    assert '사람' in prompt
    assert all(row['text'] not in prompt for row in load('evaluation.json'))
    supplier = next(row for row in load('train.json') if row['id'] == 'ollama_sourcing_start_0')
    assert supplier['text'] not in prompt


@pytest.mark.parametrize('text', ['신규 공급업체 탐색 시작', '소싱 착수', '개발 포지션 탐색 시작', '후보 @ 검색', '후보 A 탐색'])
def test_generation_rejects_vendor_or_unscoped_or_identifier_candidates(text):
    assert not generation.acceptable_candidate(text, 'SOURCING_START')


def test_generator_filters_and_writes_only_requested_label_without_network(monkeypatch, tmp_path):
    seen = []
    def fake_load(name):
        return [{'text': '새 공급업체 탐색 시작'}] if name == 'train.json' else [{'text': '평가 전용 문장'}]
    def fake_open(request, timeout):
        seen.append(json.loads(request.data.decode('utf-8')))
        return io.StringIO(json.dumps({'response': json.dumps({'examples': ['새 공급업체 탐색 시작', '인력 요청에 맞는 후보 탐색 착수']}, ensure_ascii=False)}))
    monkeypatch.setattr(generation, 'load', fake_load)
    monkeypatch.setattr(generation.urllib.request, 'urlopen', fake_open)
    rows = generation.generate(count=1, labels=['SOURCING_START'], checkpoint=tmp_path / 'checkpoint.json')
    assert len(rows) == 1
    assert rows[0]['text'] == '인력 요청에 맞는 후보 탐색 착수'
    assert rows[0]['reviewStatus'] == 'PENDING'
    assert '평가 전용 문장' not in seen[0]['prompt']
    assert '새 공급업체 탐색 시작' not in seen[0]['prompt']


def test_v1_checkpoint_cannot_silently_skip_regeneration(monkeypatch, tmp_path):
    monkeypatch.setattr(generation, 'load', lambda name: [])
    path = tmp_path / 'checkpoint.json'
    path.write_text(json.dumps([{'generatorModel': 'qwen2.5:7b', 'promptVersion': 'v1'}]), encoding='utf-8')
    with pytest.raises(ValueError):
        generation.generate(labels=['SOURCING_START'], checkpoint=path)


def test_company_like_text_repairs_use_explicit_synthetic_identifiers():
    by_id = {row['id'].removeprefix('ollama_'): row for row in load('train.json')}
    ids = ([f'meeting_prep_{i}' for i in [36,38,39,41,48,50,52,53]]
           + [f'renewal_interview_{i}' for i in [41,42,44,45,47,48,49,51,52,53,54]]
           + ['profitability_review_28'])
    assert all(by_id[key]['text'].startswith('가상조직_') for key in ids)
    assert not by_id['doc_collection_45']['trainingEligible']  # 오염된 생성 문장은 학습에 사용하지 않는다.
