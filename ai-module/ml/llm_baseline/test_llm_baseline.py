"""LLM 직접 판정·비교 집계 테스트. Ollama·네트워크 없이 가짜 제공자로 돈다."""
from app.llm.base import LLMError, LLMResponse
from ml.llm_baseline.classifier import LlmTaskTypeClassifier, build_system_prompt, parse_task_types, prompt_sha256
from ml.llm_baseline.compare import c_report_embedding_predictions, confusion_matrix, evaluate_set, hybrid, run_rule, summarize
from ml.llm_baseline.data import TASK_TYPES_DOC, load_c_evaluation, load_holdout
from ml.task_type.experiment import LABELS


class FakeProvider:
    name = 'fake'

    def __init__(self, answers):
        self.answers = list(answers)
        self.requests = []

    def model_for(self, task):
        return 'fake'

    def generate(self, request):
        self.requests.append(request)
        answer = self.answers.pop(0)
        if isinstance(answer, Exception):
            raise answer
        return LLMResponse(data=answer, provider='fake', model='fake')


def test_prompt_comes_from_task_types_doc_and_has_no_evaluation_sentences():
    definitions, boundaries = parse_task_types(TASK_TYPES_DOC.read_text(encoding='utf-8'))
    assert sorted(code for code, _, _ in definitions) == sorted(LABELS)
    assert len(boundaries) >= 10
    prompt = build_system_prompt()
    assert 'D-024' in prompt and '"taskType"' in prompt
    # 평가 문장을 예시로 넣지 않는다 (누출 방지)
    assert not [item['text'] for item in load_holdout()['items'] if item['text'] in prompt]
    assert not [row['text'] for row in load_c_evaluation() if row['text'] in prompt]
    assert prompt_sha256(prompt) == prompt_sha256(build_system_prompt())


def test_classifier_returns_label_and_reason_and_sends_deterministic_request():
    provider = FakeProvider([{'taskType': 'CONTRACT_PREP', 'reason': '계약서 송부'}])
    judgement = LlmTaskTypeClassifier(provider).classify('계약서를 보낸다')
    assert (judgement.label, judgement.reason, judgement.failure) == ('CONTRACT_PREP', '계약서 송부', None)
    request = provider.requests[0]
    assert request.user == '할 일: 계약서를 보낸다'
    assert request.temperature == 0.0 and request.json_schema is not None


def test_bad_format_or_call_failure_becomes_none_and_is_counted():
    provider = FakeProvider([
        {'taskType': 'MEETING', 'reason': '부류 이름 틀림'},
        {'reason': '부류 없음'},
        ['리스트'],
        LLMError('LLM response JSON is malformed.'),
        {'taskType': ' sourcing_start ', 'reason': '대소문자·공백은 고쳐 읽음'},
    ])
    classifier = LlmTaskTypeClassifier(provider, system_prompt='지시문')
    judgements = [classifier.classify(f'문장 {i}') for i in range(5)]
    assert [j.label for j in judgements] == ['NONE', 'NONE', 'NONE', 'NONE', 'SOURCING_START']
    assert [j.failure is not None for j in judgements] == [True, True, True, True, False]


def test_hybrid_uses_llm_only_when_rule_says_none():
    assert hybrid(['CONTRACT_PREP', 'NONE', 'NONE'], ['NONE', 'VISIT_PREP', 'NONE']) == ['CONTRACT_PREP', 'VISIT_PREP', 'NONE']


def test_summary_metrics_and_confusion():
    gold = ['MEETING_PREP', 'MEETING_PREP', 'NONE', 'NONE']
    predicted = ['MEETING_PREP', 'VISIT_PREP', 'NONE', 'MEETING_PREP']
    summary = summarize(gold, predicted, seconds=0.5)
    assert summary['accuracy'] == 0.5 and summary['noneRecall'] == 0.5 and summary['errorCount'] == 2
    assert summary['confusion'] == {'MEETING_PREP': {'MEETING_PREP': 1, 'VISIT_PREP': 1}, 'NONE': {'NONE': 1, 'MEETING_PREP': 1}}
    assert confusion_matrix(['NONE'], ['NONE']) == {'NONE': {'NONE': 1}}


def test_evaluate_set_on_holdout_with_fake_llm_counts_failures():
    rows = load_holdout()['items']
    answers = [{'taskType': row['label'], 'reason': '정답'} for row in rows]
    answers[0] = {'taskType': '모름'}
    classifier = LlmTaskTypeClassifier(FakeProvider(answers), system_prompt='지시문')
    judgements = [classifier.classify(row['text']) for row in rows]
    result = evaluate_set(rows, judgements, run_rule([row['text'] for row in rows]))
    assert result['size'] == len(rows)
    assert result['llm']['formatFailures'] == 1
    assert result['llm']['errorCount'] == (0 if rows[0]['label'] == 'NONE' else 1)
    assert set(result) >= {'rule', 'llm', 'hybridRuleThenLlm', 'items'}
    assert result['items'][1]['llmReason'] == '정답'


def test_c_report_embedding_predictions_match_reported_score():
    evaluation = load_c_evaluation()
    restored = c_report_embedding_predictions(evaluation)
    assert restored is not None
    gold = [row['label'] for row in evaluation]
    assert abs(summarize(gold, restored['predictions'])['macroF1'] - restored['reportedMacroF1']) < 1e-4
