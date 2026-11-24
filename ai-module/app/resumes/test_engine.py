"""이력서 엔진 테스트. 가짜 이력서만 쓰고, LLM은 가짜 함수로 바꿔 네트워크 없이 돈다."""
import base64
from datetime import date

import pytest
from fastapi.testclient import TestClient

import app.resumes.llm_extract as llm_extract
from app.llm.base import LLMError, LLMResponse
from app.main import app
from app.resumes.docx_reader import DocxError
from app.resumes.engine import extract_resume, schema_errors
from app.resumes.fake_docx import build_docx
from app.resumes.samples import CARD_EXPECTED, FREE_TEXT_RESUME, card_resume_docx

REF = date(2026, 11, 24)


def no_llm(request):
    raise AssertionError("이 경우에는 LLM을 부르면 안 된다")


def all_evidence(profile):
    """프로필 안의 모든 근거 글자."""
    for key in ("name", "birthYear", "grade"):
        if profile[key]:
            yield profile[key]["evidence"]
    for key in ("certifications", "educations", "careers", "projects", "skills", "industries"):
        for item in profile[key]:
            yield item["evidence"]
    if profile["availableFrom"] and profile["availableFrom"]["evidence"]:
        yield profile["availableFrom"]["evidence"]


def source_of(content):
    from app.resumes.docx_reader import read_docx
    return read_docx(content).source_text()


# ---------- 표 양식 (규칙) ----------

def test_card_resume_rules_match_expected():
    result = extract_resume(content=card_resume_docx(), reference_date=REF, mode="hybrid", generate_fn=no_llm)
    profile = result.profile

    assert schema_errors(profile) == []
    assert profile["format"] == "TABLE_CARD"
    assert profile["generatedBy"] == "RULE" and profile["llmModel"] is None
    assert profile["name"]["value"] == CARD_EXPECTED["name"]
    assert profile["birthYear"]["value"] == CARD_EXPECTED["birthYear"]
    assert profile["grade"]["value"] == CARD_EXPECTED["grade"]
    assert len(profile["projects"]) == CARD_EXPECTED["projectCount"]
    assert len(profile["careers"]) == CARD_EXPECTED["careerCount"]
    assert [item["name"] for item in profile["certifications"]] == CARD_EXPECTED["certifications"]
    assert profile["totalCareer"] == {"months": CARD_EXPECTED["totalCareerMonths"], "basis": "PROJECTS", "periodCount": 5}
    assert profile["availableFrom"]["date"] == CARD_EXPECTED["availableFrom"]
    assert profile["availableFrom"]["basis"] == "ONGOING_PROJECT_END"
    assert {skill["name"] for skill in profile["skills"] if skill["known"]} == set(CARD_EXPECTED["skills"])
    assert [item["name"] for item in profile["industries"]] == CARD_EXPECTED["industries"]
    assert profile["status"] == "DRAFT"
    assert result.llm_provider == "rule"


def test_card_resume_every_evidence_is_in_source():
    content = card_resume_docx()
    source = source_of(content)
    profile = extract_resume(content=content, reference_date=REF, mode="rule").profile
    for evidence in all_evidence(profile):
        assert evidence in source, evidence


def test_birth_evidence_keeps_year_only():
    profile = extract_resume(content=card_resume_docx(), reference_date=REF, mode="rule").profile
    assert profile["birthYear"]["evidence"] == "1991"   # 월·일(04.05)은 근거에 남기지 않는다


def test_skill_variants_are_merged_with_raw_and_project_count():
    profile = extract_resume(content=card_resume_docx(), reference_date=REF, mode="rule").profile
    java = next(skill for skill in profile["skills"] if skill["name"] == "Java")
    assert java["raw"] == ["JAVA", "Java"]
    assert java["projectCount"] == 3
    unknown = next(skill for skill in profile["skills"] if not skill["known"])
    assert unknown["name"] == "자체솔루션X" and unknown["category"] == "OTHER"


def test_two_row_project_is_merged():
    profile = extract_resume(content=card_resume_docx(), reference_date=REF, mode="rule").profile
    mes = next(project for project in profile["projects"] if project["name"] == "가상전자 MES 구축")
    assert mes["environment"]["languages"] == ["Java", "Python"]
    assert mes["environment"]["dbms"] == ["MySQL", "Redis"]


def test_unknown_values_stay_null_and_listed_missing():
    content = build_docx([
        ("table", [
            ["프로젝트명", "참여기간", "고객사", "역할"],
            ["샘플 시스템 유지보수", "2018 ~ 2019", "-", "개발"],
        ]),
    ])
    profile = extract_resume(content=content, reference_date=REF, mode="rule").profile
    assert profile["format"] == "TABLE_CARD"
    assert profile["name"] is None and profile["birthYear"] is None and profile["grade"] is None
    project = profile["projects"][0]
    assert project["client"] is None and project["period"] == {"start": None, "end": None, "ongoing": False}
    assert profile["totalCareer"] is None and profile["availableFrom"] is None
    assert {"name", "birthYear", "grade", "totalCareer", "availableFrom", "skills"} <= set(profile["missingFields"])


def test_basic_info_with_values_below_labels():
    content = build_docx([("table", [
        ["성명", "성별", "생년월일", "기술등급"],
        ["샘플진", "여", "88.07.21", "고급"],
    ])])
    profile = extract_resume(content=content, reference_date=REF, mode="rule").profile
    assert profile["name"]["value"] == "샘플진"
    assert profile["birthYear"] == {"value": 1988, "evidence": "88", "source": "RULE"}
    assert profile["grade"]["value"] == "ADVANCED"


@pytest.mark.parametrize("written, year, evidence", [
    ("1990.01.02", 1990, "1990"), ("900102", 1990, "90"), ("19900102", 1990, "1990"), ("90년 1월", 1990, "90"),
    ("05.03.04", 2005, "05"),
])
def test_birth_year_formats(written, year, evidence):
    content = build_docx([("table", [["성명", "모의현", "생년월일", written]])])
    birth = extract_resume(content=content, reference_date=REF, mode="rule").profile["birthYear"]
    assert (birth["value"], birth["evidence"]) == (year, evidence)


def test_no_ongoing_project_needs_confirmation():
    content = build_docx([("table", [
        ["프로젝트명", "참여기간", "고객사", "근무회사"],
        ["모의 포털 구축", "2020.01 ~ 2020.06", "모의시청", "샘플정보"],
    ])])
    profile = extract_resume(content=content, reference_date=REF, mode="rule").profile
    assert profile["availableFrom"] == {"date": None, "basis": "NO_ONGOING_PROJECT", "evidence": None}
    assert "availableFrom.date" in profile["missingFields"]


def test_card_missing_name_asks_llm_only_for_basic_info():
    content = build_docx([("p", "가상 경력서"), ("p", "이름은 테스트윤 입니다"), ("table", [
        ["프로젝트명", "참여기간", "고객사", "근무회사"],
        ["모의 포털 구축", "2020.01 ~ 2020.06", "모의시청", "샘플정보"],
    ])])
    asked = []

    def fake(request):
        asked.append(request)
        return LLMResponse(data={"name": {"value": "테스트윤", "evidence": "이름은 테스트윤 입니다"}},
                           provider="ollama", model="fake-model")

    result = extract_resume(content=content, reference_date=REF, mode="hybrid", generate_fn=fake)
    assert len(asked) == 1
    assert result.profile["name"] == {"value": "테스트윤", "evidence": "이름은 테스트윤 입니다", "source": "LLM"}
    assert result.profile["generatedBy"] == "HYBRID" and result.profile["llmModel"] == "fake-model"
    assert result.profile["projects"][0]["source"] == "RULE"


def test_doc_file_is_rejected():
    with pytest.raises(DocxError):
        extract_resume(content=b"\xd0\xcf\x11\xe0 old word file", reference_date=REF)


# ---------- 줄글 (가짜 LLM) ----------

def fake_free_text_llm(request):
    return LLMResponse(provider="ollama", model="fake-model", data={
        "name": {"value": "예시민", "evidence": "이름: 예시민"},
        "birthYear": {"value": 1994, "evidence": "1994년생"},
        "grade": {"value": "고급", "evidence": "고급 개발자"},             # 원문에 없는 근거 → 버림
        "certifications": [],
        "educations": [],
        "careers": [
            {"company": "모의소프트", "position": "사원", "duty": "웹 서비스 개발",
             "periodText": "2020.03 ~ 2022.08", "evidence": "2020.03 ~ 2022.08 모의소프트 / 사원 / 웹 서비스 개발"},
            {"company": "가상데이터", "position": "대리", "duty": "데이터 플랫폼 개발",
             "periodText": "2022.09 ~ 현재", "evidence": "2022.09 ~ 현재 가상데이터 / 대리 / 데이터 플랫폼 개발"},
        ],
        "projects": [
            {"name": "모의쇼핑 주문 시스템 개선", "periodText": "2021.01 ~ 2021.12", "client": "모의쇼핑",
             "employer": None, "role": None, "duty": None, "industry": "쇼핑", "skills": ["Django", "MySQL"],
             "evidence": "1) 모의쇼핑 주문 시스템 개선 (2021.01 ~ 2021.12) - 고객사 모의쇼핑, Django, MySQL 사용"},
            {"name": "가상물류 배송 추적 플랫폼 구축", "periodText": "2023.02 ~ 2026.12", "client": "가상물류",
             "employer": "지어낸회사", "role": "PM", "duty": None, "industry": None, "skills": ["python", "Kafka", "Rust"],
             "evidence": "2) 가상물류 배송 추적 플랫폼 구축 (2023.02 ~ 2026.12) - 고객사 가상물류, Python, Kafka 사용"},
            {"name": "있지도 않은 프로젝트", "periodText": "2010.01 ~ 2010.02", "evidence": "원문에 없는 문장"},
        ],
        "industries": [{"name": "유통", "evidence": "모의쇼핑 주문 시스템 개선"},
                       {"name": "물류", "evidence": "가상물류 배송 추적 플랫폼 구축"}],
    })


def test_free_text_uses_llm_and_checks_against_source():
    result = extract_resume(text=FREE_TEXT_RESUME, reference_date=REF, mode="hybrid", generate_fn=fake_free_text_llm)
    profile = result.profile

    assert schema_errors(profile) == []
    assert profile["format"] == "FREE_TEXT"
    assert profile["generatedBy"] == "HYBRID"
    assert profile["name"]["source"] == "RULE"                # "이름: 예시민"은 규칙이 먼저 읽는다
    assert profile["birthYear"]["value"] == 1994
    assert profile["grade"] is None                            # 지어낸 근거는 버린다
    assert [project["name"] for project in profile["projects"]] == ["모의쇼핑 주문 시스템 개선", "가상물류 배송 추적 플랫폼 구축"]
    logistics = profile["projects"][1]
    assert logistics["employer"] is None and logistics["role"] is None   # 원문에 없는 값은 null
    assert logistics["period"] == {"start": "2023-02", "end": "2026-12", "ongoing": True}
    assert profile["availableFrom"]["date"] == "2027-01-01"
    assert profile["totalCareer"]["basis"] == "PROJECTS"
    skills = {skill["name"]: skill for skill in profile["skills"]}
    assert skills["Python"]["projectCount"] == 1 and skills["Python"]["source"] == "LLM"
    assert "Rust" not in skills                                # 원문에 없는 기술은 버린다
    assert skills["Docker"]["source"] == "RULE"                # 보유 기술 줄은 사전 검색(규칙)
    assert {item["name"] for item in profile["industries"]} == {"유통", "물류"}
    reasons = {item["field"]: item["reason"] for item in result.dropped_items}
    assert "grade" in reasons and "projects" in reasons
    for evidence in all_evidence(profile):
        assert evidence in FREE_TEXT_RESUME, evidence


def test_free_text_rule_mode_never_calls_llm():
    profile = extract_resume(text=FREE_TEXT_RESUME, reference_date=REF, mode="rule", generate_fn=no_llm).profile
    assert profile["generatedBy"] == "RULE"
    assert profile["projects"] == []
    assert {"Python", "Django", "PostgreSQL", "Docker"} <= {skill["name"] for skill in profile["skills"]}


def test_llm_failure_returns_rule_result_with_reason():
    def broken(request):
        raise LLMError("Ollama request failed: connection refused")

    result = extract_resume(text=FREE_TEXT_RESUME, reference_date=REF, mode="hybrid", generate_fn=broken)
    assert schema_errors(result.profile) == []
    assert result.profile["projects"] == []
    assert "connection refused" in result.fallback_reason


def test_resume_llm_uses_only_local_ollama(monkeypatch):
    """개인정보라 공용 router(OpenAI 가능)를 쓰지 않고 Ollama 제공자만 쓴다."""
    calls = []

    class FakeOllama:
        def __init__(self, **kwargs):
            calls.append(kwargs)

        def generate(self, request):
            return LLMResponse(data={}, provider="ollama", model=kwargs_model())

    def kwargs_model():
        return calls[-1]["model"]

    monkeypatch.setattr("app.llm.ollama_provider.OllamaProvider", FakeOllama)
    monkeypatch.setenv("LLM_PROVIDER", "openai")
    response = llm_extract.ollama_generate(llm_extract.LLMRequest(task="resume", system="s", user="u", json_schema={}))
    assert response.provider == "ollama" and calls and calls[0]["task_models"] == {}


# ---------- 라우트 ----------

@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(llm_extract, "ollama_generate", fake_free_text_llm)
    return TestClient(app)


def test_route_extracts_docx(client):
    body = {"fileName": "샘플_이력서.docx", "contentBase64": base64.b64encode(card_resume_docx()).decode(),
            "referenceDate": "2026-11-24"}
    response = client.post("/resumes/extract", json=body)
    assert response.status_code == 200, response.text
    data = response.json()
    assert schema_errors(data["resumeProfile"]) == []
    assert data["resumeProfile"]["sourceRefs"] == [{"sourceId": "src_draft", "sourceType": "RESUME_FILE",
                                                    "fileName": "샘플_이력서.docx"}]
    assert data["llmProvider"] == "rule"


def test_route_extracts_text(client):
    response = client.post("/resumes/extract", json={"text": FREE_TEXT_RESUME, "referenceDate": "2026-11-24"})
    assert response.status_code == 200, response.text
    assert response.json()["resumeProfile"]["format"] == "FREE_TEXT"
    assert response.json()["llmModel"] == "fake-model"


@pytest.mark.parametrize("body, status", [
    ({"fileName": "옛날.doc", "contentBase64": "AAAA"}, 422),
    ({"contentBase64": "%%%"}, 422),
    ({"contentBase64": base64.b64encode(b"not a zip").decode()}, 422),
    ({}, 422),
    ({"text": "a", "contentBase64": "AAAA"}, 422),
])
def test_route_rejects_bad_input(client, body, status):
    assert client.post("/resumes/extract", json=body).status_code == status
