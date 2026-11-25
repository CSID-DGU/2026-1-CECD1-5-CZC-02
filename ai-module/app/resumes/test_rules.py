"""기간·기술명 사전 규칙 테스트 (가짜 값만, 네트워크 없음)."""
from datetime import date

import pytest

from app.resumes.dictionary import normalize_cell, normalize_industry, scan_known_skills, split_tokens
from app.resumes.periods import Period, first_day_after, parse_period, total_months

REF = date(2026, 11, 24)


@pytest.mark.parametrize("text, expected", [
    ("2019.03 ~ 2020.05", ("2019-03", "2020-05", False)),
    ("2019.03~2020.05", ("2019-03", "2020-05", False)),
    ("19.03-20.05", ("2019-03", "2020-05", False)),
    ("2019.03 2020.05", ("2019-03", "2020-05", False)),
    ("2019/03/01~2020/05/31", ("2019-03", "2020-05", False)),
    ("2019.03.01 - 2020.05.31", ("2019-03", "2020-05", False)),
    ("2019년 3월 ~ 2020년 5월", ("2019-03", "2020-05", False)),
    ("2019 03 – 2020 05", ("2019-03", "2020-05", False)),
    ("2019.3~2019.12", ("2019-03", "2019-12", False)),
    ("2024.03 ~ 현재", ("2024-03", None, True)),
    ("2024.03 ~", ("2024-03", None, True)),
    ("2025.06 ~ 2027.01", ("2025-06", "2027-01", True)),
    ("98.01~99.12", ("1998-01", "1999-12", False)),
])
def test_parse_period(text, expected):
    period = parse_period(text, REF)
    assert (period.start, period.end, period.ongoing) == expected


@pytest.mark.parametrize("text", ["", "-", "2019 ~ 2020", "미정", "2019.13 ~ 2019.14"])
def test_parse_period_unknown_stays_empty(text):
    """연도만 있거나 달이 틀리면 달을 지어내지 않는다."""
    period = parse_period(text, REF)
    assert period.start is None and period.end is None


def test_total_months_counts_overlap_once():
    periods = [
        Period("2016-03", "2017-02", False),   # 12
        Period("2017-04", "2017-12", False),   # 9
        Period("2017-10", "2018-06", False),   # 겹침 → 2017-04~2018-06 = 15
        Period("2025-06", None, True),         # 진행 중 → 기준 달까지 18
        Period(None, None, False),             # 못 읽은 기간은 빼고 센다
    ]
    assert total_months(periods, REF) == (12 + 15 + 18, 4)


def test_total_months_caps_future_end_at_reference():
    assert total_months([Period("2026-01", "2027-06", True)], REF) == (11, 1)


def test_first_day_after_rolls_year():
    assert first_day_after("2026-12") == "2027-01-01"
    assert first_day_after("2027-01") == "2027-02-01"


@pytest.mark.parametrize("raw, expected", [
    ("JAVA", "Java"), ("java", "Java"), ("Java 1.8", "Java"), ("jdk1.6", "Java"),
    ("oracle11g", "Oracle"), ("Oracle 10g", "Oracle"), ("ORACLE", "Oracle"),
    ("MySql", "MySQL"), ("MSSQL 2008", "MS-SQL"), ("SQL Server", "MS-SQL"),
    ("window", "Windows"), ("Win2008", "Windows"), ("windows 2003 server", "Windows"),
    ("HP-UX", "HP-UX"), ("hpux", "HP-UX"), ("PL/SQL", "PL/SQL"), ("Pro*C", "Pro*C"),
    ("Visual Studio 2010", "Visual Studio"), ("전자정부 프레임워크", "전자정부프레임워크"), ("JEUS 6.0", "JEUS"),
])
def test_normalize_variants(raw, expected):
    names = [match.name for match in normalize_cell(raw, "OTHER")]
    assert names == [expected]


def test_split_keeps_slash_names_and_splits_lists():
    assert split_tokens("Java, JSP / PL/SQL") == ["Java", "JSP", "PL/SQL"]
    assert split_tokens("Unix/Linux") == ["Unix", "Linux"]
    assert split_tokens("-") == []


def test_unknown_skill_kept_as_written():
    matches = normalize_cell("자체솔루션X", "TOOL")
    assert [(m.name, m.known, m.category) for m in matches] == [("자체솔루션X", False, "TOOL")]


def test_words_inside_one_token():
    names = [(m.name, m.known) for m in normalize_cell("Java Spring 사내모듈", "LANGUAGE")]
    assert names == [("Java", True), ("Spring", True), ("사내모듈", False)]


def test_scan_skips_ambiguous_short_names():
    names = {m.name for m in scan_known_skills("C 드라이브에서 Python과 PostgreSQL을 썼다. R&D 부서")}
    assert names == {"Python", "PostgreSQL"}


def test_industry_dictionary():
    assert normalize_industry("보험") == ["금융"]
    assert normalize_industry("공공(행정)") == ["공공"]
    assert normalize_industry("알수없음") == []


def test_backend_dictionary_copy_is_in_sync():
    """후보 추천(백엔드)이 쓰는 사전 사본이 이 사전과 같은지. 다르면 python -m app.resumes.dictionary 로 다시 만든다."""
    import json

    from app.resumes.dictionary import _backend_dictionary_file, export_for_backend

    target = _backend_dictionary_file()
    if not target.exists():
        pytest.skip("backend 폴더가 없는 체크아웃")
    assert json.loads(target.read_text(encoding="utf-8")) == export_for_backend()
