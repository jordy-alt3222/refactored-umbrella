"""Extract a periodic-maintenance grid (Hyundai/Kia style) into interval rules.

usage: python tools/extract_schedule.py <doc_id> <page>[,<page>...] [--title-filter TEXT] [--out FILE]

The grid has a header row of months and a row of "Km x 1,000" milestones,
then one row per item with I (inspect) / R (replace) / - marks, or a merged
cell such as "Replace every 15,000 km or 12 months".

Mark rows are converted to rules only when the marks form a regular
progression (e.g. R at 45k and 90k -> replace every 45,000 km). Anything that
does not fit is kept verbatim with "needsReview" so it is never guessed.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pdftools import clean, lines_above, open_doc, table_rows  # noqa: E402
from schedule_rules import NUM, dehyphen, parse_text_rule, progression, rules_from_marks, to_int  # noqa: E402,F401

def severe_rows(rows: list[list[str]], page_no: int) -> list[dict]:
    """'Maintenance under severe usage conditions' table: item | operation | interval | conditions."""
    head = [re.sub(r'-\s+', '', c.lower()) for c in rows[0]]
    def idx(word):
        return next((i for i, h in enumerate(head) if word in h), None)
    i_item, i_op, i_int = idx('item'), idx('oper'), idx('interval')
    out, last = [], None
    for r in rows[1:]:
        item = dehyphen(clean(re.sub(r'\*\s?\d+', '', r[i_item]))) if i_item is not None else ''
        qual = [dehyphen(c) for c in r[(i_item or 0) + 1:i_op] if c] if i_op else []
        item = item or last
        if not item:
            continue
        last = item
        interval = dehyphen(clean(r[i_int])) if i_int is not None and i_int < len(r) else ''
        interval = re.sub(r'\s*\(\s*[\d,]+\s*miles?\s*\)', '', interval)
        op = clean(r[i_op]) if i_op is not None and i_op < len(r) else ''
        out.append({'item': item, 'variant': ' · '.join(qual) or None,
                    'action': {'R': 'replace', 'I': 'inspect'}.get(op.upper(), op.lower() or None),
                    'interval': interval, 'page': page_no})
    return out


def extract(doc_id: str, pages: list[int], title_filter: str | None) -> dict:
    doc = open_doc(doc_id)
    result = {'doc': doc_id, 'pages': pages, 'title': None, 'kmColumns': None, 'monthColumns': None,
              'items': [], 'severe': []}
    for page_no in pages:
        page = doc[page_no - 1]
        for bbox, rows in table_rows(page):
            if rows and any('driving condi' in re.sub(r'-\s+', '', c.lower()) for c in rows[0]):
                result['severe'].extend(severe_rows(rows, page_no))
                continue
            heads = [t for _, _, t in lines_above(page, bbox.y0) if 'schedule' in t.lower()]
            title = heads[-1] if heads else None
            if title_filter and (not title or title_filter.lower() not in title.lower()):
                continue
            km_row = next((r for r in rows if any(re.match(r'^km\s*[x×v]\s*1,?000', c.lower()) for c in r)), None)
            mo_row = next((r for r in rows if any(c.lower() == 'months' for c in r)), None)
            if not km_row or not mo_row:
                continue
            # Mark columns are the ones whose header cell holds a kilometre figure.
            col_idx = [i for i, c in enumerate(km_row) if re.fullmatch(r'\d{1,3}', c)]
            km_cols = [to_int(km_row[i]) * 1000 for i in col_idx]
            mo_cols = [to_int(mo_row[i]) for i in col_idx]
            if result['kmColumns'] and result['kmColumns'] != km_cols:
                raise SystemExit(f'p{page_no}: column header differs from earlier page')
            result['kmColumns'], result['monthColumns'] = km_cols, mo_cols
            result['title'] = result['title'] or (re.sub(r'\s*\(continue[d]?\)\s*', ' ', title or '', flags=re.I).strip())
            start = rows.index(km_row) + 1
            last_item = None
            for r in rows[start:]:
                item = dehyphen(clean(re.sub(r'\*\s?\d+', '', r[0])))
                qualifiers = [dehyphen(c) for c in r[1:col_idx[0]] if c]
                # some fonts extract the "I" mark as "|" or "l"
                cells = [('I' if (r[i] if i < len(r) else '').strip() in ('|', 'l') else (r[i] if i < len(r) else ''))
                         for i in col_idx]
                if not item and (qualifiers or any(cells)) and last_item:
                    item = last_item  # sub-row under a merged item cell (e.g. diesel variant)
                if not item:
                    continue
                last_item = item
                entry = {'item': item, 'variant': ' · '.join(qualifiers) or None, 'page': page_no}
                filled = [c for c in cells if c]
                if len(filled) == 1 and not re.fullmatch(r'[IR\-]', filled[0], re.I):
                    rule = parse_text_rule(filled[0])
                    entry['text'] = filled[0]
                    if rule:
                        entry['rules'] = [rule]
                    else:
                        entry['needsReview'] = True
                elif all(re.fullmatch(r'[IR\-–]?', c or '', re.I) for c in cells) and len(cells) == len(km_cols):
                    entry['marks'] = [c or '-' for c in cells]
                    rules = rules_from_marks(km_cols, mo_cols, entry['marks'])
                    entry['rules'] = rules
                    if any('atKm' in x for x in rules):
                        entry['needsReview'] = True
                else:
                    entry['cells'] = cells
                    entry['needsReview'] = True
                result['items'].append(entry)
    return result


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('doc')
    ap.add_argument('pages')
    ap.add_argument('--title-filter')
    ap.add_argument('--out')
    a = ap.parse_args()
    pages = [int(p) for p in a.pages.split(',')]
    res = extract(a.doc, pages, a.title_filter)
    text = json.dumps(res, indent=1, ensure_ascii=False)
    if a.out:
        os.makedirs(os.path.dirname(a.out), exist_ok=True)
        with open(a.out, 'w', encoding='utf-8') as fh:
            fh.write(text + '\n')
    n = sum(1 for i in res['items'] if i.get('needsReview'))
    print(f"{a.doc}: '{res['title']}' {len(res['items'])} items, {n} need review", file=sys.stderr)
    if not a.out:
        print(text)


if __name__ == '__main__':
    main()
