"""기술명·산업명 사전 (규칙). 이력서마다 다른 표기(JAVA/Java/java, oracle11g, win2008…)를 하나로 통일한다.

- 비교 키: 소문자 + 공백 제거. 키가 사전에 없으면 끝의 버전 표기(11g, 1.8, 2008, v7 …)를 떼고 다시 찾는다.
- 사전에 없는 표기는 지어내지 않고 원래 표기 그대로 known=False로 남긴다.
- 사전 단어는 일반적인 기술 이름만 둔다. 실제 이력서에서 뽑은 낱말을 넣지 않는다.
"""
import re
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional, Tuple

# (통일 이름, 분류, 다른 표기들). 통일 이름 자체도 표기로 쓴다.
SKILLS: List[Tuple[str, str, Tuple[str, ...]]] = [
    # 언어
    ("Java", "LANGUAGE", ("자바", "jdk", "j2se", "j2ee", "jee", "javase", "javaee", "jsdk")),
    ("JSP", "LANGUAGE", ("jsp/servlet", "java server pages")),
    ("Servlet", "LANGUAGE", ("서블릿",)),
    ("JavaScript", "LANGUAGE", ("js", "java script", "자바스크립트", "ecmascript", "es6")),
    ("TypeScript", "LANGUAGE", ("ts",)),
    ("C", "LANGUAGE", ("c언어", "ansi c", "ansic")),
    ("C++", "LANGUAGE", ("cpp", "c/c++", "vc++", "visual c++", "visualc++", "mfc")),
    ("C#", "LANGUAGE", ("csharp", "c sharp")),
    ("Pro*C", "LANGUAGE", ("proc", "pro-c", "pro c", "pro*c/c++")),
    ("COBOL", "LANGUAGE", ("코볼", "cobol2", "pro*cobol")),
    ("PL/SQL", "LANGUAGE", ("plsql", "pl-sql")),
    ("SQL", "LANGUAGE", ("ansi sql", "t-sql", "tsql")),
    ("Python", "LANGUAGE", ("파이썬", "python3", "py")),
    ("PHP", "LANGUAGE", ()),
    ("ASP", "LANGUAGE", ("classic asp",)),
    ("ASP.NET", "FRAMEWORK", ("asp.net mvc", "aspnet")),
    (".NET", "FRAMEWORK", ("dotnet", ".net framework", "닷넷", "vb.net", "vb.net/c#")),
    ("Visual Basic", "LANGUAGE", ("vb", "vb6", "vb6.0", "visualbasic", "vba")),
    ("PowerBuilder", "LANGUAGE", ("power builder", "pb", "파워빌더")),
    ("Delphi", "LANGUAGE", ("델파이",)),
    ("Kotlin", "LANGUAGE", ("코틀린",)),
    ("Swift", "LANGUAGE", ()),
    ("Objective-C", "LANGUAGE", ("objc", "objective c")),
    ("Go", "LANGUAGE", ("golang",)),
    ("Ruby", "LANGUAGE", ()),
    ("Scala", "LANGUAGE", ()),
    ("R", "LANGUAGE", ()),
    ("Shell Script", "LANGUAGE", ("shell", "쉘", "쉘스크립트", "shellscript", "bash", "ksh", "csh")),
    ("Perl", "LANGUAGE", ()),
    ("ABAP", "LANGUAGE", ("sap abap",)),
    ("HTML", "LANGUAGE", ("html5", "xhtml", "dhtml")),
    ("CSS", "LANGUAGE", ("css3",)),
    ("XML", "LANGUAGE", ("xslt", "xsl")),
    # 프레임워크·라이브러리
    ("Spring", "FRAMEWORK", ("spring framework", "springframework", "스프링", "spring mvc", "springmvc")),
    ("Spring Boot", "FRAMEWORK", ("springboot", "스프링부트")),
    ("전자정부프레임워크", "FRAMEWORK", ("전자정부 프레임워크", "egov", "egovframe", "egovframework", "표준프레임워크")),
    ("MyBatis", "FRAMEWORK", ("마이바티스",)),
    ("iBATIS", "FRAMEWORK", ("ibatis",)),
    ("Hibernate", "FRAMEWORK", ("jpa/hibernate",)),
    ("JPA", "FRAMEWORK", ()),
    ("Struts", "FRAMEWORK", ("struts2", "스트럿츠")),
    ("jQuery", "FRAMEWORK", ("jquery ui", "제이쿼리")),
    ("React", "FRAMEWORK", ("react.js", "reactjs", "리액트")),
    ("Vue.js", "FRAMEWORK", ("vue", "vuejs")),
    ("Angular", "FRAMEWORK", ("angularjs", "angular.js")),
    ("Node.js", "FRAMEWORK", ("node", "nodejs")),
    ("Django", "FRAMEWORK", ()),
    ("Flask", "FRAMEWORK", ()),
    ("Ajax", "FRAMEWORK", ()),
    ("Flex", "FRAMEWORK", ("adobe flex",)),
    ("Android", "FRAMEWORK", ("안드로이드",)),
    ("iOS", "OS", ()),
    ("MiPlatform", "FRAMEWORK", ("mi platform", "마이플랫폼")),
    ("XPlatform", "FRAMEWORK", ("x platform", "x-platform", "엑스플랫폼")),
    ("Nexacro", "FRAMEWORK", ("넥사크로", "nexacro platform", "nexacroplatform")),
    ("WebSquare", "FRAMEWORK", ("웹스퀘어", "web square")),
    ("Gauce", "FRAMEWORK", ("가우스",)),
    ("Tuxedo", "MIDDLEWARE", ("턱시도",)),
    # DBMS
    ("Oracle", "DBMS", ("오라클", "oracle db", "oracledb", "ora")),
    ("MS-SQL", "DBMS", ("mssql", "ms sql", "ms-sql server", "sql server", "sqlserver", "mssqlserver", "ms sql server")),
    ("MySQL", "DBMS", ("my sql", "마이에스큐엘")),
    ("MariaDB", "DBMS", ("maria db", "마리아db")),
    ("PostgreSQL", "DBMS", ("postgres", "postgre", "postgre sql")),
    ("Tibero", "DBMS", ("티베로",)),
    ("DB2", "DBMS", ("ibm db2", "udb")),
    ("Informix", "DBMS", ("인포믹스",)),
    ("Sybase", "DBMS", ("사이베이스",)),
    ("Altibase", "DBMS", ("알티베이스",)),
    ("CUBRID", "DBMS", ("큐브리드",)),
    ("MongoDB", "DBMS", ("mongo",)),
    ("Redis", "DBMS", ()),
    ("SQLite", "DBMS", ()),
    ("MS Access", "DBMS", ("access", "msaccess")),
    # OS
    ("Unix", "OS", ("유닉스", "unix계열")),
    ("AIX", "OS", ("ibm aix",)),
    ("HP-UX", "OS", ("hpux", "hp ux", "hp-unix")),
    ("Solaris", "OS", ("sun solaris", "sunos")),
    ("Linux", "OS", ("리눅스", "redhat", "red hat", "rhel", "centos", "ubuntu", "suse")),
    ("Windows", "OS", ("window", "win", "윈도우", "윈도우즈", "windows server", "win server", "winnt", "windows nt",
                       "windowsxp", "windows xp", "win xp")),
    ("MVS", "OS", ("z/os", "zos", "os/390")),
    ("OS/400", "OS", ("as/400 os", "i5/os")),
    # 기종(하드웨어)
    ("IBM", "MACHINE", ("ibm서버",)),
    ("HP", "MACHINE", ("hp서버", "hpe")),
    ("SUN", "MACHINE", ("sun서버", "sun microsystems")),
    ("PC", "MACHINE", ("pc서버", "x86", "인텔서버")),
    ("Mainframe", "MACHINE", ("메인프레임", "ibm mainframe", "호스트")),
    ("AS/400", "MACHINE", ("as400", "iseries")),
    # 미들웨어·서버
    ("WebLogic", "MIDDLEWARE", ("웹로직", "bea weblogic", "oracle weblogic")),
    ("WebSphere", "MIDDLEWARE", ("웹스피어", "ibm websphere", "was(websphere)")),
    ("JEUS", "MIDDLEWARE", ("제우스",)),
    ("WebtoB", "MIDDLEWARE", ("웹투비",)),
    ("Tomcat", "MIDDLEWARE", ("톰캣", "apache tomcat")),
    ("Apache", "MIDDLEWARE", ("apache http server", "아파치")),
    ("JBoss", "MIDDLEWARE", ("wildfly",)),
    ("Nginx", "MIDDLEWARE", ()),
    ("IIS", "MIDDLEWARE", ()),
    # 도구
    ("Eclipse", "TOOL", ("이클립스",)),
    ("IntelliJ", "TOOL", ("intellij idea", "intelli j")),
    ("Visual Studio", "TOOL", ("visualstudio", "vs code", "vscode", "visual studio code")),
    ("Toad", "TOOL", ("토드",)),
    ("Orange", "TOOL", ("오렌지",)),
    ("SQL Developer", "TOOL", ("sqldeveloper",)),
    ("SQLGate", "TOOL", ("sql gate",)),
    ("DBeaver", "TOOL", ()),
    ("SVN", "TOOL", ("subversion", "tortoisesvn")),
    ("Git", "TOOL", ("github", "gitlab")),
    ("CVS", "TOOL", ()),
    ("Jenkins", "TOOL", ()),
    ("Maven", "TOOL", ()),
    ("Gradle", "TOOL", ()),
    ("Ant", "TOOL", ()),
    ("ERwin", "TOOL", ("erwin data modeler",)),
    ("Jira", "TOOL", ()),
    ("OZ Report", "TOOL", ("ozreport", "oz", "오즈리포트")),
    ("Crystal Report", "TOOL", ("crystal reports", "crystalreport")),
    ("Rexpert", "TOOL", ("렉스퍼트",)),
    ("UbiReport", "TOOL", ("ubi report", "유비리포트")),
    ("ClipReport", "TOOL", ("clip report", "클립리포트")),
    ("Docker", "TOOL", ()),
    ("Kubernetes", "TOOL", ("k8s",)),
    ("AWS", "OTHER", ("amazon web services",)),
    ("Hadoop", "OTHER", ()),
    ("SAP", "OTHER", ("sap erp", "sap r/3")),
    ("TCP/IP", "OTHER", ("tcpip", "tcp", "socket", "소켓")),
    ("X.25", "OTHER", ()),
    ("SNA", "OTHER", ()),
    ("MQ", "MIDDLEWARE", ("ibm mq", "websphere mq", "mq series", "mqseries")),
]

# 여러 기술을 나누는 문자. 빗금은 PL/SQL·HP-UX 같은 이름을 먼저 지킨 뒤에 나눈다.
SPLIT_RE = re.compile(r"[,;、，·•\n|&]|\s및\s|\s/\s")
SLASH_RE = re.compile(r"/")
VERSION_RE = re.compile(r"[\s\-_]*(?:v|ver\.?)?\d+(?:\.\d+)*(?:[a-z]{1,3})?(?:\s*(?:sp|r)\d+)?$", re.IGNORECASE)
BRACKET_RE = re.compile(r"\s*[\(\[][^)\]]*[\)\]]\s*")
GENERIC_WORDS = {"server", "서버", "standard", "enterprise", "edition", "pro", "professional", "client", "클라이언트",
                 "사용", "환경", "등", "기반"}
EMPTY_TOKENS = {"", "-", "–", "—", "없음", "해당없음", "na", "n/a", "기타", "등", "etc", "외", "o", "x", "."}

# 산업 사전: (통일 이름, 들어 있으면 그 산업으로 보는 낱말)
INDUSTRIES: List[Tuple[str, Tuple[str, ...]]] = [
    ("금융", ("금융", "은행", "증권", "보험", "카드", "캐피탈", "저축", "투자", "자산운용", "핀테크", "finance", "bank")),
    ("공공", ("공공", "정부", "행정", "지자체", "공단", "국방", "관공서", "public")),
    ("제조", ("제조", "생산", "공장", "mes", "반도체", "자동차", "철강", "화학")),
    ("통신", ("통신", "텔레콤", "telecom", "이동통신", "네트워크")),
    ("유통", ("유통", "쇼핑", "커머스", "리테일", "백화점", "마트", "e-commerce", "이커머스")),
    ("물류", ("물류", "배송", "택배", "scm")),
    ("의료", ("의료", "병원", "제약", "헬스", "보건")),
    ("교육", ("교육", "대학", "학교", "학사", "이러닝", "e-learning")),
    ("에너지", ("에너지", "전력", "발전", "가스", "석유", "원자력")),
    ("건설", ("건설", "건축", "토목", "부동산")),
    ("운송", ("항공", "철도", "해운", "교통", "운송", "여객")),
    ("미디어", ("방송", "미디어", "언론", "신문", "엔터테인먼트", "게임")),
    ("서비스", ("서비스업", "호텔", "여행", "외식")),
]


@dataclass(frozen=True)
class SkillMatch:
    name: str
    category: str
    known: bool
    raw: str


def compact_key(text: str) -> str:
    return re.sub(r"\s+", "", text).lower()


def _build_alias_map() -> Dict[str, Tuple[str, str]]:
    aliases: Dict[str, Tuple[str, str]] = {}
    for name, category, others in SKILLS:
        for alias in (name, *others):
            aliases.setdefault(compact_key(alias), (name, category))
    return aliases


ALIASES = _build_alias_map()
# 빗금이 들어간 이름은 나누기 전에 지킨다 (긴 것부터)
SLASH_NAMES = sorted({alias for alias in ALIASES if "/" in alias}, key=len, reverse=True)
# 글 전체에서 찾을 때(줄글) 헷갈리는 짧은 이름은 뺀다
SCAN_EXCLUDE = {"c", "r", "go", "vb", "pb", "js", "ts", "py", "oz", "ora", "win", "window", "node", "access", "ant",
                "shell", "쉘", "pc", "hp", "ibm", "sun", "mq", "tcp", "socket", "소켓", "호스트", "군", "udb", "sap",
                "vue", "flex", "swift", "ruby", "scala", "perl", "orange", "오렌지", "apache", "android", "git", "jee"}


def lookup(token: str) -> Optional[Tuple[str, str]]:
    key = compact_key(BRACKET_RE.sub(" ", token)) or compact_key(token)
    if key in ALIASES:
        return ALIASES[key]
    stripped = compact_key(VERSION_RE.sub("", key))
    if stripped and stripped != key and stripped in ALIASES:
        return ALIASES[stripped]
    return None


def split_tokens(text: str) -> List[str]:
    """개발환경 칸 하나를 기술 낱말로 나눈다. 원래 표기를 그대로 돌려준다."""
    if not text:
        return []
    protected: Dict[str, str] = {}

    def protect(match: re.Match) -> str:
        marker = f"\u0000{len(protected)}\u0000"
        protected[marker] = match.group(0)
        return marker

    working = text
    for alias in SLASH_NAMES:
        pattern = re.compile(r"\s*".join(re.escape(char) for char in alias), re.IGNORECASE)
        working = pattern.sub(protect, working)

    pieces: List[str] = []
    for piece in SPLIT_RE.split(working):
        pieces.extend(SLASH_RE.split(piece))

    tokens: List[str] = []
    for piece in pieces:
        for marker, original in protected.items():
            piece = piece.replace(marker, original)
        piece = piece.strip().strip("()[]-:.")
        if piece and compact_key(piece) not in EMPTY_TOKENS:
            tokens.append(piece)
    return tokens


def normalize_token(token: str, fallback_category: str) -> List[SkillMatch]:
    """낱말 하나 → 기술. 통째로 사전에 있으면 하나, 아니면 띄어쓰기 단위로 1~3단어 창을 맞춰 본다."""
    found = lookup(token)
    if found:
        return [SkillMatch(found[0], found[1], True, token)]

    words = token.split()
    if len(words) > 1:
        matches: List[SkillMatch] = []
        leftovers: List[str] = []
        index = 0
        while index < len(words):
            for size in (3, 2, 1):
                window = " ".join(words[index:index + size])
                if index + size <= len(words) and (hit := lookup(window)):
                    matches.append(SkillMatch(hit[0], hit[1], True, window))
                    index += size
                    break
            else:
                leftovers.append(words[index])
                index += 1
        if matches:
            rest = " ".join(word for word in leftovers
                            if not VERSION_RE.fullmatch(word) and compact_key(word) not in GENERIC_WORDS)
            if rest and compact_key(rest) not in EMPTY_TOKENS and re.search(r"[A-Za-z가-힣]", rest):
                matches.append(SkillMatch(rest, fallback_category, False, rest))
            return matches

    if not re.search(r"[A-Za-z가-힣]", token):
        return []
    return [SkillMatch(token.strip(), fallback_category, False, token)]


def normalize_cell(text: str, fallback_category: str) -> List[SkillMatch]:
    matches: List[SkillMatch] = []
    for token in split_tokens(text):
        matches.extend(normalize_token(token, fallback_category))
    return matches


def scan_known_skills(text: str) -> List[SkillMatch]:
    """줄글 전체에서 사전에 있는 기술 이름을 찾는다 (헷갈리는 짧은 이름 제외).

    긴 이름부터 찾고, 이미 찾은 자리와 겹치는 짧은 이름은 버린다 (Spring Boot 안의 Spring).
    """
    found: List[SkillMatch] = []
    seen, taken = set(), []
    for alias in sorted(ALIASES, key=len, reverse=True):
        name, category = ALIASES[alias]
        if alias in SCAN_EXCLUDE or len(alias) < 2:
            continue
        body = r"\s*".join(re.escape(char) for char in alias)
        # 한글 이름 뒤에는 조사만 허용(자바 ≠ 자바스크립트), 영문 이름 뒤에는 한글 조사가 붙어도 된다(Python과)
        if re.search(r"[가-힣]$", alias):
            tail = r"(?:(?=[은는이가을를과와로에의도만])|(?![가-힣]))(?![A-Za-z0-9])"
        else:
            tail = r"(?![A-Za-z0-9])"
        for match in re.finditer(r"(?<![A-Za-z0-9가-힣])" + body + tail, text, re.IGNORECASE):
            if any(start < match.end() and match.start() < end for start, end in taken):
                continue
            taken.append(match.span())
            if name not in seen:
                seen.add(name)
                found.append(SkillMatch(name, category, True, match.group(0)))
    return found


def normalize_industry(text: Optional[str]) -> List[str]:
    if not text:
        return []
    lowered = text.lower()
    return [name for name, words in INDUSTRIES if any(word in lowered for word in words)]


def unique(values: Iterable[str]) -> List[str]:
    seen, result = set(), []
    for value in values:
        if value not in seen:
            seen.add(value)
            result.append(value)
    return result
