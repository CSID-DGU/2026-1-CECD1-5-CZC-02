"""명시적으로 실행할 때만 로컬 Ollama로 학습 후보 생성. 유료 API 없음."""
import argparse
import json
import urllib.request
from pathlib import Path
from .experiment import LABELS, ROOT, load


def generate(base_url='http://localhost:11434', model='qwen2.5:7b', count=60):
    evaluation_texts = {row['text'] for row in load('evaluation.json')}
    hints = {label: [r['text'] for r in load('train.json') if r['label'] == label][:2] for label in LABELS}
    checkpoint = ROOT / 'generation-progress.json'
    rows = json.loads(checkpoint.read_text(encoding='utf-8')) if checkpoint.exists() else []
    if any(row.get('generatorModel') != model for row in rows):
        raise ValueError('다른 모델의 체크포인트입니다. 기존 파일을 별도 보관하고 새로 생성하세요.')
    for label in LABELS:
        if sum(row['label'] == label for row in rows) >= count:
            continue
        examples = []
        for attempt in range(10):
            needed = min(15, count - len(examples))
            if needed <= 0:
                break
            prompt = (f'한국어 영업 후속 할 일 분류 학습용 가짜 문장 {needed}개를 작성하세요. '
                      f'라벨은 {label}. 전체 라벨: {LABELS}. 참고: {hints[label]}. '
                      '각 문장은 10~30자이며 정확히 요청한 개수만 만드세요. 날짜나 부사만 바꾸지 말고 동사, 목적어, 상황, 문장 구조를 다양하게 하세요. '
                      '이름, 회사명, 이메일, 전화번호, 실제 고객 정보는 쓰지 마세요. '
                      'NONE은 미팅/출장/계약/만료면담/후보제출/탈락통보/서류회수/종료평가/마진/소싱에 속하지 않는 일반 후속 업무입니다. '
                      'JSON 객체의 examples 키에 문자열 배열만 반환하세요.')
            body = json.dumps({'model': model, 'prompt': prompt, 'stream': False,
                               'format': {'type': 'object', 'properties': {'examples': {'type': 'array', 'items': {'type': 'string'}}}, 'required': ['examples']},
                               'options': {'temperature': 0.7, 'seed': 42 + attempt, 'num_predict': 1800, 'num_ctx': 8192}}).encode('utf-8')
            request = urllib.request.Request(base_url + '/api/generate', data=body, headers={'Content-Type': 'application/json'})
            with urllib.request.urlopen(request, timeout=600) as response:
                result = json.load(response)
            generated = json.loads(result['response'])['examples']
            for text in generated:
                if isinstance(text, str):
                    text = text.strip()
                if (isinstance(text, str) and text.strip() and text not in examples and text not in evaluation_texts
                        and not any(ch in text for ch in ['@', '㈜'])):
                    examples.append(text.strip())
            print(label, len(examples), flush=True)
        if len(examples) < count:
            raise ValueError(f'{label}: 충분한 독립 문장을 만들지 못함')
        rows.extend({'id': f'ollama_{label.lower()}_{i}', 'text': text, 'label': label,
                     'source': 'local_ollama_training', 'group': f'{label}_candidate_{i}',
                     'reviewStatus': 'PENDING', 'generatorModel': model}
                    for i, text in enumerate(examples[:count]))
        (ROOT / 'generation-progress.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return rows


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', default='http://localhost:11434')
    parser.add_argument('--model', default='qwen2.5:7b')
    parser.add_argument('--output', type=Path, default=ROOT / 'generated-train.json')
    args = parser.parse_args()
    args.output.write_text(json.dumps(generate(args.base_url, args.model), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
