"""원 학습 생성기의 인물/오타 생성 함수를 재사용하지 않는 독립 합성 스트레스 자료."""
import random
import string
from copy import deepcopy

from app.identity.normalization import normalize_name

SYLLABLES = ("루", "모", "티", "누", "보", "페", "라", "제", "푸", "네", "리", "토", "바", "코", "미", "포", "도", "테", "비", "파")


def make_stress(seed=77123, heavy=False, roster_size=80):
    if roster_size < 80:
        raise ValueError("기본 인물 80명을 포함해야 합니다")
    rng = random.Random(seed)
    people = []

    def fake_name():
        marker = rng.choice(("가상", "가상표본", "가상대상"))
        return marker + "_" + "".join(rng.choice(SYLLABLES) for _ in range(rng.randint(3, 8)))

    def replace_one(name):
        marker, value = name.split("_", 1)
        index = rng.randrange(len(value))
        replacement = rng.choice([v for v in SYLLABLES if v != value[index]])
        return marker + "_" + value[:index] + replacement + value[index + 1:]

    def person(index, name, company, previous):
        alias = "Synthetic Record " + "".join(rng.choice(string.ascii_lowercase) for _ in range(9))
        return {"personId": f"p_audit_{seed}_{index}", "name": name, "nameVariants": [alias],
                "company": company, "department": None, "title": "과장", "emails": [f"audit-{seed}-{index}@audit.example"],
                "phones": [f"000-{seed}-{index:04}"], "channels": ["MEETING", "EMAIL"], "isSynthetic": True,
                "companyHistory": [{"company": previous, "title": "대리", "until": "2025-12-31"}], "photoAvailable": False}

    for group in range(20):
        company = "가상회사_" + "".join(rng.choice(SYLLABLES) for _ in range(5))
        previous = "가상이전회사_" + "".join(rng.choice(SYLLABLES) for _ in range(5))
        other_company = "가상회사_" + "".join(rng.choice(SYLLABLES) for _ in range(5))
        name = fake_name()
        first = person(len(people), name, company, previous)
        second = person(len(people)+1, name, company if group % 2 == 0 else other_company, previous)
        second["nameVariants"] = list(first["nameVariants"])
        people += [first, second, person(len(people)+2, replace_one(name), company, previous),
                   person(len(people)+3, fake_name(), company, previous)]
    base = deepcopy(people)
    queries = []

    def add(mention, group, case, targets, kind=None):
        targets = sorted(set(targets))
        kind = kind or ("KNOWN" if len(targets) == 1 else "AMBIGUOUS")
        queries.append({"queryId": f"audit_{group}_{case}", "cluster": f"audit_group_{group}", "caseType": case,
                        "mention": mention, "expected": {"kind": kind, "targetPersonIds": targets}})

    for group in range(20):
        anchor, homonym, _, other = base[group*4:group*4+4]
        default = {"name": anchor["name"], "company": anchor["company"], "title": "과장", "email": None,
                   "phone": None, "channel": "MEETING"}
        for case in ("contact", "name_only", "alias", "noisy_name"):
            mention = deepcopy(default)
            if case == "contact":
                mention.update(email=anchor["emails"][0], phone=anchor["phones"][0])
                targets = [anchor["personId"]]
            else:
                if case == "name_only":
                    mention["company"] = None
                elif case == "alias":
                    mention["name"] = anchor["nameVariants"][0].upper()
                elif case == "noisy_name":
                    mention["name"] = replace_one(anchor["name"])
                    if heavy:
                        prefix, value = mention["name"].split("_", 1)
                        # 순서 변경 + 일부 글자 소실 + 소속 누락. 정보 파괴를 과적합 증거로 단정하지 않는다.
                        value = value[1:] + value[:1]
                        mention["name"] = prefix + "_" + value[:max(2, len(value)-2)]
                        mention["company"] = None
                    elif group % 3 == 0:
                        mention["company"] = None
                possible = [p for p in base if p["name"] == anchor["name"] or normalize_name(mention["name"]) in
                            {normalize_name(p["name"]), *(normalize_name(v) for v in p["nameVariants"])}]
                targets = [p["personId"] for p in possible if mention["company"] is None or
                           mention["company"] in [p["company"], *(h["company"] for h in p["companyHistory"])]]
                if anchor["personId"] not in targets:
                    targets.append(anchor["personId"])
            add(mention, group, case, targets)
        conflict = deepcopy(default)
        conflict.update(company=None, email=other["emails"][0])
        add(conflict, group, "name_contact_conflict", [anchor["personId"], homonym["personId"], other["personId"]], "CONFLICT")
        new = deepcopy(default)
        while True:
            new["name"] = fake_name()
            if all(new["name"] != p["name"] for p in base):
                break
        add(new, group, "new_name_only", [], "NEW")
    add({"name": None, "company": None, "title": "부장", "email": None, "phone": None, "channel": "MEETING"},
        0, "insufficient", [], "INSUFFICIENT")

    # 같은 시험 질문을 유지한 채 무관한 대장 인물만 추가한다. 정답 집합을 바꾸지 않는 확장이다.
    names = {p["name"] for p in people}
    while len(people) < roster_size:
        name = fake_name()
        if name in names:
            continue
        names.add(name)
        people.append(person(len(people), name, "가상회사_" + str(len(people)), "가상이전회사_" + str(len(people))))
    for query in queries:
        query["mention"]["sourceRef"] = "src_" + query["queryId"]
    return {"persons": people, "queries": queries, "manifest": {"seed": seed, "people": len(people),
            "queries": len(queries), "heavyNoise": heavy, "independentGenerator": True,
            "labelSource": "synthetic_source_and_possible_identity_sets_not_human_reviewed"}}
