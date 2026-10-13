"""회의록 평가셋(가짜 메모 10개 + 사람이 쓴 정답 meeting_note)으로 회의록 엔진 결과를 채점한다.

사용법 (ai-module 폴더에서):
    python -m eval.minutes.score --self                   # 정답끼리 채점 (만점이 나와야 정상)
    python -m eval.minutes.score --pred eval/results/minutes_run1
    python -m eval.minutes.score --pred <폴더> --verbose --json report.json

--pred 폴더에는 케이스마다 <caseId>.json (meeting_note 형식)을 둔다. 파일이 없는 케이스는 빈 결과로 채점한다.

지표
- 문항별 정밀도·재현율·F1: 예측 항목과 정답 항목을 1:1로 짝지어 센다. text 또는 evidence의
  글자 bigram 유사도(Dice) 중 큰 값이 MATCH_THRESHOLD 이상이면 같은 항목으로 본다.
  LIGHT 모드(perPerson)는 같은 사람(displayName) 안에서만 짝짓는다.
- 근거 일치율: 예측 항목 중 evidence가 원문에 그대로 있는 비율 (공백 차이만 무시)
- missingFields 정확도: 칸마다 "비어 있음" 판단이 정답과 같은 비율, 회의록 단위 완전 일치율
  FULL은 5문항, LIGHT는 사람마다 3칸(perPerson[i].status 등)이 칸이다.
- 참고: 스키마 통과율, mode 일치율
"""
import argparse
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

MINUTES_DIR = Path(__file__).resolve().parent
CASES_FILE = MINUTES_DIR / "cases.json"
MEMOS_DIR = MINUTES_DIR / "memos"
GOLD_DIR = MINUTES_DIR / "gold"
SCHEMA_FILE = MINUTES_DIR.parents[2] / "contracts" / "meeting_note.schema.json"

FIELD_NAMES = ("discussion", "customerRequests", "concerns", "commitments", "nextContact")
PER_PERSON_FIELDS = ("status", "request", "followUp")
METRIC_KEYS = FIELD_NAMES + tuple(f"perPerson.{name}" for name in PER_PERSON_FIELDS)
MATCH_THRESHOLD = 0.5
PER_PERSON_SLOT = re.compile(r"^perPerson\[(\d+)\]\.(status|request|followUp)$")


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def load_cases() -> List[Dict[str, Any]]:
    """cases.json 목록에 원문(text)과 정답(gold)을 붙여 돌려준다."""
    cases = []
    for entry in load_json(CASES_FILE):
        case_id = entry["caseId"]
        cases.append({
            **entry,
            "text": (MEMOS_DIR / f"{case_id}.txt").read_text(encoding="utf-8"),
            "gold": load_json(GOLD_DIR / f"{case_id}.json"),
        })
    return cases


def load_predictions(pred_dir: Path, cases: List[Dict[str, Any]]) -> Dict[str, Optional[dict]]:
    predictions = {}
    for case in cases:
        path = pred_dir / f"{case['caseId']}.json"
        try:
            predictions[case["caseId"]] = load_json(path)
        except FileNotFoundError:
            predictions[case["caseId"]] = None
        except json.JSONDecodeError as error:
            print(f"[경고] {path} JSON 오류: {error}", file=sys.stderr)
            predictions[case["caseId"]] = None
    return predictions


# ---------- 비교 도구 ----------

def normalize(text: Optional[str]) -> str:
    """유사도 비교용: 공백·문장부호를 지우고 한글·영문·숫자만 남긴다."""
    text = unicodedata.normalize("NFC", text or "").lower()
    return re.sub(r"[^0-9a-z가-힣]", "", text)


def similarity(a: Optional[str], b: Optional[str]) -> float:
    """글자 bigram Dice 계수 (0~1). 어순이 조금 달라도 같은 내용이면 높게 나온다."""
    a, b = normalize(a), normalize(b)
    if not a or not b:
        return 0.0
    if len(a) < 2 or len(b) < 2:
        return 1.0 if a == b else 0.0
    grams_a = Counter(a[i:i + 2] for i in range(len(a) - 1))
    grams_b = Counter(b[i:i + 2] for i in range(len(b) - 1))
    common = sum((grams_a & grams_b).values())
    return 2 * common / (sum(grams_a.values()) + sum(grams_b.values()))


def item_similarity(pred: dict, gold: dict) -> float:
    return max(similarity(pred.get("text"), gold.get("text")), similarity(pred.get("evidence"), gold.get("evidence")))


def match_items(pred_items: List[dict], gold_items: List[dict]) -> List[Tuple[int, int]]:
    """유사도가 높은 쌍부터 1:1로 짝짓는다. (예측 번호, 정답 번호) 목록."""
    candidates = sorted(
        ((item_similarity(p, g), i, j) for i, p in enumerate(pred_items) for j, g in enumerate(gold_items)),
        reverse=True,
    )
    used_pred, used_gold, pairs = set(), set(), []
    for score, i, j in candidates:
        if score < MATCH_THRESHOLD:
            break
        if i in used_pred or j in used_gold:
            continue
        used_pred.add(i)
        used_gold.add(j)
        pairs.append((i, j))
    return pairs


def squash_spaces(text: Optional[str]) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


def evidence_in_source(evidence: Optional[str], source: str) -> bool:
    evidence = squash_spaces(evidence)
    return bool(evidence) and evidence in squash_spaces(source)


def person_key(name: Optional[str]) -> str:
    return normalize(name)


def collect_items(note: dict) -> Dict[Tuple[str, str], List[dict]]:
    """(지표 이름, 사람) → 항목 목록. FULL 문항은 사람 자리가 빈 문자열."""
    slots: Dict[Tuple[str, str], List[dict]] = defaultdict(list)
    fields = note.get("fields") or {}
    for name in FIELD_NAMES:
        slots[(name, "")].extend(item for item in fields.get(name) or [] if isinstance(item, dict))
    for person in note.get("perPerson") or []:
        for name in PER_PERSON_FIELDS:
            item = person.get(name)
            if isinstance(item, dict):
                slots[(f"perPerson.{name}", person_key(person.get("displayName")))].append(item)
    return slots


def missing_slots(note: dict) -> Set[str]:
    """missingFields를 비교 가능한 칸 이름으로 바꾼다. perPerson[i]는 순서 대신 사람 이름으로."""
    people = note.get("perPerson") or []
    slots = set()
    for raw in note.get("missingFields") or []:
        found = PER_PERSON_SLOT.match(str(raw))
        if found and int(found.group(1)) < len(people):
            slots.add(f"perPerson.{person_key(people[int(found.group(1))].get('displayName'))}.{found.group(2)}")
        else:
            slots.add(str(raw))
    return slots


def slot_universe(gold: dict) -> Set[str]:
    if gold.get("mode") == "LIGHT":
        return {
            f"perPerson.{person_key(person['displayName'])}.{name}"
            for person in gold.get("perPerson") or []
            for name in PER_PERSON_FIELDS
        }
    return set(FIELD_NAMES)


def schema_validator():
    try:
        from jsonschema import Draft202012Validator
    except ImportError:  # 채점만 돌리는 환경에서는 스키마 검사를 건너뛴다
        return None
    return Draft202012Validator(load_json(SCHEMA_FILE))


# ---------- 채점 ----------

def score_case(case: Dict[str, Any], pred: Optional[dict], validator=None) -> Dict[str, Any]:
    gold = case["gold"]
    note = pred if isinstance(pred, dict) else {}
    pred_slots, gold_slots = collect_items(note), collect_items(gold)

    counts = {key: {"tp": 0, "fp": 0, "fn": 0} for key in METRIC_KEYS}
    unmatched_pred, unmatched_gold = [], []
    for slot in sorted(set(pred_slots) | set(gold_slots)):
        pred_items, gold_items = pred_slots.get(slot, []), gold_slots.get(slot, [])
        pairs = match_items(pred_items, gold_items)
        counts[slot[0]]["tp"] += len(pairs)
        counts[slot[0]]["fp"] += len(pred_items) - len(pairs)
        counts[slot[0]]["fn"] += len(gold_items) - len(pairs)
        label = slot[0] if not slot[1] else f"{slot[0]}({slot[1]})"
        unmatched_pred += [(label, item) for i, item in enumerate(pred_items) if i not in {p for p, _ in pairs}]
        unmatched_gold += [(label, item) for j, item in enumerate(gold_items) if j not in {g for _, g in pairs}]

    pred_items = [item for items in pred_slots.values() for item in items]
    bad_evidence = [item for item in pred_items if not evidence_in_source(item.get("evidence"), case["text"])]

    gold_missing, pred_missing = missing_slots(gold), missing_slots(note)
    universe = slot_universe(gold) | gold_missing | pred_missing

    return {
        "caseId": case["caseId"],
        "predicted": pred is not None,
        "schemaValid": None if validator is None or pred is None else validator.is_valid(pred),
        "modeMatch": note.get("mode") == gold.get("mode"),
        "counts": counts,
        "evidence": {"inSource": len(pred_items) - len(bad_evidence), "total": len(pred_items)},
        "missingFields": {
            "agree": sum((slot in gold_missing) == (slot in pred_missing) for slot in universe),
            "total": len(universe),
            "exact": gold_missing == pred_missing,
        },
        "unmatchedPred": unmatched_pred,
        "unmatchedGold": unmatched_gold,
        "badEvidence": bad_evidence,
    }


def ratio(numerator: float, denominator: float) -> Optional[float]:
    return round(numerator / denominator, 4) if denominator else None


def prf(tp: int, fp: int, fn: int) -> Dict[str, Any]:
    precision, recall = ratio(tp, tp + fp), ratio(tp, tp + fn)
    f1 = None
    if precision is not None and recall is not None:
        f1 = round(2 * precision * recall / (precision + recall), 4) if precision + recall else 0.0
    return {"precision": precision, "recall": recall, "f1": f1, "tp": tp, "fp": fp, "fn": fn}


def score(cases: List[Dict[str, Any]], predictions: Dict[str, Optional[dict]]) -> Dict[str, Any]:
    validator = schema_validator()
    results = [score_case(case, predictions.get(case["caseId"]), validator) for case in cases]

    fields = {}
    for key in METRIC_KEYS:
        tp, fp, fn = (sum(result["counts"][key][name] for result in results) for name in ("tp", "fp", "fn"))
        fields[key] = prf(tp, fp, fn)
    overall = prf(*(sum(fields[key][name] for key in METRIC_KEYS) for name in ("tp", "fp", "fn")))

    predicted = [result for result in results if result["predicted"]]
    schema_checked = [result for result in predicted if result["schemaValid"] is not None]
    return {
        "cases": len(results),
        "predicted": len(predicted),
        "matchThreshold": MATCH_THRESHOLD,
        "overall": overall,
        "fields": fields,
        "evidenceInSourceRate": ratio(
            sum(result["evidence"]["inSource"] for result in results),
            sum(result["evidence"]["total"] for result in results),
        ),
        "missingFields": {
            "slotAccuracy": ratio(
                sum(result["missingFields"]["agree"] for result in results),
                sum(result["missingFields"]["total"] for result in results),
            ),
            "exactMatchRate": ratio(sum(result["missingFields"]["exact"] for result in results), len(results)),
        },
        "schemaValidRate": ratio(sum(result["schemaValid"] for result in schema_checked), len(schema_checked)),
        "modeAccuracy": ratio(sum(result["modeMatch"] for result in results), len(results)),
        "perCase": results,
    }


def is_perfect(report: Dict[str, Any]) -> bool:
    values = [
        report["overall"]["precision"], report["overall"]["recall"], report["overall"]["f1"],
        report["evidenceInSourceRate"],
        report["missingFields"]["slotAccuracy"], report["missingFields"]["exactMatchRate"],
        report["modeAccuracy"],
    ]
    values += [field["f1"] for field in report["fields"].values() if field["f1"] is not None]
    if report["schemaValidRate"] is not None:
        values.append(report["schemaValidRate"])
    return all(value == 1.0 for value in values)


# ---------- 출력 ----------

def fmt(value: Optional[float]) -> str:
    return "  -  " if value is None else f"{value:.3f}"


def print_report(report: Dict[str, Any], verbose: bool = False) -> None:
    print(f"케이스 {report['cases']}개 · 예측 {report['predicted']}개 · 짝 기준 유사도 ≥ {report['matchThreshold']}")
    print(f"{'문항':<24}{'정밀도':>8}{'재현율':>8}{'F1':>8}   정답/예측/일치")
    for key, field in list(report["fields"].items()) + [("전체", report["overall"])]:
        print(
            f"{key:<24}{fmt(field['precision']):>8}{fmt(field['recall']):>8}{fmt(field['f1']):>8}"
            f"   {field['tp'] + field['fn']}/{field['tp'] + field['fp']}/{field['tp']}"
        )
    print(f"근거 일치율          {fmt(report['evidenceInSourceRate'])}")
    print(f"missingFields 칸 정확도 {fmt(report['missingFields']['slotAccuracy'])}"
          f" · 완전 일치율 {fmt(report['missingFields']['exactMatchRate'])}")
    print(f"스키마 통과율        {fmt(report['schemaValidRate'])}")
    print(f"mode 일치율          {fmt(report['modeAccuracy'])}")

    if not verbose:
        return
    for result in report["perCase"]:
        lines = []
        if not result["predicted"]:
            lines.append("  예측 파일 없음")
        lines += [f"  놓침 {label}: {item.get('text')}" for label, item in result["unmatchedGold"]]
        lines += [f"  초과 {label}: {item.get('text')}" for label, item in result["unmatchedPred"]]
        lines += [f"  근거 불일치: {item.get('evidence')}" for item in result["badEvidence"]]
        if not result["missingFields"]["exact"]:
            lines.append("  missingFields 다름")
        if lines:
            print(f"\n[{result['caseId']}]")
            print("\n".join(lines))


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="회의록 평가셋 채점")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--pred", type=Path, help="<caseId>.json 예측 파일이 있는 폴더")
    source.add_argument("--self", action="store_true", help="정답끼리 채점 (채점기 점검용)")
    parser.add_argument("--json", type=Path, help="리포트를 JSON으로 저장할 경로")
    parser.add_argument("--verbose", action="store_true", help="케이스별 놓친·초과 항목 출력")
    args = parser.parse_args()

    cases = load_cases()
    if args.self:
        predictions = {case["caseId"]: case["gold"] for case in cases}
    else:
        predictions = load_predictions(args.pred, cases)
    report = score(cases, predictions)
    print_report(report, verbose=args.verbose)

    if args.json:
        summary = {key: value for key, value in report.items() if key != "perCase"}
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    if args.self:
        return 0 if is_perfect(report) else 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
