"""이력서 엔진: 워드 파일·텍스트 → resume_profile 초안 (DRAFT).

순서: docx 읽기 → 양식 판별(표 머리글) → 표 규칙 → (줄글·남은 칸만) 로컬 LLM + 근거 대조
      → 기술명 통일·총 경력·투입 가능일(규칙) → 스키마 검사.
mode: rule=규칙만, llm=로컬 LLM만(평가 비교용), hybrid=규칙 + 남은 칸만 LLM (기본).
"""
import json
import time
from dataclasses import dataclass, field
from datetime import date
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, List, Optional

from jsonschema import Draft202012Validator

from app.resumes.dictionary import normalize_cell, scan_known_skills, unique
from app.resumes.docx_reader import DocxDocument, Paragraph, distinct_cells, read_docx
from app.resumes.llm_extract import GenerateFn, LlmResult, extract_with_llm
from app.resumes.periods import Period, first_day_after, period_bounds, total_months
from app.resumes.tables import RuleResult, extract_rules, industries_from_projects, project_skills

SCHEMA_PATH = Path(__file__).resolve().parents[3] / "contracts" / "resume_profile.schema.json"
MODES = ("rule", "llm", "hybrid")
LIST_FIELDS = ("certifications", "educations", "careers", "projects")


@dataclass
class ResumeResult:
    profile: Dict[str, Any]
    llm_provider: str = "rule"
    llm_model: Optional[str] = None
    fallback_reason: Optional[str] = None
    dropped_items: List[Dict[str, Any]] = field(default_factory=list)
    elapsed_ms: int = 0
    llm_calls: int = 0
    source_text: str = ""   # 근거(evidence)가 가리키는 원문. 확인 화면에서 근거를 강조할 때 쓴다


def extract_resume(*, content: Optional[bytes] = None, text: Optional[str] = None, profile_id: str = "rp_draft",
                   source_refs: Optional[List[Dict[str, Any]]] = None, reference_date: Optional[date] = None,
                   mode: str = "hybrid", generate_fn: Optional[GenerateFn] = None) -> ResumeResult:
    """content(.docx 바이트) 또는 text 중 하나. 읽을 수 없는 파일은 DocxError를 던진다."""
    if mode not in MODES:
        raise ValueError(f"mode는 {', '.join(MODES)} 중 하나여야 합니다.")
    started = time.perf_counter()
    reference = reference_date or date.today()

    if content is not None:
        document = read_docx(content)
    else:
        document = DocxDocument(blocks=[Paragraph(text=line.strip()) for line in (text or "").split("\n")])
    source = document.source_text()

    rules = extract_rules(document, reference) if mode != "llm" else RuleResult()
    doc_format = detect_format(document, extract_rules(document, reference) if mode == "llm" else rules)

    llm: Optional[LlmResult] = None
    want = llm_targets(mode, doc_format, rules)
    if want is not None and source.strip():
        llm = extract_with_llm(source, reference, generate_fn=generate_fn, want=want or None)

    profile = assemble(profile_id, source_refs, doc_format, rules, llm, source, reference, mode)
    result = ResumeResult(profile=profile, elapsed_ms=int((time.perf_counter() - started) * 1000), source_text=source)
    if llm is not None:
        result.llm_provider = llm.provider or "rule"
        result.llm_model = llm.model
        result.fallback_reason = llm.error
        result.dropped_items = llm.dropped
        result.llm_calls = llm.calls

    errors = schema_errors(profile)
    if errors:  # 규칙 검증을 거쳤으므로 정상이면 일어나지 않는다
        raise ValueError(f"resume_profile 스키마 검사 실패: {errors[0]}")
    return result


def detect_format(document: DocxDocument, rules: RuleResult) -> str:
    if rules.project_table_found and rules.projects:
        return "TABLE_CARD"
    table_chars = sum(len(cell.text) for table in document.tables for row in table.rows
                      for cell in distinct_cells(row))
    paragraph_chars = sum(len(block.text) for block in document.blocks if isinstance(block, Paragraph))
    if document.tables and table_chars >= 0.3 * (table_chars + paragraph_chars):
        return "OTHER_TABLE"
    return "FREE_TEXT"


def llm_targets(mode: str, doc_format: str, rules: RuleResult) -> Optional[set]:
    """LLM에 물을 항목. None이면 부르지 않음, 빈 set이면 전부."""
    if mode == "rule":
        return None
    if mode == "llm":
        return set()
    want = set()
    if rules.name is None:
        want.add("name")
    if rules.birth_year is None:
        want.add("birthYear")
    if doc_format == "TABLE_CARD":
        # 표 양식은 규칙으로 충분하다. 이름·생년이 비었을 때만 앞부분을 묻는다
        return want or None
    if rules.grade is None:
        want.add("grade")
    for name in LIST_FIELDS:
        if not getattr(rules, name):
            want.add(name)
    want.add("industries")
    return want


# ---------- 합치기 ----------

def assemble(profile_id: str, source_refs: Optional[List[Dict[str, Any]]], doc_format: str, rules: RuleResult,
             llm: Optional[LlmResult], source: str, reference: date, mode: str) -> Dict[str, Any]:
    def pick(rule_value, llm_value):
        return rule_value if rule_value is not None else llm_value

    llm = llm or LlmResult()
    projects = [{key: value for key, value in project.items() if not key.startswith("_")} for project in rules.projects]
    use_llm_projects = not projects and llm.projects
    if use_llm_projects:
        projects = llm.projects

    profile: Dict[str, Any] = {
        "profileId": profile_id,
        "sourceRefs": source_refs or [{"sourceId": "src_draft", "sourceType": "MANUAL_INPUT", "fileName": None}],
        "format": doc_format,
        "name": pick(rules.name, llm.name),
        "birthYear": pick(rules.birth_year, llm.birth_year),
        "grade": pick(rules.grade, llm.grade),
        "certifications": rules.certifications or llm.certifications,
        "educations": rules.educations or llm.educations,
        "careers": rules.careers or llm.careers,
        "projects": projects,
        "skills": build_skills(rules, llm, use_llm_projects, doc_format != "TABLE_CARD" and mode != "llm", source),
        "industries": merge_industries(industries_from_projects(llm.projects if use_llm_projects else rules.projects),
                                       llm.industries),
        "totalCareer": None,
        "availableFrom": None,
        "referenceDate": reference.isoformat(),
        "missingFields": [],
        "status": "DRAFT",
        "generatedBy": "RULE",
        "llmModel": llm.model,
    }
    profile["totalCareer"] = total_career(profile, reference)
    profile["availableFrom"] = available_from(profile["projects"], reference)
    profile["missingFields"] = missing_fields(profile)
    profile["generatedBy"] = generated_by(profile, mode)
    if profile["generatedBy"] == "RULE":
        profile["llmModel"] = None
    return profile


def build_skills(rules: RuleResult, llm: LlmResult, use_llm_projects: bool, scan_text: bool,
                 source: str) -> List[Dict[str, Any]]:
    skills: Dict[str, Dict[str, Any]] = {}

    def add(name: str, category: str, known: bool, raw: str, evidence: str, source_name: str,
            project_key: Optional[int]) -> None:
        skill = skills.setdefault(name, {"name": name, "category": category, "known": known, "raw": [],
                                         "_projects": set(), "evidence": evidence, "source": source_name})
        if raw not in skill["raw"]:
            skill["raw"].append(raw)
        if project_key is not None:
            skill["_projects"].add(project_key)

    for index, (_, matches) in enumerate(project_skills(rules.projects)):
        for cell_text, match in matches:
            add(match.name, match.category, match.known, match.raw, cell_text, "RULE", index)
    for cell_text, _level in rules.owned_skills:
        for match in normalize_cell(cell_text, "OTHER"):
            add(match.name, match.category, match.known, match.raw, cell_text, "RULE", None)
    if use_llm_projects:
        for evidence, match, index in llm.skill_hits:
            add(match.name, match.category, match.known, match.raw, evidence, "LLM", index)
    if scan_text:
        # 줄글·다른 표는 사전에 있는 기술 이름을 글 전체에서도 찾는다 (규칙)
        for match in scan_known_skills(source):
            add(match.name, match.category, True, match.raw, match.raw, "RULE", None)

    result = []
    for skill in skills.values():
        skill["projectCount"] = len(skill.pop("_projects"))
        skill["raw"] = unique(skill["raw"])
        result.append(skill)
    result.sort(key=lambda item: (-item["projectCount"], not item["known"], item["name"].lower()))
    return result


def merge_industries(*groups: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    merged: Dict[str, Dict[str, Any]] = {}
    for group in groups:
        for item in group:
            merged.setdefault(item["name"], item)
    return list(merged.values())


def as_period(value: Dict[str, Any]) -> Period:
    return Period(value.get("start"), value.get("end"), bool(value.get("ongoing")))


def total_career(profile: Dict[str, Any], reference: date) -> Optional[Dict[str, Any]]:
    for basis, key in (("PROJECTS", "projects"), ("CAREERS", "careers")):
        periods = [as_period(item["period"]) for item in profile[key]]
        months, used = total_months(periods, reference)
        if used:
            return {"months": months, "basis": basis, "periodCount": used}
    return None


def available_from(projects: List[Dict[str, Any]], reference: date) -> Optional[Dict[str, Any]]:
    readable = [project for project in projects if period_bounds(as_period(project["period"]), reference)]
    if not readable:
        return None
    ongoing = [project for project in readable if project["period"]["ongoing"]]
    if not ongoing:
        return {"date": None, "basis": "NO_ONGOING_PROJECT", "evidence": None}
    with_end = [project for project in ongoing if project["period"]["end"]]
    if len(with_end) < len(ongoing):
        without_end = next(project for project in ongoing if not project["period"]["end"])
        return {"date": None, "basis": "ONGOING_WITHOUT_END", "evidence": without_end["evidence"]}
    latest = max(with_end, key=lambda project: project["period"]["end"])
    return {"date": first_day_after(latest["period"]["end"]), "basis": "ONGOING_PROJECT_END",
            "evidence": latest["evidence"]}


def missing_fields(profile: Dict[str, Any]) -> List[str]:
    missing = [name for name in ("name", "birthYear", "grade", "totalCareer", "availableFrom") if profile[name] is None]
    missing += [name for name in ("projects", "skills", "industries") if not profile[name]]
    if profile["availableFrom"] is not None and profile["availableFrom"]["date"] is None:
        missing.append("availableFrom.date")
    order = ["name", "birthYear", "grade", "projects", "skills", "industries", "totalCareer", "availableFrom",
             "availableFrom.date"]
    return sorted(missing, key=order.index)


def generated_by(profile: Dict[str, Any], mode: str) -> str:
    if mode == "llm":
        return "LLM"
    sources = set()
    for key in ("name", "birthYear", "grade"):
        if profile[key]:
            sources.add(profile[key]["source"])
    for key in (*LIST_FIELDS, "skills", "industries"):
        sources.update(item["source"] for item in profile[key])
    return "HYBRID" if "LLM" in sources else "RULE"


# ---------- 스키마 ----------

@lru_cache(maxsize=1)
def validator() -> Draft202012Validator:
    return Draft202012Validator(json.loads(SCHEMA_PATH.read_text(encoding="utf-8")))


def schema_errors(profile: Dict[str, Any]) -> List[str]:
    return [f"{list(error.path)}: {error.message}" for error in validator().iter_errors(profile)]
