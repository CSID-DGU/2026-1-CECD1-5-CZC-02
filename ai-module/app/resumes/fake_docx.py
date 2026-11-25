"""가짜 이력서 .docx 만들기 (테스트·평가 개발셋·시연용). python-docx 없이 최소 XML로 만든다.

blocks 예:
    [("p", "개인이력카드"),
     ("table", [["성명", "가상인", "생년월일", "1990.01.01"],
                [{"text": "프로젝트명", "vmerge": "restart"}, {"text": "개발환경", "span": 2}],
                [{"vmerge": "continue"}, "언어", "DBMS"]])]
칸: 글자 하나 또는 {"text", "span"(가로 병합 칸 수), "vmerge": "restart"|"continue"}. 글자 안의 줄바꿈은 문단을 나눈다.
"""
import io
import zipfile
from typing import Any, List, Tuple
from xml.sax.saxutils import escape

CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"


def _paragraphs(text: str) -> str:
    lines = (text or "").split("\n")
    return "".join(f'<w:p><w:r><w:t xml:space="preserve">{escape(line)}</w:t></w:r></w:p>' if line else "<w:p/>"
                   for line in lines)


def _cell(spec: Any) -> str:
    if not isinstance(spec, dict):
        spec = {"text": "" if spec is None else str(spec)}
    properties = ""
    if spec.get("span", 1) > 1:
        properties += f'<w:gridSpan w:val="{int(spec["span"])}"/>'
    if spec.get("vmerge") == "restart":
        properties += '<w:vMerge w:val="restart"/>'
    elif spec.get("vmerge") == "continue":
        properties += "<w:vMerge/>"
    text = "" if spec.get("vmerge") == "continue" else spec.get("text", "")
    return f"<w:tc><w:tcPr>{properties}</w:tcPr>{_paragraphs(text) or '<w:p/>'}</w:tc>"


def _table(rows: List[List[Any]]) -> str:
    body = "".join("<w:tr>" + "".join(_cell(cell) for cell in row) + "</w:tr>" for row in rows)
    return f"<w:tbl>{body}</w:tbl>"


def build_docx(blocks: List[Tuple[str, Any]]) -> bytes:
    parts = []
    for kind, value in blocks:
        parts.append(_table(value) if kind == "table" else _paragraphs(value))
    document = (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                f'<w:document xmlns:w="{W_NS}"><w:body>{"".join(parts)}</w:body></w:document>')
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("[Content_Types].xml", CONTENT_TYPES)
        archive.writestr("_rels/.rels", RELS)
        archive.writestr("word/document.xml", document)
    return buffer.getvalue()
