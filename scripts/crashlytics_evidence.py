"""Retain immutable, deduplicated Firebase sample evidence in the reporting DB."""
import argparse
import hashlib
import json
from pathlib import Path
import sqlite3
import zlib

ROOT = Path(__file__).resolve().parents[1]
ISSUE_FIELDS = ('type', 'title', 'subtitle', 'firebase_state', 'console_uri',
                'first_seen_version', 'last_seen_version', 'signals_json', 'raw_issue_text')
EVENT_FIELDS = ('stack_trace', 'sample_event_name', 'sample_event_id', 'event_time',
                'received_time', 'device_brand', 'device_model', 'device_marketing_name',
                'device_architecture', 'os_version', 'process_state', 'app_orientation',
                'device_orientation', 'blame_symbol', 'blame_file', 'blame_line',
                'blame_owner', 'raw_event_text')


def ensure_evidence_schema(conn):
    conn.execute('''CREATE TABLE IF NOT EXISTS crashlytics_evidence_blobs (
        id INTEGER PRIMARY KEY, sha256 TEXT NOT NULL UNIQUE, payload BLOB NOT NULL)''')
    conn.execute('''CREATE TABLE IF NOT EXISTS crashlytics_issue_evidence (
        id INTEGER PRIMARY KEY, firebase_app_id TEXT NOT NULL, version_code INTEGER NOT NULL,
        issue_id TEXT NOT NULL, captured_at TEXT NOT NULL, capture_kind TEXT NOT NULL,
        issue_blob_id INTEGER NOT NULL REFERENCES crashlytics_evidence_blobs(id),
        event_blob_id INTEGER NOT NULL REFERENCES crashlytics_evidence_blobs(id),
        FOREIGN KEY(firebase_app_id,version_code,issue_id)
            REFERENCES crashlytics_issues(firebase_app_id,version_code,issue_id),
        UNIQUE(firebase_app_id,version_code,issue_id,issue_blob_id,event_blob_id))''')
    columns = {r[1] for r in conn.execute('PRAGMA table_info(crashlytics_issue_snapshots)')}
    if 'evidence_id' not in columns:
        # Older snapshots have no retained payload association; never backfill a guessed sample.
        conn.execute('''ALTER TABLE crashlytics_issue_snapshots ADD COLUMN evidence_id INTEGER
            REFERENCES crashlytics_issue_evidence(id)''')
    for table in ('crashlytics_evidence_blobs', 'crashlytics_issue_evidence'):
        for event in ('UPDATE', 'DELETE'):
            conn.execute(f'''CREATE TRIGGER IF NOT EXISTS {table}_immutable_{event.lower()}
                BEFORE {event} ON {table} BEGIN
                SELECT RAISE(ABORT,'append-only crashlytics evidence'); END''')


def _blob(conn, data):
    raw = json.dumps(data, sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode()
    digest = hashlib.sha256(raw).hexdigest()
    existing = conn.execute('SELECT id FROM crashlytics_evidence_blobs WHERE sha256=?', (digest,)).fetchone()
    if existing:
        return existing[0]
    return conn.execute('INSERT INTO crashlytics_evidence_blobs(sha256,payload) VALUES (?,?)',
                        (digest, zlib.compress(raw))).lastrowid


def archive_evidence(conn, row, now, kind):
    issue_blob = _blob(conn, {key: row.get(key) for key in ISSUE_FIELDS})
    event_blob = _blob(conn, {key: row.get(key) for key in EVENT_FIELDS})
    identity = (row['firebase_app_id'], row['version_code'], row['issue_id'], issue_blob, event_blob)
    conn.execute('''INSERT OR IGNORE INTO crashlytics_issue_evidence
        (firebase_app_id,version_code,issue_id,issue_blob_id,event_blob_id,captured_at,capture_kind)
        VALUES (?,?,?,?,?,?,?)''', (*identity, now, kind))
    return conn.execute('''SELECT id FROM crashlytics_issue_evidence WHERE
        firebase_app_id=? AND version_code=? AND issue_id=? AND issue_blob_id=? AND event_blob_id=?''', identity).fetchone()[0]


def read_evidence(conn, evidence_id):
    cursor = conn.execute('SELECT * FROM crashlytics_issue_evidence WHERE id=?', (evidence_id,))
    row = cursor.fetchone()
    if row is None:
        raise ValueError('No retained evidence for this ID')
    result = dict(zip((c[0] for c in cursor.description), row))
    for key in ('issue', 'event'):
        digest, payload = conn.execute('SELECT sha256,payload FROM crashlytics_evidence_blobs WHERE id=?',
                                       (result.pop(key + '_blob_id'),)).fetchone()
        raw = zlib.decompress(payload)
        if hashlib.sha256(raw).hexdigest() != digest:
            raise ValueError('Evidence checksum mismatch')
        result[key] = json.loads(raw)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', type=Path, default=ROOT / 'play_reporting.db')
    parser.add_argument('--id', type=int, required=True, help='Evidence ID from an import snapshot')
    args = parser.parse_args()
    with sqlite3.connect(args.database.resolve().as_uri() + '?mode=ro', uri=True) as connection:
        print(json.dumps(read_evidence(connection, args.id), indent=2, ensure_ascii=False))
