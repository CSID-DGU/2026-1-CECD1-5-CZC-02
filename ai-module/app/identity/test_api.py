"""실제 main 라우트·계약 응답·잘못된 입력을 검사한다. 외부 서버에 접속하지 않는다."""

import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from jsonschema import Draft202012Validator

from app.main import app

SCHEMA = json.loads((Path(__file__).resolve().parents[3]/'contracts/identity_resolution.schema.json').read_text(encoding='utf-8'))


def payload():
    return {'mentions':[{'name':'가상인물_0001','company':'가상회사_001','channel':'MEETING','sourceRef':'src_demo'}],
            'persons':[{'personId':'p_demo','name':'가상인물_0001','company':'가상회사_001','isSynthetic':True}]}


@pytest.mark.parametrize('scenario', ['candidates','confirmed','new','empty'])
def test_main_route_returns_identity_contract_array(scenario):
    body = payload()
    expected = 'CANDIDATES'
    if scenario == 'confirmed':
        body['persons'][0]['emails'] = ['synthetic@people.example']
        body['mentions'][0]['email'] = 'synthetic@people.example'
        expected = 'CONFIRMED'
    elif scenario == 'new':
        body['persons'] = []
        expected = 'NEW'
    elif scenario == 'empty':
        body['mentions'] = []
    response = TestClient(app).post('/identity/resolve',json=body)
    assert response.status_code == 200
    results = response.json()
    assert len(results) == len(body['mentions'])
    for result in results:
        Draft202012Validator(SCHEMA).validate(result)
        assert result['decision'] == expected
        assert result['mention'] == body['mentions'][0]
        assert result['decidedBy'] == 'RULE'
        assert all(c['score'] is None and c['reasons'] for c in result['candidates'])


@pytest.mark.parametrize('bad', ['missing_persons','missing_channel','extra_mention','bad_person','duplicate','extra_wrapper','non_object'])
def test_invalid_input_is_422_and_never_silently_coerced(bad):
    body = payload()
    if bad == 'missing_persons': del body['persons']
    elif bad == 'missing_channel': del body['mentions'][0]['channel']
    elif bad == 'extra_mention': body['mentions'][0]['label'] = 1
    elif bad == 'bad_person': body['persons'][0]['isSynthetic'] = 'true'
    elif bad == 'duplicate': body['persons'].append(dict(body['persons'][0]))
    elif bad == 'extra_wrapper': body['decidedBy'] = 'HUMAN'
    else: body['mentions'] = [1]
    response = TestClient(app).post('/identity/resolve',json=body)
    assert response.status_code == 422


def test_batch_has_one_output_per_mention_with_source_preserved():
    body = payload()
    body['mentions'].append({'name':None,'company':None,'channel':'EMAIL','sourceRef':'src_missing'})
    response = TestClient(app).post('/identity/resolve',json=body)
    assert response.status_code == 200
    assert [r['mention']['sourceRef'] for r in response.json()] == ['src_demo','src_missing']
    assert [r['decision'] for r in response.json()] == ['CANDIDATES','NEW']
