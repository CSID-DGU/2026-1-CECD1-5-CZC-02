"""실제 이력서 평가용 정답 초안 만들기 (레포 밖 폴더에만 쓴다).

양식별로 정해진 수만큼 번호를 고르고(seed 고정), 규칙 결과로 정답 칸을 미리 채운 초안을 만든다.
사람이 원본 이력서를 보며 초안을 고치고 "reviewed": true로 바꾼 것만 정답으로 쓴다.
초안에 LLM 결과는 넣지 않는다 (LLM 방식 점수를 부풀리지 않도록). 줄글·다른 표의 빈 칸은 사람이 채운다.

    python -m eval.resumes.make_gold_draft --docx-dir ../.private/resumes/work/docx \
        --out-dir ../.private/resumes/eval --table 20 --other 5 --free 5
"""
import argparse
import json
import random
from datetime import date
from pathlib import Path

from app.resumes.docx_reader import DocxError
from app.resumes.engine import extract_resume
from eval.resumes.run import REFERENCE
from eval.resumes.score import predicted


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--docx-dir", type=Path, required=True)
    parser.add_argument("--out-dir", type=Path, required=True)
    parser.add_argument("--table", type=int, default=20)
    parser.add_argument("--other", type=int, default=5)
    parser.add_argument("--free", type=int, default=5)
    parser.add_argument("--seed", type=int, default=20261124)
    args = parser.parse_args()

    by_format = {"TABLE_CARD": [], "OTHER_TABLE": [], "FREE_TEXT": []}
    drafts = {}
    for path in sorted(args.docx_dir.glob("*.docx")):
        try:
            profile = extract_resume(content=path.read_bytes(), reference_date=REFERENCE, mode="rule").profile
        except (DocxError, ValueError):
            continue
        by_format[profile["format"]].append(path.stem)
        drafts[path.stem] = predicted(profile)

    rng = random.Random(args.seed)
    chosen = []
    for doc_format, count in (("TABLE_CARD", args.table), ("OTHER_TABLE", args.other), ("FREE_TEXT", args.free)):
        ids = by_format[doc_format]
        chosen += sorted(rng.sample(ids, min(count, len(ids))))

    gold_dir = args.out_dir / "gold_draft"
    gold_dir.mkdir(parents=True, exist_ok=True)
    for case_id in chosen:
        draft = {**drafts[case_id], "reviewed": False}
        (gold_dir / f"{case_id}.json").write_text(json.dumps(draft, ensure_ascii=False, indent=2), encoding="utf-8")
    (args.out_dir / "ids.txt").write_text("\n".join(chosen) + "\n", encoding="utf-8")
    counts = {doc_format: sum(1 for case_id in chosen if drafts[case_id]["format"] == doc_format) for doc_format in by_format}
    print(f"정답 초안 {len(chosen)}건 → {gold_dir} (양식별 {counts}, 기준일 {REFERENCE})")


if __name__ == "__main__":
    main()
