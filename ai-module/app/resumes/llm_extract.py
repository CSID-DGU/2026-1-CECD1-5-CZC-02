"""줄글·다른 표 양식 이력서를 로컬 LLM(Ollama)으로 읽는다.

- 이력서는 개인정보라 **Ollama만** 부른다. 공용 router를 쓰지 않으므로 LLM_PROVIDER=openai여도 외부로 나가지 않는다.
- LLM은 값과 원문 인용만 고른다. 기간은 원문 표기(periodText)를 받아 규칙(periods.py)으로 계산하고,
  기술명은 원문 표기를 받아 사전(dictionary.py)으로 통일한다.
- 근거가 원문에 없으면 버리고, 값 글자가 원문에 없으면 null로 둔다 (지어낸 값 방지).
- Ollama 기본 문맥 길이(4096토큰)를 넘지 않도록 원문을 나눠 부르고 결과를 합친다.
"""
import os
import re
from dataclasses import dataclass, field
from datetime import date
from typing import Any, Callable, Dict, List, Optional

from app.config import OLLAMA_BASE_URL, OLLAMA_MODEL, OLLAMA_MODEL_MINUTES, OLLAMA_SEED, OLLAMA_THINK_MINUTES
from app.llm.base import LLMError, LLMRequest, LLMResponse
from app.minutes.engine import find_evidence
from app.resumes.dictionary import normalize_industry, normalize_token
from app.resumes.periods import EMPTY_PERIOD, parse_period, parse_single_month
from app.resumes.tables import GRADES, clean_name

# 이력서 모델: 비우면 회의록 모델(D-029)을 따른다. 평가 결과에 따라 바꾼다
RESUME_MODEL = os.getenv("OLLAMA_MODEL_RESUME", "").strip() or OLLAMA_MODEL_MINUTES or OLLAMA_MODEL
RESUME_TIMEOUT_SECONDS = float(os.getenv("OLLAMA_TIMEOUT_SECONDS_RESUME", "300"))
CHUNK_CHARS = int(os.getenv("RESUME_LLM_CHUNK_CHARS", "2500"))
MAX_CHUNKS = int(os.getenv("RESUME_LLM_MAX_CHUNKS", "8"))

SYSTEM_PROMPT = """너는 IT 인력 이력서에서 정보를 옮겨 적는 도우미다. 아래 규칙을 반드시 지킨다.
1. 원문에 적힌 것만 옮긴다. 원문에 없으면 null 또는 빈 배열로 둔다. 추측하지 않는다.
2. 모든 항목의 evidence에는 원문 글자를 그대로(한 글자도 바꾸지 않고) 복사한다.
3. 기간은 계산하지 말고 원문의 기간 표기 그대로 periodText에 복사한다. 예: "2021.03 ~ 2022.02", "21.3-현재"
4. skills에는 그 프로젝트에 적힌 기술 이름을 원문 표기 그대로 넣는다. 예: ["JAVA", "oracle11g"]
5. grade는 원문에 초급·중급·고급·특급이 적혀 있을 때만 채운다. 경력으로 짐작하지 않는다.
6. birthYear는 생년월일·출생 연도가 적혀 있을 때만 숫자 연도로 쓴다.
7. 이력서의 일부만 보일 수 있다. 보이는 부분에 있는 것만 적는다.

출력 형식(JSON 하나만):
{"name": {"value": "이름", "evidence": "원문"} 또는 null,
 "birthYear": {"value": 1990, "evidence": "원문"} 또는 null,
 "grade": {"value": "중급", "evidence": "원문"} 또는 null,
 "certifications": [{"name": "자격증 이름", "acquiredText": "원문 날짜 표기 또는 null", "evidence": "원문"}],
 "educations": [{"name": "교육·학교 이름", "institution": "기관 또는 null", "periodText": "원문 기간 표기 또는 null", "evidence": "원문"}],
 "careers": [{"company": "회사", "position": "직위 또는 null", "duty": "담당 업무 또는 null", "periodText": "원문 기간 표기 또는 null", "evidence": "원문"}],
 "projects": [{"name": "프로젝트 이름", "periodText": "원문 기간 표기 또는 null", "client": "고객사 또는 null", "employer": "소속 회사 또는 null", "role": "역할 또는 null", "duty": "수행 업무 또는 null", "industry": "산업 분야 또는 null", "skills": ["원문 기술 표기"], "evidence": "원문"}],
 "industries": [{"name": "산업 이름(예: 금융, 공공, 제조, 통신, 유통)", "evidence": "원문"}]}

예시 (가짜 이력서):
원문:
샘플 개발자 이력서
이름: 가상인 / 1990년생 / 중급 개발자
2021.03 ~ 2022.02 예시은행 차세대 계정계 구축 (모의정보 소속, 개발) - JAVA, Spring, oracle11g
출력:
{"name": {"value": "가상인", "evidence": "이름: 가상인"}, "birthYear": {"value": 1990, "evidence": "1990년생"}, "grade": {"value": "중급", "evidence": "중급 개발자"}, "certifications": [], "educations": [], "careers": [], "projects": [{"name": "예시은행 차세대 계정계 구축", "periodText": "2021.03 ~ 2022.02", "client": "예시은행", "employer": "모의정보", "role": "개발", "duty": null, "industry": null, "skills": ["JAVA", "Spring", "oracle11g"], "evidence": "2021.03 ~ 2022.02 예시은행 차세대 계정계 구축 (모의정보 소속, 개발) - JAVA, Spring, oracle11g"}], "industries": [{"name": "금융", "evidence": "예시은행 차세대 계정계 구축"}]}"""

OUTPUT_SCHEMA: Dict[str, Any] = {"type": "object"}
GRADE_WORDS = {"초급": "초", "중급": "중", "고급": "고", "특급": "특"}

GenerateFn = Callable[[LLMRequest], LLMResponse]


@dataclass
class LlmResult:
    name: Optional[dict] = None
    birth_year: Optional[dict] = None
    grade: Optional[dict] = None
    certifications: List[dict] = field(default_factory=list)
    educations: List[dict] = field(default_factory=list)
    careers: List[dict] = field(default_factory=list)
    projects: List[dict] = field(default_factory=list)     # environment에 skills 원문 표기를 담는다
    industries: List[dict] = field(default_factory=list)
    skill_hits: List[tuple] = field(default_factory=list)  # (근거, SkillMatch, 프로젝트 순번)
    model: Optional[str] = None
    provider: Optional[str] = None
    error: Optional[str] = None
    dropped: List[dict] = field(default_factory=list)
    calls: int = 0


def ollama_generate(request: LLMRequest) -> LLMResponse:
    from app.llm.ollama_provider import OllamaProvider

    think = OLLAMA_THINK_MINUTES if RESUME_MODEL.startswith("gemma4") or RESUME_MODEL == OLLAMA_MODEL_MINUTES else None
    provider = OllamaProvider(base_url=OLLAMA_BASE_URL, model=RESUME_MODEL, timeout_seconds=RESUME_TIMEOUT_SECONDS,
                              seed=OLLAMA_SEED, think=think, task_models={}, task_think={})
    return provider.generate(request)


def split_chunks(source: str, size: int = CHUNK_CHARS) -> List[str]:
    """줄 단위로 size 글자 안팎씩 나눈다. 한 줄이 size보다 길면 그 줄만 따로 둔다."""
    chunks, current = [], ""
    for line in source.split("\n"):
        if current and len(current) + len(line) + 1 > size:
            chunks.append(current)
            current = ""
        current = f"{current}\n{line}" if current else line
    if current.strip():
        chunks.append(current)
    return chunks


def extract_with_llm(source: str, reference: date, generate_fn: Optional[GenerateFn] = None,
                     want: Optional[set] = None) -> LlmResult:
    """want: 필요한 항목만 (예: {"name", "birthYear"}). None이면 전부."""
    generate_fn = generate_fn or ollama_generate
    result = LlmResult()
    chunks = split_chunks(source)
    if want and want <= {"name", "birthYear", "grade"}:
        chunks = chunks[:1]   # 기본 정보는 보통 첫 부분에 있다
    for chunk in chunks[:MAX_CHUNKS]:
        try:
            response = generate_fn(LLMRequest(
                task="resume",
                system=SYSTEM_PROMPT,
                user=f"원문:\n{chunk}\n출력:",
                json_schema=OUTPUT_SCHEMA,
                schema_name="resume_profile",
                max_output_tokens=1500,
                temperature=0.0,
            ))
        except LLMError as exception:
            result.error = str(exception)
            continue
        result.calls += 1
        result.model, result.provider = response.model, response.provider
        data = response.data if isinstance(response.data, dict) else {}
        _merge(result, data, source, reference, want)
    if len(chunks) > MAX_CHUNKS:
        result.error = (result.error + " / " if result.error else "") + f"원문이 길어 앞 {MAX_CHUNKS}개 부분만 읽음"
    return result


# ---------- 검증 ----------

def _squash(text: str) -> str:
    return re.sub(r"\s+", "", text).lower()


def exact_span(value: str, source: str) -> Optional[str]:
    """값을 원문에서 찾아 원문 쪽 글자를 돌려준다. 공백·대소문자 차이만 허용한다."""
    needle = re.sub(r"\s+", "", value)
    if not needle:
        return None
    pattern = r"\s*".join(re.escape(char) for char in needle)
    match = re.search(pattern, source, re.IGNORECASE)
    return match.group(0) if match else None


def in_source(value: Any, source: str) -> Optional[str]:
    """값 글자가 원문에 있으면 원문 쪽 글자를, 없으면 None (지어낸 값 방지)."""
    if not isinstance(value, str):
        return None
    value = value.strip()
    if not value or value.lower() in {"null", "none", "없음", "-"}:
        return None
    return exact_span(value, source)


def _evidence(raw: Any, source: str) -> Optional[str]:
    return find_evidence(raw, source) if isinstance(raw, str) and raw.strip() else None


def _period(raw: Any, source: str, reference: date):
    text = in_source(raw, source)
    return parse_period(text, reference) if text else EMPTY_PERIOD


def _drop(result: LlmResult, field_name: str, raw: Any, reason: str) -> None:
    raw = raw if isinstance(raw, dict) else {}
    result.dropped.append({"field": field_name, "text": raw.get("name") or raw.get("value") or raw.get("company"),
                           "evidence": raw.get("evidence") if isinstance(raw.get("evidence"), str) else None,
                           "reason": reason})


def _merge(result: LlmResult, data: Dict[str, Any], source: str, reference: date, want: Optional[set]) -> None:
    def wanted(name: str) -> bool:
        return want is None or name in want

    if wanted("name") and result.name is None and isinstance(data.get("name"), dict):
        raw = data["name"]
        evidence = _evidence(raw.get("evidence"), source)
        name = clean_name(raw["value"]) if isinstance(raw.get("value"), str) else None
        if evidence and name and name in _squash(evidence):
            result.name = {"value": name, "evidence": evidence, "source": "LLM"}
        else:
            _drop(result, "name", raw, "근거가 원문에 없거나 이름이 근거에 없음")

    if wanted("birthYear") and result.birth_year is None and isinstance(data.get("birthYear"), dict):
        raw = data["birthYear"]
        evidence = _evidence(raw.get("evidence"), source)
        value = raw.get("value")
        value = int(value) if isinstance(value, (int, float)) or (isinstance(value, str) and value.isdigit()) else None
        if evidence and value and 1940 <= value <= reference.year - 15:
            year_text = str(value) if str(value) in evidence else (str(value)[2:] if str(value)[2:] in evidence else None)
            if year_text:
                # 월·일이 근거에 남지 않도록 연도 글자만 근거로 둔다
                result.birth_year = {"value": value, "evidence": year_text, "source": "LLM"}
        if result.birth_year is None:
            _drop(result, "birthYear", raw, "연도가 근거에 없음")

    if wanted("grade") and result.grade is None and isinstance(data.get("grade"), dict):
        raw = data["grade"]
        evidence = _evidence(raw.get("evidence"), source)
        letter = GRADE_WORDS.get(str(raw.get("value") or "").strip())
        if evidence and letter and f"{letter}급" in _squash(evidence):
            result.grade = {"value": GRADES[letter], "evidence": evidence, "source": "LLM"}
        elif raw.get("value"):
            _drop(result, "grade", raw, "등급이 근거에 없음")

    if wanted("certifications"):
        for raw in _items(data.get("certifications")):
            evidence = _evidence(raw.get("evidence"), source)
            name = in_source(raw.get("name"), source)
            if not (evidence and name):
                _drop(result, "certifications", raw, "근거 또는 이름이 원문에 없음")
                continue
            acquired = in_source(raw.get("acquiredText"), source)
            result.certifications.append({"name": name, "acquiredOn": parse_single_month(acquired, reference),
                                          "evidence": evidence, "source": "LLM"})

    if wanted("educations"):
        for raw in _items(data.get("educations")):
            evidence = _evidence(raw.get("evidence"), source)
            name = in_source(raw.get("name"), source)
            if not (evidence and name):
                _drop(result, "educations", raw, "근거 또는 이름이 원문에 없음")
                continue
            result.educations.append({"name": name, "institution": in_source(raw.get("institution"), source),
                                      "period": _period(raw.get("periodText"), source, reference).as_dict(),
                                      "evidence": evidence, "source": "LLM"})

    if wanted("careers"):
        for raw in _items(data.get("careers")):
            evidence = _evidence(raw.get("evidence"), source)
            company = in_source(raw.get("company"), source)
            if not (evidence and company):
                _drop(result, "careers", raw, "근거 또는 회사가 원문에 없음")
                continue
            result.careers.append({"company": company, "position": in_source(raw.get("position"), source),
                                   "duty": in_source(raw.get("duty"), source),
                                   "period": _period(raw.get("periodText"), source, reference).as_dict(),
                                   "evidence": evidence, "source": "LLM"})

    if wanted("projects"):
        for raw in _items(data.get("projects")):
            evidence = _evidence(raw.get("evidence"), source)
            if not evidence:
                _drop(result, "projects", raw, "근거가 원문에 없음")
                continue
            period = _period(raw.get("periodText"), source, reference)
            name = in_source(raw.get("name"), source)
            if not name and period.start is None:
                _drop(result, "projects", raw, "이름·기간이 모두 원문에 없음")
                continue
            if _duplicate_project(result.projects, name, period):
                continue
            skills = [skill for skill in (in_source(s, source) for s in raw.get("skills") or [] if isinstance(s, str)) if skill]
            project = {
                "name": name, "period": period.as_dict(), "client": in_source(raw.get("client"), source),
                "employer": in_source(raw.get("employer"), source), "role": in_source(raw.get("role"), source),
                "duty": in_source(raw.get("duty"), source), "industry": in_source(raw.get("industry"), source),
                "environment": {"machines": [], "os": [], "languages": [], "dbms": [], "tools": [], "others": skills},
                "evidence": evidence, "source": "LLM",
            }
            index = len(result.projects)
            result.projects.append(project)
            for skill in skills:
                for match in normalize_token(skill, "OTHER"):
                    result.skill_hits.append((skill, match, index))

    if wanted("industries"):
        for raw in _items(data.get("industries")):
            evidence = _evidence(raw.get("evidence"), source)
            names = normalize_industry(str(raw.get("name") or "")) if evidence else []
            for name in names:
                if not any(item["name"] == name for item in result.industries):
                    result.industries.append({"name": name, "evidence": evidence, "source": "LLM"})


def _items(value: Any) -> List[dict]:
    return [item for item in value if isinstance(item, dict)] if isinstance(value, list) else []


def _duplicate_project(projects: List[dict], name: Optional[str], period) -> bool:
    """나눠 부른 부분이 겹칠 때 같은 프로젝트를 두 번 넣지 않는다."""
    return any(project["name"] == name and project["period"]["start"] == period.start for project in projects)
