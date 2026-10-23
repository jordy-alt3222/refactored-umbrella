"""Copy the NHTSA bulletin summaries that data/problems/*.toml cites into
data/extracted/nhtsa/bulletins.json, so the catalogue build does not need the
large CSV files.

Source: NHTSA Manufacturer Communications files (US government data, public
domain), https://static.nhtsa.gov/odi/ffdd/tsbs/ — download the
MFR_COMMS_RECEIVED_<years>.zip files into tools/work/nhtsa/ and unzip them.

usage: python tools/extract_nhtsa.py
writes {"<NHTSA ID>": {"summary": "...", "vehicles": [{"make", "model", "years": [...]}]}}
"""
from __future__ import annotations

import csv
import glob
import json
import os
import re
import sys
import tomllib
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'data', 'extracted', 'nhtsa', 'bulletins.json')


def cited_ids() -> set[str]:
    ids = set()
    for path in glob.glob(os.path.join(ROOT, 'data', 'problems', '*.toml')):
        with open(path, 'rb') as fh:
            doc = tomllib.load(fh)
        for p in doc.get('problem', []):
            ids |= set(p.get('bulletins', []))
    return ids


def main() -> int:
    want = cited_ids()
    files = sorted(glob.glob(os.path.join(ROOT, 'tools', 'work', 'nhtsa', 'MFR_COMMS_*.csv')))
    if not files:
        raise SystemExit('No tools/work/nhtsa/MFR_COMMS_*.csv files; see the docstring.')
    csv.field_size_limit(10 ** 9)
    summary: dict[str, str] = {}
    years: dict[str, dict[tuple[str, str], set[int]]] = defaultdict(lambda: defaultdict(set))
    for f in files:
        with open(f, newline='', encoding='utf-8', errors='replace') as fh:
            for r in csv.DictReader(fh):
                nid = r['TSB/Document ID']
                if nid not in want:
                    continue
                s = ' '.join(r['Concise Summary'].split())
                # The same ID is filed once per model; keep the most complete summary text.
                if len(s) > len(summary.get(nid, '')):
                    summary[nid] = s
                ys = {int(y) for y in re.findall(r'\d{4}', r['Model Year']) if y != '9999'}
                years[nid][(r['Make'].strip().upper(), ' '.join(r['Model'].split()).upper())] |= ys
    missing = sorted(want - summary.keys())
    if missing:
        print('not found in the CSV files: ' + ', '.join(missing), file=sys.stderr)
        return 1
    out = {nid: {'summary': summary[nid],
                 'vehicles': [{'make': mk, 'model': mo, 'years': sorted(ys)}
                              for (mk, mo), ys in sorted(years[nid].items())]}
           for nid in sorted(summary)}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8') as fh:
        json.dump(out, fh, ensure_ascii=False, indent=1)
    print(f'{OUT}: {len(out)} bulletins')
    return 0


if __name__ == '__main__':
    sys.exit(main())
