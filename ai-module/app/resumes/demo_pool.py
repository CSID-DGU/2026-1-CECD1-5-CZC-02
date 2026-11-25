"""시연용 가짜 후보 풀 (12/4 시연: 가상물산 자바 백엔드 3명 요청).

요청에 맞는 후보·안 맞는 후보를 섞어 둔다. 사람·회사 이름은 전부 지어낸 것이다(가상·예시·모의·샘플·테스트).
표 양식(개인이력카드)이라 규칙만으로 읽힌다 → 로컬 LLM 없이 바로 확인 대기로 들어간다.

| 파일 | 맞는가 | 이유 |
| --- | --- | --- |
| 모의준호 | 맞음 | Java·Spring, 경력 8년대, 금융 경험, 진행 중 프로젝트 없음 |
| 샘플현우 | 맞음 | Java·Spring Boot, 경력 6년대, 진행 중 프로젝트가 2027-01 끝남 → 2027-02-01 투입 가능 |
| 모의태오 | 맞음(확인 필요) | Java·전자정부프레임워크, 진행 중 프로젝트 종료일 모름 |
| 예시지민 | 맞음(늦음) | Java·Spring, 금융, 투입 가능 2027-04-01 (요청 시기보다 늦음) |
| 가상민수 | 안 맞음 | 경력 3년대 (5년 이상 요청) |
| 테스트서연 | 안 맞음 | C#·.NET만 있음 (Java 없음) |
"""
from typing import Any, Dict, List, Tuple

from app.resumes.fake_docx import build_docx
from app.resumes.samples import CONT, vm

PROJECT_HEADER = [
    [vm("프로젝트명(업무명)"), vm("참여기간"), vm("고객사"), vm("근무회사"), {"text": "개발분야", "span": 2},
     vm("역할"), {"text": "개발환경", "span": 6}],
    [CONT, CONT, CONT, CONT, "산업", "응용", CONT, "기종", "O.S", "언어", "DBMS", "TOOL", "기타"],
]

# (이름, 생년월일, 등급, 근무 회사, [(프로젝트명, 기간, 고객사, 산업, 응용, 역할, 언어, DBMS, TOOL)])
DEMO_POOL: List[Dict[str, Any]] = [
    {"file": "모의준호_이력카드.docx", "name": "모의준호", "birth": "1988.02.11", "grade": "고급", "company": "가상정보기술",
     "projects": [
         ("예시은행 인터넷뱅킹 재구축", "2018.01 ~ 2019.12", "예시은행", "금융", "인터넷뱅킹", "개발", "Java, JSP", "Oracle", "Eclipse"),
         ("샘플증권 주문 시스템 구축", "2020.01 ~ 2022.06", "샘플증권", "증권", "주문", "개발", "Java, Spring", "Oracle", "IntelliJ"),
         ("모의카드 정산 시스템 고도화", "2022.07 ~ 2024.12", "모의카드", "금융", "정산", "PL", "Java, Spring Boot", "Oracle", "Git"),
         ("가상캐피탈 여신 시스템 개선", "2025.01 ~ 2026.10", "가상캐피탈", "금융", "여신", "PL", "Java, Spring Boot", "PostgreSQL", "Git"),
     ]},
    {"file": "샘플현우_이력카드.docx", "name": "샘플현우", "birth": "1990.07.23", "grade": "중급", "company": "예시시스템",
     "projects": [
         ("모의시청 민원 포털 구축", "2020.03 ~ 2022.02", "모의시청", "공공", "민원", "개발", "Java, Spring", "MySQL", "Eclipse"),
         ("가상공단 연금 시스템 고도화", "2022.03 ~ 2024.06", "가상공단", "공공", "연금", "개발", "Java, Spring Boot", "MySQL", "IntelliJ"),
         ("샘플유통 주문 관리 개선", "2024.07 ~ 2027.01", "샘플유통", "유통", "주문", "개발", "Java, Spring Boot", "MySQL", "Git"),
     ]},
    {"file": "모의태오_이력카드.docx", "name": "모의태오", "birth": "1987.05.30", "grade": "고급", "company": "모의아이티",
     "projects": [
         ("가상도청 행정 정보 시스템 구축", "2019.01 ~ 2021.12", "가상도청", "공공", "행정", "개발", "Java, 전자정부프레임워크", "Oracle", "Eclipse"),
         ("예시공사 자산 관리 시스템 구축", "2022.01 ~ 2025.08", "예시공사", "공공", "자산", "PL", "Java, 전자정부프레임워크", "Tibero", "Eclipse"),
         ("모의교육청 학사 시스템 고도화", "2025.09 ~ 현재", "모의교육청", "교육", "학사", "PL", "Java, Spring", "Oracle", "Git"),
     ]},
    {"file": "예시지민_이력카드.docx", "name": "예시지민", "birth": "1991.11.02", "grade": "중급", "company": "샘플소프트",
     "projects": [
         ("샘플보험 계약 시스템 구축", "2019.06 ~ 2021.05", "샘플보험", "보험", "계약", "개발", "Java, Spring", "Oracle", "Eclipse"),
         ("가상은행 대출 심사 고도화", "2021.06 ~ 2024.09", "가상은행", "금융", "대출", "개발", "Java, Spring Boot", "Oracle", "IntelliJ"),
         ("예시저축 계정계 전환", "2024.10 ~ 2027.03", "예시저축", "금융", "계정계", "개발", "Java, Spring Boot", "Oracle", "Git"),
     ]},
    {"file": "가상민수_이력카드.docx", "name": "가상민수", "birth": "1996.03.17", "grade": "초급", "company": "가상정보기술",
     "projects": [
         ("모의쇼핑 상품 관리 개선", "2023.01 ~ 2024.06", "모의쇼핑", "유통", "상품", "개발", "Java, Spring Boot", "MySQL", "IntelliJ"),
         ("가상물류 배송 조회 구축", "2024.07 ~ 2026.09", "가상물류", "물류", "배송", "개발", "Java, Spring Boot", "MySQL", "Git"),
     ]},
    {"file": "테스트서연_이력카드.docx", "name": "테스트서연", "birth": "1985.09.08", "grade": "특급", "company": "테스트솔루션",
     "projects": [
         ("예시전자 MES 구축", "2016.01 ~ 2019.12", "예시전자", "제조", "MES", "개발", "C#, .NET", "MSSQL", "Visual Studio"),
         ("모의화학 설비 관리 고도화", "2020.01 ~ 2023.06", "모의화학", "제조", "설비", "PL", "C#, .NET", "MSSQL", "Visual Studio"),
         ("가상병원 진료 예약 개선", "2023.07 ~ 2026.08", "가상병원", "의료", "예약", "PM", "C#, ASP.NET", "MSSQL", "Visual Studio"),
     ]},
]


def demo_blocks(spec: Dict[str, Any]) -> List[Tuple[str, Any]]:
    first_start = spec["projects"][0][1].split("~")[0].strip()
    rows = [[project[0], project[1], project[2], spec["company"], project[3], project[4], project[5],
             "PC", "Linux", project[6], project[7], project[8], ""] for project in spec["projects"]]
    return [
        ("p", "개인이력카드"),
        ("table", [
            ["성명", spec["name"], "성별", "-", "생년월일", spec["birth"]],
            ["기술등급", spec["grade"], "주소", {"text": "샘플시 예시구", "span": 3}],
        ]),
        ("p", ""),
        ("table", [
            ["회사명", "기간", "직위", "담당업무"],
            [spec["company"], f"{first_start} ~ 현재", "과장", "SI 개발"],
        ]),
        ("table", PROJECT_HEADER + rows),
    ]


def demo_pool_docx() -> List[Tuple[str, bytes]]:
    return [(spec["file"], build_docx(demo_blocks(spec))) for spec in DEMO_POOL]
