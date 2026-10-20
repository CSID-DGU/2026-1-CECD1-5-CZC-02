"""ML-2 비교용 규칙 기준 모델. 점수는 확률이 아닌 규칙 일치 신뢰도다."""

import re


RULES = {
    "MEETING_PREP": r"미팅|면담|재방문|뵙|만나|회의 일정",
    "VISIT_PREP": r"출장|현장\s*(?:방문|점검)|지역 이동",
    "CONTRACT_PREP": r"계약서|계약\s*(?:체결|초안|작성|송부|대조)",
    "RENEWAL_INTERVIEW": r"계약\s*(?:만료|종료).*면담|만료\s*대응\s*면담",
    "PROPOSAL_SUBMISSION": r"제안서.*(?:제출|송부)|(?:후보|인력).*(?:리스트|프로필).*(?:제출|전달|송부)",
    "REJECTION_NOTICE": r"(?:미채택|탈락|불합격).*(?:통보|안내|알림)",
    "DOC_COLLECTION": r"(?:서류|계약서).*(?:회수|취합|독촉|회신\s*없)|미회수",
    "END_EVALUATION": r"(?:투입|프로젝트|계약)\s*종료.*평가|종료\s*평가",
    "PROFITABILITY_REVIEW": r"수익성|마진\s*(?:검토|계산|확인)",
    "SOURCING_START": r"소싱|(?:신규\s*)?(?:인력|후보).*(?:탐색|물색|모집)",
}


def classify_task(text: str) -> tuple[str, float]:
    matches = {kind for kind, pattern in RULES.items() if re.search(pattern, text)}
    # 문서에 명시된 경계: 출장 > 미팅, 서류 회수 > 계약서 작성.
    if "VISIT_PREP" in matches:
        matches.discard("MEETING_PREP")
    if "DOC_COLLECTION" in matches:
        matches.discard("CONTRACT_PREP")
    if "RENEWAL_INTERVIEW" in matches:
        matches.discard("MEETING_PREP")
    return (matches.pop(), 1.0) if len(matches) == 1 else ("NONE", 0.0)
