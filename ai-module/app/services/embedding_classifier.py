"""Open-source embedding based mail/action classifier.

This module is optional. If sentence-transformers or model weights are not
available, callers receive None and the rule-based analyzer remains active.
"""
from dataclasses import dataclass
from functools import lru_cache
from typing import Optional

from app.config import (
    EMBEDDING_ACTION_THRESHOLD,
    EMBEDDING_BUSINESS_THRESHOLD,
    EMBEDDING_CLASSIFIER_ENABLED,
    EMBEDDING_MODEL_NAME,
)


@dataclass(frozen=True)
class EmbeddingClassification:
    business_type: Optional[str]
    business_score: float
    action_type: Optional[str]
    action_score: float
    reason: str


BUSINESS_LABEL_EXAMPLES = {
    "SALES_ACTIVITY": [
        "고객사가 제품 도입을 문의하는 영업 메일",
        "미팅 일정 조율과 제품 소개 요청",
        "견적서, 제안서, 서비스 도입, 계약 관련 업무 메일",
        "고객과 후속 미팅이나 상담을 진행하는 메일",
        "CRM, Sales Solution, 플랫폼, 자동화 서비스 도입 검토",
    ],
    "NON_BUSINESS": [
        "소셜 미디어 알림 메일",
        "광고, 뉴스레터, 추천 콘텐츠, 자동 발송 알림",
        "개인적인 알림이나 업무와 관련 없는 메일",
        "구독 안내, 프로모션, 이벤트 홍보 메일",
    ],
}

ACTION_LABEL_EXAMPLES = {
    "CREATE": [
        "새 미팅 일정을 등록해 달라는 요청",
        "제품 소개 미팅을 진행하고 싶다는 메일",
        "상담이나 회의를 잡아 달라는 요청",
        "날짜와 시간을 제안하며 미팅 가능 여부를 묻는 메일",
        "일정 등록 부탁드립니다",
    ],
    "UPDATE": [
        "기존 미팅 시간을 변경해 달라는 요청",
        "잡아둔 일정을 다른 시간으로 조정하는 메일",
        "예정된 회의를 연기하거나 앞당기는 요청",
        "기존 일정 대신 같은 날 다른 시간으로 바꾸자는 메일",
        "일정 변경 요청",
    ],
    "CANCEL": [
        "예정된 미팅을 취소해 달라는 요청",
        "내부 사정으로 회의를 진행하지 않겠다는 메일",
        "일정 취소 요청",
        "이번 미팅은 진행하지 않겠습니다",
    ],
    "CONFIRM": [
        "제안한 일정으로 확정하겠다는 메일",
        "예정대로 진행하겠다는 일정 확인 메일",
        "참석 가능하며 그 시간에 진행하겠다는 회신",
    ],
    "UNKNOWN": [
        "자료나 견적서를 전달하는 일반 영업 메일",
        "일정 의도가 명확하지 않은 문의 메일",
        "검토 후 필요하면 연락하겠다는 메일",
        "제품 자료 요청이지만 미팅 일정은 아직 정하지 않은 메일",
    ],
}


def classify_with_embeddings(text: str) -> Optional[EmbeddingClassification]:
    if not EMBEDDING_CLASSIFIER_ENABLED:
        return None

    try:
        model = get_embedding_model()
        business_type, business_score = classify_label(model, text, BUSINESS_LABEL_EXAMPLES)
        action_type, action_score = classify_label(model, text, ACTION_LABEL_EXAMPLES)
    except Exception as exception:
        print(f"[EMBEDDING_CLASSIFIER] skipped. reason={exception}")
        return None

    if business_score < EMBEDDING_BUSINESS_THRESHOLD:
        business_type = None
    if action_score < EMBEDDING_ACTION_THRESHOLD:
        action_type = None

    return EmbeddingClassification(
        business_type=business_type,
        business_score=business_score,
        action_type=action_type,
        action_score=action_score,
        reason=(
            f"BGE-M3 유사도 기반 분류: "
            f"business={business_type or 'fallback'}({business_score:.2f}), "
            f"action={action_type or 'fallback'}({action_score:.2f})"
        ),
    )


@lru_cache(maxsize=1)
def get_embedding_model():
    from sentence_transformers import SentenceTransformer

    print(f"[EMBEDDING_CLASSIFIER] loading model={EMBEDDING_MODEL_NAME}")
    return SentenceTransformer(EMBEDDING_MODEL_NAME)


def classify_label(model, text: str, label_examples: dict[str, list[str]]) -> tuple[str, float]:
    labels = list(label_examples.keys())
    prototypes = [
        " ".join(label_examples[label])
        for label in labels
    ]
    vectors = model.encode([text, *prototypes], normalize_embeddings=True)
    text_vector = vectors[0]
    prototype_vectors = vectors[1:]
    scores = prototype_vectors @ text_vector
    best_index = int(scores.argmax())
    return labels[best_index], float(scores[best_index])
