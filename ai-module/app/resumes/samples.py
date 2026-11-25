"""가짜 이력서 샘플 (테스트·contracts 예시·시연용). 사람·회사 이름은 전부 지어낸 것이다(가상·예시·모의·샘플).

card_resume_blocks(): 개인이력카드 모양 표 양식 — 두 줄 머리글, 세로 병합, 두 줄짜리 프로젝트, 겹친 기간, 진행 중 프로젝트.
FREE_TEXT_RESUME: 줄글 경력기술서.
"""
from typing import Any, List, Tuple

from app.resumes.fake_docx import build_docx

CONT = {"vmerge": "continue"}


def vm(text: str) -> dict:
    return {"text": text, "vmerge": "restart"}


def card_resume_blocks() -> List[Tuple[str, Any]]:
    return [
        ("p", "개인이력카드"),
        ("table", [
            ["성명", "가상인", "성별", "남", "생년월일", "1991.04.05"],
            ["기술등급", "중급", "주소", {"text": "샘플시 가상구", "span": 3}],
        ]),
        ("p", ""),
        ("table", [
            ["회사명", "기간", "직위", "담당업무"],
            ["가상정보기술", "2016.01 ~ 2020.12", "대리", "SI 개발"],
            ["예시솔루션", "2021.01 ~ 현재", "과장", "금융 SI"],
        ]),
        ("table", [
            ["교육명", "기관", "시작일", "종료일", "자격증명", "취득일"],
            ["자바 웹 개발 과정", "샘플교육원", "2015.07", "2015.12", "정보처리기사", "2015.11"],
            ["", "", "", "", "SQLD", "2018.05"],
        ]),
        ("table", [
            [vm("프로젝트명(업무명)"), vm("참여기간"), vm("고객사"), vm("근무회사"), {"text": "개발분야", "span": 2},
             vm("역할"), {"text": "개발환경", "span": 6}],
            [CONT, CONT, CONT, CONT, "산업", "응용", CONT, "기종", "O.S", "언어", "DBMS", "TOOL", "기타"],
            ["예시은행 차세대 계정계 구축", "2016.03 ~ 2017.02", "예시은행", "가상정보기술", "금융", "계정계", "개발",
             "IBM", "AIX", "JAVA, JSP", "Oracle 11g", "Eclipse", "JEUS"],
            ["모의시청 민원 시스템 고도화", "17.04~17.12", "모의시청", "가상정보기술", "공공", "민원", "개발",
             "HP", "HP-UX", "Java, Spring", "oracle10g", "Toad", "-"],
            ["샘플카드 포인트 시스템 운영", "2017.10 - 2018.06", "샘플카드", "가상정보기술", "금융", "포인트", "운영",
             "PC", "Win2008", "C#", "MSSQL", "Visual Studio 2010", ""],
            [vm("가상전자 MES 구축"), vm("2019.01 ~ 2019.12"), vm("가상전자"), vm("가상정보기술"), vm("제조"), vm("MES"),
             vm("PL"), "PC", "Linux", "Java", "MySQL", "IntelliJ", "Tomcat"],
            [CONT, CONT, CONT, CONT, CONT, CONT, CONT, "", "", "Python", "Redis", "", ""],
            ["예시보험 보상 시스템 구축", "2025.06 ~ 2027.01", "예시보험", "예시솔루션", "보험", "보상", "PL",
             "PC", "Linux", "Spring Boot, 전자정부프레임워크", "PostgreSQL", "Git", "자체솔루션X"],
        ]),
    ]


def card_resume_docx() -> bytes:
    return build_docx(card_resume_blocks())


# 기준일 2026-11-24에서 기대하는 값 (테스트·평가 개발셋 정답)
CARD_EXPECTED = {
    "name": "가상인",
    "birthYear": 1991,
    "grade": "INTERMEDIATE",
    "projectCount": 5,
    "careerCount": 2,
    "certifications": ["정보처리기사", "SQLD"],
    "totalCareerMonths": 57,   # 12 + (17.04~18.06 겹침 합쳐 15) + 12 + (25.06~26.11 18)
    "availableFrom": "2027-02-01",
    "skills": ["Java", "JSP", "Oracle", "Eclipse", "JEUS", "IBM", "AIX", "HP", "HP-UX", "Spring", "Toad", "PC",
               "Windows", "C#", "MS-SQL", "Visual Studio", "Linux", "MySQL", "IntelliJ", "Tomcat", "Python", "Redis",
               "Spring Boot", "전자정부프레임워크", "PostgreSQL", "Git"],
    "industries": ["금융", "공공", "제조"],
}

FREE_TEXT_RESUME = """샘플 개발자 경력기술서
이름: 예시민 (1994년생)
보유 기술: Python, Django, PostgreSQL, Docker
경력
- 2020.03 ~ 2022.08 모의소프트 / 사원 / 웹 서비스 개발
- 2022.09 ~ 현재 가상데이터 / 대리 / 데이터 플랫폼 개발
프로젝트
1) 모의쇼핑 주문 시스템 개선 (2021.01 ~ 2021.12) - 고객사 모의쇼핑, Django, MySQL 사용
2) 가상물류 배송 추적 플랫폼 구축 (2023.02 ~ 2026.12) - 고객사 가상물류, Python, Kafka 사용"""
