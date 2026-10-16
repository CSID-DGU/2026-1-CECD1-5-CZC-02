"""A 담당: 가짜 인물 300명·비교 쌍 3,000개 생성. 네트워크·모델 호출 없음."""
import argparse
import json
import random
from collections import Counter, defaultdict
from pathlib import Path

from jsonschema import Draft202012Validator

PERSON_COUNT = 300
TRAIN_PAIR_COUNT = 2400
EVAL_PAIR_COUNT = 600
DEFAULT_SEED = 42
TITLES = ("대리", "과장", "차장", "부장", "팀장")
COMPANIES = tuple(f"가상회사_{index:03d}" for index in range(1, 21))
POSITIVE_CASES = ("exact", "honorific", "english", "typo", "company_change", "missing_company")
NEGATIVE_CASES = ("random", "same_name", "same_company")
CONTRACTS_DIR = Path(__file__).resolve().parents[3] / "contracts"


def make_person(person_id, name_number, company, rng, *, with_history=False):
    name = f"가상인물_{name_number:04d}"
    title = rng.choice(TITLES)
    history = []
    if with_history or rng.random() < 0.35:
        history = [{"company": rng.choice([value for value in COMPANIES if value != company]),
                    "title": rng.choice(TITLES), "until": "2025-12-31"}]
    return {
        "personId": person_id, "name": name,
        "nameVariants": [f"{name} {title}님", f"Synthetic Person {name_number:04d}", f"{name}님"],
        "company": company, "department": None, "title": title,
        "emails": [f"synthetic-{person_id}@people.example"],
        "phones": [f"000-0000-{int(person_id.removeprefix('p_')):04d}"],
        "channels": ["MEETING", "EMAIL", "PHONE"], "companyHistory": history,
        "photoAvailable": False, "isSynthetic": True,
    }


def make_persons(rng):
    # 같은 이름·다른 회사인 동명이인 10쌍 + 이름이 고유한 280명.
    name_numbers = rng.sample(range(1, 10000), 290)
    people = []
    for number in name_numbers[:10]:
        for company in rng.sample(COMPANIES, 2):
            people.append(make_person(f"p_{len(people) + 1:03d}", number, company, rng, with_history=True))
    for number in name_numbers[10:]:
        people.append(make_person(f"p_{len(people) + 1:03d}", number, rng.choice(COMPANIES), rng))
    return people


def split_persons(persons, rng):
    # 먼저 인물을 나눈다. 동명이인 그룹도 함께 배치해서 같은 이름의 양쪽 유출을 피한다.
    by_name = defaultdict(list)
    for person in persons:
        by_name[person["name"]].append(person)
    homonyms = [group for group in by_name.values() if len(group) == 2]
    singles = [group[0] for group in by_name.values() if len(group) == 1]
    rng.shuffle(homonyms)
    rng.shuffle(singles)
    train = [person for group in homonyms[:8] for person in group] + singles[:224]
    evaluation = [person for group in homonyms[8:] for person in group] + singles[224:]
    rng.shuffle(train)
    rng.shuffle(evaluation)
    return train, evaluation


def make_mention(person, case, rng):
    mention = {
        "name": person["name"], "company": person["company"], "title": person["title"],
        "email": person["emails"][0] if rng.random() < 0.5 else None,
        "phone": person["phones"][0] if rng.random() < 0.5 else None,
        "channel": rng.choice(person["channels"]), "sourceRef": None,
    }
    if case == "honorific":
        mention["name"] = person["nameVariants"][0]
    elif case == "english":
        mention["name"] = person["nameVariants"][1]
    elif case == "typo":
        # 번호를 바꿔 다른 가상 인물의 이름이 되지 않도록 공통 표기의 한 글자만 바꾼다.
        mention["name"] = person["name"].replace("가상인물_", "가상인믈_", 1)
    elif case == "company_change":
        previous = rng.choice(person["companyHistory"])
        mention["company"], mention["title"] = previous["company"], previous["title"]
    elif case == "missing_company":
        mention["company"] = None
    if case != "exact":
        mention["email"] = mention["phone"] = None
    return mention


def source_text(mention):
    # 추출기를 실행하는 대신 가짜 관측 원문을 함께 생성해 검수 근거를 남긴다.
    fields = (("이름", "name"), ("회사", "company"), ("직함", "title"),
              ("이메일", "email"), ("전화", "phone"), ("채널", "channel"))
    return " | ".join(f"{label}: {mention[key]}" for label, key in fields if mention[key] is not None)


def make_pairs(persons, count, split_name, rng):
    with_history = [person for person in persons if person["companyHistory"]]
    different = [(left, right) for index, left in enumerate(persons) for right in persons[index + 1:]]
    homonyms = [(left, right) for left, right in different if left["name"] == right["name"]]
    colleagues = [(left, right) for left, right in different
                  if left["company"] == right["company"] and left["name"] != right["name"]]
    pairs = []
    for index in range(count):
        same_person = index < count // 2
        if same_person:
            case = POSITIVE_CASES[index % len(POSITIVE_CASES)]
            left = right = rng.choice(with_history if case == "company_change" else persons)
            mention_a, mention_b = make_mention(left, "exact", rng), make_mention(right, case, rng)
        else:
            case = NEGATIVE_CASES[(index - count // 2) % len(NEGATIVE_CASES)]
            pool = homonyms if case == "same_name" else colleagues if case == "same_company" else different
            left, right = rng.choice(pool)
            mention_a, mention_b = make_mention(left, "exact", rng), make_mention(right, "exact", rng)
            # 이름·회사 단서로도 구분해야 하는 어려운 반례는 연락처를 비운다.
            if case != "random":
                for mention in (mention_a, mention_b):
                    mention["email"] = mention["phone"] = None
        pair_id = f"pair_{split_name}_{index + 1:04d}"
        mention_a["sourceRef"], mention_b["sourceRef"] = f"src_{pair_id}_a", f"src_{pair_id}_b"
        pairs.append({
            "pairId": pair_id, "label": int(same_person), "caseType": case,
            "personIdA": left["personId"], "personIdB": right["personId"],
            "mentionA": mention_a, "mentionB": mention_b,
            "evidence": {"mentionA": source_text(mention_a), "mentionB": source_text(mention_b)},
        })
    rng.shuffle(pairs)
    return pairs


def build_dataset(seed=DEFAULT_SEED):
    rng = random.Random(seed)
    persons = make_persons(rng)
    train, evaluation = split_persons(persons, rng)
    return {
        "persons": persons,
        "trainPairs": make_pairs(train, TRAIN_PAIR_COUNT, "train", rng),
        "evalPairs": make_pairs(evaluation, EVAL_PAIR_COUNT, "eval", rng),
        "split": {"seed": seed, "trainPersonIds": sorted(p["personId"] for p in train),
                  "evalPersonIds": sorted(p["personId"] for p in evaluation)},
    }


def validate_dataset(dataset, contracts_dir=CONTRACTS_DIR):
    def load_schema(name):
        return json.loads((Path(contracts_dir) / name).read_text(encoding="utf-8"))

    person_validator = Draft202012Validator(load_schema("person.schema.json"))
    mention_validator = Draft202012Validator(load_schema("identity_resolution.schema.json")["properties"]["mention"])
    persons = dataset["persons"]
    by_id = {person["personId"]: person for person in persons}
    if len(persons) != PERSON_COUNT or len(by_id) != PERSON_COUNT:
        raise ValueError("인물 300명과 고유 personId 300개가 필요합니다")
    for person in persons:
        person_validator.validate(person)
        if person["isSynthetic"] is not True:
            raise ValueError("가짜 인물만 사용할 수 있습니다")
    train_ids = set(dataset["split"]["trainPersonIds"])
    eval_ids = set(dataset["split"]["evalPersonIds"])
    if len(train_ids) != 240 or len(eval_ids) != 60 or train_ids & eval_ids or train_ids | eval_ids != by_id.keys():
        raise ValueError("인물 단위 학습 240명·평가 60명 분리가 올바르지 않습니다")
    seen_pairs, seen_sources = set(), set()
    for key, allowed_ids, expected in (("trainPairs", train_ids, TRAIN_PAIR_COUNT), ("evalPairs", eval_ids, EVAL_PAIR_COUNT)):
        pairs = dataset[key]
        if len(pairs) != expected or Counter(pair["label"] for pair in pairs) != {0: expected // 2, 1: expected // 2}:
            raise ValueError("비교 쌍 개수 또는 정답 비율이 올바르지 않습니다")
        for pair in pairs:
            if pair["pairId"] in seen_pairs:
                raise ValueError("비교 쌍 ID가 중복됩니다")
            seen_pairs.add(pair["pairId"])
            left, right = pair["personIdA"], pair["personIdB"]
            if not {left, right} <= allowed_ids or pair["label"] != int(left == right):
                raise ValueError("비교 쌍의 인물 소속 또는 정답이 올바르지 않습니다")
            for side, pid in (("mentionA", left), ("mentionB", right)):
                mention = pair[side]
                mention_validator.validate(mention)
                source = mention["sourceRef"]
                if not source or source in seen_sources:
                    raise ValueError("원본 참조 ID가 없거나 중복됩니다")
                seen_sources.add(source)
                if pair["evidence"][side] != source_text(mention):
                    raise ValueError("가짜 관측 원문과 언급이 일치하지 않습니다")
                person = by_id[pid]
                allowed_names = [person["name"], *person["nameVariants"], person["name"].replace("가상인물_", "가상인믈_", 1)]
                allowed_companies = [None, person["company"], *(item["company"] for item in person["companyHistory"])]
                if mention["name"] not in allowed_names or mention["company"] not in allowed_companies:
                    raise ValueError("언급의 이름·소속이 정답 인물과 일치하지 않습니다")
                if mention["email"] not in [None, *person["emails"]] or mention["phone"] not in [None, *person["phones"]]:
                    raise ValueError("언급의 연락처가 정답 인물과 일치하지 않습니다")
                allowed_titles = [None, person["title"], *(item["title"] for item in person["companyHistory"])]
                if mention["title"] not in allowed_titles or mention["channel"] not in person["channels"]:
                    raise ValueError("언급의 직함·채널이 정답 인물과 일치하지 않습니다")


def write_dataset(dataset, output_dir):
    # 검증을 먼저 끝낸 후 파일을 쓴다. 검증 실패로 기존 데이터 일부만 바뀌는 것을 피한다.
    validate_dataset(dataset)
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    for filename, key in (("persons.json", "persons"), ("pairs_train.json", "trainPairs"),
                          ("pairs_eval.json", "evalPairs"), ("split.json", "split")):
        (output_dir / filename).write_text(json.dumps(dataset[key], ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).resolve().parent / "data")
    args = parser.parse_args()
    dataset = build_dataset(args.seed)
    write_dataset(dataset, args.output_dir)
    print(f"persons: {len(dataset['persons'])} (train 240 / eval 60)")
    print(f"pairs: train {len(dataset['trainPairs'])} / eval {len(dataset['evalPairs'])}")
    print(f"saved: {args.output_dir}")


if __name__ == "__main__":
    main()
