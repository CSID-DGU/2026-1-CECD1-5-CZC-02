"""새 생성 규칙의 합성 자료를 고정한 뒤 기존 모델만 평가한다. 운영 코드는 변경하지 않는다."""
import argparse
import hashlib
import json
import random
import unicodedata
from collections import Counter
from copy import deepcopy
from pathlib import Path

from app.identity.normalization import normalize_name
from ml.identity.controlled_data import digest, make_corpus
from ml.identity.controlled_experiment import _scores, cluster_interval, evaluate, outcome, prepare, summarize
from ml.identity.overfit_audit import reproduce_models

ROOT = Path(__file__).resolve().parent
SEED = 816293


def make_fresh(seed=SEED, groups=40):
    """인물 출처와 관측 가능한 동명이인 집합으로 정답을 만든다. 모델 출력은 읽지 않는다."""
    rng = random.Random(seed)
    people, queries = [], []
    syllables = ("쀼", "뇨", "쪼", "뿌", "쨔", "뀨", "뾰", "쬐", "쮸", "뺘", "냐", "뵤")
    def word(length):
        return "".join(rng.choice(syllables) for _ in range(length))
    def typo(name, two=False):
        prefix, value = name.split("_", 1)
        k = rng.randrange(len(value)-1)
        value = value[:k] + "쮜" + value[k+1:]
        if two:
            value = value[:-1]  # 대체+삭제 조합. 원 생성기의 오타 함수를 사용하지 않는다.
        return prefix + "_" + value
    for group in range(groups):
        base_name = "가상검증_" + word(rng.randint(6, 11))
        for offset in range(6):
            index = len(people)
            name = base_name if offset < 4 else typo(base_name) if offset == 4 else "가상시험대상_" + word(rng.randint(6, 11))
            people.append({"personId": f"p_fresh_{seed}_{index}", "name": name,
                "nameVariants": ["Synthetic Fixture " + "".join(rng.choice("abcdefghijkmnpqrstuvwxyz") for _ in range(12))],
                "company": f"가상검증회사_{group}_{offset}", "title": "차장", "department": None,
                "emails": [f"fixture-{seed}-{index}@fresh.example"], "phones": [f"000-{seed}-{index:04}"],
                "companyHistory": [], "channels": ["EMAIL", "MEETING"], "isSynthetic": True, "photoAvailable": False})
        block = people[-6:]
        block[0]["companyHistory"] = [{"company": block[1]["company"], "until": "2025-12-31", "title": "과장"}]
        block[4]["company"] = block[0]["company"]
        block[4]["emails"] = list(block[0]["emails"])
    for group in range(groups):
        block = people[group*6:group*6+6]
        anchor, unique = block[0], block[5]
        def add(case, owner, changes, targets, kind="KNOWN"):
            mention = {"name": owner["name"], "company": owner["company"], "title": "차장", "email": None,
                       "phone": None, "channel": rng.choice(("EMAIL", "MEETING", "KAKAO", "JANDI"))}
            mention.update(changes)
            mention["sourceRef"] = f"src_fresh_{seed}_{group}_{case}"
            # 오타가 우연히 다른 표기와 정확히 겹치면 단일 정답을 강요하지 않는다.
            if kind == "KNOWN" and not mention["email"] and not mention["phone"]:
                exact = [p["personId"] for p in people if normalize_name(mention["name"]) in
                         {normalize_name(p["name"]), *(normalize_name(v) for v in p["nameVariants"])}
                         and (mention["company"] is None or mention["company"] == p["company"] or
                              any(h["company"] == mention["company"] for h in p["companyHistory"]))]
                targets = sorted(set(targets) | set(exact))
                if len(targets) > 1:
                    kind = "AMBIGUOUS"
            queries.append({"queryId": f"q_fresh_{seed}_{group}_{case}", "cluster": f"fresh_{seed}_{group}",
                "caseType": case, "mention": mention, "expected": {"kind": kind, "targetPersonIds": sorted(targets)}})
        add("phone_only", unique, {"name": None, "company": None, "phone": unique["phones"][0].replace("-", " ")}, [unique["personId"]])
        add("domain_case_honorific", unique, {"name": unique["name"] + " 차장님", "email": unique["emails"][0].replace("fresh.example", "FRESH.EXAMPLE")}, [unique["personId"]])
        add("unicode_registered_alias", unique, {"name": unicodedata.normalize("NFD", unique["name"])}, [unique["personId"]])
        add("alias_with_extra_space", unique, {"name": unique["nameVariants"][0].replace(" ", "   ").upper()}, [unique["personId"]])
        add("alias_punctuation_typo", unique, {"name": unique["nameVariants"][0].replace("Fixture ", "Fixture-"), "company": None}, [unique["personId"]])
        add("compound_typo_company", unique, {"name": typo(unique["name"], two=True)}, [unique["personId"]])
        add("compound_typo_missing_company", unique, {"name": typo(unique["name"], two=True), "company": None}, [unique["personId"]])
        add("homonym_current_company", anchor, {}, [anchor["personId"]])
        add("homonym_previous_company", anchor, {"company": block[1]["company"]}, [anchor["personId"], block[1]["personId"]], "AMBIGUOUS")
        add("shared_email_missing_name", anchor, {"name": None, "company": None, "email": anchor["emails"][0]}, [anchor["personId"], block[4]["personId"]], "AMBIGUOUS")
        add("conflicting_name_contact", unique, {"company": None, "email": anchor["emails"][0]}, [anchor["personId"], block[4]["personId"], unique["personId"]], "CONFLICT")
        add("four_homonyms_overflow", anchor, {"company": None}, [p["personId"] for p in block[:4]], "AMBIGUOUS")
        # 신규 이름은 오타와 구분할 수 없는 근접 이름을 피한다. 근접 신규/오타 구분은 별도 한계다.
        add("new_distant_identity", unique, {"name": "가상신규검체_" + "".join(rng.choice("ZXVQWK") for _ in range(15)), "company": None}, [], "NEW")
        add("company_without_identity", unique, {"name": None}, [], "INSUFFICIENT")
    return {"persons": people, "queries": queries, "manifest": {"seed": seed, "groups": groups,
            "independentScenarioImplementation": True, "goldSource": "synthetic_origin_and_explicit_possible_identity_sets",
            "near_new_vs_typo_not_resolved": True}}


def validate_data(data):
    from jsonschema import Draft202012Validator
    contracts = ROOT.parents[2] / "contracts"
    person = Draft202012Validator(json.loads((contracts/"person.schema.json").read_text(encoding="utf-8")))
    mention = Draft202012Validator(json.loads((contracts/"identity_resolution.schema.json").read_text(encoding="utf-8"))["properties"]["mention"])
    ids = {p["personId"] for p in data["persons"]}
    assert len(ids) == len(data["persons"])
    for p in data["persons"]:
        person.validate(p)
        assert p["isSynthetic"] and p["name"].startswith("가상")
        assert all(e.endswith("@fresh.example") for e in p["emails"])
    observations = set()
    for q in data["queries"]:
        mention.validate(q["mention"])
        assert set(q["expected"]["targetPersonIds"]) <= ids
        key = tuple(q["mention"].get(k) for k in ("name", "company", "email", "phone"))
        assert key not in observations, "동일 관측 반복"
        observations.add(key)


def sections(rows, prepared):
    feasible = [i for i, r in enumerate(prepared) if len(r["query"]["expected"]["targetPersonIds"]) <= 3]
    return {"all": summarize(rows), "withinThreeCandidateCapacity": summarize([rows[i] for i in feasible]),
            "byCase": {case: summarize([r for r in rows if r["caseType"] == case]) for case in sorted({r["caseType"] for r in rows})}}


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False)+"\n", encoding="utf-8", newline="\n")


def run(output, source_path):
    import numpy as np
    import sklearn
    source = json.loads(source_path.read_text(encoding="utf-8"))
    if sklearn.__version__ != source["sklearnVersion"]:
        raise ValueError("원 실험과 sklearn 버전이 다릅니다")
    for name, sha in source["sourceSha256"].items():
        if hashlib.sha256((ROOT/name).read_bytes()).hexdigest() != sha:
            raise ValueError("원 학습 코드 해시 불일치")
    corpus = make_corpus(source["protocol"])
    assert all(digest(data) == source["manifests"][name]["sha256"] for name, data in corpus.items())
    fresh = make_fresh()
    validate_data(fresh)
    old_ids = {p["personId"] for data in corpus.values() for p in data["persons"]}
    old_names = {normalize_name(p["name"]) for data in corpus.values() for p in data["persons"]}
    assert not old_ids & {p["personId"] for p in fresh["persons"]}
    assert not old_names & {normalize_name(p["name"]) for p in fresh["persons"]}
    # 점수 계산 전에 원본 자료·정답·선택된 모델·판정 규정을 파일로 고정한다.
    write_json(output/"dataset.json", fresh)
    write_json(output/"frozen_protocol.json", {"seed": SEED, "modelSeed": 42, "datasetSha256": digest(fresh),
        "sourceReportSha256": digest(source), "codeSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "selected": {r["family"]: {"parameter": r["selection"]["parameter"], "threshold": r["selection"]["selectedPolicy"]["threshold"]}
                     for r in source["runs"] if r["seed"] == 42},
        "policy": "no_new_fit_calibration_threshold_selection; report_all_cases_and_separate_impossible_overflow",
        "decisionCriteria": source["protocol"]["gate"],
        "safetyCheck": "no_worse_than_rule_on_ambiguity_new_and_insufficient; no_wrong_confirmation",
        "syntheticGoldNotHumanReviewed": True, "productionChange": False})
    print(f"새 자료 {len(fresh['persons'])}명·{len(fresh['queries'])}질문 고정", flush=True)
    original = {name: prepare(corpus[name]) for name in ("fit", "calibration")}
    models = reproduce_models(source, original, {"modelSeed": 42})
    original.clear()
    old_test = prepare(corpus["test"])
    for model in models:
        metrics, _ = evaluate(old_test, _scores(model["model"], model["calibration"], old_test, np), model["threshold"])
        assert metrics == model["frozen"]["test"], "원 최종 평가 재현 불일치"
    old_test = None
    prepared = prepare(fresh)
    base, base_rows = evaluate(prepared)
    reports = {"rule": sections(base_rows, prepared)}
    predictions = []
    for model in models:
        scores = _scores(model["model"], model["calibration"], prepared, np)
        metrics, rows = evaluate(prepared, scores, model["threshold"])
        reports[model["family"]] = {**sections(rows, prepared), "pairedCluster95CI": cluster_interval(base_rows, rows, source["protocol"])}
        for row, score in zip(prepared, scores):
            a = outcome(row, score, model["threshold"])
            predictions.append({"model": model["family"], "queryId": row["query"]["queryId"], "caseType": row["query"]["caseType"],
                "expected": row["query"]["expected"], "decision": a["decision"], "personId": a["personId"],
                "candidateIds": [pid for pid, _ in a["ranked"][:3]], "candidatePoolSize": len(a["ranked"])})
        print(model["family"]+" 평가 완료", flush=True)
    write_json(output/"predictions.json", predictions)
    write_json(output/"report.json", {"datasetSha256": digest(fresh), "persons": len(fresh["persons"]), "queries": len(fresh["queries"]),
        "caseCounts": dict(Counter(q["caseType"] for q in fresh["queries"])), "reports": reports,
        "modelsRetuned": False, "productionChanged": False,
        "limitations": ["automatic_synthetic_gold", "same_feature_extractor_and_shared_confirmation_guard",
            "overflow_four_targets_cannot_all_fit_in_three_candidates", "distant_new_only_not_near_new_disambiguation",
            "candidate_scores_not_validated_as_real_world_probabilities", "now_observed_do_not_reuse_for_unseen_claims"]})


def main():
    parser = argparse.ArgumentParser(description="새 미사용 합성 자료로 고정 ML-1 후보 검색 평가")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--source-report", type=Path, default=ROOT/"results/controlled_v1/report.json")
    args = parser.parse_args()
    if args.output_dir.exists():
        parser.error("기존 결과 폴더는 덮어쓰지 않습니다")
    args.output_dir.mkdir(parents=True)
    run(args.output_dir, args.source_report)


if __name__ == "__main__":
    main()
