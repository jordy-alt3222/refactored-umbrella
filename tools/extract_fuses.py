"""Extract fuse allocation tables from owner's manuals with ruled tables
(Hyundai, Kia, Mazda layouts).

usage: python tools/extract_fuses.py <doc_id> <first_page>-<last_page> [--out data/extracted/fuses/<vehicle>.json]

Each output row keeps the PDF page it came from so it can be checked against
the manual. Rows the parser cannot read confidently are written with
"needsReview": true and must be resolved by hand before data/build_data.py
will accept the file.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pymupdf  # noqa: E402
from pdftools import cell_text, clean, lines_above, open_doc  # noqa: E402

BOX_RX = re.compile(
    r'(fuse\s*(panel|box|block)|junction block|relay box|instrument panel|engine (room|compartment)|'
    r'battery terminal|passenger compartment|luggage|trunk|cargo)', re.I)
RATING_RX = re.compile(r'^(\d{1,3}(?:\.\d)?)\s?A$', re.I)
GROUP_LABELS = {'FUSE', 'FUSES', 'RELAY', 'MULTI FUSE', 'FUSIBLE LINK', 'MEGA FUSE', 'MIDI FUSE'}
UNUSED = {'', '-', '―', '—', 'not used', 'spare', 'empty'}


def classify_header(header: list[str]) -> dict[str, int] | None:
    cols: dict[str, int] = {}
    for i, h in enumerate(h.lower() for h in header):
        if re.search(r'fuse no|^no\.?$', h):
            cols['number'] = i
        elif re.search(r'rating|amp|capacity|rat.?ing', h):
            cols['rating'] = i
        elif re.search(r'circuit|protected|component|electrical system', h):
            cols['circuit'] = i
        elif re.search(r'fuse name|description|^name', h):
            cols.setdefault('name', i)
        elif 'symbol' in h:
            cols['symbol'] = i
    if 'rating' in cols and 'circuit' in cols:
        return cols
    return None


def parse_rating(text: str) -> tuple[str | None, float | None]:
    """Normalise a printed rating. Returns (verbatim text, amps if a single value).
    "10A" -> ("10 A", 10.0); "150(180)A" -> ("150 (180) A", None)."""
    t = clean(text).replace(' ', '')
    m = re.fullmatch(r'(\d{1,3}(?:\.\d)?)A', t, re.I)
    if m:
        return f'{m.group(1)} A', float(m.group(1))
    m = re.fullmatch(r'(\d{1,3}(?:\.\d)?)\((\d{1,3}(?:\.\d)?)\)A', t, re.I)
    if m:
        return f'{m.group(1)} ({m.group(2)}) A', None
    m = re.fullmatch(r'(\d{1,3}(?:\.\d)?)A?/(\d{1,3}(?:\.\d)?)A', t, re.I)
    if m:
        return f'{m.group(1)} A / {m.group(2)} A', None
    return None, None


RATING_TOKEN = re.compile(r'^\d{1,3}(?:\.\d)?A$|^\d{1,3}(?:\.\d)?\(\d{1,3}(?:\.\d)?\)A$', re.I)


def split_merged(page: pymupdf.Page, cells: list, rating_idx: int) -> list[list[str]] | None:
    """Some tables omit the ruling line between two fuses, so find_tables returns
    one row holding two fuses ("25A 25A"). Split it into horizontal bands centred
    on each rating line and re-read every cell inside each band."""
    rb = cells[rating_idx]
    if rb is None:
        return None
    words = page.get_text('words', clip=pymupdf.Rect(rb))
    lines: dict[tuple, list] = {}
    for w in words:
        lines.setdefault((w[5], w[6]), []).append(w)
    centres = []
    for ws in lines.values():
        token = ''.join(w[4] for w in sorted(ws, key=lambda w: w[0]))
        if RATING_TOKEN.match(token):
            centres.append((min(w[1] for w in ws) + max(w[3] for w in ws)) / 2)
    centres.sort()
    if len(centres) < 2:
        return None
    top = min(c[1] for c in cells if c)
    bottom = max(c[3] for c in cells if c)
    bounds = [top] + [(centres[i] + centres[i + 1]) / 2 for i in range(len(centres) - 1)] + [bottom]
    bands = []
    for k in range(len(centres)):
        band = []
        for c in cells:
            if c is None:
                band.append('')
                continue
            band.append(cell_text(page, (c[0], max(c[1], bounds[k]), c[2], min(c[3], bounds[k + 1]))))
        bands.append(band)
    return bands


def extract(doc_id: str, first: int, last: int) -> list[dict]:
    doc = open_doc(doc_id)
    rows_out: list[dict] = []
    box = None
    for page_no in range(first, last + 1):
        page = doc[page_no - 1]
        tables = page.find_tables().tables
        if not tables:
            continue
        top = min(t.bbox[1] for t in tables)
        heads = [t for _, _, t in lines_above(page, top) if BOX_RX.search(t)]
        if heads:
            box = heads[-1]
        for t in tables:
            raw = [[c for c in row.cells] for row in t.rows]
            if not raw:
                continue
            header = [cell_text(page, c) for c in raw[0]]
            cols = classify_header(header)
            if cols is None:
                continue
            rows = []
            for cells in raw[1:]:
                texts = [cell_text(page, c) for c in cells]
                rt = texts[cols['rating']] if cols['rating'] < len(texts) else ''
                if len(re.findall(r'\d\s?A\b', rt)) > 1 and not re.search(r'\(', rt):
                    bands = split_merged(page, cells, cols['rating'])
                    if bands:
                        rows.extend(bands)
                        continue
                rows.append(texts)
            for r in rows:
                def col(key):
                    i = cols.get(key)
                    return clean(r[i]) if i is not None and i < len(r) else ''
                number, name = col('number'), col('name')
                if 'name' in cols:
                    # The "Fuse Name" header can span a group-label column ("FUSE")
                    # and the real name column; join the span and drop the label.
                    later = [i for i in cols.values() if i > cols['name']]
                    span = range(cols['name'], min(later) if later else cols['name'] + 1)
                    parts = [clean(r[i]) for i in span if i < len(r) and clean(r[i])]
                    if len(parts) > 1 and parts[0].upper() in GROUP_LABELS:
                        parts = parts[1:]
                    name = ' '.join(parts)
                # Mazda2 layout: first column number, second column name, header only on the first.
                if 'number' not in cols and 'name' in cols and re.fullmatch(r'\d{1,3}', name or ''):
                    number = name
                    name = clean(r[cols['name'] + 1]) if cols['name'] + 1 < len(r) else ''
                rating_text, circuit = col('rating'), col('circuit')
                if not (number or name or circuit):
                    continue
                rating, amps = parse_rating(rating_text)
                unused = circuit.lower() in UNUSED or rating_text.lower() in UNUSED
                row = {
                    'box': box or 'Fuse box',
                    'number': number or None,
                    'name': name or None,
                    'rating': rating,
                    'amps': amps,
                    'circuit': circuit or None,
                    'page': page_no,
                }
                if unused:
                    row['unused'] = True
                if rating is None and not unused:
                    row['needsReview'] = True
                    row['ratingText'] = rating_text
                rows_out.append(row)
    return rows_out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('doc')
    ap.add_argument('pages')
    ap.add_argument('--out')
    a = ap.parse_args()
    first, last = (int(x) for x in a.pages.split('-'))
    rows = extract(a.doc, first, last)
    payload = {'doc': a.doc, 'pages': [first, last], 'fuses': rows}
    text = json.dumps(payload, indent=1, ensure_ascii=False)
    if a.out:
        os.makedirs(os.path.dirname(a.out), exist_ok=True)
        with open(a.out, 'w', encoding='utf-8') as fh:
            fh.write(text + '\n')
    review = sum(1 for r in rows if r.get('needsReview'))
    print(f'{a.doc}: {len(rows)} rows, {review} need review', file=sys.stderr)
    if not a.out:
        print(text)


if __name__ == '__main__':
    main()
