from unittest.mock import Mock, patch

import pytest

from app.llm.base import LLMError
from .engine import extract_tasks, CONTRACTS, _validator
from .llm_classifier import suggest_task_type
import json


@pytest.mark.parametrize('data, expected', [
    ({'taskType': 'MEETING_PREP'}, 'MEETING_PREP'),
    ({'taskType': 'NONE'}, 'NONE'), ({'taskType': 'unknown'}, 'NONE'),
    ({'taskType': ['MEETING_PREP']}, 'NONE'), (None, 'NONE'),
    ({'taskType': 'MEETING_PREP', 'extra': True}, 'NONE'),
])
def test_strict_output_and_local_model(data, expected):
    with patch('app.tasks.llm_classifier.OllamaProvider') as provider:
        provider.return_value.generate.return_value.data = data
        assert suggest_task_type('가상 업무') == expected
        assert provider.call_args.kwargs['task_models'] == {'task_type': 'gemma4:e4b'}
        assert provider.call_args.kwargs['task_think'] == {'task_type': False}


def test_unavailable_keeps_none():
    with patch('app.tasks.llm_classifier.OllamaProvider') as provider:
        provider.return_value.generate.side_effect = LLMError('offline')
        assert suggest_task_type('가상 업무') == 'NONE'


def test_only_rule_none_calls_llm_and_preserves_contract():
    note = json.loads((CONTRACTS / 'examples/meeting_note.example.json').read_text(encoding='utf-8'))
    suggestion = Mock(return_value='MEETING_PREP')
    tasks = extract_tasks(note, assignee='user_demo', type_suggester=suggestion)
    suggestion.assert_not_called()  # 규칙 일치 및 고객 측 약속은 호출하지 않음
    note['fields']['nextContact'] = [{'text': '가상 대상과 일정 조율', 'evidence': '가상 대상과 일정 조율'}]
    tasks = extract_tasks(note, type_suggester=suggestion)
    suggestion.assert_called_once_with('가상 대상과 일정 조율')
    assert tasks[-1]['taskTypeSource'] == 'LLM'
    assert tasks[-1]['status'] == 'DRAFT'
    for task in tasks:
        _validator('task').validate(task)
