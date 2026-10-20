"""명시적으로 실행할 때만 로컬 Ollama로 학습 후보 생성. 유료 API 없음."""
import argparse
import json
import re
import urllib.request
from pathlib import Path

from .experiment import LABELS, ROOT, load

PROMPT_VERSION = 'task_type_domains_v2'
DOMAINS = {
    'MEETING_PREP': '고객과 만나 논의할 미팅 준비. 고객사 사무실 방문도 논의가 목적이면 포함. 내부 회의나 대상 불명 회의는 제외.',
    'VISIT_PREP': '지역 이동이 있는 출장, 현장 방문, 설비 점검의 이동·출입 준비. 단순 고객사 사무실 논의 방문은 미팅이다.',
    'CONTRACT_PREP': '계약서·협약서 초안 작성, 수정, 대조, 송부. 이미 발송한 서명본의 회수·취합·독촉은 제외.',
    'RENEWAL_INTERVIEW': '계약 만료·갱신·재계약을 앞두고 인력이나 고객과 연장 의사를 확인하는 면담. 갱신 계약서 작성은 제외.',
    'PROPOSAL_SUBMISSION': '인력 요청이나 공고에 제안서·후보 프로필·인력 리스트를 준비하여 제출. 이미 제출됐는지 확인만 하는 업무는 제외.',
    'REJECTION_NOTICE': '선정에서 탈락하거나 미채택된 인력 후보에게 결과·사유를 알리는 통보. 재접촉이나 단순 연락만은 제외.',
    'DOC_COLLECTION': '받아야 할 서류의 수집·취합, 이미 보낸 계약서 서명본의 회수·독촉. 계약서 작성·다시 송부·일반 준비는 제외.',
    'END_EVALUATION': '개별 인력 투입 또는 프로젝트 종료 시 한 번 작성하는 수행 평가. 월간 영업 실적 평가, 내부 회의 후기, 손익 분석은 제외.',
    'PROFITABILITY_REVIEW': '인력 투입 조건 협의 전 마진·마진율·수익률·수익성을 계산·검토. 계약 문서 작업은 제외.',
    'SOURCING_START': 'S-10은 사람을 찾는 인력 소싱이다. 고객의 인력 요청을 접수한 뒤 해당 직무·포지션에 맞는 인력·인재·후보를 탐색하기 시작한다. 원자재·제품·부품·공급업체·공급망·구매처 찾기는 절대 아니다. 이미 찾은 후보의 프로필 제출도 아니다.',
    'NONE': '10개 서비스에 없는 일반 후속 업무. 공급업체 찾기, 실적 정리, 내부 회의, 재접촉, 이미 제출한 제안서 접수 확인 등이 해당한다.',
}
CONTEXTS = ['추가 인력 수요 접수', '신규 포지션 요청', '필요 직무와 경력 조건 확인',
            '긴급 충원 요청', '특정 기술 역량 조건', '근무 조건에 맞는 후보 찾기',
            '기존 후보군 외 탐색', '다른 직무 인재 물색', '지원자 풀 검색', '요청별 후보 탐색 착수']


def build_prompt(label, count, attempt):
    context = (CONTEXTS[attempt % len(CONTEXTS)] if label == 'SOURCING_START'
               else ['명시적 후속 약속', '요청 내용 재확인', '기한 전 준비', '담당자 후속 작업'][attempt % 4])
    sourcing_constraint = ('인력·후보·인재 중 하나와 탐색·검색·물색·찾기·소싱 중 하나를 반드시 함께 쓰세요. '
                           '포지션은 요청 조건이고 찾는 대상은 사람입니다. '
                           if label == 'SOURCING_START' else '')
    return (f'한국어 영업 후속 할 일 학습용 가짜 문장을 정확히 {count}개 작성하세요. '
            f'라벨은 {label}. 정의: {DOMAINS[label]} '
            f'이번 묶음의 상황: {context}. '
            f'{sourcing_constraint}'
            '채용 플랫폼명이나 영문 기술명도 쓰지 말고 일반 한국어로 표현하세요. '
            '각 문장은 하나의 할 일로 15~45자. 날짜만 바꾼 반복은 피하고 동사·직무·상황을 다양하게 쓰세요. '
            '실제 또는 실제처럼 보이는 회사명·인명·브랜드·영문 고유명사·이메일·전화번호는 절대 쓰지 마세요. '
            '고객, 담당자, 후보 같은 일반 명칭만 쓰세요. '
            'JSON 객체 examples 키에 문자열 배열만 반환하세요.')


def acceptable_candidate(text, label):
    if not isinstance(text, str) or not text.strip():
        return False
    if re.search(r'[A-Za-z@\u3400-\u9fff]|㈜|주식회사|\d{2,3}[- ]\d{3,4}[- ]\d{4}', text):
        return False
    if label == 'SOURCING_START':
        if re.search(r'공급|원자재|구매처|부품|납품업체|업체\s*(?:찾|탐색|발굴)', text):
            return False
        return bool(re.search(r'인력|인재|후보|지원자|개발자|전문가', text)
                    and re.search(r'찾|탐색|검색|물색|소싱|모집|발굴|착수', text))
    return True


def generate(base_url='http://localhost:11434', model='qwen2.5:7b', count=60, labels=None, checkpoint=None):
    selected = list(LABELS if labels is None else labels)
    if not selected or any(label not in LABELS for label in selected):
        raise ValueError('유효한 생성 부류를 지정하세요.')
    evaluation_texts = {row['text'] for row in load('evaluation.json')}
    existing_texts = {row['text'] for row in load('train.json')}
    checkpoint = checkpoint or ROOT / 'generation-v2-progress.json'
    rows = json.loads(checkpoint.read_text(encoding='utf-8')) if checkpoint.exists() else []
    if any(row.get('generatorModel') != model or row.get('promptVersion') != PROMPT_VERSION for row in rows):
        raise ValueError('다른 모델/프롬프트의 체크포인트입니다. 새 --checkpoint를 지정하세요.')
    for label in selected:
        examples = [row['text'] for row in rows if row['label'] == label]
        for attempt in range(20):
            needed = min(10, count - len(examples))
            if needed <= 0:
                break
            body = json.dumps({'model': model, 'prompt': build_prompt(label, needed, attempt), 'stream': False,
                               'format': {'type': 'object', 'properties': {'examples': {'type': 'array', 'items': {'type': 'string'}}}, 'required': ['examples']},
                               'options': {'temperature': 0.7, 'seed': 142 + attempt, 'num_predict': 1800, 'num_ctx': 8192}}).encode('utf-8')
            request = urllib.request.Request(base_url + '/api/generate', data=body, headers={'Content-Type': 'application/json'})
            with urllib.request.urlopen(request, timeout=600) as response:
                result = json.load(response)
            candidates = json.loads(result['response'])['examples']
            print('candidates', len(candidates), 'unsafe', sum(isinstance(t, str) and bool(re.search(r'[A-Za-z@]|㈜|주식회사', t)) for t in candidates), flush=True)
            for text in candidates:
                text = text.strip() if isinstance(text, str) else text
                if (acceptable_candidate(text, label) and text not in examples
                        and text not in evaluation_texts and text not in existing_texts
                        and text not in {row['text'] for row in rows if row['label'] != label}):
                    examples.append(text)
            print(label, min(len(examples), count), flush=True)
            rows = [row for row in rows if row['label'] != label]
            rows.extend({'id': f'ollama_{label.lower()}_v2_{i}', 'text': text, 'label': label,
                         'source': 'local_ollama_training', 'group': f'{label}_v2_candidate_{i}',
                         'reviewStatus': 'PENDING', 'generatorModel': model, 'promptVersion': PROMPT_VERSION}
                        for i, text in enumerate(examples[:count]))
            checkpoint.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        if len(examples) < count:
            raise ValueError(f'{label}: 충분한 독립 문장을 만들지 못함')
    return [row for row in rows if row['label'] in selected]


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', default='http://localhost:11434')
    parser.add_argument('--model', default='qwen2.5:7b')
    parser.add_argument('--label', action='append', choices=LABELS)
    parser.add_argument('--checkpoint', type=Path)
    parser.add_argument('--output', type=Path, default=ROOT / 'generated-train.json')
    args = parser.parse_args()
    args.output.write_text(json.dumps(generate(args.base_url, args.model, labels=args.label, checkpoint=args.checkpoint), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
