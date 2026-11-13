"""검수 대기 없이 재현하는 합성 시나리오. 생성 출처/가능한 집합이 정답이며 모델 출력을 읽지 않는다."""
import hashlib
import json
import random
import string
from copy import deepcopy

from app.identity.normalization import normalize_name


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode("utf-8")).hexdigest()


def token(rng, size=None):
    return "".join(rng.choice("abcdefghjkmnpqrstuvwxyz") for _ in range(size or rng.choice((6, 8, 11, 15))))


def mutate(text, rng, operation, count=1):
    """가짜 표시는 보존하고 가변 토큰에 삽입/삭제/교체/전치를 적용한다."""
    prefix, value = text.split("_", 1)
    for _ in range(count):
        pos = rng.randrange(len(value) - 1)
        if operation == "delete":
            value = value[:pos] + value[pos + 1:]
        elif operation == "insert":
            value = value[:pos] + rng.choice(string.ascii_lowercase) + value[pos:]
        elif operation == "transpose":
            value = value[:pos] + value[pos + 1] + value[pos] + value[pos + 2:]
        else:
            replacement = rng.choice([c for c in string.ascii_lowercase if c != value[pos]])
            value = value[:pos] + replacement + value[pos + 1:]
    return prefix + "_" + value


def make_partition(name, seed, count):
    if count < 8 or count % 4:
        raise ValueError("분할 인물 수는 8 이상인 4의 배수여야 합니다")
    rng = random.Random(seed)
    people = []
    for group in range(count // 4):
        company = "가상회사_" + token(rng, 9)
        previous = "가상이전회사_" + token(rng, 9)
        other_company = "가상회사_" + token(rng, 9)
        base = "가상인물_" + token(rng)
        near = mutate(base, rng, "replace")
        for offset in range(4):
            index = len(people)
            person_name = base if offset in (0, 2) else near if offset == 1 else "가상인물_" + token(rng)
            current = other_company if offset == 2 and group % 4 != 2 else company
            old = other_company if offset == 0 and group % 4 == 3 else previous
            # 동명이인은 등록 영문 별칭도 같게 만든다. 그룹 전체를 한 분할에서 생성한다.
            alias_token = base.split("_", 1)[1] if offset in (0, 2) else person_name.split("_", 1)[1]
            people.append({"personId": f"p_controlled_{name}_{index}", "name": person_name,
                           "nameVariants": [f"Synthetic Person {alias_token}"], "company": current,
                           "department": None, "title": "과장", "emails": [f"{name}-{seed}-{index}@synthetic.example"],
                           "phones": [f"000-{seed:05}-{index:04}"], "channels": ["MEETING", "EMAIL"],
                           "companyHistory": [{"company": old, "title": "대리", "until": "2025-12-31"}],
                           "isSynthetic": True, "photoAvailable": False})
        if group % 4 == 0:
            people[-1]["emails"] = list(people[-3]["emails"])
            people[-1]["phones"] = list(people[-3]["phones"])

    queries = []

    def add(mention, case, targets, group, kind=None):
        targets = sorted(set(targets))
        kind = kind or ("KNOWN" if len(targets) == 1 else "AMBIGUOUS")
        queries.append({"queryId": f"q_{name}_{len(queries)}", "cluster": f"{name}_group_{group}", "caseType": case,
                        "mention": mention, "expected": {"kind": kind, "targetPersonIds": targets}})

    for index, person in enumerate(people):
        group = index // 4
        default = {"name": person["name"], "company": person["company"], "title": person["title"],
                   "email": None, "phone": None, "channel": "MEETING"}
        for case in ("contact_exact", "honorific", "alias", "typo_replace", "typo_delete", "typo_insert", "typo_transpose", "missing_company", "previous_company"):
            mention = deepcopy(default)
            if case == "contact_exact":
                mention["email"] = person["emails"][0].replace("synthetic.example", "SYNTHETIC.EXAMPLE")
                mention["phone"] = person["phones"][0].replace("-", " ")
            elif case == "honorific":
                mention["name"] = "  " + person["name"] + "   과장님  "
            elif case == "alias":
                mention["name"] = person["nameVariants"][0].upper()
            elif case.startswith("typo_"):
                operation = case.split("_")[1]
                mention["name"] = mutate(person["name"], rng, operation, count=rng.choice((1, 1, 2)))
                if rng.random() < 0.4:
                    mention["company"] = None
                # 다른 대장 표기와 정확히 겹친 오타는 별도 애매함으로 보수적으로 처리한다.
            elif case == "missing_company":
                mention["company"] = None
            elif case == "previous_company":
                mention["company"] = person["companyHistory"][0]["company"]
            if case == "contact_exact":
                targets = [person["personId"]]
            else:
                # 같은 원래 이름 그룹에서 관측 소속과 양립하는 인물은 모두 가능하다.
                possible = [p for p in people if p["name"] == person["name"] or normalize_name(mention["name"]) in
                            {normalize_name(p["name"]), *(normalize_name(v) for v in p["nameVariants"])}]
                targets = [p["personId"] for p in possible if mention["company"] is None or
                           mention["company"] in [p["company"], *(h["company"] for h in p["companyHistory"])]]
                # 생성된 오타는 소속이 누락돼도 원래 이름 그룹 안에서만 관측한 것으로 가정한다.
                if person["personId"] not in targets:
                    targets.append(person["personId"])
            add(mention, case, targets, group)

        contact = deepcopy(default)
        contact.update(name=None, company=None, title=None, email=person["emails"][0])
        ids = [p["personId"] for p in people if contact["email"] in p["emails"]]
        add(contact, "email_only", ids, group)
        if index % 4 == 0:
            other = people[index + 3]
            conflict = deepcopy(default)
            conflict.update(name=None, company=None, title=None, email=person["emails"][0], phone=other["phones"][0])
            related = [p["personId"] for p in people if conflict["email"] in p["emails"] or conflict["phone"] in p["phones"]]
            add(conflict, "contact_conflict", related, group, "CONFLICT")
            conflict_name = deepcopy(default)
            conflict_name.update(name=other["name"], company=None, email=person["emails"][0])
            add(conflict_name, "name_contact_conflict", [person["personId"], other["personId"]], group, "CONFLICT")
            missing = {"name": None, "company": None, "title": "부장", "email": None, "phone": None, "channel": "MEETING"}
            add(missing, "insufficient", [], group, "INSUFFICIENT")
            new = deepcopy(default)
            new.update(name="가상인물_" + token(rng), email=f"new-{name}-{group}@synthetic.example", phone=None)
            add(new, "new_with_contacts", [], group, "NEW")
            new_text = deepcopy(new)
            new_text["email"] = None
            add(new_text, "new_name_only", [], group, "NEW")

    # 같은 관측을 반복해 점수/가중치를 늘리지 않는다. 가능한 정답 집합은 보수적으로 합친다.
    before_dedup = len(queries)
    unique = {}
    for query in queries:
        key = json.dumps({k: query["mention"].get(k) for k in ("name", "company", "email", "phone")}, sort_keys=True)
        old = unique.get(key)
        if old is None:
            unique[key] = query
        else:
            if old["expected"]["kind"] in {"KNOWN", "AMBIGUOUS"} and query["expected"]["kind"] in {"KNOWN", "AMBIGUOUS"}:
                ids = sorted(set(old["expected"]["targetPersonIds"]) | set(query["expected"]["targetPersonIds"]))
                old["expected"] = {"kind": "KNOWN" if len(ids) == 1 else "AMBIGUOUS", "targetPersonIds": ids}
            elif old["expected"] != query["expected"]:
                raise ValueError("동일 관측의 정답이 충돌합니다")
    queries = list(unique.values())
    for query in queries:
        query["mention"]["sourceRef"] = "src_" + query["queryId"]
    return {"persons": people, "queries": queries, "manifest": {"name": name, "seed": seed,
            "persons": len(people), "queriesBeforeDedup": before_dedup, "queries": len(queries),
            "labelSource": "automatic_synthetic_scenarios_not_human_reviewed"}}


def make_corpus(protocol):
    corpus = {name: make_partition(name, seed, protocol["partitionPeople"][name])
              for name, seed in protocol["partitionSeeds"].items()}
    seen = set()
    for data in corpus.values():
        values = {value for p in data["persons"] for value in [p["personId"], normalize_name(p["name"]),
                  *(normalize_name(v) for v in p["nameVariants"]), *p["emails"], *p["phones"]]}
        if seen & values:
            raise ValueError("분할 사이에 인물 표기/연락처가 겹칩니다")
        seen.update(values)
    return corpus
