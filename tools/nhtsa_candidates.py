"""List candidate "common problem" bulletins from NHTSA's public Manufacturer
Communications files (US government data, public domain).

Download the CSV files from https://static.nhtsa.gov/odi/ffdd/tsbs/
(MFR_COMMS_RECEIVED_<years>.zip) into tools/work/nhtsa/ and unzip them.

usage: python tools/nhtsa_candidates.py <MAKE> <MODEL-regex> <yearFrom> <yearTo> [keyword-regex]

Prints one line per bulletin (NHTSA ID, model years, summary) for bulletins that
describe a fault condition, so they can be reviewed and curated into
data/problems/*.toml. Nothing here is published automatically.
"""
from __future__ import annotations

import csv
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FAULT = re.compile(r'(leak|noise|fail|inoperative|no start|does not start|won.t start|stall|misfire|rough|'
                   r'overheat|warning (light|lamp)|MIL|DTC|P[0-9]{4}|vibration|loss of|intermittent|'
                   r'drain|consum|smoke|knock|rattle|crack|corros|water ingress|hesitat)', re.I)
SKIP = re.compile(r'(software update for (the )?(radio|infotainment)|navigation map|bluetooth|apple carplay|'
                  r'android auto|warranty extension letter|customer satisfaction program notification|'
                  r'paint|wind noise from door seal)', re.I)


def main():
    make, model_rx, y0, y1 = sys.argv[1].upper(), re.compile(sys.argv[2], re.I), int(sys.argv[3]), int(sys.argv[4])
    kw = re.compile(sys.argv[5], re.I) if len(sys.argv) > 5 else None
    csv.field_size_limit(10 ** 9)
    seen = {}
    for f in sorted(glob.glob(os.path.join(ROOT, 'tools', 'work', 'nhtsa', 'MFR_COMMS_*.csv'))):
        with open(f, newline='', encoding='utf-8', errors='replace') as fh:
            for r in csv.DictReader(fh):
                if r['Make'].upper() != make or not model_rx.search(r['Model']):
                    continue
                years = [int(y) for y in re.findall(r'\d{4}', r['Model Year']) if y != '9999']
                if not years or max(years) < y0 or min(years) > y1:
                    continue
                s = ' '.join(r['Concise Summary'].split())
                if not FAULT.search(s) or SKIP.search(s) or (kw and not kw.search(s)):
                    continue
                key = s[:160]
                if key in seen:
                    continue
                seen[key] = (r['TSB/Document ID'], r['Model'], r['Model Year'], s)
    for nid, model, yrs, s in seen.values():
        print(f'{nid} | {model} | {yrs} | {s[:700]}')
    print(f'-- {len(seen)} candidates', file=sys.stderr)


if __name__ == '__main__':
    main()
