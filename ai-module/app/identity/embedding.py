"""선택적인 로컬 임베딩 실험 부품. 기본 모델·네트워크 호출은 없다."""

import math
from dataclasses import replace
from pathlib import Path

from app.identity.candidates import ReviewCandidate
from app.identity.normalization import normalize_company, normalize_name
from app.identity.ranking import RankedCandidate, rank_comparisons


def identity_text(name, company):
    name = normalize_name(name)
    if name is None:
        return None
    return f"이름: {name}\n회사: {normalize_company(company) or ''}"


def person_texts(person):
    names = [person["name"], *person.get("nameVariants", [])]
    companies = [person.get("company"), *(h["company"] for h in person.get("companyHistory", []))]
    return tuple(dict.fromkeys(text for name in names for company in companies
                               if (text := identity_text(name, company)) is not None))


def cosine(left, right):
    if len(left) != len(right) or not left:
        raise ValueError("임베딩 차원이 다릅니다")
    if not all(math.isfinite(v) for v in (*left, *right)):
        raise ValueError("임베딩에 유한수가 아닌 값이 있습니다")
    norm = math.sqrt(sum(v * v for v in left) * sum(v * v for v in right))
    if norm == 0:
        raise ValueError("0 벡터는 비교할 수 없습니다")
    return max(-1.0, min(1.0, sum(a * b for a, b in zip(left, right)) / norm))


class LocalEncoder:
    """명시한 로컬 경로만 로드한다. 의존성 import·모델 로딩은 객체 생성 시에만 한다."""

    def __init__(self, model_path, *, batch_size=8):
        path = Path(model_path).expanduser().resolve()
        if not path.is_dir():
            raise FileNotFoundError("로컬 모델 폴더가 없습니다")
        import torch
        from sentence_transformers import SentenceTransformer
        torch.set_num_threads(2)
        self.model = SentenceTransformer(str(path), device="cpu", local_files_only=True, trust_remote_code=False)
        self.model.max_seq_length = 64
        self.batch_size = batch_size
        self.vectors = {}

    def encode(self, texts):
        missing = sorted(set(texts) - self.vectors.keys())
        if missing:
            vectors = self.model.encode(missing, batch_size=self.batch_size, normalize_embeddings=True,
                                        convert_to_numpy=True, show_progress_bar=True)
            self.vectors.update(zip(missing, (vector.tolist() for vector in vectors)))
        return {text: self.vectors[text] for text in texts}


def rank_with_embeddings(comparisons, similarities, *, rule_threshold, weight, embedding_threshold=None):
    """연락처/정확 이름/소속 우선순위는 유지하고 마지막 순위만 혼합한다.

    임베딩 후보 추가도 회사가 일치해야 한다. 유사도는 확률이 아니며 경고는 보존한다.
    weight=0, embedding_threshold=None이면 기존 규칙과 동일하다.
    """
    if not math.isfinite(weight) or not 0 <= weight <= 1:
        raise ValueError("weight는 0~1입니다")
    if embedding_threshold is not None and (not math.isfinite(embedding_threshold) or not -1 <= embedding_threshold <= 1):
        raise ValueError("embedding_threshold는 -1~1입니다")
    if any(not math.isfinite(v) or not -1 <= v <= 1 for v in similarities.values()):
        raise ValueError("코사인 유사도는 -1~1입니다")
    baseline = {r.person_id: r for r in rank_comparisons(comparisons, fuzzy_threshold=rule_threshold)}
    for comparison in comparisons:
        similarity = similarities.get(comparison.person_id)
        if (comparison.person_id not in baseline and embedding_threshold is not None
                and comparison.company.status == "MATCH" and comparison.name_similarity is not None
                and similarity is not None and similarity >= embedding_threshold):
            priority = (0, 0, 1, comparison.name_similarity)
            baseline[comparison.person_id] = RankedCandidate(
                ReviewCandidate(comparison, ("embedding_similarity", "company")), priority)
    results = []
    for pid, candidate in baseline.items():
        similarity = similarities.get(pid)
        value = candidate.priority[-1]
        if similarity is not None:
            value = (1 - weight) * value + weight * similarity
        results.append(replace(candidate, priority=(*candidate.priority[:3], value)))
    return sorted(results, key=lambda r: (tuple(-v for v in r.priority), r.person_id))
