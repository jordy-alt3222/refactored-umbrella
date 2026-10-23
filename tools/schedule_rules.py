"""Interval-rule logic shared by the PDF extractor and the data build.

No PDF dependency, so tools/build_data.py can use it with the standard library only.
"""
from __future__ import annotations

import re


def clean(text: str | None) -> str:
    return re.sub(r'\s+', ' ', text or '').strip()


NUM = r'(\d{1,3}(?:,\d{3})+|\d+)'


def to_int(s: str) -> int:
    return int(s.replace(',', ''))


def dehyphen(s: str) -> str:
    """Join words split across lines in the manual ("ventila- tion")."""
    return re.sub(r'([a-z])- ([a-z])', r'\1\2', s)


VERBS = {'replace': 'replace', 'inspect': 'inspect', 'add': 'add', 'adjust': 'adjust', 'rotate': 'rotate',
         'check': 'inspect', 'clean': 'clean', 'tighten': 'tighten', 'lubricate': 'lubricate'}
MONTHS = r'(?: or (\d+) months)?'


def parse_text_rule(text: str) -> dict | None:
    """Rules written in a merged cell. Returns None if not understood (never guesses)."""
    t = clean(text)
    low = t.lower().rstrip('.').strip()
    low = re.sub(r'\s*\(\s*[\d,]+\s*miles?\s*\)', '', low)          # drop "(10,000 miles)" asides
    low = re.sub(r'(\d+) years?', lambda m: f'{int(m.group(1)) * 12} months', low)
    low = re.sub(r'(\d)\s*km', r'\1 km', low)
    if re.search(r'no check,? no service required', low):
        return {'action': 'none', 'note': t}
    verbs = '|'.join(VERBS)
    # "At first, Replace 210,000 km or 120 months after that, Replace every 30,000 km or 24 months"
    # "Replace at first 200,000 km or 120 months; after that, every 100,000 km or 60 months"
    m = re.match(rf'^(?:at first,? ({verbs}) (?:at )?|({verbs}) at first )({NUM[1:-1]}) km{MONTHS}[,;]? after that,? (?:({verbs}) )?every ({NUM[1:-1]}) km{MONTHS}$', low)
    if m:
        verb = m.group(1) or m.group(2)
        return {
            'action': VERBS[verb],
            'firstKm': to_int(m.group(3)), 'firstMonths': int(m.group(4)) if m.group(4) else None,
            'everyKm': to_int(m.group(6)), 'everyMonths': int(m.group(7)) if m.group(7) else None,
        }
    m = re.match(rf'^({verbs}) every ({NUM[1:-1]}) km{MONTHS}$', low)
    if m:
        return {'action': VERBS[m.group(1)], 'everyKm': to_int(m.group(2)),
                'everyMonths': int(m.group(3)) if m.group(3) else None}
    m = re.match(rf'^({verbs}) every (\d+) months$', low)
    if m:
        return {'action': VERBS[m.group(1)], 'everyMonths': int(m.group(2))}
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


MARK_ACTIONS = {'R': 'replace', 'I': 'inspect', 'T': 'tighten', 'C': 'clean', 'L': 'lubricate', 'A': 'adjust'}


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
    for letter, action in MARK_ACTIONS.items():
        if letter in 'RI':
            continue
        idx = [i for i, m in enumerate(marks) if m == letter]
        if idx:
            out.append({'action': action, **progression(km_cols, month_cols, idx)})
    return out
