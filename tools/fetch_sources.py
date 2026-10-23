"""Download the source documents listed in data/sources.toml into tools/work/pdfs/
and check each against its recorded SHA-256.

usage: python tools/fetch_sources.py [<source id> ...]

Sources without a URL (the textbook, a library copy) must be placed in
tools/work/pdfs/<id>.pdf by hand. The NHTSA files are fetched separately; see
tools/extract_nhtsa.py.
"""
from __future__ import annotations

import hashlib
import os
import sys
import tomllib
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PDF_DIR = os.path.join(ROOT, 'tools', 'work', 'pdfs')
UA = {'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) ServiceFinder-data-tools'}


def sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, 'rb') as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    with open(os.path.join(ROOT, 'data', 'sources.toml'), 'rb') as fh:
        sources = tomllib.load(fh)['source']
    wanted = set(sys.argv[1:])
    os.makedirs(PDF_DIR, exist_ok=True)
    failed = 0
    for s in sources:
        if wanted and s['id'] not in wanted or not s.get('sha256'):
            continue
        path = os.path.join(PDF_DIR, f"{s['id']}.pdf")
        if not os.path.exists(path):
            if not s.get('url'):
                print(f"{s['id']}: no URL; place the PDF at {path}")
                failed += 1
                continue
            print(f"{s['id']}: downloading {s['url']}")
            req = urllib.request.Request(s['url'], headers=UA)
            with urllib.request.urlopen(req, timeout=300) as r, open(path + '.part', 'wb') as out:
                out.write(r.read())
            os.replace(path + '.part', path)
        digest = sha256(path)
        if digest != s['sha256']:
            # Publishers sometimes replace a PDF in place; page numbers may then differ.
            print(f"{s['id']}: SHA-256 mismatch (got {digest}); re-check the cited pages before rebuilding")
            failed += 1
        else:
            print(f"{s['id']}: ok")
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
