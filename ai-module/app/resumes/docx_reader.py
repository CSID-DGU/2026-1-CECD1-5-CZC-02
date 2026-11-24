"""워드(.docx) 읽기. python-docx 없이 zipfile + XML로 문단과 표를 순서대로 꺼낸다.

- 가로 병합(gridSpan)은 같은 글자를 칸 수만큼 펼쳐 열 번호가 줄마다 맞게 한다.
- 세로 병합(vMerge)의 아래 칸은 빈 글자로 두고 merged=True로 표시한다 (위 글자를 복사하지 않음).
- 표 안의 표는 바깥 칸 글자에 섞지 않고 바깥 표 다음에 따로 둔다.
- 원문 텍스트(source_text)는 문단은 한 줄, 표는 한 줄에 칸을 " | "로 이어 붙인다. 근거(evidence)는 이 원문에서 찾는다.
"""
import io
import re
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Union
from xml.etree import ElementTree

W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
CELL_SEPARATOR = " | "


class DocxError(ValueError):
    """읽을 수 없는 워드 파일 (.doc, 손상 파일 등)."""


@dataclass
class Cell:
    text: str
    merged: bool = False      # 세로 병합의 아래 칸
    span: int = 1             # 원래 가로로 몇 칸을 차지했는지


@dataclass
class Table:
    rows: List[List[Cell]] = field(default_factory=list)

    def texts(self) -> List[List[str]]:
        return [[cell.text for cell in row] for row in self.rows]


@dataclass
class Paragraph:
    text: str


Block = Union[Paragraph, Table]


@dataclass
class DocxDocument:
    blocks: List[Block]

    @property
    def tables(self) -> List[Table]:
        return [block for block in self.blocks if isinstance(block, Table)]

    def source_text(self) -> str:
        return blocks_to_text(self.blocks)


def blocks_to_text(blocks: List[Block]) -> str:
    lines: List[str] = []
    for block in blocks:
        if isinstance(block, Paragraph):
            if block.text:
                lines.append(block.text)
            continue
        for row in block.rows:
            line = row_line(row)
            if line:
                lines.append(line)
    return "\n".join(lines)


def distinct_cells(row: List[Cell]) -> List[Cell]:
    """가로 병합으로 펼친 같은 칸을 한 번만 돌려준다."""
    cells: List[Cell] = []
    for cell in row:
        if not cells or cell is not cells[-1]:
            cells.append(cell)
    return cells


def row_line(row: List[Cell]) -> str:
    """표 한 줄의 원문. 빈 칸은 빼고 " | "로 잇는다 (source_text와 같은 모양)."""
    return CELL_SEPARATOR.join(cell.text for cell in distinct_cells(row) if cell.text)


def read_docx(source: Union[bytes, str, Path]) -> DocxDocument:
    try:
        if isinstance(source, (str, Path)):
            archive = zipfile.ZipFile(source)
        else:
            archive = zipfile.ZipFile(io.BytesIO(source))
        with archive:
            xml = archive.read("word/document.xml")
    except (zipfile.BadZipFile, KeyError, OSError) as exception:
        raise DocxError("워드(.docx) 파일을 읽을 수 없습니다. .doc 파일이면 워드에서 .docx로 다시 저장해 주세요.") from exception

    try:
        root = ElementTree.fromstring(xml)
    except ElementTree.ParseError as exception:
        raise DocxError("워드 문서 내용(XML)이 손상되었습니다.") from exception

    body = root.find(f"{W}body")
    blocks: List[Block] = []
    if body is not None:
        _read_container(body, blocks)
    return DocxDocument(blocks=blocks)


def _read_container(element, blocks: List[Block]) -> None:
    for child in element:
        if child.tag == f"{W}p":
            text = paragraph_text(child)
            blocks.append(Paragraph(text=text))
        elif child.tag == f"{W}tbl":
            nested: List[Block] = []
            blocks.append(_read_table(child, nested))
            blocks.extend(nested)
        elif child.tag == f"{W}sdt":
            content = child.find(f"{W}sdtContent")
            if content is not None:
                _read_container(content, blocks)


def _read_table(table_element, nested: List[Block]) -> Table:
    table = Table()
    for row_element in table_element.findall(f"{W}tr"):
        row: List[Cell] = []
        for cell_element in row_element.findall(f"{W}tc"):
            properties = cell_element.find(f"{W}tcPr")
            span, merged = 1, False
            if properties is not None:
                grid_span = properties.find(f"{W}gridSpan")
                if grid_span is not None:
                    try:
                        span = max(1, int(grid_span.get(f"{W}val", "1")))
                    except ValueError:
                        span = 1
                v_merge = properties.find(f"{W}vMerge")
                if v_merge is not None and v_merge.get(f"{W}val") != "restart":
                    merged = True

            parts: List[str] = []
            for child in cell_element:
                if child.tag == f"{W}p":
                    text = paragraph_text(child)
                    if text:
                        parts.append(text)
                elif child.tag == f"{W}tbl":
                    inner: List[Block] = []
                    nested.append(_read_table(child, inner))
                    nested.extend(inner)
                elif child.tag == f"{W}sdt":
                    content = child.find(f"{W}sdtContent")
                    if content is not None:
                        for paragraph in content.iter(f"{W}p"):
                            text = paragraph_text(paragraph)
                            if text:
                                parts.append(text)

            cell = Cell(text="" if merged else " ".join(parts), merged=merged, span=span)
            row.extend([cell] * span)
        table.rows.append(row)
    return table


def paragraph_text(paragraph) -> str:
    pieces: List[str] = []
    for node in paragraph.iter():
        if node.tag == f"{W}t" and node.text:
            pieces.append(node.text)
        elif node.tag == f"{W}tab":
            pieces.append(" ")
        elif node.tag in (f"{W}br", f"{W}cr"):
            pieces.append(" ")
    return normalize_space("".join(pieces))


def normalize_space(text: str) -> str:
    return re.sub(r"\s+", " ", text.replace(" ", " ").replace("　", " ")).strip()
