"""실제 이력서 정답 초안을 사람이 검토하는 화면(HTML) 만들기와, 검토 결과 가져오기. 결과는 레포 밖 폴더에만 쓴다.

1) 검토 화면 만들기 — 한 건씩 왼쪽에 원문(줄 번호), 오른쪽에 뽑은 값·근거·정답 입력 칸
    python -m eval.resumes.review_sheet build --docx-dir ../.private/resumes/work/docx \
        --eval-dir ../.private/resumes/eval
   → <eval-dir>/review/review.html 을 브라우저로 연다. 입력은 브라우저에 저장되고(같은 PC·같은 브라우저),
     다 끝나면 "검토 결과 내보내기"로 gold_reviewed.json을 받는다.
2) 검토 결과 가져오기 — 사람이 확인한 건만 gold/<번호>.json (reviewed: true)으로 쓴다
    python -m eval.resumes.review_sheet import --file <받은 gold_reviewed.json> --eval-dir ../.private/resumes/eval
3) 칸별 정확도
    python -m eval.resumes.run --modes rule,hybrid --docx-dir ../.private/resumes/work/docx \
        --gold-dir ../.private/resumes/eval/gold --ids-file ../.private/resumes/eval/ids.txt

이 스크립트는 이력서 내용을 화면(터미널)에 출력하지 않는다. 만든 HTML은 원문을 담고 있으니 레포 밖에만 둔다.
"""
import argparse
import html
import json
import sys
from pathlib import Path
from typing import Any, Dict, List

from app.resumes.engine import extract_resume
from eval.resumes.run import REFERENCE

GOLD_KEYS = ["format", "name", "birthYear", "grade", "projectCount", "skills", "industries", "totalCareerMonths",
             "availableFrom"]
LABELS = {
    "format": "양식 (TABLE_CARD·OTHER_TABLE·FREE_TEXT)", "name": "이름", "birthYear": "생년 (숫자)",
    "grade": "등급 (JUNIOR·INTERMEDIATE·ADVANCED·EXPERT, 없으면 비움)", "projectCount": "프로젝트 수",
    "skills": "기술 (사전의 통일 이름, 쉼표로)", "industries": "산업 (쉼표로)",
    "totalCareerMonths": "총 경력 (개월, 겹친 달 한 번, 진행 중이면 2026-11까지)",
    "availableFrom": "투입 가능일 (YYYY-MM-DD, 진행 중이 없으면 비움)",
}


def _repo_outside(path: Path) -> None:
    repo = Path(__file__).resolve().parents[3]
    if repo in path.resolve().parents or path.resolve() == repo:
        sys.exit("검토 파일은 원문을 담고 있어 레포 밖 폴더에만 만들 수 있습니다.")


def _evidence_lines(lines: List[str], evidence: str) -> List[int]:
    if not evidence:
        return []
    found = [index for index, line in enumerate(lines) if evidence in line]
    if found:
        return found[:3]
    first = evidence.split("\n")[0]
    return [index for index, line in enumerate(lines) if first and first in line][:3]


def _case(case_id: str, docx: Path, draft: Dict[str, Any]) -> Dict[str, Any]:
    result = extract_resume(content=docx.read_bytes(), reference_date=REFERENCE, mode="rule")
    profile, lines = result.profile, result.source_text.split("\n")
    items = []

    def add(field, value, evidence):
        items.append({"field": field, "value": value, "evidence": evidence, "lines": _evidence_lines(lines, evidence)})

    for key in ("name", "birthYear", "grade"):
        item = profile.get(key)
        add(key, item["value"] if item else None, item["evidence"] if item else None)
    for project in profile["projects"]:
        period = project["period"]
        span = f"{period['start'] or '?'} ~ {'진행 중' if period['ongoing'] else (period['end'] or '?')}" if period else "기간 없음"
        add("project", f"{project['name'] or '(이름 없음)'} · {span}", project["evidence"])
    for skill in profile["skills"]:
        if skill["known"]:
            add("skill", skill["name"], skill["evidence"])
    for industry in profile["industries"]:
        add("industry", industry["name"], industry["evidence"])
    if profile["availableFrom"]:
        add("availableFrom", profile["availableFrom"]["date"], profile["availableFrom"]["evidence"])
    return {"caseId": case_id, "lines": lines, "items": items, "draft": {key: draft.get(key) for key in GOLD_KEYS}}


def build(docx_dir: Path, eval_dir: Path) -> None:
    _repo_outside(eval_dir)
    ids = [line.strip() for line in (eval_dir / "ids.txt").read_text(encoding="utf-8").splitlines() if line.strip()]
    cases = []
    for case_id in ids:
        draft = json.loads((eval_dir / "gold_draft" / f"{case_id}.json").read_text(encoding="utf-8"))
        cases.append(_case(case_id, docx_dir / f"{case_id}.docx", draft))
    out = eval_dir / "review" / "review.html"
    out.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps({"cases": cases, "keys": GOLD_KEYS, "labels": LABELS}, ensure_ascii=False)
    out.write_text(TEMPLATE.replace("__DATA__", payload.replace("</", "<\\/")), encoding="utf-8")
    print(f"검토 화면 {len(cases)}건 → {out}")


def _parse(key: str, raw: Any) -> Any:
    if raw is None or (isinstance(raw, str) and not raw.strip()):
        return [] if key in ("skills", "industries") else None
    if key in ("skills", "industries"):
        items = raw if isinstance(raw, list) else str(raw).split(",")
        return sorted({str(item).strip() for item in items if str(item).strip()})
    if key in ("birthYear", "projectCount", "totalCareerMonths"):
        return int(raw)
    return str(raw).strip()


def import_reviews(file: Path, eval_dir: Path) -> None:
    _repo_outside(eval_dir)
    data = json.loads(file.read_text(encoding="utf-8"))
    gold_dir = eval_dir / "gold"
    gold_dir.mkdir(parents=True, exist_ok=True)
    written = skipped = 0
    for case_id, entry in data.items():
        if not entry.get("reviewed"):
            skipped += 1
            continue
        gold = {key: _parse(key, entry.get("values", {}).get(key)) for key in GOLD_KEYS}
        gold["reviewed"] = True
        (gold_dir / f"{case_id}.json").write_text(json.dumps(gold, ensure_ascii=False, indent=2), encoding="utf-8")
        written += 1
    print(f"정답 {written}건 저장 (검토 안 된 {skipped}건 제외) → {gold_dir}")


TEMPLATE = r"""<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><title>이력서 정답 검토</title>
<style>
body{font-family:system-ui,'Malgun Gothic',sans-serif;margin:0;color:#222;background:#f6f7f9}
header{position:sticky;top:0;background:#fff;border-bottom:1px solid #ddd;padding:8px 16px;display:flex;gap:12px;align-items:center;z-index:2}
header select,header button{font-size:14px;padding:4px 8px}
main{display:grid;grid-template-columns:1fr 1fr;gap:12px;padding:12px;height:calc(100vh - 60px);box-sizing:border-box}
.pane{background:#fff;border:1px solid #ddd;border-radius:6px;overflow:auto}
.src{font:12px/1.5 Consolas,monospace;white-space:pre-wrap;margin:0}
.src div{padding:0 8px;border-left:3px solid transparent}
.src div.hit{background:#fff4c2;border-left-color:#e0a800}
.src div.focus{background:#ffd76a}
.src span.no{color:#999;display:inline-block;width:40px;user-select:none}
table{border-collapse:collapse;width:100%;font-size:13px}
td,th{border-bottom:1px solid #eee;padding:4px 6px;text-align:left;vertical-align:top}
td.ev{color:#555;font-size:12px;cursor:pointer}
td.ev:hover{text-decoration:underline}
.form{padding:10px;border-bottom:2px solid #ccc;background:#fbfbfd}
.form label{display:block;font-size:12px;color:#555;margin-top:6px}
.form input{width:100%;box-sizing:border-box;font-size:14px;padding:3px 6px}
.form input.changed{background:#e8f4ff;border:1px solid #4a90d9}
.done{color:#1a7f37;font-weight:600}
</style></head><body>
<header>
<b>이력서 정답 검토</b>
<select id="pick"></select>
<button id="prev">◀ 이전</button><button id="next">다음 ▶</button>
<label><input type="checkbox" id="reviewed"> 이 건 검토 끝</label>
<span id="progress"></span>
<button id="export" style="margin-left:auto">검토 결과 내보내기</button>
</header>
<main><div class="pane"><pre class="src" id="src"></pre></div>
<div class="pane"><div class="form" id="form"></div>
<table><thead><tr><th>칸</th><th>뽑은 값 (규칙)</th><th>근거 (누르면 원문 위치)</th></tr></thead><tbody id="items"></tbody></table></div></main>
<script>
const DATA = __DATA__;
const KEY = "resume-gold-review-v1";
let saved = {};
try { saved = JSON.parse(localStorage.getItem(KEY) || "{}"); } catch (e) { saved = {}; }
const save = () => { try { localStorage.setItem(KEY, JSON.stringify(saved)); } catch (e) {} };
const esc = s => String(s ?? "").replace(/[&<>"]/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
const show = v => Array.isArray(v) ? v.join(", ") : (v ?? "");
let index = 0;
const pick = document.getElementById("pick");
DATA.cases.forEach((c, i) => pick.add(new Option(c.caseId, i)));
function entry(c) {
  if (!saved[c.caseId]) saved[c.caseId] = {reviewed: false, values: Object.fromEntries(DATA.keys.map(k => [k, show(c.draft[k])]))};
  return saved[c.caseId];
}
function render() {
  const c = DATA.cases[index], e = entry(c);
  pick.value = index;
  const hits = new Set(c.items.flatMap(it => it.lines));
  document.getElementById("src").innerHTML = c.lines.map((l, i) =>
    `<div id="L${i}" class="${hits.has(i) ? "hit" : ""}"><span class="no">${i + 1}</span>${esc(l)}</div>`).join("");
  document.getElementById("form").innerHTML = "<b>정답 (초안은 규칙 결과 — 원문과 다르면 고치기, 파란 칸 = 고친 칸)</b>" +
    DATA.keys.map(k => `<label>${esc(DATA.labels[k])}</label><input data-k="${k}" value="${esc(e.values[k])}"
      class="${e.values[k] !== show(c.draft[k]) ? "changed" : ""}">`).join("");
  document.querySelectorAll("#form input").forEach(inp => inp.oninput = () => {
    e.values[inp.dataset.k] = inp.value;
    inp.classList.toggle("changed", inp.value !== show(c.draft[inp.dataset.k]));
    save();
  });
  document.getElementById("items").innerHTML = c.items.map(it => `<tr><td>${esc(it.field)}</td><td>${esc(it.value)}</td>
    <td class="ev" data-l="${it.lines[0] ?? ""}">${esc(it.evidence)}${it.lines.length ? " (" + (it.lines[0] + 1) + "줄)" : ""}</td></tr>`).join("");
  document.querySelectorAll("td.ev").forEach(td => td.onclick = () => {
    if (td.dataset.l === "") return;
    document.querySelectorAll(".focus").forEach(x => x.classList.remove("focus"));
    const line = document.getElementById("L" + td.dataset.l);
    line.classList.add("focus"); line.scrollIntoView({block: "center"});
  });
  document.getElementById("reviewed").checked = e.reviewed;
  const done = DATA.cases.filter(x => saved[x.caseId]?.reviewed).length;
  document.getElementById("progress").innerHTML = `<span class="done">${done}</span> / ${DATA.cases.length}건 검토`;
}
document.getElementById("reviewed").onchange = ev => { entry(DATA.cases[index]).reviewed = ev.target.checked; save(); render(); };
pick.onchange = () => { index = +pick.value; render(); };
document.getElementById("prev").onclick = () => { index = Math.max(0, index - 1); render(); };
document.getElementById("next").onclick = () => { index = Math.min(DATA.cases.length - 1, index + 1); render(); };
document.getElementById("export").onclick = () => {
  DATA.cases.forEach(entry);
  const blob = new Blob([JSON.stringify(saved, null, 2)], {type: "application/json"});
  const a = document.createElement("a"); a.href = URL.createObjectURL(blob); a.download = "gold_reviewed.json"; a.click();
};
render();
</script></body></html>
"""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    build_parser = sub.add_parser("build")
    build_parser.add_argument("--docx-dir", type=Path, required=True)
    build_parser.add_argument("--eval-dir", type=Path, required=True)
    import_parser = sub.add_parser("import")
    import_parser.add_argument("--file", type=Path, required=True)
    import_parser.add_argument("--eval-dir", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "build":
        build(args.docx_dir, args.eval_dir)
    else:
        import_reviews(args.file, args.eval_dir)


if __name__ == "__main__":
    main()
