"""이력서 추출을 규칙만 · 로컬 LLM만 · 섞은 방식으로 돌려 비교한다. LLM 방식은 로컬 Ollama가 필요하다 (CI에서 안 돌림).

사용법 (ai-module 폴더에서):
    python -m eval.resumes.run                                  # 가짜 개발셋(dev/cases.json), 세 방식
    python -m eval.resumes.run --modes rule                     # 규칙만 (Ollama 없이)
    OLLAMA_MODEL_RESUME=qwen2.5:7b python -m eval.resumes.run   # 다른 모델로
    # 실제 이력서(레포 밖 .private)는 숫자만 출력·저장한다. 정답이 없으면 정답 없이 셀 수 있는 지표만 낸다
    python -m eval.resumes.run --docx-dir ../.private/resumes/work/docx --gold-dir ../.private/resumes/eval/gold \
        --ids-file ../.private/resumes/eval/ids.txt --output ../.private/resumes/eval/results/hybrid.json

실제 이력서는 개인정보다. 이 스크립트는 프로필 내용(이름·회사·근거 글)을 출력하거나 저장하지 않는다 (--save-profiles는
가짜 개발셋에서만 허용). LLM은 Ollama만 부른다 (app/resumes/llm_extract.py).
"""
import argparse
import json
import time
from datetime import date, datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from app.resumes import llm_extract
from app.resumes.docx_reader import DocxError, read_docx
from app.resumes.engine import extract_resume, schema_errors
from app.resumes.fake_docx import build_docx
from app.resumes.samples import FREE_TEXT_RESUME, card_resume_blocks
from eval.resumes.score import predicted, print_report, score_case, summarize

DEV_CASES = Path(__file__).resolve().parent / "dev" / "cases.json"
REFERENCE = date(2026, 11, 24)


def dev_inputs() -> List[Tuple[str, Dict[str, Any], Optional[Dict[str, Any]]]]:
    """(caseId, extract_resume 인자, 정답)."""
    cases = json.loads(DEV_CASES.read_text(encoding="utf-8"))
    inputs = []
    for case in cases:
        if case.get("builder") == "card_sample":
            kwargs = {"content": build_docx(card_resume_blocks())}
        elif case.get("builder") == "free_text_sample":
            kwargs = {"text": FREE_TEXT_RESUME}
        elif "text" in case:
            kwargs = {"text": case["text"]}
        else:
            kwargs = {"content": build_docx([tuple(block) for block in case["blocks"]])}
        inputs.append((case["caseId"], kwargs, case["gold"]))
    return inputs


def real_inputs(docx_dir: Path, gold_dir: Optional[Path], ids_file: Optional[Path], limit: Optional[int]):
    paths = sorted(docx_dir.glob("*.docx"))
    if ids_file:
        wanted = [line.strip() for line in ids_file.read_text(encoding="utf-8").splitlines() if line.strip()]
        by_id = {path.stem: path for path in paths}
        paths = [by_id[case_id] for case_id in wanted if case_id in by_id]
    for path in paths[:limit] if limit else paths:
        gold_path = gold_dir / f"{path.stem}.json" if gold_dir else None
        gold = json.loads(gold_path.read_text(encoding="utf-8")) if gold_path and gold_path.exists() else None
        if gold is not None and gold.get("reviewed") is False:
            gold = None   # 사람이 검토하지 않은 초안은 정답으로 쓰지 않는다
        yield path.stem, {"content": path.read_bytes()}, gold


def source_text(kwargs: Dict[str, Any]) -> str:
    if "content" in kwargs:
        return read_docx(kwargs["content"]).source_text()
    from app.resumes.docx_reader import DocxDocument, Paragraph
    return DocxDocument(blocks=[Paragraph(text=line.strip()) for line in kwargs["text"].split("\n")]).source_text()


def run_mode(mode: str, inputs, pseudo_gold: Dict[str, Dict[str, Any]]) -> Tuple[Dict[str, Any], List[Dict[str, Any]],
                                                                                 Dict[str, Dict[str, Any]]]:
    rows, times, per_case, profiles = [], [], [], {}
    failures = {"unreadable": 0, "schema": 0, "llmFallback": 0}
    for case_id, kwargs, gold in inputs:
        try:
            started = time.perf_counter()
            result = extract_resume(reference_date=REFERENCE, mode=mode, **kwargs)
            elapsed = int((time.perf_counter() - started) * 1000)
        except DocxError:
            failures["unreadable"] += 1
            continue
        except ValueError:
            failures["schema"] += 1
            continue
        if result.fallback_reason:
            failures["llmFallback"] += 1
        failures["schema"] += bool(schema_errors(result.profile))
        row = score_case(result.profile, gold or pseudo_gold.get(case_id), source_text(kwargs))
        rows.append(row)
        times.append(elapsed)
        profiles[case_id] = result.profile
        per_case.append({"caseId": case_id, "ms": elapsed, "llmCalls": result.llm_calls,
                         **{key: row[key] for key in row if key not in ("filled",)}})
    summary = summarize(rows, times)
    summary["failures"] = failures
    return summary, per_case, profiles


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--modes", default="rule,llm,hybrid")
    parser.add_argument("--docx-dir", type=Path, help="실제 이력서 폴더 (레포 밖)")
    parser.add_argument("--gold-dir", type=Path)
    parser.add_argument("--ids-file", type=Path, help="평가할 번호 목록 (한 줄에 하나, 예: r001)")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--pseudo-gold-rule", action="store_true",
                        help="정답이 없는 표 양식은 규칙 결과를 기준으로 LLM과의 일치율을 잰다 (정답이 아님)")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    real = args.docx_dir is not None
    inputs = list(real_inputs(args.docx_dir, args.gold_dir, args.ids_file, args.limit) if real else dev_inputs())
    modes = [mode.strip() for mode in args.modes.split(",") if mode.strip()]

    pseudo_gold: Dict[str, Dict[str, Any]] = {}
    results, cases = {}, {}
    for mode in modes:
        summary, per_case, profiles = run_mode(mode, inputs, pseudo_gold)
        results[mode], cases[mode] = summary, per_case
        if mode == "rule" and args.pseudo_gold_rule:
            pseudo_gold = {case_id: predicted(profile) for case_id, profile in profiles.items()
                           if profile["format"] == "TABLE_CARD"}

    print(f"이력서 추출 평가 — {'실제 이력서(숫자만)' if real else '가짜 개발셋'} {len(inputs)}건, 기준일 {REFERENCE}, "
          f"LLM 모델 {llm_extract.RESUME_MODEL}")
    print_report(results)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        payload = {"createdAt": datetime.now().isoformat(timespec="seconds"), "model": llm_extract.RESUME_MODEL,
                   "split": "real" if real else "dev", "referenceDate": REFERENCE.isoformat(), "summary": results,
                   "cases": cases}
        args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"\n저장: {args.output}")


if __name__ == "__main__":
    main()
