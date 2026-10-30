"""교차 검수된 애매한 쌍의 실험 정답. 생성 출처 ID를 관측상 단일 정답으로 쓰지 않는다."""

from app.identity.normalization import normalize_company, normalize_email, normalize_name, normalize_phone, normalize_text


def ambiguous_expectation(pair, persons):
    """검수 대상 두 유형만 재검사한다. 이름·연락처·소속/이력에 맞는 사람들을 기록한다.

    직함은 소속이 있을 때 해당 소속의 이력과 함께만 검사한다.
    소속 없는 이름의 직함만으로 동명이인을 제거하지 않는다.
    """
    case, mention = pair["caseType"], pair["mentionB"]
    if case not in ("same_name", "missing_company"):
        return None
    name = normalize_name(mention.get("name"))
    company = normalize_company(mention.get("company"))
    title = normalize_text(mention.get("title"))
    if name is None or (case == "same_name" and (company is None or title is None)):
        return None
    if case == "missing_company" and company is not None:
        return None
    targets = []
    for person in persons:
        if name not in {normalize_name(v) for v in [person["name"], *person.get("nameVariants", [])]}:
            continue
        contact_conflict = False
        for field, normalizer in (("email", normalize_email), ("phone", normalize_phone)):
            contact = normalizer(mention.get(field))
            if contact is not None and contact not in {normalizer(v) for v in person.get(field + "s", [])}:
                contact_conflict = True
        if contact_conflict:
            continue
        if company is not None:
            affiliations = [{"company": person.get("company"), "title": person.get("title")}, *person.get("companyHistory", [])]
            if not any(normalize_company(h["company"]) == company
                       and (title is None or normalize_text(h.get("title")) == title) for h in affiliations):
                continue
        targets.append(person["personId"])
    if len(targets) < 2:
        return None
    return {"kind": "AMBIGUOUS", "targetPersonIds": sorted(targets),
            "relatedPersonIds": sorted({pair["personIdA"], pair["personIdB"]}),
            "reason": "HOMONYM_WITH_HISTORY_OVERLAP" if case == "same_name" else "HOMONYM_WITHOUT_COMPANY"}


def check_ambiguous_response(pair, response):
    """향후 API의 응답 채점. 후보와 이유가 있어야 하며 단일 확정/신규는 오답이다.

    전체 계약 검증은 API 테스트에서 별도로 한다. 이 함수는 실험의 의미상 정답만 검사한다.
    정답이 3명보다 많으면 3명의 가능한 후보와 추가 검색 안내를 확인한다.
    """
    expected = pair.get("expected", {})
    if pair.get("label") is not None or expected.get("kind") != "AMBIGUOUS":
        raise ValueError("AMBIGUOUS 쌍만 채점할 수 있습니다")
    targets = set(expected["targetPersonIds"])
    candidates = response.get("candidates", [])
    ids = [item.get("personId") for item in candidates]
    valid = (response.get("decision") == "CANDIDATES" and response.get("personId") is None
             and 1 <= len(ids) <= 3 and len(ids) == len(set(ids)) and set(ids) <= targets
             and len(ids) == min(3, len(targets))
             and all(isinstance(item.get("reasons"), list) and item["reasons"]
                     and all(isinstance(reason, str) and reason.strip() for reason in item["reasons"]) for item in candidates))
    if valid and len(targets) > 3:
        valid = any("검색" in reason for item in candidates for reason in item["reasons"])
    return bool(valid)
