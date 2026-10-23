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

NUM = r'(\d{1,3}(?:,\d{3})+|\d+)'


def to_int(s: str) -> int:
    return int(s.replace(',', ''))


def dehyphen(s: str) -> str:
    """Join words split across lines in the manual ("ventila- tion")."""
    return re.sub(r'([a-z])- ([a-z])', r'\1\2', s)


def parse_text_rule(text: str) -> dict | None:
    """Rules written in a merged cell. Returns None if not understood."""
    t = clean(text)
    low = t.lower()
    if re.search(r'no check,? no service required', low):
        return {'action': 'none', 'note': t}
    m = re.match(rf'^at first,? (replace|inspect) (?:at )?{NUM} km(?: or {NUM} months)?,? after that,? (replace|inspect) every {NUM} km(?: or {NUM} months)?$', low)
    if m:
        return {
            'action': 'replace' if m.group(1) == 'replace' else 'inspect',
            'firstKm': to_int(m.group(2)), 'firstMonths': to_int(m.group(3)) if m.group(3) else None,
            'everyKm': to_int(m.group(5)), 'everyMonths': to_int(m.group(6)) if m.group(6) else None,
        }
    m = re.match(rf'^(replace|inspect|add|adjust|rotate|check) every {NUM} km(?: or {NUM} months)?$', low)
    if m:
        verb = m.group(1)
        return {
            'action': {'replace': 'replace', 'add': 'add'}.get(verb, 'inspect'),
            'everyKm': to_int(m.group(2)), 'everyMonths': to_int(m.group(3)) if m.group(3) else None,
        }
    return None


def progression(km_cols: list[int], month_cols: list[int], idx: list[int]) -> dict | None:
    """Positions idx (0-based) of a mark -> {everyKm, everyMonths, firstKm?} if regular."""
    if not idx:
        return None
    kms = [km_cols[i] for i in idx]
    months = [month_cols[i] for i in idx]
    if len(kms) == 1:
        # A single mark in the table: only safe if it is the last column's multiple pattern
        return {'atKm': kms, 'atMonths': months}
    step = kms[1] - kms[0]
    mstep = months[1] - months[0]
    regular = all(kms[i + 1] - kms[i] == step for i in range(len(kms) - 1)) and \
        all(months[i + 1] - months[i] == mstep for i in range(len(months) - 1))
    # every column after the first mark that is a multiple of step must be marked
    if regular:
        expected = [k for k in km_cols if k >= kms[0] and (k - kms[0]) % step == 0]
        if expected != kms:
            regular = False
    if not regular:
        return {'atKm': kms, 'atMonths': months}
    rule = {'everyKm': step, 'everyMonths': mstep}
    if kms[0] != step:
        rule['firstKm'] = kms[0]
        rule['firstMonths'] = months[0]
    return rule


def rules_from_marks(km_cols, month_cols, marks: list[str]) -> list[dict]:
    marks = [m.strip().upper() for m in marks]
    out = []
    r_idx = [i for i, m in enumerate(marks) if m == 'R']
    i_idx = [i for i, m in enumerate(marks) if m == 'I']
    if r_idx:
        p = progression(km_cols, month_cols, r_idx)
        out.append({'action': 'replace', **p})
    if i_idx:
        # Inspections at every non-replace column of a regular base step count as "inspect every"
        combined = sorted(set(i_idx) | set(r_idx))
        p = progression(km_cols, month_cols, combined)
        if p and 'everyKm' in p and r_idx:
            out.append({'action': 'inspect', **p, 'note': 'Inspect at services where not replaced'})
        else:
            p = progression(km_cols, month_cols, i_idx)
            out.append({'action': 'inspect', **p})
    return out


def extract(doc_id: str, pages: list[int], title_filter: str | None) -> dict:
    doc = open_doc(doc_id)
    result = {'doc': doc_id, 'pages': pages, 'title': None, 'kmColumns': None, 'monthColumns': None, 'items': []}
    for page_no in pages:
        page = doc[page_no - 1]
        for bbox, rows in table_rows(page):
            heads = [t for _, _, t in lines_above(page, bbox.y0) if 'schedule' in t.lower()]
            title = heads[-1] if heads else None
            if title_filter and (not title or title_filter.lower() not in title.lower()):
                continue
            km_row = next((r for r in rows if any(re.match(r'^km\s*[x×]\s*1,?000', c.lower()) for c in r)), None)
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
                cells = [r[i] if i < len(r) else '' for i in col_idx]
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
