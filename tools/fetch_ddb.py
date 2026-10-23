"""Download an English owner's handbook from Stellantis' public documentation
database (Peugeot "AP", Citroën "AC", DS "DS"): https://public.servicebox.peugeot.com/

The handbooks themselves point owners to this address ("View or download the
handbook at the following address: http://public.servicebox.peugeot.com/APddb/").

usage: python tools/fetch_ddb.py <brand AP|AC|DS> <model> <silhouette> <edition> <out.pdf>
e.g.   python tools/fetch_ddb.py AP 3008 3008_p84 ed01-23 tools/work/pdfs/peugeot_3008_p84_2023.pdf

Silhouettes ending in "dag" are left-hand-drive (direction à gauche); use the
plain silhouette for right-hand-drive markets.
"""
from __future__ import annotations

import json
import re
import sys
import urllib.request

BASE = 'https://public.servicebox.peugeot.com'
UA = {'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) ServiceFinder-data-tools'}


def get(url: str) -> bytes:
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
        return r.read()


def resolve(brand: str, model: str, silhouette: str, edition: str, lang: str = 'en_us') -> str:
    root = f'{BASE}/{brand}ddb/modeles/{model}'
    folder = f'{root}/{silhouette}/{edition}/{lang}'
    ed = get(f'{folder}/edition.xml').decode('utf-8')
    kind = re.search(r'type="([^"]+)"', ed).group(1)
    if kind == 'eGuide':
        params = json.loads(get(f'{folder}/ddbViewerParams.json'))
        doc = f"{root}/{params['documentation_path']}"
        veh = get(f'{doc}/vehicule.xml').decode('utf-8')
        m = re.search(r'<pdfddb lang="en-GB">([^<]+)</pdfddb>', veh)
        if not m:
            raise SystemExit('no en-GB PDF listed in vehicule.xml')
        return f'{doc}/pdfs/{m.group(1)}'
    if kind == 'pdfsimple':
        # index.html embeds the PDF directly: <... src="21K9P.0042_en.pdf#view=...">
        page = get(f'{folder}/index.html').decode('utf-8', 'replace')
        m = re.search(r'src="([\w.-]+\.pdf)', page)
        if not m:
            raise SystemExit('pdfsimple: no PDF referenced by index.html')
        return f'{folder}/{m.group(1)}'
    m = re.search(r'([\w.-]+\.pdf)', ed)
    if not m:
        raise SystemExit(f'edition type {kind}: no PDF named in edition.xml')
    return f'{folder}/{m.group(1)}'


def main():
    brand, model, silhouette, edition, out = sys.argv[1:6]
    url = resolve(brand, model, silhouette, edition)
    data = get(url)
    if not data.startswith(b'%PDF'):
        raise SystemExit(f'not a PDF: {url}')
    with open(out, 'wb') as fh:
        fh.write(data)
    print(f'{out} <- {url} ({len(data) // 1024} KiB)')


if __name__ == '__main__':
    main()
