"""Shared helpers for reading owner's-manual PDFs (PyMuPDF).

Page numbers everywhere in this project are 1-based PDF page indexes, i.e. the
number a PDF viewer shows in its page box. Printed page labels (e.g. "8-88")
are recorded separately where useful.
"""
from __future__ import annotations

import os
import re
from functools import lru_cache

import pymupdf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WORK = os.path.join(ROOT, 'tools', 'work')
PDF_DIR = os.path.join(WORK, 'pdfs')


def pdf_path(doc_id: str) -> str:
    return os.path.join(PDF_DIR, f'{doc_id}.pdf')


@lru_cache(maxsize=8)
def open_doc(doc_id: str) -> pymupdf.Document:
    path = pdf_path(doc_id)
    if not os.path.exists(path):
        raise SystemExit(f'Missing {path}. Run tools/fetch_sources.py first.')
    return pymupdf.open(path)


# Some Kia PDFs map punctuation to private CJK code points.
GLYPHS = str.maketrans({'䳓': '’', '䳍': '–', '䳜': '•', '僅': '•', '\u00ad': ''})


def clean(text: str | None) -> str:
    return re.sub(r'\s+', ' ', (text or '').translate(GLYPHS)).strip()


def cell_text(page: pymupdf.Page, bbox) -> str:
    """Text inside a table cell: the words whose centre lies in the cell, in
    reading order. (Table.extract() drops word spacing on these manuals, and a
    plain clipped read picks up glyphs that overhang from the next row.)"""
    if bbox is None:
        return ''
    r = pymupdf.Rect(bbox)
    words = page.get_text('words', clip=pymupdf.Rect(r.x0 - 3, r.y0 - 3, r.x1 + 3, r.y1 + 3))
    inside = [w for w in words
              if r.x0 <= (w[0] + w[2]) / 2 <= r.x1 and r.y0 <= (w[1] + w[3]) / 2 <= r.y1]
    inside.sort(key=lambda w: (w[5], w[6], w[7]))
    return clean(' '.join(w[4] for w in inside))


def table_rows(page: pymupdf.Page) -> list[tuple[pymupdf.Rect, list[list[str]]]]:
    """Every ruled table on the page as (bbox, rows of cell strings)."""
    out = []
    for t in page.find_tables().tables:
        rows = [[cell_text(page, c) for c in row.cells] for row in t.rows]
        out.append((pymupdf.Rect(t.bbox), rows))
    return out


def lines_above(page: pymupdf.Page, y_limit: float) -> list[tuple[float, float, str]]:
    """(y, font size, text) for text lines that start above y_limit."""
    found = []
    for block in page.get_text('dict')['blocks']:
        for line in block.get('lines', []):
            y = line['bbox'][1]
            if y >= y_limit:
                continue
            text = clean(''.join(span['text'] for span in line['spans']))
            if text:
                size = max(span['size'] for span in line['spans'])
                found.append((y, size, text))
    return sorted(found)


def page_text(doc_id: str, page_no: int) -> str:
    return open_doc(doc_id)[page_no - 1].get_text('text', sort=True)
