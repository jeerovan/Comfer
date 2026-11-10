#!/usr/bin/env python3
"""Version Comfer's Google Play listing and immutable performance evidence.

Examples (from repository root, using venv/bin/python for network collection):
  scripts/play_listing.py import-baseline --snapshot store-listing/private/publisher-baseline.json --reports store-listing/private/report-manifest.json
  scripts/play_listing.py add-candidate --manifest store-listing/v002/manifest.json
  scripts/play_listing.py sync --days 90
  scripts/play_listing.py summary --start 2026-10-01 --end 2026-10-16
  scripts/play_listing.py compare --version 2 --days 28

Remote operations only read data; temporary Publisher edits are deleted, never
committed. Activation is a local evidence event, not a publishing operation.
"""
import argparse
import collections
import csv
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation
import hashlib
import io
import json
from pathlib import Path
import re
import sqlite3
import sys
from urllib.parse import urlparse
from urllib.request import Request, urlopen
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.jeerovan.comfer'
BUCKET = 'pubsite_prod_5435940062993501423'
ZONE = 'America/Los_Angeles'
IMAGE_TYPES = ('icon', 'featureGraphic', 'phoneScreenshots', 'sevenInchScreenshots',
               'tenInchScreenshots', 'tvBanner', 'tvScreenshots', 'wearScreenshots')
DIMENSIONS = {'Country / region', 'Country', 'Language', 'Traffic source', 'Search term',
              'UTM source', 'UTM campaign', 'App Version Code', 'Device', 'Android OS Version'}
SCHEMA = '''
PRAGMA foreign_keys=ON;
CREATE TABLE IF NOT EXISTS listing_versions(
 id INTEGER PRIMARY KEY, version INTEGER NOT NULL UNIQUE, package_name TEXT NOT NULL,
 label TEXT NOT NULL, prepared_at TEXT NOT NULL, initial_state TEXT NOT NULL,
 content_sha256 TEXT NOT NULL, content_json TEXT NOT NULL, rationale TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS listing_locales(
 version_id INTEGER NOT NULL REFERENCES listing_versions(id), locale TEXT NOT NULL,
 title TEXT NOT NULL, short_description TEXT NOT NULL, full_description TEXT NOT NULL,
 video_url TEXT, PRIMARY KEY(version_id,locale));
CREATE TABLE IF NOT EXISTS listing_assets(
 version_id INTEGER NOT NULL REFERENCES listing_versions(id), locale TEXT NOT NULL,
 kind TEXT NOT NULL, ordinal INTEGER NOT NULL, local_path TEXT, source_url TEXT,
 sha256 TEXT, platform_sha256 TEXT, metadata_json TEXT NOT NULL,
 PRIMARY KEY(version_id,locale,kind,ordinal));
CREATE TABLE IF NOT EXISTS source_snapshots(
 id INTEGER PRIMARY KEY, kind TEXT NOT NULL, observed_at TEXT NOT NULL,
 sha256 TEXT NOT NULL, payload_json TEXT NOT NULL, UNIQUE(kind,sha256));
CREATE TABLE IF NOT EXISTS asset_files(
 sha256 TEXT PRIMARY KEY, byte_length INTEGER NOT NULL, payload BLOB NOT NULL);
CREATE TABLE IF NOT EXISTS listing_observations(
 source_id INTEGER NOT NULL REFERENCES source_snapshots(id),
 version_id INTEGER NOT NULL REFERENCES listing_versions(id), observed_at TEXT NOT NULL,
 PRIMARY KEY(source_id,version_id));
CREATE TABLE IF NOT EXISTS listing_identities(
 version_id INTEGER NOT NULL REFERENCES listing_versions(id), public_sha256 TEXT NOT NULL,
 PRIMARY KEY(version_id,public_sha256));
CREATE TABLE IF NOT EXISTS listing_inheritance(
 version_id INTEGER NOT NULL REFERENCES listing_versions(id), parent_id INTEGER NOT NULL REFERENCES listing_versions(id),
 locale TEXT NOT NULL, kind TEXT NOT NULL, PRIMARY KEY(version_id,locale,kind));
CREATE TABLE IF NOT EXISTS lifecycle_events(
 id INTEGER PRIMARY KEY, version_id INTEGER NOT NULL REFERENCES listing_versions(id),
 event TEXT NOT NULL, recorded_at TEXT NOT NULL, effective_at TEXT, evidence_json TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS report_objects(
 id INTEGER PRIMARY KEY, bucket TEXT NOT NULL, object_name TEXT NOT NULL, generation TEXT NOT NULL,
 kind TEXT NOT NULL, dimension TEXT NOT NULL, aggregation TEXT NOT NULL, timezone TEXT NOT NULL,
 fetched_at TEXT NOT NULL, updated_at TEXT, sha256 TEXT NOT NULL, payload BLOB NOT NULL,
 headers_json TEXT NOT NULL, row_count INTEGER NOT NULL,
 UNIQUE(bucket,object_name,generation));
CREATE TABLE IF NOT EXISTS performance_rows(
 report_id INTEGER NOT NULL REFERENCES report_objects(id), row_number INTEGER NOT NULL,
 date TEXT NOT NULL, dimensions_json TEXT NOT NULL, metrics_json TEXT NOT NULL, raw_json TEXT NOT NULL,
 PRIMARY KEY(report_id,row_number));
CREATE INDEX IF NOT EXISTS performance_dates ON performance_rows(date,report_id);
CREATE TABLE IF NOT EXISTS evidence(
 id INTEGER PRIMARY KEY, kind TEXT NOT NULL, evidence_key TEXT NOT NULL,
 observed_at TEXT NOT NULL, sha256 TEXT NOT NULL, payload_json TEXT NOT NULL,
 UNIQUE(kind,evidence_key,sha256));
CREATE TABLE IF NOT EXISTS collection_runs(
 id INTEGER PRIMARY KEY, started_at TEXT NOT NULL, finished_at TEXT, status TEXT NOT NULL,
 result_json TEXT NOT NULL);
CREATE VIEW IF NOT EXISTS latest_reports AS SELECT r.* FROM report_objects r
 WHERE NOT EXISTS(SELECT 1 FROM report_objects n WHERE n.bucket=r.bucket AND n.object_name=r.object_name
 AND (CAST(n.generation AS INTEGER)>CAST(r.generation AS INTEGER)
 OR (n.generation=r.generation AND n.id>r.id)));
CREATE VIEW IF NOT EXISTS latest_performance AS SELECT p.*,r.kind,r.dimension,r.aggregation,
 r.timezone,r.object_name,r.generation,r.fetched_at FROM performance_rows p
 JOIN latest_reports r ON r.id=p.report_id;
CREATE VIEW IF NOT EXISTS version_status AS SELECT v.version,v.label,v.prepared_at,
 COALESCE((SELECT e.event FROM lifecycle_events e WHERE e.version_id=v.id ORDER BY e.id DESC LIMIT 1),
 v.initial_state) status FROM listing_versions v;
'''


def now():
    return datetime.now(timezone.utc).isoformat()


def dump(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True)


def sha(payload):
    return hashlib.sha256(payload).hexdigest()


def connect(path):
    path = Path(path)
    if path.exists() and path.stat().st_size:
        # SQLite's backup API handles an existing WAL consistently.
        with sqlite3.connect(path) as source, sqlite3.connect(str(path) + '.backup-' +
                datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')) as dest:
            source.backup(dest)
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    conn.executescript(SCHEMA)
    for table in ('listing_versions', 'listing_locales', 'listing_assets', 'source_snapshots',
                  'listing_observations', 'listing_identities', 'listing_inheritance', 'lifecycle_events',
                  'report_objects', 'performance_rows', 'evidence', 'asset_files'):
        for operation in ('UPDATE', 'DELETE'):
            conn.execute(f"CREATE TRIGGER IF NOT EXISTS immutable_{table}_{operation.lower()} BEFORE {operation} ON {table} BEGIN SELECT RAISE(ABORT,'append-only evidence'); END")
    conn.commit()
    return conn


def save_evidence(conn, kind, key, payload, observed_at=None):
    raw = dump(payload)
    conn.execute('INSERT OR IGNORE INTO evidence(kind,evidence_key,observed_at,sha256,payload_json) VALUES(?,?,?,?,?)',
                 (kind, key, observed_at or now(), sha(raw.encode()), raw))


def save_asset_file(conn, payload):
    conn.execute('INSERT OR IGNORE INTO asset_files VALUES(?,?,?)', (sha(payload), len(payload), payload))


def public_digest(locales, assets):
    """Canonical visible content, independent of storage/collection ordering."""
    copy = [{key: row.get(key) for key in ('language','title','shortDescription','fullDescription','video')} for row in locales]
    media = [{key: asset[key] for key in ('locale','kind','order')} |
             {'sha256':asset.get('platform_sha256') or asset.get('sha256')} for asset in assets]
    return sha(dump({'locales':sorted(copy,key=lambda row:row['language']),
                     'assets':sorted(media,key=lambda row:(row['locale'],row['kind'],row['order']))}).encode())


def effective_assets(conn, ident, seen=None):
    seen = set() if seen is None else seen
    if ident in seen:
        raise ValueError('Cyclic listing inheritance')
    seen = seen | {ident}
    assets = [json.loads(row[0]) for row in conn.execute('SELECT metadata_json FROM listing_assets WHERE version_id=?', (ident,))]
    for row in conn.execute('SELECT parent_id,locale,kind FROM listing_inheritance WHERE version_id=?', (ident,)):
        assets.extend(asset for asset in effective_assets(conn,row['parent_id'],seen)
                      if asset['locale']==row['locale'] and asset['kind']==row['kind'])
    return assets


def save_version(conn, locales, assets, *, label, state, rationale, version=None, prepared_at=None):
    if not locales or len({l['language'] for l in locales}) != len(locales):
        raise ValueError('Listing locales must be nonempty and unique')
    for listing in locales:
        for field, limit in [('title', 30), ('shortDescription', 80), ('fullDescription', 4000)]:
            if not isinstance(listing.get(field), str) or not listing[field].strip() or len(listing[field]) > limit:
                raise ValueError(f'{field} must contain 1–{limit} characters')
    content = {'locales': locales, 'assets': assets}
    raw = dump(content)
    # Storage locations and observation metadata must not create a new listing.
    fingerprint = {'locales': locales, 'assets': [
        {key: asset[key] for key in ('locale', 'kind', 'order')} |
        {'sha256': asset.get('platform_sha256') or asset.get('sha256')}
        for asset in assets]}
    digest = sha(dump(fingerprint).encode())
    if version is not None:
        previous = conn.execute('SELECT id,content_sha256 FROM listing_versions WHERE version=?', (version,)).fetchone()
        if previous:
            if previous['content_sha256'] != digest:
                raise ValueError('Version already exists with different content; create the next revision')
            return previous['id']
    elif state == 'observed':
        # A deployed candidate keeps its revision identity. Observation still
        # does not supply a verified historical activation timestamp.
        known = conn.execute('SELECT v.id FROM listing_identities i JOIN listing_versions v ON v.id=i.version_id WHERE i.public_sha256=? ORDER BY v.version DESC LIMIT 1',
                             (public_digest(locales,assets),)).fetchone()
        if known:
            return known['id']
        previous = conn.execute('SELECT id FROM listing_versions WHERE content_sha256=? AND initial_state=?', (digest, state)).fetchone()
        if previous:
            return previous['id']
    if version is None:
        version = conn.execute('SELECT COALESCE(max(version),0)+1 FROM listing_versions').fetchone()[0]
    with conn:
        ident = conn.execute('INSERT INTO listing_versions(version,package_name,label,prepared_at,initial_state,content_sha256,content_json,rationale) VALUES(?,?,?,?,?,?,?,?)',
                             (version, PACKAGE, label, prepared_at or now(), state, digest, raw, rationale)).lastrowid
        for listing in locales:
            conn.execute('INSERT INTO listing_locales VALUES(?,?,?,?,?,?)',
                         (ident, listing['language'], listing['title'], listing['shortDescription'], listing['fullDescription'], listing.get('video')))
        for asset in assets:
            conn.execute('INSERT INTO listing_assets VALUES(?,?,?,?,?,?,?,?,?)',
                         (ident, asset['locale'], asset['kind'], asset['order'], asset.get('local_path'),
                          asset.get('source_url'), asset.get('sha256'), asset.get('platform_sha256'), dump(asset)))
    return ident


def save_snapshot(conn, snapshot, root=ROOT, version=None):
    locales = snapshot['listings']['listings']
    assets = []
    for locale, kinds in snapshot.get('images', {}).items():
        for kind, response in kinds.items():
            if 'error_status' in response:
                raise ValueError(f'Incomplete asset inventory: {locale}/{kind}')
            for order, image in enumerate(response.get('images', []), 1):
                path = image.get('local_path')
                if path:
                    payload = (root / path).read_bytes()
                    if sha(payload) != image.get('download_sha256'):
                        raise ValueError('Baseline asset hash mismatch')
                    with conn:
                        save_asset_file(conn, payload)
                assets.append({'locale': locale, 'kind': kind, 'order': order, 'local_path': path,
                               'source_url': image['url'], 'sha256': image.get('download_sha256'),
                               'platform_sha256': image.get('sha256'), 'platform_id': image.get('id'),
                               'download_error': image.get('download_error'),
                               'byte_provenance': 'Google-served image download; platform upload hash retained separately'})
    observed_at = snapshot.get('observed_at', now())
    ident = save_version(conn, locales, assets, label='Observed Play main listing', state='observed',
                         rationale='Exact observation; historical effective date and custom-listing/experiment exposure unknown.',
                         version=version, prepared_at=observed_at)
    raw = dump(snapshot)
    with conn:
        conn.execute('INSERT OR IGNORE INTO listing_identities VALUES(?,?)', (ident,public_digest(locales,assets)))
        conn.execute('INSERT OR IGNORE INTO source_snapshots(kind,observed_at,sha256,payload_json) VALUES(?,?,?,?)',
                     ('publisher', observed_at, sha(raw.encode()), raw))
        source = conn.execute('SELECT id FROM source_snapshots WHERE kind=? AND sha256=?', ('publisher', sha(raw.encode()))).fetchone()[0]
        conn.execute('INSERT OR IGNORE INTO listing_observations VALUES(?,?,?)', (source, ident, observed_at))
    return ident


def report_identity(name):
    match = re.fullmatch(r'(total_)?(store_performance|installs|ratings|crashes)_' + re.escape(PACKAGE) + r'_(\d{6})_(.+)\.csv', Path(name).name)
    if match:
        return match[2], match[4], 'monthly' if match[1] else 'daily', match[3]
    match = re.fullmatch(r'reviews_' + re.escape(PACKAGE) + r'_(\d{6})\.csv', Path(name).name)
    if match:
        return 'reviews', 'review', 'event', match[1]
    raise ValueError('Unsupported report name or different package: ' + name)


def number(value):
    if value is None or value.strip() == '':
        return None
    try:
        numeric = Decimal(value)
    except InvalidOperation as exc:
        raise ValueError('Invalid metric value') from exc
    if not numeric.is_finite() or numeric < 0:
        raise ValueError('Metrics must be finite and nonnegative')
    return float(numeric)


def parse_report(payload, name):
    kind, dimension, aggregation, month = report_identity(name)
    encoding = 'utf-16' if payload.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig'
    reader = csv.DictReader(io.StringIO(payload.decode(encoding)))
    headers = reader.fieldnames or []
    if not headers or len(headers) != len(set(headers)):
        raise ValueError('Missing or duplicate CSV headers')
    package_header = next((h for h in ('Package name', 'Package Name') if h in headers), None)
    if not package_header or ('Date' not in headers and kind != 'reviews'):
        raise ValueError('CSV lacks expected package/date headers')
    output = []
    for index, row in enumerate(reader, 1):
        if None in row or any(value is None for value in row.values()) or row[package_header] != PACKAGE:
            raise ValueError('Malformed CSV or a different app in report')
        day = row.get('Date', row.get('Review Submit Date and Time', ''))[:10]
        if day:
            date.fromisoformat(day)
            if kind != 'reviews' and day[:7].replace('-', '') != month:
                raise ValueError('Report date outside object month')
        elif kind != 'reviews':
            raise ValueError('Missing metric date')
        dims = {key: value for key, value in row.items() if key in DIMENSIONS}
        metrics = {key: value for key, value in row.items() if key not in DIMENSIONS | {package_header, 'Date'}}
        for metric in ('Store listing visitors', 'Store listing acquisitions'):
            if metric in metrics:
                number(metrics[metric])
        visitors, acquired = number(metrics.get('Store listing visitors')), number(metrics.get('Store listing acquisitions'))
        if visitors is not None and acquired is not None and acquired > visitors:
            raise ValueError('Acquisitions exceed matched visitors')
        output.append((index, day, dump(dims), dump(metrics), dump(row)))
    return (kind, dimension, aggregation), headers, output


def import_report(conn, obj, payload, bucket=BUCKET):
    name, generation = obj['name'], str(obj['generation'])
    if not generation.isdigit():
        raise ValueError('GCS generation must be numeric')
    digest = sha(payload)
    if obj.get('sha256') and digest != obj['sha256']:
        raise ValueError('Report SHA-256 mismatch')
    previous = conn.execute('SELECT id,sha256 FROM report_objects WHERE bucket=? AND object_name=? AND generation=?', (bucket, name, generation)).fetchone()
    if previous:
        if previous['sha256'] != digest:
            raise ValueError('Same generation has changed bytes')
        return previous['id']
    identity, headers, rows = parse_report(payload, name)
    with conn:
        ident = conn.execute('INSERT INTO report_objects(bucket,object_name,generation,kind,dimension,aggregation,timezone,fetched_at,updated_at,sha256,payload,headers_json,row_count) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)',
                             (bucket, name, generation, *identity, 'UTC' if identity[0] == 'reviews' else ZONE,
                              now(), obj.get('updated'), digest, payload, dump(headers), len(rows))).lastrowid
        conn.executemany('INSERT INTO performance_rows VALUES(?,?,?,?,?,?)', [(ident, *row) for row in rows])
    return ident


def add_candidate(conn, manifest_path, root=ROOT):
    root = Path(root).resolve()
    path = Path(manifest_path).resolve()
    manifest = json.loads(path.read_text())
    listing = dict(manifest['listing'])
    listing['language'] = listing.pop('locale')
    assets, counts = [], collections.Counter()
    for asset in manifest['assets']:
        file = path.parent / asset['path']
        digest = sha(file.read_bytes())
        if digest != asset.get('sha256'):
            raise ValueError('Candidate requires correct upload hashes')
        with conn:
            save_asset_file(conn, file.read_bytes())
        counts[(asset['locale'], asset['kind'])] += 1
        assets.append(dict(asset, local_path=str(file.relative_to(root)), order=counts[(asset['locale'], asset['kind'])]))
    ident = save_version(conn, [listing], assets, label=manifest['label'], state='prepared', version=manifest['version'],
                         rationale=manifest['hypothesis'])
    with conn:
        for kind in manifest.get('retained_assets',[]):
            if conn.execute('SELECT 1 FROM listing_inheritance WHERE version_id=? AND locale=? AND kind=?',
                            (ident,listing['language'],kind)).fetchone():
                continue
            parent = conn.execute('SELECT version_id FROM listing_observations WHERE version_id<>? ORDER BY observed_at DESC LIMIT 1', (ident,)).fetchone()
            if not parent or not any(asset['kind']==kind and asset['locale']==listing['language'] for asset in effective_assets(conn,parent[0])):
                raise ValueError('Retained asset has no observed baseline: '+kind)
            if any(asset['kind']==kind and asset['locale']==listing['language'] for asset in assets):
                raise ValueError('Asset cannot be replaced and retained together')
            conn.execute('INSERT INTO listing_inheritance VALUES(?,?,?,?)',(ident,parent[0],listing['language'],kind))
        conn.execute('INSERT OR IGNORE INTO listing_identities VALUES(?,?)',(ident,public_digest([listing],effective_assets(conn,ident))))
        save_evidence(conn, 'candidate_manifest', str(manifest['version']), manifest)
    return ident


def selected(conn, kind, dimension, start, end):
    return conn.execute('SELECT * FROM latest_performance WHERE kind=? AND dimension=? AND aggregation=? AND date>=? AND date<? ORDER BY date,report_id,row_number',
                        (kind, dimension, 'daily', str(start), str(end))).fetchall()


def grouped(rows, key=None, only_source=None):
    values = {}
    for row in rows:
        dims, metrics = json.loads(row['dimensions_json']), json.loads(row['metrics_json'])
        if only_source and dims.get('Traffic source') != only_source:
            continue
        label = dims.get(key, '') if key else 'all reported segments'
        stats = values.setdefault(label, {'visitors': 0., 'acquisitions': 0., 'rows': 0,
                                         'missing_rows': 0, 'missing_visitors': 0, 'missing_acquisitions': 0})
        numerator, denominator = number(metrics.get('Store listing acquisitions')), number(metrics.get('Store listing visitors'))
        stats['rows'] += 1
        if denominator is not None:
            stats['visitors'] += denominator
        else:
            stats['missing_visitors'] += 1
        if numerator is not None:
            stats['acquisitions'] += numerator
        else:
            stats['missing_acquisitions'] += 1
        if numerator is None or denominator is None:
            stats['missing_rows'] += 1
    for stats in values.values():
        if stats['missing_visitors'] == stats['rows']:
            stats['visitors'] = None
        if stats['missing_acquisitions'] == stats['rows']:
            stats['acquisitions'] = None
        stats['acquisition_conversion_rate'] = stats['acquisitions']/stats['visitors'] if stats['visitors'] and not stats['missing_rows'] else None
    return dict(sorted(values.items(), key=lambda pair: -(pair[1]['visitors'] or 0)))


def summary(conn, start, end):
    traffic = selected(conn, 'store_performance', 'traffic_source', start, end)
    countries = selected(conn, 'store_performance', 'country', start, end)
    installs = selected(conn, 'installs', 'overview', start, end)
    ratings = selected(conn, 'ratings', 'overview', start, end)
    latest_rating = json.loads(ratings[-1]['metrics_json']) if ratings else None
    install_counts = [number(json.loads(row['metrics_json']).get('Daily User Installs')) for row in installs]
    return {
        'start_inclusive': str(start), 'end_exclusive': str(end), 'timezone': ZONE,
        'versions': [dict(r) for r in conn.execute('SELECT * FROM version_status')],
        'freshness': [dict(r) for r in conn.execute('SELECT kind,dimension,aggregation,min(date) first_date,max(date) latest_date,count(*) rows FROM latest_performance GROUP BY 1,2,3')],
        'traffic_sources': grouped(traffic, 'Traffic source'),
        'search_terms_search_only': grouped(traffic, 'Search term', only_source='Google Play search'),
        'countries_separate_axis': grouped(countries, 'Country / region'),
        'daily_user_installs_sum': sum(install_counts) if install_counts and all(n is not None for n in install_counts) else None,
        'install_rows_with_missing_metric': sum(n is None for n in install_counts),
        'latest_rating_export': {'date': ratings[-1]['date'], 'metrics': latest_rating} if ratings else None,
        'limitations': ['Segment-day counts are not period-unique people; separate report axes must not be added.',
                        'Legacy successful acquisitions are not 2026 Console install clicks/CTR.',
                        'The baseline observation does not establish which listing was shown on historical dates.',
                        'Missing/suppressed terms are not zero; Other cannot identify individual queries.',
                        'Custom listings, experiments, retention, benchmarks and current CTR require further Console evidence.'],
    }


def activate(conn, version, effective_at, evidence):
    parsed = datetime.fromisoformat(effective_at.replace('Z', '+00:00'))
    if parsed.tzinfo is None or parsed > datetime.now(timezone.utc):
        raise ValueError('Activation requires a nonfuture timezone-aware timestamp')
    row = conn.execute('SELECT id FROM listing_versions WHERE version=?', (version,)).fetchone()
    if row is None or not evidence.strip():
        raise ValueError('Known version and publication evidence required')
    with conn:
        conn.execute('INSERT INTO lifecycle_events(version_id,event,recorded_at,effective_at,evidence_json) VALUES(?,?,?,?,?)',
                     (row[0], 'activated', now(), parsed.isoformat(), dump({'publication_evidence': evidence, 'scope': 'main listing; verify exposure in Console'})))


def compare(conn, version, days):
    if days < 7 or days % 7:
        raise ValueError('Comparison windows must be positive whole weeks')
    activation = conn.execute('SELECT e.* FROM lifecycle_events e JOIN listing_versions v ON v.id=e.version_id WHERE v.version=? AND e.event=? ORDER BY e.id DESC LIMIT 1', (version, 'activated')).fetchone()
    if activation is None:
        return {'status': 'not_started', 'reason': 'No verified listing activation recorded; candidate preparation is not exposure.'}
    change = datetime.fromisoformat(activation['effective_at']).astimezone(ZoneInfo(ZONE)).date()
    windows = [(change-timedelta(days=days), change), (change+timedelta(days=1), change+timedelta(days=days+1))]
    output = {'status': 'observational', 'activation': activation['effective_at'], 'timezone': ZONE, 'windows': []}
    for start, end in windows:
        rows = selected(conn, 'store_performance', 'traffic_source', start, end)
        present = {row['date'] for row in rows}
        absent = [str(start+timedelta(days=i)) for i in range(days) if str(start+timedelta(days=i)) not in present]
        overall = grouped(rows).get('all reported segments', {})
        if absent or overall.get('missing_rows'):
            output['status'] = 'incomplete'
        output['windows'].append({'start_inclusive': str(start), 'end_exclusive': str(end), 'missing_dates': absent,
                                  'overall': overall, 'traffic_sources': grouped(rows, 'Traffic source'),
                                  'search_terms': grouped(rows, 'Search term', only_source='Google Play search'),
                                  'countries_separate_axis': grouped(selected(conn, 'store_performance', 'country', start, end), 'Country / region')})
    other = conn.execute('SELECT v.version,e.effective_at FROM lifecycle_events e JOIN listing_versions v ON v.id=e.version_id WHERE e.event=? AND e.version_id<>?', ('activated', activation['version_id'])).fetchall()
    conflicts = [dict(r) for r in other if windows[1][0] <= datetime.fromisoformat(r['effective_at']).astimezone(ZoneInfo(ZONE)).date() < windows[1][1]]
    if conflicts:
        output['status'] = 'exposure_conflict'
        output['other_activations'] = conflicts
    if output['status'] == 'observational':
        before, after = (window['overall'].get('acquisition_conversion_rate') for window in output['windows'])
        output['conversion_change_percentage_points'] = (after-before)*100 if before is not None and after is not None else None
    output['interpretation'] = 'No causal or significance claim. Check audience mix, custom listings, experiments, campaigns, app releases/quality and unknown historical exposure before deciding.'
    return output


def list_objects(service, bucket, prefix):
    token, seen = None, set()
    while True:
        response = service.objects().list(bucket=bucket, prefix=prefix, pageToken=token, maxResults=1000).execute(num_retries=3)
        yield from response.get('items', [])
        token = response.get('nextPageToken')
        if not token:
            return
        if token in seen:
            raise ValueError('Repeated pagination token')
        seen.add(token)


def sync(conn, args):
    # Imports are lazy: local inspection works with the standard library only.
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    import httplib2
    import google_auth_httplib2
    credentials = service_account.Credentials.from_service_account_file(str(args.credentials), scopes=[
        'https://www.googleapis.com/auth/androidpublisher', 'https://www.googleapis.com/auth/devstorage.read_only'])
    def service(name, version):
        http = google_auth_httplib2.AuthorizedHttp(credentials, http=httplib2.Http(timeout=60))
        return build(name, version, http=http, cache_discovery=False)
    started = now()
    with conn:
        run_id = conn.execute('INSERT INTO collection_runs(started_at,status,result_json) VALUES(?,?,?)', (started, 'running', '{}')).lastrowid
    result = {'reports_imported': 0, 'errors': [], 'remote_committed': False}
    publisher, edit = None, None
    try:
        publisher = service('androidpublisher', 'v3')
        edit = publisher.edits().insert(packageName=PACKAGE, body={}).execute()['id']
        snapshot = {'observed_at': started,
                    'details': publisher.edits().details().get(packageName=PACKAGE, editId=edit).execute(),
                    'listings': publisher.edits().listings().list(packageName=PACKAGE, editId=edit).execute(),
                    'tracks': publisher.edits().tracks().list(packageName=PACKAGE, editId=edit).execute(), 'images': {}}
        for listing in snapshot['listings'].get('listings', []):
            lang = listing['language']
            snapshot['images'][lang] = {}
            for kind in IMAGE_TYPES:
                response = publisher.edits().images().list(packageName=PACKAGE, editId=edit, language=lang, imageType=kind).execute()
                snapshot['images'][lang][kind] = response
                for image in response.get('images', []):
                    host = urlparse(image['url']).hostname or ''
                    if host != 'googleusercontent.com' and not host.endswith('.googleusercontent.com'):
                        raise ValueError('Unexpected image host')
                    with urlopen(Request(image['url']+'=s0', headers={'User-Agent': 'ComferListingEvidence/1'}), timeout=60) as fetched:
                        payload = fetched.read()
                    digest = sha(payload)
                    path = ROOT/'store-listing/private/assets'/digest
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(payload)
                    image.update(local_path=str(path.relative_to(ROOT)), download_sha256=digest)
        result['observed_version_id'] = save_snapshot(conn, snapshot)
    except Exception as exc:
        result['errors'].append({'source': 'listing', 'type': type(exc).__name__, 'detail': str(exc)[:800]})
    finally:
        if publisher:
            try:
                if edit:
                    publisher.edits().delete(packageName=PACKAGE, editId=edit).execute()
            except Exception as exc:
                result['errors'].append({'source': 'temporary_edit_cleanup', 'type': type(exc).__name__})
            publisher.close()
    storage = service('storage', 'v1')
    cutoff = (date.today()-timedelta(days=args.days)).strftime('%Y%m')
    try:
        for prefix in ('stats/store_performance/', 'stats/installs/', 'stats/ratings/', 'stats/crashes/', 'reviews/'):
            try:
                objects = list(list_objects(storage, args.bucket, prefix))
            except Exception as exc:
                result['errors'].append({'source': prefix, 'type': type(exc).__name__})
                continue
            selected_count = 0
            for obj in objects:
                if PACKAGE not in obj['name']:
                    continue
                try:
                    kind, dimension, aggregation, month = report_identity(obj['name'])
                    if month < cutoff or (kind not in {'store_performance', 'reviews'} and dimension not in {'country', 'language', 'overview', 'app_version'}):
                        continue
                    selected_count += 1
                    if conn.execute('SELECT 1 FROM report_objects WHERE bucket=? AND object_name=? AND generation=?', (args.bucket, obj['name'], str(obj['generation']))).fetchone():
                        continue
                    payload = storage.objects().get_media(bucket=args.bucket, object=obj['name'], generation=obj['generation']).execute(num_retries=3)
                    import_report(conn, obj, payload, args.bucket)
                    result['reports_imported'] += 1
                except Exception as exc:
                    result['errors'].append({'source': obj['name'], 'type': type(exc).__name__, 'detail': str(exc)[:500]})
            if selected_count == 0:
                result.setdefault('not_available', []).append(prefix)
    finally:
        storage.close()
    with conn:
        conn.execute('UPDATE collection_runs SET finished_at=?,status=?,result_json=? WHERE id=?',
                     (now(), 'partial' if result['errors'] else 'complete', dump(result), run_id))
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--database', type=Path, default=ROOT/'play_listing.db')
    sub = parser.add_subparsers(dest='command', required=True)
    baseline = sub.add_parser('import-baseline')
    baseline.add_argument('--snapshot', type=Path, required=True)
    baseline.add_argument('--reports', type=Path, required=True)
    baseline.add_argument('--version', type=int, default=1)
    candidate = sub.add_parser('add-candidate')
    candidate.add_argument('--manifest', type=Path, required=True)
    evidence = sub.add_parser('record-evidence')
    evidence.add_argument('--input', type=Path, required=True, help='JSON array of kind/key/payload records')
    refresh = sub.add_parser('sync')
    refresh.add_argument('--credentials', type=Path, default=ROOT/'service-account.json')
    refresh.add_argument('--bucket', default=BUCKET)
    refresh.add_argument('--days', type=int, default=90)
    stats = sub.add_parser('summary')
    stats.add_argument('--start', type=date.fromisoformat, required=True)
    stats.add_argument('--end', type=date.fromisoformat, required=True, help='Exclusive date')
    activation = sub.add_parser('activate')
    activation.add_argument('--version', type=int, required=True)
    activation.add_argument('--effective-at', required=True)
    activation.add_argument('--evidence', required=True, help='Observed publication/exposure evidence; never a preparation date')
    comparison = sub.add_parser('compare')
    comparison.add_argument('--version', type=int, required=True)
    comparison.add_argument('--days', type=int, default=28)
    args = parser.parse_args(argv)
    writes = args.command not in {'summary', 'compare'}
    conn = None
    try:
        if args.command == 'summary' and args.start >= args.end:
            raise ValueError('Start must precede exclusive end')
        if args.command == 'sync' and args.days <= 0:
            raise ValueError('Days must be positive')
        conn = connect(args.database) if writes else sqlite3.connect(f'file:{args.database}?mode=ro', uri=True)
        conn.row_factory = sqlite3.Row
        if args.command == 'import-baseline':
            snapshot = json.loads(args.snapshot.read_text())
            # Validate all files before any import, then preserve every source generation.
            objects = json.loads(args.reports.read_text())
            for obj in objects:
                payload = (ROOT/obj['local_path']).read_bytes()
                if sha(payload) != obj['sha256']:
                    raise ValueError('Report file mismatch')
                parse_report(payload, obj['name'])
            ident = save_snapshot(conn, snapshot, version=args.version)
            for obj in objects:
                import_report(conn, obj, (ROOT/obj['local_path']).read_bytes())
            result = {'version_id': ident, 'report_objects': len(objects)}
        elif args.command == 'add-candidate':
            result = {'version_id': add_candidate(conn, args.manifest)}
        elif args.command == 'record-evidence':
            records = json.loads(args.input.read_text())
            with conn:
                for row in records:
                    save_evidence(conn, row['kind'], row['key'], row['payload'], row.get('observed_at'))
            result = {'records_processed': len(records)}
        elif args.command == 'summary':
            result = summary(conn, args.start, args.end)
        elif args.command == 'activate':
            activate(conn, args.version, args.effective_at, args.evidence)
            result = {'version': args.version, 'local_event': 'activated', 'remote_changed': False}
        elif args.command == 'compare':
            result = compare(conn, args.version, args.days)
        else:
            result = sync(conn, args)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 1 if result.get('errors') else 0
    except Exception as exc:
        print(dump({'status': 'failed', 'type': type(exc).__name__, 'detail': str(exc)[:1000]}), file=sys.stderr)
        return 1
    finally:
        if conn:
            conn.close()


if __name__ == '__main__':
    sys.exit(main())
