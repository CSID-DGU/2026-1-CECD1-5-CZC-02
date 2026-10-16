"""A 내부 미사용 검증 자료 생성. 모델·검색·평가 코드는 호출하지 않는다."""

import argparse
import hashlib
import json
import random
from collections import Counter
from pathlib import Path

from jsonschema import Draft202012Validator

from app.identity.normalization import normalize_name
from ml.identity.generate_dataset import CONTRACTS_DIR, source_text

DEFAULT_DATA = Path(__file__).resolve().parent / "data"
CASES = ("exact", "honorific", "english", "typo_substitution", "typo_deletion",
         "typo_insertion", "typo_transposition", "company_change", "missing_company",
         "same_company_homonyms", "shared_email", "shared_phone", "contact_conflict",
         "new_person", "insufficient")


def make_person(index, seed, rng, *, new=False):
    token = "".join(rng.sample("abcdefghjkmnpqrstuvwxyz", 10))
    prefix = "가상검증신규" if new else "가상검증인물"
    return {"personId": f"p_holdout_{seed}_{index:03}", "name": f"{prefix}_{seed}_{token}",
            "nameVariants": [f"Synthetic Holdout {seed} {token}"],
            "company": f"가상검증회사_{seed}_{index % 12:02}", "title": "과장", "department": None,
            "emails": [f"synthetic-{seed}-{index}@holdout.example"],
            "phones": [f"000-{seed:06}-{index:04}"], "channels": ["MEETING", "EMAIL"],
            "companyHistory": [{"company": f"가상검증이전회사_{seed}_{index % 7:02}", "title": "대리", "until": "2025-12-31"}],
            "photoAvailable": False, "isSynthetic": True}


def make_mention(person):
    return {"name": person["name"], "company": person["company"], "title": person["title"],
            "email": None, "phone": None, "channel": "MEETING", "sourceRef": None}


def build_holdout(seed=20261015):
    if not 0 <= seed <= 99999999:
        raise ValueError("seed는 0~99999999 범위입니다")
    rng = random.Random(seed)
    persons = [make_person(i, seed, rng) for i in range(120)]
    newcomers = [make_person(i, seed, rng, new=True) for i in range(120, 140)]
    for i in range(20):
        left, right = persons[2 * i:2 * i + 2]
        right["name"] = left["name"]
        right["nameVariants"] = list(left["nameVariants"])
        right["company"] = left["company"]
        left, right = persons[40 + 2 * i:42 + 2 * i]
        right["emails"] = list(left["emails"])
        right["phones"] = list(left["phones"])
    queries = []
    for case in CASES:
        for i in range(20):
            person = persons[80 + i]
            mention = make_mention(person)
            expected = {"kind": "KNOWN", "targetPersonIds": [person["personId"]], "relatedPersonIds": [person["personId"]]}
            if case == "exact":
                mention["email"], mention["phone"] = person["emails"][0], person["phones"][0]
            elif case == "honorific":
                mention["name"] += " 과장님"
            elif case == "english":
                mention["name"] = person["nameVariants"][0].upper()
            elif case.startswith("typo_"):
                # 가상 표기 접두어 대신 가변 토큰의 여러 위치에 오타를 만든다.
                name = person["name"]
                pos = len(name) - 10 + i % 8
                if case == "typo_substitution":
                    mention["name"] = name[:pos] + "o" + name[pos + 1:]
                elif case == "typo_deletion":
                    mention["name"] = name[:pos] + name[pos + 1:]
                elif case == "typo_insertion":
                    mention["name"] = name[:pos] + "o" + name[pos:]
                else:
                    mention["name"] = name[:pos] + name[pos + 1] + name[pos] + name[pos + 2:]
            elif case == "company_change":
                mention["company"], mention["title"] = person["companyHistory"][0]["company"], "대리"
            elif case == "missing_company":
                mention["company"] = None
            elif case in ("same_company_homonyms", "shared_email", "shared_phone"):
                offset = 0 if case == "same_company_homonyms" else 40
                left, right = persons[offset + 2 * i:offset + 2 * i + 2]
                mention = make_mention(left)
                if case != "same_company_homonyms":
                    mention["name"] = mention["company"] = mention["title"] = None
                    field = "email" if case == "shared_email" else "phone"
                    mention[field] = left[field + "s"][0]
                expected = {"kind": "AMBIGUOUS", "targetPersonIds": [left["personId"], right["personId"]],
                            "relatedPersonIds": [left["personId"], right["personId"]]}
            elif case == "contact_conflict":
                left, right = persons[80 + 2 * i:82 + 2 * i]
                mention = make_mention(left)
                mention["name"] = mention["company"] = mention["title"] = None
                mention["email"], mention["phone"] = left["emails"][0], right["phones"][0]
                expected = {"kind": "CONFLICT", "targetPersonIds": [], "relatedPersonIds": [left["personId"], right["personId"]]}
            elif case == "new_person":
                new = newcomers[i]
                mention = make_mention(new)
                mention["email"], mention["phone"] = new["emails"][0], new["phones"][0]
                expected = {"kind": "NEW", "targetPersonIds": [], "relatedPersonIds": [new["personId"]]}
            elif case == "insufficient":
                mention = {key: None for key in mention}
                mention["channel"] = "MEETING"
                expected = {"kind": "INSUFFICIENT", "targetPersonIds": [], "relatedPersonIds": []}
            query_id = f"holdout_{seed}_{case}_{i:03}"
            mention["sourceRef"] = f"src_{query_id}"
            queries.append({"queryId": query_id, "caseType": case, "mention": mention,
                            "expected": expected, "evidence": source_text(mention)})
    rng.shuffle(queries)
    return {"persons": persons, "newcomers": newcomers, "queries": queries,
            "manifest": {"seed": seed, "purpose": "synthetic_fresh_holdout", "modelEvaluationPerformed": False,
                         "counts": {"persons": 120, "newcomers": 20, "queries": 300},
                         "caseCounts": dict(Counter(q["caseType"] for q in queries))}}


def identity_values(persons):
    return {
        "ids": {p["personId"] for p in persons},
        "names": {normalize_name(n) for p in persons for n in [p["name"], *p.get("nameVariants", [])]},
        "emails": {e for p in persons for e in p.get("emails", [])},
        "phones": {v for p in persons for v in p.get("phones", [])},
    }


def validate_holdout(dataset, baseline_persons):
    person_validator = Draft202012Validator(json.loads((CONTRACTS_DIR / "person.schema.json").read_text()))
    mention_validator = Draft202012Validator(json.loads((CONTRACTS_DIR / "identity_resolution.schema.json").read_text())["properties"]["mention"])
    persons, newcomers, queries = dataset["persons"], dataset["newcomers"], dataset["queries"]
    if (len(persons), len(newcomers), len(queries)) != (120, 20, 300):
        raise ValueError("검증 데이터 개수가 올바르지 않습니다")
    all_persons = persons + newcomers
    for p in all_persons:
        person_validator.validate(p)
        if p["isSynthetic"] is not True or not p["name"].startswith("가상검증") or not p["company"].startswith("가상검증"):
            raise ValueError("명백한 가짜 인물·회사만 사용합니다")
        if not all(e.endswith("@holdout.example") for e in p["emails"]) or not all(v.startswith("000-") for v in p["phones"]):
            raise ValueError("가짜 연락처만 사용합니다")
    current, baseline = identity_values(all_persons), identity_values(baseline_persons)
    if len(current["ids"]) != 140 or any(current[key] & baseline[key] for key in current):
        raise ValueError("기존 데이터와 식별 정보가 겹칩니다")
    roster, new_ids = identity_values(persons), identity_values(newcomers)
    if any(roster[key] & new_ids[key] for key in roster):
        raise ValueError("신규 인물은 검색 대장과 겹치면 안 됩니다")
    by_id = {p["personId"]: p for p in all_persons}
    if Counter(q["caseType"] for q in queries) != Counter({case: 20 for case in CASES}):
        raise ValueError("검사 유형 분포가 올바르지 않습니다")
    if len({q["queryId"] for q in queries}) != 300 or len({q["mention"]["sourceRef"] for q in queries}) != 300:
        raise ValueError("검증 ID가 중복됩니다")
    for q in queries:
        mention_validator.validate(q["mention"])
        if q["evidence"] != source_text(q["mention"]):
            raise ValueError("원문 근거가 언급과 다릅니다")
        expected, m, case = q["expected"], q["mention"], q["caseType"]
        targets, related = set(expected["targetPersonIds"]), expected["relatedPersonIds"]
        if not targets <= roster["ids"] or not set(related) <= by_id.keys():
            raise ValueError("정답 인물 참조가 올바르지 않습니다")
        kind = "AMBIGUOUS" if case in ("same_company_homonyms", "shared_email", "shared_phone") else "CONFLICT" if case == "contact_conflict" else "NEW" if case == "new_person" else "INSUFFICIENT" if case == "insufficient" else "KNOWN"
        if expected["kind"] != kind:
            raise ValueError("유형별 정답 종류가 다릅니다")
        sources = [by_id[pid] for pid in related]
        for field in ("email", "phone"):
            if m[field] is not None and not any(m[field] in p[field + "s"] for p in sources):
                raise ValueError("연락처의 출처가 올바르지 않습니다")
        if kind == "KNOWN":
            if len(sources) != 1 or targets != set(related):
                raise ValueError("기존 인물 정답이 올바르지 않습니다")
            p = sources[0]
            if case.startswith("typo_"):
                if m["name"] == p["name"] or not m["name"].startswith("가상검증인물_"):
                    raise ValueError("오타가 실제로 존재해야 합니다")
            elif normalize_name(m["name"]) not in {normalize_name(n) for n in [p["name"], *p["nameVariants"]]}:
                raise ValueError("이름의 출처가 올바르지 않습니다")
            if case == "missing_company" and m["company"] is not None:
                raise ValueError("소속 누락 사례에 소속이 있습니다")
            if case == "company_change" and m["company"] not in [h["company"] for h in p["companyHistory"]]:
                raise ValueError("소속 이동 사례가 이전 소속을 가리키지 않습니다")
            allowed_companies = [None, p["company"], *(h["company"] for h in p["companyHistory"])]
            if m["company"] not in allowed_companies:
                raise ValueError("소속의 출처가 올바르지 않습니다")
        elif kind == "AMBIGUOUS":
            if len(sources) != 2 or targets != set(related):
                raise ValueError("모호한 정답 집합이 올바르지 않습니다")
            if case == "same_company_homonyms":
                if not (sources[0]["name"] == sources[1]["name"] == m["name"] and sources[0]["company"] == sources[1]["company"] == m["company"]):
                    raise ValueError("같은 회사 동명이인이 아닙니다")
            else:
                field = "email" if case == "shared_email" else "phone"
                if any(m[field] not in p[field + "s"] for p in sources) or m["name"] is not None:
                    raise ValueError("공유 연락처 사례가 아닙니다")
        elif kind == "CONFLICT":
            if targets or len(sources) != 2 or sources[0]["personId"] == sources[1]["personId"] or m["email"] not in sources[0]["emails"] or m["phone"] not in sources[1]["phones"]:
                raise ValueError("연락처 충돌 정답이 올바르지 않습니다")
        elif kind == "NEW":
            if targets or len(sources) != 1 or related[0] not in new_ids["ids"] or m["name"] != sources[0]["name"]:
                raise ValueError("신규 인물 정답이 올바르지 않습니다")
        elif targets or related or any(m[key] is not None for key in ("name", "company", "title", "email", "phone")):
            raise ValueError("정보 부족 정답에 추측 값이 있습니다")


def write_holdout(dataset, baseline_path, output_dir):
    baseline_path, output_dir = Path(baseline_path), Path(output_dir)
    if output_dir.resolve() == baseline_path.parent.resolve():
        raise ValueError("기존 데이터 폴더에 덮어쓸 수 없습니다")
    baseline = json.loads(baseline_path.read_text())
    validate_holdout(dataset, baseline)
    # 최초 생성만 허용한다. 검증 자료를 실수로 재생성하지 않도록 기존 폴더는 거절한다.
    output_dir.mkdir(parents=True, exist_ok=False)
    for filename, key in (("persons.json", "persons"), ("newcomers.json", "newcomers"), ("queries.json", "queries")):
        (output_dir / filename).write_text(json.dumps(dataset[key], ensure_ascii=False, indent=2) + "\n")
    manifest = dict(dataset["manifest"], baselinePersonSha256=hashlib.sha256(baseline_path.read_bytes()).hexdigest())
    manifest["fileSha256"] = {name: hashlib.sha256((output_dir / name).read_bytes()).hexdigest() for name in ("persons.json", "newcomers.json", "queries.json")}
    (output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, default=20261015)
    parser.add_argument("--baseline", type=Path, default=DEFAULT_DATA / "persons.json")
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).resolve().parent / "holdout/v1")
    args = parser.parse_args()
    dataset = build_holdout(args.seed)
    write_holdout(dataset, args.baseline, args.output_dir)
    print("holdout: 대장 120명 / 대장 밖 신규 20명 / 15유형 300건 생성·형식 검사 완료. 모델 평가 미실행")


if __name__ == "__main__":
    main()
