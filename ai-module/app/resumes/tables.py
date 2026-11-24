"""표 양식 이력서를 규칙으로 읽는다 (LLM 안 씀).

1. 머리글 줄 찾기: 칸 이름 대응표(LABELS)에 있는 글자가 줄의 절반 넘게 차지하면 머리글.
   한 줄에 여러 묶음(예: 교육명·기관·시작일·종료일 | 자격증명·취득일)이 있으면, 기준 칸(ANCHORS) 오른쪽의
   애매한 칸(기간·시작일 …)은 가장 가까운 왼쪽 기준 칸의 묶음으로 본다.
2. 두 줄 머리글: 다음 줄에 하위 칸 이름(기종·OS·언어·DBMS·TOOL / 산업·응용 / 시작일·종료일)이 있으면 합친다.
3. 자료 줄: 머리글 아래 줄을 묶음별로 읽는다. 프로젝트명·기간이 비고 나머지만 있는 줄은 앞 프로젝트에 이어 붙인다.
4. 기본 정보(성명·생년월일·등급)는 "칸 이름 | 값" 모양에서 읽는다.
값마다 근거(evidence)는 원문에 그대로 있는 칸 글자나 줄 글자다.
"""
import re
from dataclasses import dataclass, field
from datetime import date
from typing import Dict, List, Optional, Tuple

from app.resumes.dictionary import normalize_cell, normalize_industry
from app.resumes.docx_reader import Cell, DocxDocument, Paragraph, Table, distinct_cells, row_line
from app.resumes.periods import EMPTY_PERIOD, Period, expand_year, parse_period, parse_single_month

PROJECT, CAREER, EDUCATION, CERT, OWNED_SKILL = "project", "career", "education", "cert", "owned_skill"

# 묶음을 시작하는 칸 이름
ANCHORS: Dict[str, Tuple[str, ...]] = {
    PROJECT: ("프로젝트명(업무명)", "프로젝트명/업무명", "프로젝트명", "프로젝트", "업무명", "사업명", "과제명", "프로젝트명(사업명)"),
    CAREER: ("회사명", "근무처", "직장명", "근무회사명", "회사"),
    EDUCATION: ("교육명", "교육과정", "과정명", "교육과정명", "학교명"),
    CERT: ("자격증명", "자격증", "자격명", "자격/면허", "자격면허", "자격증/면허", "자격사항", "자격종류"),
    OWNED_SKILL: ("보유기술", "특수기술", "보유기술및외국어능력", "기술명", "보유기술및외국어", "보유기술명"),
}

# 묶음 안의 칸 이름 → 필드
FIELDS: Dict[str, Dict[str, Tuple[str, ...]]] = {
    PROJECT: {
        "period": ("참여기간", "기간", "수행기간", "투입기간", "프로젝트기간", "참여기간(년월)", "근무기간"),
        "start": ("시작일", "시작", "투입일", "시작년월"),
        "end": ("종료일", "종료", "철수일", "종료년월"),
        "client": ("고객사", "발주처", "고객", "발주기관", "고객사(발주처)", "발주처(고객사)"),
        "employer": ("근무회사", "소속회사", "소속", "수행사", "근무처", "수행회사"),
        "industry": ("개발분야", "개별분야", "산업", "업종", "분야", "산업분야"),
        "duty": ("응용", "수행업무", "담당업무", "업무내용", "주요업무", "업무", "응용분야"),
        "role": ("역할", "담당", "직무", "담당역할", "역활"),
        "env": ("개발환경", "환경", "개발환경및기술"),
        "machines": ("기종", "h/w", "hw", "하드웨어", "서버"),
        "os": ("os", "o.s", "o/s", "운영체제"),
        "languages": ("언어", "개발언어", "language", "프레임워크", "framework", "언어/프레임워크"),
        "dbms": ("dbms", "db", "데이터베이스"),
        "tools": ("tool", "tools", "툴", "개발도구", "도구", "개발툴"),
        "middleware": ("was", "미들웨어", "middleware"),
        "others": ("통신", "기타", "network", "네트워크"),
    },
    CAREER: {
        "period": ("기간", "근무기간", "재직기간", "근무년월"),
        "start": ("입사일", "시작일", "입사"),
        "end": ("퇴사일", "종료일", "퇴사"),
        "position": ("직위", "직급", "직책", "최종직위"),
        "duty": ("담당업무", "업무", "주요업무", "담당"),
    },
    EDUCATION: {
        "institution": ("기관", "교육기관", "기관명", "교육기관명", "주관"),
        "period": ("기간", "교육기간", "재학기간"),
        "start": ("시작일", "입학", "시작", "입학일"),
        "end": ("종료일", "졸업", "종료", "졸업일"),
    },
    CERT: {
        "acquired": ("취득일", "취득일자", "취득년월", "취득년도", "취득시기", "발급일", "취득년월일"),
        "issuer": ("발급기관", "시행기관", "발행처"),
    },
    OWNED_SKILL: {
        "level": ("숙련도", "수준", "level"),
    },
}

ENV_FIELDS = {"machines": "MACHINE", "os": "OS", "languages": "LANGUAGE", "dbms": "DBMS", "tools": "TOOL",
              "middleware": "MIDDLEWARE", "others": "OTHER", "env": "OTHER"}
SUB_HEADER_FIELDS = {"start", "end", "industry", "duty", "machines", "os", "languages", "dbms", "tools", "middleware",
                     "others"}

NAME_LABELS = ("성명", "이름", "성명(한글)", "한글성명", "성명(국문)", "국문성명")
BIRTH_LABELS = ("생년월일", "생년", "출생", "생일", "출생년도", "생년월일(나이)")
GRADE_LABELS = ("등급", "기술등급", "기술자등급", "기술자등급(kosa)", "kosa등급", "기술등급(kosa)")
GRADES = {"초": "JUNIOR", "중": "INTERMEDIATE", "고": "ADVANCED", "특": "EXPERT"}
GRADE_TEXT_RE = re.compile(r"기술\s*등급\s*[:：]?\s*(초|중|고|특)\s*급|(초|중|고|특)급\s*(?:기술자|개발자|기술인|엔지니어)")
GRADE_VALUE_RE = re.compile(r"(초|중|고|특)\s*급")
NAME_TEXT_RE = re.compile(r"(?:성[ \t]*명|이[ \t]*름)[ \t]*[:：][ \t]*([가-힣](?:[ \t]?[가-힣]){1,4})(?![가-힣])")
BIRTH_TEXT_RE = re.compile(r"(?:생\s*년\s*월\s*일|출\s*생)\s*[:：]?\s*((?:19|20)\d{2}|\d{2})(?=\s*[./년\-])")
YEAR4_RE = re.compile(r"(?<!\d)((?:19|20)\d{2})(?!\d)")
YEAR8_RE = re.compile(r"^\s*((?:19|20)\d{2})\d{4}(?!\d)")   # 19850312
YEAR2_RE = re.compile(r"^\s*(\d{2})(?:\s*[./년\-]|\d{4}(?!\d))")   # 85.03.12 / 85년 / 850312


def label_key(text: str) -> str:
    return re.sub(r"[\s:：*※·]+", "", text).lower()


def _label_index() -> Dict[str, List[Tuple[str, str]]]:
    index: Dict[str, List[Tuple[str, str]]] = {}
    for section, names in ANCHORS.items():
        for name in names:
            index.setdefault(label_key(name), []).append((section, "name"))
    for section, fields in FIELDS.items():
        for field_name, names in fields.items():
            for name in names:
                index.setdefault(label_key(name), []).append((section, field_name))
    return index


LABEL_INDEX = _label_index()
ANCHOR_KEYS = {label_key(name): section for section, names in ANCHORS.items() for name in names}
OTHER_LABEL_KEYS = {label_key(name) for name in (*NAME_LABELS, *BIRTH_LABELS, *GRADE_LABELS, "성별", "주소", "전화",
                                                 "연락처", "주민등록번호", "병역", "결혼", "부서", "직위", "학력")}


@dataclass
class RuleResult:
    name: Optional[dict] = None
    birth_year: Optional[dict] = None
    grade: Optional[dict] = None
    certifications: List[dict] = field(default_factory=list)
    educations: List[dict] = field(default_factory=list)
    careers: List[dict] = field(default_factory=list)
    projects: List[dict] = field(default_factory=list)
    owned_skills: List[Tuple[str, str]] = field(default_factory=list)   # (칸 글자, 숙련도 칸 글자)
    project_table_found: bool = False


@dataclass
class Header:
    columns: List[Optional[Tuple[str, str]]]      # 열마다 (묶음, 필드)
    width: int


# ---------- 머리글 ----------

def classify_header(row: List[Cell]) -> Optional[Header]:
    cells = [label_key(cell.text) for cell in row]
    distinct = [label_key(cell.text) for cell in distinct_cells(row) if cell.text.strip()]
    if len(distinct) < 2:
        return None
    recognized = [key for key in distinct if key in LABEL_INDEX]
    if len(set(recognized)) < 2 or len(recognized) / len(distinct) <= 0.5:
        return None
    if not any(key in ANCHOR_KEYS for key in recognized) and not _project_like(recognized):
        return None

    columns: List[Optional[Tuple[str, str]]] = []
    current: Optional[str] = None
    for key in cells:
        if key in ANCHOR_KEYS:
            current = ANCHOR_KEYS[key]
            columns.append((current, "name"))
            continue
        options = LABEL_INDEX.get(key, [])
        chosen = None
        if current:
            chosen = next((option for option in options if option[0] == current), None)
        if chosen is None and options and current is None:
            chosen = options[0]
        columns.append(chosen)
    return Header(columns=columns, width=len(row))


def _project_like(keys: List[str]) -> bool:
    """기준 칸 없이 '참여기간·고객사·역할'처럼 프로젝트 칸만 있는 머리글."""
    project_fields = {option[1] for key in keys for option in LABEL_INDEX.get(key, []) if option[0] == PROJECT}
    return len(project_fields & {"period", "client", "employer", "role", "env"}) >= 3


def apply_sub_header(header: Header, row: List[Cell]) -> bool:
    """다음 줄이 하위 칸 이름이면 열 정보를 덮어쓴다. 덮어썼으면 True."""
    updates: Dict[int, Tuple[str, str]] = {}
    for index, cell in enumerate(row[:header.width]):
        key = label_key(cell.text)
        if not key:
            continue
        top = header.columns[index] if index < len(header.columns) else None
        section = top[0] if top else None
        options = [option for option in LABEL_INDEX.get(key, []) if option[1] in SUB_HEADER_FIELDS]
        if section:
            options = [option for option in options if option[0] == section] or []
        if options:
            updates[index] = options[0]
        elif key not in LABEL_INDEX:
            return False   # 칸 이름이 아닌 글자가 있으면 자료 줄
    if len(updates) < 2:
        return False
    for index, value in updates.items():
        header.columns[index] = value
    return True


# ---------- 자료 줄 ----------

def read_row(header: Header, row: List[Cell]) -> Dict[str, Dict[str, List[Cell]]]:
    """묶음 → 필드 → 칸들 (가로 병합으로 펼친 같은 칸은 한 번만)."""
    grouped: Dict[str, Dict[str, List[Cell]]] = {}
    for index, cell in enumerate(row):
        if index >= len(header.columns) or header.columns[index] is None:
            continue
        section, field_name = header.columns[index]
        cells = grouped.setdefault(section, {}).setdefault(field_name, [])
        if not any(existing is cell for existing in cells):
            cells.append(cell)
    return grouped


def joined(cells: List[Cell]) -> Optional[str]:
    text = " ".join(cell.text for cell in cells if cell.text.strip()).strip()
    return text or None


def first_text(cells: List[Cell]) -> str:
    """근거용: 여러 칸이면 첫 칸 글자 (원문에서는 칸 사이에 " | "가 들어가므로 이어 붙이지 않는다)."""
    return next(cell.text for cell in cells if cell.text.strip())


def is_blank_value(text: Optional[str]) -> bool:
    return text is None or label_key(text) in {"", "-", "–", "—", "없음", "."}


def extract_rules(document: DocxDocument, reference: date) -> RuleResult:
    result = RuleResult()
    header: Optional[Header] = None
    for block in document.blocks:
        if not isinstance(block, Table):
            continue
        if header is not None and (not block.rows or len(block.rows[0]) != header.width):
            header = None
        _read_basic_info(block, result, reference)
        last_project: Optional[dict] = None
        row_index = 0
        while row_index < len(block.rows):
            row = block.rows[row_index]
            found = classify_header(row)
            if found is not None:
                header = found
                last_project = None
                if row_index + 1 < len(block.rows) and apply_sub_header(header, block.rows[row_index + 1]):
                    row_index += 1
                if any(column and column[0] == PROJECT for column in header.columns):
                    result.project_table_found = True
                row_index += 1
                continue
            if header is not None and row_line(row):
                last_project = _read_data_row(header, row, result, last_project, reference)
            row_index += 1

    _read_basic_text(document, result, reference)
    return result


def _read_data_row(header: Header, row: List[Cell], result: RuleResult, last_project: Optional[dict],
                   reference: date) -> Optional[dict]:
    grouped = read_row(header, row)
    line = row_line(row)
    for section, fields in grouped.items():
        values = {name: joined(cells) for name, cells in fields.items()}
        if section == PROJECT:
            last_project = _project_from_row(fields, values, line, last_project, result, reference)
        elif section == CAREER and not is_blank_value(values.get("name")):
            result.careers.append({
                "company": values.get("name"),
                "position": values.get("position"),
                "duty": values.get("duty"),
                "period": _period(values, reference).as_dict(),
                "evidence": line,
                "source": "RULE",
            })
        elif section == EDUCATION and not is_blank_value(values.get("name")):
            result.educations.append({
                "name": values["name"],
                "institution": values.get("institution"),
                "period": _period(values, reference).as_dict(),
                "evidence": line,
                "source": "RULE",
            })
        elif section == CERT and not is_blank_value(values.get("name")):
            result.certifications.append({
                "name": values["name"],
                "acquiredOn": parse_single_month(values.get("acquired"), reference),
                "evidence": first_text(fields["name"]),
                "source": "RULE",
            })
        elif section == OWNED_SKILL and not is_blank_value(values.get("name")):
            for cell in fields["name"]:
                if not is_blank_value(cell.text):
                    result.owned_skills.append((cell.text, values.get("level") or ""))
    return last_project


def _period(values: Dict[str, Optional[str]], reference: date) -> Period:
    period = parse_period(values.get("period"), reference)
    if period.start is None and values.get("start"):
        if parse_single_month(values["start"], reference) is None:
            return period
        return parse_period(f"{values['start']} ~ {values.get('end') or ''}", reference)
    return period


def _project_from_row(fields: Dict[str, List[Cell]], values: Dict[str, Optional[str]], line: str,
                      last_project: Optional[dict], result: RuleResult, reference: date) -> Optional[dict]:
    name_cells = fields.get("name", [])
    name_merged = bool(name_cells) and all(cell.merged for cell in name_cells)
    period = _period(values, reference)
    environment = _environment(fields)

    starts_new = (not is_blank_value(values.get("name")) and not name_merged) or period.start is not None
    if not starts_new and last_project is not None:
        _continue_project(last_project, fields, values, environment, line, reference)
        last_project["_cells"].extend(_env_cells(fields))
        return last_project
    if not starts_new:
        return last_project

    project = {
        "name": None if is_blank_value(values.get("name")) else values["name"],
        "period": period.as_dict(),
        "client": _value(values.get("client")),
        "employer": _value(values.get("employer")),
        "role": _value(values.get("role")),
        "duty": _value(values.get("duty")),
        "industry": _value(values.get("industry")),
        "environment": environment,
        "evidence": line,
        "source": "RULE",
        "_cells": _env_cells(fields),
        "_industry_evidence": first_text(fields["industry"]) if _value(values.get("industry")) else None,
    }
    result.projects.append(project)
    return project


def _continue_project(project: dict, fields: Dict[str, List[Cell]], values: Dict[str, Optional[str]],
                      environment: Dict[str, List[str]],
                      line: str, reference: date) -> None:
    for key in ("client", "employer", "role", "duty", "industry"):
        if project[key] is None and _value(values.get(key)):
            project[key] = _value(values.get(key))
            if key == "industry":
                project["_industry_evidence"] = first_text(fields["industry"])
    for key, tokens in environment.items():
        project["environment"][key].extend(token for token in tokens if token not in project["environment"][key])
    if project["period"]["start"] and project["period"]["end"] is None and values.get("period"):
        end = parse_single_month(values["period"], reference)
        if end and end >= project["period"]["start"]:
            project["period"] = parse_period(f"{project['period']['start'].replace('-', '.')} ~ {end.replace('-', '.')}",
                                             reference).as_dict()
    project["evidence"] = f"{project['evidence']}\n{line}"


def _value(text: Optional[str]) -> Optional[str]:
    return None if is_blank_value(text) else text


def _environment(fields: Dict[str, List[Cell]]) -> Dict[str, List[str]]:
    environment = {"machines": [], "os": [], "languages": [], "dbms": [], "tools": [], "others": []}
    target = {"machines": "machines", "os": "os", "languages": "languages", "dbms": "dbms", "tools": "tools",
              "middleware": "others", "others": "others", "env": "others"}
    for field_name, key in target.items():
        for cell in fields.get(field_name, []):
            if cell.text and not is_blank_value(cell.text) and cell.text not in environment[key]:
                environment[key].append(cell.text)
    return environment


def _env_cells(fields: Dict[str, List[Cell]]) -> List[Tuple[str, str]]:
    """(칸 글자, 분류 힌트) — 기술명 통일에 쓴다."""
    cells = []
    for field_name, category in ENV_FIELDS.items():
        for cell in fields.get(field_name, []):
            if cell.text and not is_blank_value(cell.text):
                cells.append((cell.text, category))
    return cells


# ---------- 기본 정보 ----------

def _value_after(row: List[Cell], index: int) -> Optional[Cell]:
    cells = distinct_cells(row)
    position = next((i for i, cell in enumerate(cells) if cell is row[index]), None)
    if position is None:
        return None
    for cell in cells[position + 1:]:
        key = label_key(cell.text)
        if not key or cell.merged:
            continue
        if key in OTHER_LABEL_KEYS or key in LABEL_INDEX:
            return None
        return cell
    return None


def _value_below(table: Table, row_index: int, index: int) -> Optional[Cell]:
    """칸 이름 줄 아래에 값 줄이 있는 모양 (성명 | 성별 | 생년월일 / 가상인 | 남 | 1990.01.01)."""
    if row_index + 1 >= len(table.rows) or index >= len(table.rows[row_index + 1]):
        return None
    cell = table.rows[row_index + 1][index]
    key = label_key(cell.text)
    if not key or cell.merged or key in OTHER_LABEL_KEYS or key in LABEL_INDEX:
        return None
    return cell


def _read_basic_info(table: Table, result: RuleResult, reference: date) -> None:
    for row_index, row in enumerate(table.rows):
        for index, cell in enumerate(row):
            if index > 0 and row[index - 1] is cell:
                continue
            key = label_key(cell.text)
            if not key:
                continue
            if result.name is None and key in {label_key(label) for label in NAME_LABELS}:
                value_cell = _value_after(row, index) or _value_below(table, row_index, index)
                name = clean_name(value_cell.text) if value_cell else None
                if name:
                    result.name = {"value": name, "evidence": value_cell.text, "source": "RULE"}
            elif result.birth_year is None and key in {label_key(label) for label in BIRTH_LABELS}:
                value_cell = _value_after(row, index) or _value_below(table, row_index, index)
                if value_cell:
                    result.birth_year = birth_year_from(value_cell.text, reference)
            elif result.grade is None and key in {label_key(label) for label in GRADE_LABELS}:
                value_cell = _value_after(row, index) or _value_below(table, row_index, index)
                match = GRADE_VALUE_RE.search(value_cell.text) if value_cell else None
                if match:
                    result.grade = {"value": GRADES[match.group(1)], "evidence": match.group(0), "source": "RULE"}


def clean_name(text: str) -> Optional[str]:
    """값 칸에서 한글 이름(2~5자)만 꺼낸다. '홍 길 동'처럼 띄어 쓴 것도 붙여서 본다."""
    match = re.match(r"[ \t]*([가-힣](?:[ \t]?[가-힣]){1,4})(?![가-힣])", text)
    if not match:
        return None
    name = re.sub(r"\s+", "", match.group(1))
    return name if 2 <= len(name) <= 5 else None


def birth_year_from(text: str, reference: date) -> Optional[dict]:
    """생년월일 칸에서 연도만 꺼낸다. 근거도 연도 글자만 둔다(월·일은 남기지 않음)."""
    match = YEAR4_RE.search(text) or YEAR8_RE.match(text)
    if match:
        return {"value": int(match.group(1)), "evidence": match.group(1), "source": "RULE"}
    match = YEAR2_RE.match(text)
    if match:
        return {"value": expand_year(int(match.group(1)), reference), "evidence": match.group(1), "source": "RULE"}
    return None


def _read_basic_text(document: DocxDocument, result: RuleResult, reference: date) -> None:
    text = "\n".join(block.text for block in document.blocks if isinstance(block, Paragraph))
    if result.name is None and (match := NAME_TEXT_RE.search(text)):
        name = clean_name(match.group(1))
        if name:
            result.name = {"value": name, "evidence": match.group(1), "source": "RULE"}
    if result.birth_year is None and (match := BIRTH_TEXT_RE.search(text)):
        result.birth_year = birth_year_from(match.group(1) + ".", reference)
        if result.birth_year:
            result.birth_year["evidence"] = match.group(1)
    if result.grade is None:
        full = document.source_text()
        if match := GRADE_TEXT_RE.search(full):
            letter = match.group(1) or match.group(2)
            result.grade = {"value": GRADES[letter], "evidence": match.group(0), "source": "RULE"}


def project_skills(projects: List[dict]) -> List[Tuple[dict, List]]:
    """프로젝트마다 (프로젝트, 통일한 기술 목록)."""
    pairs = []
    for project in projects:
        matches = []
        for text, category in project.get("_cells", []):
            matches.extend((text, match) for match in normalize_cell(text, category))
        pairs.append((project, matches))
    return pairs


def industries_from_projects(projects: List[dict]) -> List[dict]:
    industries: Dict[str, dict] = {}
    for project in projects:
        for name in normalize_industry(project.get("industry")):
            evidence = project.get("_industry_evidence") or project["industry"]
            industries.setdefault(name, {"name": name, "evidence": evidence, "source": "RULE"})
    return list(industries.values())


__all__ = ["RuleResult", "extract_rules", "project_skills", "industries_from_projects", "EMPTY_PERIOD"]
