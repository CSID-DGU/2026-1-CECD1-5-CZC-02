"""비교용 표기 정리. 원문·인물 대장을 수정하거나 식별 결정을 내리지 않는다."""

import re
import unicodedata
from copy import deepcopy


# 공백으로 구분된 직함만 제거한다. 이름 일부를 직함으로 추측하지 않는다.
_TITLES = frozenset(("사원", "주임", "대리", "과장", "차장", "부장", "팀장", "실장", "이사", "상무", "전무", "대표"))


def normalize_text(value: str | None) -> str | None:
    """한글 조합(NFC)·공백만 정리하며, 빈 문자열은 정보 없음으로 둔다."""
    if value is None:
        return None
    if not isinstance(value, str):
        raise TypeError("문자열 또는 None이 필요합니다")
    return " ".join(unicodedata.normalize("NFC", value).split()) or None


def normalize_name(value: str | None) -> str | None:
    """경계의 직함·호칭과 영문 대소문자를 정리한다. 영문 이름을 생성하지 않는다."""
    name = normalize_text(value)
    if name is None:
        return None
    tokens = name.split()
    def is_title(token):
        return token in _TITLES or (token.endswith("님") and token[:-1] in _TITLES) or token == "님"
    while tokens and is_title(tokens[0]):
        tokens.pop(0)
    while tokens and is_title(tokens[-1]):
        tokens.pop()
    if not tokens:
        return None
    # 붙여 쓴 '님'만 제거. 붙여 쓴 직함은 경계가 불분명하므로 유지한다.
    if tokens[-1].endswith("님") and len(tokens[-1]) > 1:
        tokens[-1] = tokens[-1][:-1]
    return " ".join(tokens).lower()


def normalize_company(value: str | None) -> str | None:
    """공백·유니코드만 정리. 회사명 약칭·기호·대소문자는 임의 통합하지 않는다."""
    return normalize_text(value)


def normalize_email(value: str | None) -> str | None:
    """도메인만 소문자로. 계정의 대소문자·점·+태그는 보존한다."""
    if value is None:
        return None
    if not isinstance(value, str):
        raise TypeError("문자열 또는 None이 필요합니다")
    email = value.strip()
    if not email or email.count("@") != 1 or any(c.isspace() for c in email):
        return None
    local, domain = email.split("@")
    return f"{local}@{domain.lower()}" if local and domain else None


def normalize_phone(value: str | None) -> str | None:
    """구분자만 제거한다. 국가·내선·누락 숫자를 추측하지 않는다."""
    if value is None:
        return None
    if not isinstance(value, str):
        raise TypeError("문자열 또는 None이 필요합니다")
    phone = re.sub(r"[\s().-]", "", value)
    # 내선·마스킹·문자 포함 입력은 부분 번호만 추출하지 않고 비교에서 제외한다.
    return phone if re.fullmatch(r"\+?[0-9]+", phone) else None


def normalize_mention(mention: dict) -> dict:
    """계약 mention의 복사본을 비교용으로 정리한다. 외부 응답에는 원문을 쓴다.

    생략된 선택 필드는 추가하지 않는다. 채널·sourceRef는 원문 그대로 보존한다.
    반환값의 None끼리 같아도 동일 인물의 근거가 되지 않는다.
    """
    result = deepcopy(mention)
    for field, normalizer in (
        ("name", normalize_name), ("company", normalize_company),
        ("title", normalize_text), ("email", normalize_email), ("phone", normalize_phone),
    ):
        if field in result:
            result[field] = normalizer(result[field])
    return result
