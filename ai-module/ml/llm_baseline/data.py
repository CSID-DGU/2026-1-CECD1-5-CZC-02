"""ML-2 비교에 쓰는 평가셋 읽기와 겹침 검사. C 데이터(ml/task_type)는 읽기만 한다."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TASK_TYPE_DIR = ROOT.parent / 'task_type'
TASK_TYPES_DOC = ROOT.parents[2] / 'docs' / 'project' / 'task-types.md'


def load_holdout():
    return json.loads((ROOT / 'holdout.json').read_text(encoding='utf-8'))


def load_c_evaluation():
    return json.loads((TASK_TYPE_DIR / 'evaluation.json').read_text(encoding='utf-8'))


def load_c_texts():
    """C의 학습 후보·평가·추출 회귀셋에 나오는 모든 문장 (겹침 검사용)."""
    train = json.loads((TASK_TYPE_DIR / 'train.json').read_text(encoding='utf-8'))
    evaluation = load_c_evaluation()
    extraction = json.loads((TASK_TYPE_DIR / 'extraction.json').read_text(encoding='utf-8'))
    texts = [row['text'] for row in train + evaluation]
    for case in extraction:
        texts.extend(item['content'] for item in case.get('expected', []))
        texts.extend(item['evidence'] for item in case.get('expected', []) if item.get('evidence'))
    return texts


def doc_example_sentences():
    """task-types.md 경계표의 "예시" 따옴표 문장. 지시문에 들어가므로 평가 문장과 겹치면 안 된다."""
    text = TASK_TYPES_DOC.read_text(encoding='utf-8')
    return re.findall(r'"([^"]+)"', text)


def _bigrams(text):
    compact = re.sub(r'\s+', '', text)
    return {compact[i:i + 2] for i in range(len(compact) - 1)}


def similarity(left, right):
    """공백을 뺀 글자 2-gram Jaccard. 표현만 조금 바꾼 거의 같은 문장을 잡는다."""
    a, b = _bigrams(left), _bigrams(right)
    return len(a & b) / len(a | b) if a and b else 0.0


def overlaps(texts, references, threshold=0.6):
    """threshold 이상 비슷한 (평가 문장, 참고 문장, 유사도) 목록."""
    found = []
    for text in texts:
        for reference in references:
            score = similarity(text, reference)
            if score >= threshold:
                found.append((text, reference, round(score, 3)))
    return found
