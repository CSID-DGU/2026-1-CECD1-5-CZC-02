"""ML-2 비교용 규칙 기준 모델. 점수는 확률이 아닌 규칙 일치 신뢰도다."""

import re


RULES = {
    'MEETING_PREP': r'재방문|뵙|고객.*(?:미팅|면담|만나|방문|회의|인터뷰|논의)',
    'VISIT_PREP': r'출장|현장\s*(?:방문|점검)|설비\s*점검|지역 이동',
    'CONTRACT_PREP': r'계약서|협약|계약\s*(?:체결|초안|작성|송부|대조)|계약\s*(?:내용|조건)(?:을|를)?\s*(?:확인|검토|정리)',
    'RENEWAL_INTERVIEW': r'재계약(?!서)|(?:계약.*(?:만료|종료|갱신|연장|재협상|재심의)|만료\s*계약).*(?:면담|인터뷰|대화|논의|협상|대응)|만료\s*대응\s*면담',
    'PROPOSAL_SUBMISSION': r'제안서.*(?:제출|송부|준비|작성|완성)|(?:후보|인력).*(?:리스트|프로필).*(?:제출|전달|송부)',
    'REJECTION_NOTICE': r'(?:미채택|탈락|불합격|미선택).*(?:통보|안내|알림|알리|결과.*(?:알려|전달)|사유.*(?:보내|전달))',
    'DOC_COLLECTION': r'(?:서류|문서|계약서|서명본).*(?:수집|회수|취합|독촉|반환|수령|회신\s*없)|미회수',
    'END_EVALUATION': r'(?:투입|프로젝트|계약).*?(?:종료|만료).*평가|종료\s*평가',
    'PROFITABILITY_REVIEW': r'수익성|수익률|마진율|마진(?:을|의|이)?\s*(?:검토|계산|확인|분석)',
    'SOURCING_START': r'(?:인력|인재|후보|포지션|채용|지원자|개발자|전문가).*(?:탐색|검색|물색|소싱|모집|발굴|찾)|(?:탐색|검색|물색|소싱|모집|발굴).*(?:인력|인재|후보|지원자)',
}


def classify_task(text: str) -> tuple[str, float]:
    matches = {kind for kind, pattern in RULES.items() if re.search(pattern, text)}
    # 인력 소싱만 S-10이다. 공급업체/자재 소싱을 사람 찾기로 해석하지 않는다.
    if re.search(r'공급업체|공급망|원자재|구매처|납품업체|부품', text):
        matches.discard('SOURCING_START')
    # 제출 사실 확인만으로 새 제안서 준비를 만들지 않는다.
    if re.search(r'제안서.*(?:제출|접수).*(?:확인|조회)', text) and not re.search(r'준비|작성|송부|재제출', text):
        matches.discard('PROPOSAL_SUBMISSION')
    # 명확한 내부 회의, 대상 불명의 시장 조사 미팅은 고객 미팅으로 확정하지 않는다.
    if re.search(r'내부|사내|팀\s*회의', text):
        matches.discard('MEETING_PREP')
    if re.search(r'매월|매달|월간|주간|영업\s*(?:활동|성과|실적)|업무\s*종료', text) and not re.search(r'투입|프로젝트|인력', text):
        matches.discard('END_EVALUATION')
    # 문서에 명시된 경계: 출장 > 미팅, 서류 회수 > 계약서 작성.
    if 'VISIT_PREP' in matches:
        matches.discard('MEETING_PREP')
    if 'DOC_COLLECTION' in matches:
        matches.discard('CONTRACT_PREP')
    if 'RENEWAL_INTERVIEW' in matches:
        matches.discard('MEETING_PREP')
    # 재계약서라는 문서와 재계약 면담을 구별한다.
    if 'CONTRACT_PREP' in matches and re.search(r'재계약서', text):
        matches.discard('RENEWAL_INTERVIEW')
    return (matches.pop(), 1.0) if len(matches) == 1 else ('NONE', 0.0)
