"""실험용 임베딩 분류기 로딩. 운영 추출기는 계속 RULE만 사용한다."""
from pathlib import Path


class EmbeddingClassifier:
    def __init__(self, model_path: str, weights_path: Path):
        import numpy as np
        from sentence_transformers import SentenceTransformer
        self.encoder = SentenceTransformer(model_path, local_files_only=True)
        with np.load(weights_path, allow_pickle=False) as artifact:
            self.weights = artifact['weights'].copy()
            self.bias = artifact['bias'].copy()
            self.classes = artifact['classes'].copy()
        if len(self.classes) != 11 or self.weights.shape[0] != 11:
            raise ValueError('ML-2는 11부류 아티팩트가 필요합니다.')

    def predict(self, texts: list[str]) -> list[tuple[str, float]]:
        import numpy as np
        if not texts:
            return []
        vectors = self.encoder.encode(texts, normalize_embeddings=True)
        scores = vectors @ self.weights.T + self.bias
        exponentials = np.exp(scores - scores.max(axis=1, keepdims=True))
        probabilities = exponentials / exponentials.sum(axis=1, keepdims=True)
        winners = probabilities.argmax(axis=1)
        return [(str(self.classes[index]), float(probabilities[row, index])) for row, index in enumerate(winners)]
