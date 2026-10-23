"""Work out the printed page label ("8-88", "286", ...) of every PDF page so
citations can show both the PDF page and the number printed on the paper.

usage: python tools/page_labels.py <doc_id> [<doc_id> ...]
writes data/extracted/pagelabels/<doc_id>.json  {"<pdf page>": "<label>"}

Labels are only recorded when a footer/header pattern matches cleanly;
pages without a confident match are omitted (the app then shows the PDF page only).
"""
from __future__ import annotations

import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pdftools import ROOT, open_doc  # noqa: E402

OUT = os.path.join(ROOT, 'data', 'extracted', 'pagelabels')

PATTERNS = [
    re.compile(r'^(\d{1,2})\s*[-–]\s*(\d{1,3})$'),          # "5-32"
    re.compile(r'^(\d{1,2})\s{1,16}(\d{1,3})$'),             # Kia "8 88" / "4      173"
    re.compile(r'^(\d{1,2})[-–](\d{1,3})\s+\S.*$'),          # Isuzu "7-28   IN CASE OF EMERGENCY"
    re.compile(r'^\S.*\s(\d{1,2})[-–](\d{1,3})$'),           # "CONTROLS AND INSTRUMENTS 4-53" / "Maintenance 10-19"
]
BARE = re.compile(r'^(\d{1,4})$')


def label_for(lines: list[str]) -> str | None:
    edges = [lines[-1], lines[0]]
    for line in edges:
        for rx in PATTERNS:
            m = rx.match(line)
            if m:
                return f'{int(m.group(1))}-{int(m.group(2))}'
    m = BARE.match(lines[-1])
    if m:
        return m.group(1)
    # Mercedes headers: "328 Technical data" / "Technical data 347"
    m = re.match(r'^(\d{1,4}) [A-Z][a-z]', lines[0]) or re.match(r'^[A-Z][A-Za-z ,&-]+ (\d{1,4})$', lines[0])
    if m:
        return m.group(1)
    return None


def labels(doc_id: str) -> dict[str, str]:
    doc = open_doc(doc_id)
    out = {}
    for i, page in enumerate(doc, 1):
        lines = [re.sub(r'\s+', ' ', l).strip() for l in page.get_text('text', sort=True).splitlines()]
        lines = [l for l in lines if l]
        if not lines:
            continue
        lab = label_for(lines)
        if lab:
            out[str(i)] = lab
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    for doc_id in sys.argv[1:]:
        res = labels(doc_id)
        with open(os.path.join(OUT, f'{doc_id}.json'), 'w', encoding='utf-8') as fh:
            json.dump(res, fh, indent=0, sort_keys=False)
        print(f'{doc_id}: labelled {len(res)} of {open_doc(doc_id).page_count} pages')


if __name__ == '__main__':
    main()
