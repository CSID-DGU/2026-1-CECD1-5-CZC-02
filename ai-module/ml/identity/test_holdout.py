"""새 검증 자료의 계약·분리·모호함·근거·재현성만 검사한다. 모델은 호출하지 않는다."""

import hashlib
import json
import random
from collections import Counter
from copy import deepcopy
from pathlib import Path

import pytest

from ml.identity.generate_holdout import CASES, build_holdout, validate_holdout, write_holdout

DATA = Path(__file__).resolve().parent / "data"
BASELINE = json.loads((DATA / "persons.json").read_text(encoding="utf-8"))


@pytest.fixture(scope="module")
def dataset():
    return build_holdout()


def test_contracts_identity_separation_and_scenario_counts(dataset):
    validate_holdout(dataset, BASELINE)
    assert dataset["manifest"]["modelEvaluationPerformed"] is False
    assert Counter(q["caseType"] for q in dataset["queries"]) == {case: 20 for case in CASES}
    assert Counter(q["expected"]["kind"] for q in dataset["queries"]) == {
        "KNOWN": 180, "AMBIGUOUS": 60, "CONFLICT": 20, "NEW": 20, "INSUFFICIENT": 20}


def test_seed_reproducibility_without_global_random_change(dataset):
    state = random.getstate()
    assert build_holdout() == dataset
    assert random.getstate() == state
    other = build_holdout(7)
    validate_holdout(other, BASELINE)
    assert other != dataset


@pytest.mark.parametrize("case", ["typo_substitution", "typo_deletion", "typo_insertion", "typo_transposition"])
def test_typos_modify_variable_token_at_multiple_positions(dataset, case):
    people = {p["personId"]: p for p in dataset["persons"]}
    positions = set()
    for q in dataset["queries"]:
        if q["caseType"] != case:
            continue
        raw = people[q["expected"]["relatedPersonIds"][0]]["name"]
        changed = q["mention"]["name"]
        assert raw != changed
        assert changed.rsplit("_", 1)[0] == raw.rsplit("_", 1)[0]
        positions.add(next(i for i, (a, b) in enumerate(zip(raw, changed)) if a != b))
        assert len(changed) - len(raw) == (1 if case == "typo_insertion" else -1 if case == "typo_deletion" else 0)
    assert len(positions) == 8


@pytest.mark.parametrize("corruption", ["overlap", "target", "evidence", "shared", "conflict", "missing", "new"])
def test_corrupt_data_rejected(dataset, corruption):
    broken = deepcopy(dataset)
    if corruption == "overlap":
        baseline = [broken["persons"][0]]
    else:
        baseline = BASELINE
        case = {"target": "exact", "evidence": "exact", "shared": "same_company_homonyms",
                "conflict": "contact_conflict", "missing": "missing_company", "new": "new_person"}[corruption]
        q = next(q for q in broken["queries"] if q["caseType"] == case)
        if corruption == "target":
            q["expected"]["targetPersonIds"] = []
        elif corruption == "evidence":
            q["evidence"] = "잘못된 가짜 근거"
        elif corruption == "shared":
            ids = q["expected"]["relatedPersonIds"]
            next(p for p in broken["persons"] if p["personId"] == ids[1])["company"] = "가상검증회사_다른소속"
        elif corruption == "conflict":
            q["expected"]["relatedPersonIds"].reverse()
        elif corruption == "missing":
            q["mention"]["company"] = "가상검증회사_잘못채운소속"
        else:
            q["expected"]["targetPersonIds"] = [broken["persons"][0]["personId"]]
    with pytest.raises(ValueError):
        validate_holdout(broken, baseline)


def test_files_and_manifest_hashes_and_no_overwrite(dataset, tmp_path):
    baseline_before = {p.name: p.read_bytes() for p in DATA.glob('*.json')}
    output = tmp_path / "holdout"
    write_holdout(dataset, DATA / "persons.json", output)
    manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
    for name, digest in manifest["fileSha256"].items():
        assert hashlib.sha256((output / name).read_bytes()).hexdigest() == digest
    assert manifest["baselinePersonSha256"] == hashlib.sha256((DATA / "persons.json").read_bytes()).hexdigest()
    with pytest.raises(FileExistsError):
        write_holdout(dataset, DATA / "persons.json", output)
    with pytest.raises(ValueError, match="덮어쓸"):
        write_holdout(dataset, DATA / "persons.json", DATA)
    assert baseline_before == {p.name: p.read_bytes() for p in DATA.glob('*.json')}


def test_checked_in_holdout_matches_generator(dataset):
    root = DATA.parent / "holdout/v1"
    for filename, key in (("persons.json", "persons"), ("newcomers.json", "newcomers"), ("queries.json", "queries")):
        assert json.loads((root / filename).read_text(encoding="utf-8")) == dataset[key]
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["modelEvaluationPerformed"] is False
    for name, digest in manifest["fileSha256"].items():
        assert hashlib.sha256((root / name).read_bytes()).hexdigest() == digest
