"""Validate the curated data and build the app's offline catalogue.

usage: python tools/build_data.py

Inputs (all committed):
  data/sources.toml                      source documents
  data/vehicles/*.toml                   curated per-vehicle figures, each with a PDF page
  data/extracted/fuses/*.json            fuse tables extracted from the manuals
  data/extracted/schedules/*.json        maintenance grids extracted from the manuals
  data/extracted/pagelabels/*.json       printed page labels
  data/problems/*.toml                   common problems (NHTSA manufacturer bulletins etc.)
  data/procedures/*.toml                 electrical test walkthroughs
Output:
  app/src/main/assets/catalog.json

The build fails rather than guess: a figure without a page, an out-of-range
value, an unmapped schedule variant or an unreviewed extraction row stops it.
"""
from __future__ import annotations

import datetime as dt
import glob
import json
import os
import re
import sys
import tomllib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, 'data')
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'catalog.json')
CATALOG_VERSION = 1

errors: list[str] = []


def err(where: str, msg: str) -> None:
    errors.append(f'{where}: {msg}')


def load_toml(path: str) -> dict:
    with open(path, 'rb') as fh:
        return tomllib.load(fh)


def load_json(path: str):
    with open(path, encoding='utf-8') as fh:
        return json.load(fh)


# ---------------------------------------------------------------- sources

def build_sources() -> dict[str, dict]:
    src = {}
    for s in load_toml(os.path.join(DATA, 'sources.toml'))['source']:
        for k in ('id', 'kind', 'title', 'publisher'):
            if not s.get(k):
                err(f"sources/{s.get('id')}", f'missing {k}')
        src[s['id']] = {
            'id': s['id'], 'kind': s['kind'], 'title': s['title'], 'publisher': s['publisher'],
            'url': s.get('url'), 'market': s.get('market'), 'pages': s.get('pages'),
            'retrieved': s.get('retrieved'), 'note': s.get('note'),
        }
    return src


LABELS: dict[str, dict] = {}


def ref(where: str, sources: dict, doc: str, page, extra: str | None = None) -> dict:
    if doc not in sources:
        err(where, f'unknown source {doc}')
    if sources.get(doc, {}).get('kind') in ('owners_manual', 'service_manual', 'textbook'):
        if not isinstance(page, int) or page < 1:
            err(where, 'missing page')
        elif sources[doc].get('pages') and page > sources[doc]['pages']:
            err(where, f'page {page} beyond document length')
    if doc not in LABELS:
        p = os.path.join(DATA, 'extracted', 'pagelabels', f'{doc}.json')
        LABELS[doc] = load_json(p) if os.path.exists(p) else {}
    r = {'sourceId': doc, 'page': page, 'label': LABELS[doc].get(str(page)) if page else None}
    if extra:
        r['detail'] = extra
    return r


def check_range(where: str, value, lo: float, hi: float, what: str) -> None:
    if value is None:
        return
    if not isinstance(value, (int, float)) or not (lo <= value <= hi):
        err(where, f'{what} {value!r} outside plausible range {lo}-{hi}')


# ---------------------------------------------------------------- schedules

def resolve_engines(where: str, qualifier: str | None, variants: dict, default: list[str]) -> list[str]:
    if not qualifier:
        return list(default)
    result = None
    for part in qualifier.split(' · '):
        if part not in variants:
            err(where, f'schedule qualifier "{part}" not mapped in [schedule.variants]')
            return list(default)
        ids = set(variants[part])
        result = ids if result is None else result & ids
    return [e for e in default if e in (result or set())]


def build_schedule(vid: str, s: dict, engines: set[str], sources: dict, doc: str) -> dict | None:
    where = f'{vid}/schedule/{s["id"]}'
    for e in s.get('engines', []):
        if e not in engines:
            err(where, f'unknown engine {e}')
    items = []
    severe: list[dict] = []
    base_km = base_months = None
    if s.get('method', 'tables') == 'tables':
        path = os.path.join(DATA, 'extracted', 'schedules', f'{vid}__{s["id"]}.json')
        if not os.path.exists(path):
            err(where, f'missing {os.path.relpath(path, ROOT)} (run tools/extract_all.py)')
            return None
        ext = load_json(path)
        base_km = ext['kmColumns'][0] if ext.get('kmColumns') else None
        base_months = ext['monthColumns'][0] if ext.get('monthColumns') else None
        overrides = {(o['item'], o.get('variant')): o for o in s.get('override', [])}
        for it in ext['items']:
            key = (it['item'], it.get('variant'))
            o = overrides.pop(key, None)
            if o and o.get('drop'):
                continue
            rules = o['rules'] if o and 'rules' in o else it.get('rules')
            if it.get('needsReview') and not o:
                err(where, f'p{it["page"]} "{it["item"]}" needs review: add an [[schedule.override]]')
                continue
            if not rules:
                err(where, f'p{it["page"]} "{it["item"]}" has no rules')
                continue
            items.append({
                'name': it['item'],
                'qualifier': it.get('variant'),
                'engineIds': resolve_engines(where, it.get('variant'), s.get('variants', {}), s['engines']),
                'rules': rules,
                'text': it.get('text'),
                'reviewed': bool(o),
                'ref': ref(where, sources, doc, it['page']),
            })
        for key in overrides:
            err(where, f'override for {key} matched no extracted row')
        for sv in ext.get('severe', []):
            severe.append({
                'name': sv['item'], 'qualifier': sv.get('variant'), 'action': sv.get('action'),
                'interval': sv['interval'],
                'engineIds': resolve_engines(where, sv.get('variant'), s.get('variants', {}), s['engines']),
                'ref': ref(where, sources, doc, sv['page']),
            })
    else:
        base_km, base_months = s.get('baseKm'), s.get('baseMonths')
        for it in s.get('item', []):
            items.append({
                'name': it['name'], 'qualifier': it.get('qualifier'),
                'engineIds': resolve_engines(where, it.get('qualifier'), s.get('variants', {}), s['engines']),
                'rules': it['rules'], 'text': it.get('text'),
                'ref': ref(where, sources, doc, it['page']),
            })
    for it in items:
        for r in it['rules']:
            if r.get('action') not in ('replace', 'inspect', 'add', 'none', 'rotate', 'adjust', 'clean', 'lubricate'):
                err(where, f'"{it["name"]}" bad action {r.get("action")}')
            for k in ('everyKm', 'firstKm'):
                check_range(where, r.get(k), 1000, 400000, k)
            for k in ('everyMonths', 'firstMonths'):
                check_range(where, r.get(k), 1, 240, k)
            if 'atKm' in r and not it.get('reviewed', True):
                err(where, f'"{it["name"]}" has irregular marks {r["atKm"]}: needs an override')
            if 'atKm' in r:
                for k in r['atKm']:
                    check_range(where, k, 1000, 400000, 'atKm')
    return {
        'id': s['id'], 'title': s['title'], 'engineIds': s['engines'],
        'baseKm': base_km, 'baseMonths': base_months, 'note': s.get('note'),
        'ref': ref(where, sources, doc, s.get('introPage') or (s['pages'][0] if s.get('pages') else None)),
        'severeRef': ref(where, sources, doc, s['severePages'][0]) if s.get('severePages') else None,
        'items': items,
        'severe': severe,
    }


# ---------------------------------------------------------------- vehicles

TPMS_STATUS = {'standard', 'optional', 'none', 'unknown'}


def build_vehicle(path: str, sources: dict, fuses_out: list) -> dict:
    v = load_toml(path)
    vid = v['id']
    where = vid
    if os.path.basename(path)[:-5] != vid:
        err(where, 'file name must match id')
    doc = v['doc']
    for k in ('make', 'model', 'yearFrom', 'yearTo', 'doc'):
        if k not in v:
            err(where, f'missing {k}')
    if v.get('yearFrom', 0) > v.get('yearTo', 0):
        err(where, 'yearFrom after yearTo')
    engines = []
    for e in v.get('engine', []):
        engines.append({'id': e['id'], 'name': e['name'], 'code': e.get('code'), 'fuel': e.get('fuel')})
        if e.get('fuel') not in ('petrol', 'diesel', 'hybrid', 'plug-in hybrid', 'electric', 'lpg'):
            err(where, f"engine {e['id']} bad fuel {e.get('fuel')}")
    engine_ids = {e['id'] for e in engines}

    def page_of(x, ctx):
        return ref(f'{where}/{ctx}', sources, x.get('doc', doc), x.get('page'))

    oil = []
    for o in v.get('oil', []):
        if o.get('engine') not in engine_ids:
            err(where, f"oil for unknown engine {o.get('engine')}")
        check_range(f'{where}/oil', o.get('litres'), 1.0, 20.0, 'litres')
        oil.append({'engineId': o['engine'], 'litres': o.get('litres'), 'basis': o.get('basis'),
                    'note': o.get('note'), 'ref': page_of(o, 'oil')})
    tyres = []
    for t in v.get('tyre', []):
        for k in ('front', 'rear'):
            check_range(f'{where}/tyre', t.get(k), 150, 700, f'{k} kPa')
        tyres.append({'sizes': t.get('sizes', []), 'frontKpa': t.get('front'), 'rearKpa': t.get('rear'),
                      'spare': bool(t.get('spare')), 'condition': t.get('condition'), 'note': t.get('note'),
                      'engineIds': t.get('engines'), 'ref': page_of(t, 'tyre')})
    tpms = None
    if 'tpms' in v:
        t = v['tpms']
        if t.get('status') not in TPMS_STATUS:
            err(where, f"tpms status {t.get('status')}")
        tpms = {'status': t['status'], 'system': t.get('system', 'unknown'), 'text': t.get('text'),
                'ref': page_of(t, 'tpms')}
    wheel = None
    if 'wheelTorque' in v:
        w = v['wheelTorque']
        check_range(f'{where}/wheelTorque', w.get('minNm'), 40, 400, 'Nm')
        check_range(f'{where}/wheelTorque', w.get('maxNm'), 40, 400, 'Nm')
        if w.get('minNm') and w.get('maxNm') and w['minNm'] > w['maxNm']:
            err(where, 'wheel torque min > max')
        wheel = {'minNm': w.get('minNm'), 'maxNm': w.get('maxNm'), 'text': w.get('text'), 'note': w.get('note'),
                 'ref': page_of(w, 'wheelTorque')}
    calipers = []
    for c in v.get('caliperTorque', []):
        check_range(f'{where}/caliper', c.get('nm'), 5, 400, 'Nm')
        calipers.append({'axle': c['axle'], 'fastener': c['fastener'], 'nm': c.get('nm'), 'text': c.get('text'),
                         'note': c.get('note'), 'variant': c.get('variant'), 'ref': page_of(c, 'caliper')})

    # fuses
    fz = v.get('fuses', {})
    if fz.get('method', 'tables') == 'tables' and fz.get('pages'):
        p = os.path.join(DATA, 'extracted', 'fuses', f'{vid}.json')
        if not os.path.exists(p):
            err(where, f'missing {os.path.relpath(p, ROOT)}')
        else:
            fixes = {(f['page'], f.get('name'), f.get('number')): f for f in fz.get('fix', [])}
            for row in load_json(p)['fuses']:
                key = (row['page'], row.get('name'), row.get('number'))
                fix = fixes.pop(key, None)
                if fix and fix.get('drop'):
                    continue
                if fix:
                    row = {**row, **{k: fix[k] for k in ('rating', 'amps', 'circuit', 'box', 'name', 'number') if k in fix}}
                    row.pop('needsReview', None)
                if row.get('needsReview'):
                    err(where, f"fuse p{row['page']} {row.get('name') or row.get('number')} needs review ({row.get('ratingText')})")
                    continue
                if row.get('amps') is not None:
                    check_range(f'{where}/fuse', row['amps'], 1, 500, 'amps')
                fuses_out.append(fuse_row(vid, row, ref(f'{where}/fuse', sources, doc, row['page']), fz))
            for key in fixes:
                err(where, f'fuse fix {key} matched no row')
    for row in fz.get('row', []):  # hand-transcribed rows (manuals without ruled tables)
        fuses_out.append(fuse_row(vid, row, ref(f'{where}/fuse', sources, row.get('doc', doc), row['page']), fz))

    schedules = [s for s in (build_schedule(vid, s, engine_ids, sources, doc) for s in v.get('schedule', [])) if s]

    gaps = list(v.get('gaps', []))
    if not oil:
        gaps.append('Engine oil capacity not published in the source manual.')
    if not tyres:
        gaps.append('Tyre pressures not published in the source manual (see the tyre placard on the vehicle).')
    if not wheel:
        gaps.append('Wheel nut torque not published in the source manual.')
    if not calipers:
        gaps.append('Brake caliper bolt torque is not in the owner\'s manual; it is only published in the workshop manual.')
    if not schedules:
        gaps.append('Maintenance schedule not available from the source manual.')
    if not fz:
        gaps.append('Fuse allocation not available from the source manual.')
    return {
        'id': vid, 'make': v['make'], 'model': v['model'], 'series': v.get('series'),
        'yearFrom': v['yearFrom'], 'yearTo': v['yearTo'], 'market': v.get('market'),
        'sourceId': doc, 'summary': v.get('summary'), 'engines': engines, 'oil': oil, 'tyres': tyres,
        'tpms': tpms, 'wheelTorque': wheel, 'caliperTorque': calipers, 'schedules': schedules,
        'gaps': gaps, 'fuseNote': fz.get('note'),
    }


def fuse_row(vid: str, row: dict, r: dict, fz: dict | None = None) -> dict:
    fz = fz or {}
    box = row.get('box') or 'Fuse box'
    box = fz.get('rename', {}).get(box, box)
    return {
        'vehicleId': vid, 'box': box, 'engineIds': fz.get('boxEngines', {}).get(box),
        'number': row.get('number'),
        'name': row.get('name'), 'rating': row.get('rating'), 'amps': row.get('amps'),
        'circuit': row.get('circuit'), 'unused': bool(row.get('unused')), 'ref': r,
    }


# ---------------------------------------------------------------- problems / procedures

def build_problems(sources: dict, vehicle_ids: set[str]) -> list[dict]:
    out = []
    for path in sorted(glob.glob(os.path.join(DATA, 'problems', '*.toml'))):
        doc = load_toml(path)
        for p in doc.get('problem', []):
            where = f"problems/{os.path.basename(path)}/{p.get('id')}"
            for k in ('id', 'title', 'make', 'model', 'symptoms', 'diagnosis', 'source'):
                if not p.get(k):
                    err(where, f'missing {k}')
            for v in p.get('vehicles', []):
                if v not in vehicle_ids:
                    err(where, f'unknown vehicle {v}')
            out.append({
                'id': p['id'], 'make': p['make'], 'model': p['model'],
                'yearFrom': p.get('yearFrom'), 'yearTo': p.get('yearTo'), 'market': p.get('market'),
                'vehicleIds': p.get('vehicles', []), 'title': p['title'], 'system': p.get('system'),
                'engines': p.get('engines', []), 'symptoms': p['symptoms'], 'diagnosis': p['diagnosis'],
                'remedy': p.get('remedy'), 'note': p.get('note'),
                'ref': ref(where, sources, p['source'], p.get('page'), p.get('reference')),
            })
    return out


def build_procedures(sources: dict) -> list[dict]:
    out = []
    for path in sorted(glob.glob(os.path.join(DATA, 'procedures', '*.toml'))):
        doc = load_toml(path)
        for p in doc.get('procedure', []):
            where = f"procedures/{p.get('id')}"
            if not p.get('step'):
                err(where, 'no steps')
            out.append({
                'id': p['id'], 'title': p['title'], 'system': p['system'],
                'appliesTo': p.get('appliesTo', 'All makes and models with 12 V negative-earth electrical systems'),
                'summary': p.get('summary'), 'tools': p.get('tools', []), 'safety': p.get('safety', []),
                'steps': [{'title': s['title'], 'detail': s.get('detail'), 'expected': s.get('expected')}
                          for s in p.get('step', [])],
                'results': p.get('results', []),
                'ref': ref(where, sources, p['source'], p.get('page'), p.get('pages')),
            })
    return out


def main() -> int:
    sources = build_sources()
    fuses: list[dict] = []
    vehicles = [build_vehicle(p, sources, fuses) for p in sorted(glob.glob(os.path.join(DATA, 'vehicles', '*.toml')))]
    ids = [v['id'] for v in vehicles]
    if len(ids) != len(set(ids)):
        err('vehicles', 'duplicate id')
    problems = build_problems(sources, set(ids))
    procedures = build_procedures(sources)
    used = {v['sourceId'] for v in vehicles} | {p['ref']['sourceId'] for p in problems} | \
        {p['ref']['sourceId'] for p in procedures} | {f['ref']['sourceId'] for f in fuses}
    for v in vehicles:
        for sec in ('oil', 'tyres', 'caliperTorque'):
            used |= {x['ref']['sourceId'] for x in v[sec]}
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        print(f'\n{len(errors)} problem(s); catalog not written.', file=sys.stderr)
        return 1
    catalog = {
        'version': CATALOG_VERSION,
        'generated': dt.date.today().isoformat(),
        'sources': [s for s in sources.values() if s['id'] in used],
        'vehicles': vehicles,
        'fuses': fuses,
        'problems': problems,
        'procedures': procedures,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8') as fh:
        json.dump(catalog, fh, ensure_ascii=False, separators=(',', ':'))
    print(f'catalog.json: {len(vehicles)} vehicles, {len(fuses)} fuses, '
          f'{sum(len(s["items"]) for v in vehicles for s in v["schedules"])} schedule rows, '
          f'{len(problems)} problems, {len(procedures)} procedures, {os.path.getsize(OUT) // 1024} KiB')
    return 0


if __name__ == '__main__':
    sys.exit(main())
