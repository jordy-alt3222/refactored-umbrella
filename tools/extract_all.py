"""Re-run every table extraction declared in data/vehicles/*.toml.

usage: python tools/extract_all.py [vehicle_id ...]

Needs the source PDFs in tools/work/pdfs (python tools/fetch_sources.py).
Writes:
  data/extracted/fuses/<vehicle>.json
  data/extracted/schedules/<vehicle>__<schedule>.json
  data/extracted/pagelabels/<doc>.json
The extracted files are committed so the app data can be rebuilt without the PDFs.
"""
from __future__ import annotations

import json
import os
import sys
import tomllib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import extract_fuses  # noqa: E402
import extract_schedule  # noqa: E402
import page_labels  # noqa: E402
from pdftools import ROOT  # noqa: E402

VEH = os.path.join(ROOT, 'data', 'vehicles')
EXT = os.path.join(ROOT, 'data', 'extracted')


def write(path: str, payload) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(payload, fh, indent=1, ensure_ascii=False)
        fh.write('\n')


def run(vehicle_file: str, docs_done: set[str]) -> None:
    with open(vehicle_file, 'rb') as fh:
        v = tomllib.load(fh)
    vid, doc = v['id'], v['doc']
    if doc not in docs_done:
        write(os.path.join(EXT, 'pagelabels', f'{doc}.json'), page_labels.labels(doc))
        docs_done.add(doc)
    fz = v.get('fuses', {})
    if fz.get('pages') and fz.get('method', 'tables') == 'tables':
        first, last = (int(x) for x in str(fz['pages']).split('-'))
        rows = extract_fuses.extract(doc, first, last)
        write(os.path.join(EXT, 'fuses', f'{vid}.json'), {'doc': doc, 'pages': [first, last], 'fuses': rows})
        print(f'{vid}: {len(rows)} fuses')
    for s in v.get('schedule', []):
        if s.get('method', 'tables') != 'tables':
            continue
        res = extract_schedule.extract(doc, s['pages'], None)
        write(os.path.join(EXT, 'schedules', f"{vid}__{s['id']}.json"), res)
        print(f"{vid}/{s['id']}: {len(res['items'])} schedule rows")


def main():
    wanted = set(sys.argv[1:])
    done: set[str] = set()
    for name in sorted(os.listdir(VEH)):
        if name.endswith('.toml') and (not wanted or name[:-5] in wanted):
            run(os.path.join(VEH, name), done)


if __name__ == '__main__':
    main()
