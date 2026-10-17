#!/usr/bin/env python3
"""Preserve and query historical issue reviews in play_reporting.db.

Historical reports describe evidence and decisions at the time, not current app
behavior or proof of production resolution. Existing telemetry is never edited.
"""
import argparse
from collections import defaultdict
from contextlib import closing
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import subprocess
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
APP = '1:141154670700:android:3463921d29d4a97bee2049'
PACKAGE = 'com.jeerovan.comfer'
ATTEMPT = re.compile(r'(?<![\w-])\d{2,4}-(?:R)?\d{2}(?![\w-])')
ISSUE = re.compile(r'\b[0-9a-f]{32}\b')
HEADING = re.compile(r'^(#{1,6})\s+(.+?)\s*#*\s*$')
SCHEMA = [
    '''CREATE TABLE IF NOT EXISTS issue_history_schema (
        id INTEGER PRIMARY KEY CHECK(id=1), version INTEGER NOT NULL CHECK(version>=1))''',
    '''CREATE TABLE IF NOT EXISTS issue_history_documents (
        id INTEGER PRIMARY KEY, source_path TEXT NOT NULL,
        source_kind TEXT NOT NULL CHECK(source_kind IN ('markdown','record')),
        release_version INTEGER, git_revision TEXT, content_sha256 TEXT NOT NULL,
        content TEXT NOT NULL, imported_at TEXT NOT NULL,
        UNIQUE(source_path, content_sha256))''',
    '''CREATE TABLE IF NOT EXISTS issue_history_entries (
        id INTEGER PRIMARY KEY,
        document_id INTEGER NOT NULL REFERENCES issue_history_documents(id),
        start_line INTEGER NOT NULL CHECK(start_line>0),
        end_line INTEGER NOT NULL CHECK(end_line>=start_line),
        kind TEXT NOT NULL, section TEXT NOT NULL, title TEXT NOT NULL,
        body TEXT NOT NULL, fields_json TEXT NOT NULL,
        unresolved_ids_json TEXT NOT NULL DEFAULT '[]',
        UNIQUE(document_id,start_line,end_line))''',
    '''CREATE TABLE IF NOT EXISTS issue_history_issue_links (
        entry_id INTEGER NOT NULL REFERENCES issue_history_entries(id),
        provider TEXT NOT NULL CHECK(provider IN ('firebase','play')),
        scope_id TEXT NOT NULL, issue_id TEXT NOT NULL,
        basis TEXT NOT NULL CHECK(basis IN ('catalog_id','explicit_url','explicit_record')),
        PRIMARY KEY(entry_id,provider,scope_id,issue_id))''',
    '''CREATE TABLE IF NOT EXISTS issue_history_attempt_links (
        entry_id INTEGER NOT NULL REFERENCES issue_history_entries(id),
        attempt_id TEXT NOT NULL,
        role TEXT NOT NULL CHECK(role IN ('definition','mention')),
        PRIMARY KEY(entry_id,attempt_id))''',
    '''CREATE INDEX IF NOT EXISTS issue_history_document_version
        ON issue_history_documents(release_version,source_path)''',
    '''CREATE INDEX IF NOT EXISTS issue_history_issue_lookup
        ON issue_history_issue_links(provider,scope_id,issue_id,entry_id)''',
    '''CREATE INDEX IF NOT EXISTS issue_history_attempt_lookup
        ON issue_history_attempt_links(attempt_id,entry_id)''',
    '''CREATE VIEW IF NOT EXISTS issue_history AS
        SELECT e.*,d.source_path,d.source_kind,d.release_version,d.git_revision,
            d.content_sha256,d.imported_at
        FROM issue_history_entries e JOIN issue_history_documents d ON d.id=e.document_id''',
    '''CREATE VIEW IF NOT EXISTS issue_fix_history AS
        SELECT a.attempt_id,a.role,h.* FROM issue_history_attempt_links a
        JOIN issue_history h ON h.id=a.entry_id''',
    '''CREATE VIEW IF NOT EXISTS issue_review_history AS
        SELECT l.provider,l.scope_id,l.issue_id,l.basis,h.*
        FROM issue_history_issue_links l JOIN issue_history h ON h.id=l.entry_id''',
]


def digest(content):
    return hashlib.sha256(content.encode('utf-8')).hexdigest()


def has_history(conn):
    return bool(conn.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='issue_history_schema'").fetchone())


def ensure_schema(conn):
    if has_history(conn):
        row = conn.execute('SELECT version FROM issue_history_schema WHERE id=1').fetchone()
        if row is None or row[0] != 1:
            raise ValueError('Unsupported issue history schema version')
    for statement in SCHEMA:
        conn.execute(statement)
    conn.execute('INSERT OR IGNORE INTO issue_history_schema VALUES (1,1)')


def catalog(conn):
    result = defaultdict(set)
    for table, provider, scope in [('issues', 'play', 'package_name'),
                                   ('crashlytics_issues', 'firebase', 'firebase_app_id')]:
        if conn.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (table,)).fetchone():
            for issue, identity in conn.execute(f'SELECT DISTINCT issue_id,{scope} FROM {table}'):
                result[issue].add((provider, identity))
    return result


def cells(line):
    """Split Markdown cells without breaking escaped pipes or inline code."""
    text = line.strip().strip('|')
    parts, current, ticks = [], [], 0
    index = 0
    while index < len(text):
        char = text[index]
        if char == '\\' and index + 1 < len(text):
            current.extend(text[index:index+2]); index += 2; continue
        if char == '`':
            end = index
            while end < len(text) and text[end] == '`': end += 1
            count = end - index
            ticks = 0 if ticks == count else count if not ticks else ticks
            current.append(text[index:end]); index = end; continue
        if char == '|' and not ticks:
            parts.append(''.join(current).strip()); current = []
        else:
            current.append(char)
        index += 1
    return parts + [''.join(current).strip()]


def bullet_fields(body):
    fields, active = {}, None
    for line in body.splitlines():
        match = re.match(r'^- ([A-Za-z][A-Za-z /_-]{0,50}):\s*(.*)', line)
        if match:
            active = match[1]
            fields.setdefault(active, []).append(match[2])
        elif active and line.startswith('  '):
            fields[active][-1] += '\n' + line.strip()
        else:
            active = None
    return {key: values[0] if len(values) == 1 else values for key, values in fields.items()}


def parse_markdown(content):
    """Non-overlapping sections and individual table rows; full source is archived too."""
    lines = content.splitlines(keepends=True)
    result, headings, prose = [], [], []
    start, index, fence = 0, 0, None

    def flush():
        if prose and ''.join(prose).strip():
            title = headings[-1][1] if headings else 'Document context'
            kind = 'attempt' if ATTEMPT.match(title) else 'section'
            result.append(dict(start_line=start+1, end_line=start+len(prose),
                kind=kind, section=' > '.join(t for _, t in headings), title=title,
                body=''.join(prose), fields=bullet_fields(''.join(prose))))
        prose.clear()

    while index < len(lines):
        line = lines[index]
        marker = re.match(r'^\s*(`{3,}|~{3,})', line)
        if marker:
            token = marker[1]
            if fence is None: fence = token
            elif token[0] == fence[0] and len(token) >= len(fence): fence = None
            if not prose: start = index
            prose.append(line); index += 1; continue
        heading = HEADING.match(line.rstrip('\r\n')) if fence is None else None
        if heading:
            flush()
            level, title = len(heading[1]), heading[2]
            while headings and headings[-1][0] >= level: headings.pop()
            headings.append((level, title)); start = index; prose.append(line); index += 1
            continue
        table = (fence is None and '|' in line and index+1 < len(lines)
                 and all(re.fullmatch(r':?-{3,}:?', value) for value in cells(lines[index+1])))
        if table:
            flush(); header = cells(line); index += 2
            while index < len(lines) and lines[index].strip().startswith('|'):
                values = cells(lines[index])
                if len(values) > len(header):
                    values = values[:len(header)-1] + [' | '.join(values[len(header)-1:])]
                values += [''] * (len(header)-len(values))
                fields = dict(zip(header, values))
                title = ' — '.join([values[0], values[-1]])[:240]
                result.append(dict(start_line=index+1, end_line=index+1,
                    kind='table_row', section=' > '.join(t for _, t in headings),
                    title=title, body=lines[index], fields=fields))
                index += 1
            continue
        if not prose: start = index
        prose.append(line); index += 1
    flush()
    return result


def links_for(entry, known, app_id=APP, package=PACKAGE):
    body = entry['body']
    context = entry['section'] + ' ' + ' '.join(entry['fields'])
    links, unresolved = {}, []
    for app, issue in re.findall(r'/app/([^/\s)]+)/issues/([0-9a-f]{32})', body):
        links[('firebase', unquote(app), issue)] = 'explicit_url'
    for issue in sorted(set(ISSUE.findall(body))):
        if any(key[2] == issue for key in links): continue
        choices = {pair for pair in known.get(issue, ())
                   if pair in {('firebase', app_id), ('play', package)}}
        if len(choices) > 1:
            firebase = bool(re.search(r'firebase|crashlytics', context, re.I))
            play = bool(re.search(r'\bplay\b', context, re.I))
            if firebase != play:
                choices = {p for p in choices if p[0] == ('firebase' if firebase else 'play')}
        if len(choices) == 1:
            provider, scope = next(iter(choices)); links[(provider, scope, issue)] = 'catalog_id'
        elif len(choices) > 1:
            unresolved.append({'issue_id': issue, 'reason': 'ambiguous provider/scope'})
        elif re.search(r'\b(?:issue|group)s?\s*:?\s*`?' + issue, body, re.I):
            unresolved.append({'issue_id': issue, 'reason': 'not in scoped telemetry catalog'})
    attempts = {key: 'mention' for key in ATTEMPT.findall(entry['section']+'\n'+body)}
    definition = ATTEMPT.match(entry['title']) if entry['kind'] == 'attempt' else None
    if entry['kind'] == 'table_row' and entry['fields']:
        label, value = next(iter(entry['fields'].items()))
        if label.strip().lower() in {'attempt', 'attempt id', 'fix', 'fix id'}:
            definition = ATTEMPT.fullmatch(value.strip('`* '))
    if definition: attempts[definition[0]] = 'definition'
    return links, attempts, unresolved


def import_sources(conn, sources, app_id=APP, package=PACKAGE):
    if conn.in_transaction:
        raise ValueError('Commit pending work before importing issue history')
    known = catalog(conn)
    prepared = []
    identities = set()
    for source in sources:
        if digest(source['content']) != source['content_sha256']:
            raise ValueError('Source checksum mismatch: '+source['source_path'])
        identity = (source['source_path'], source['content_sha256'])
        if identity in identities: raise ValueError('Duplicate source: '+source['source_path'])
        identities.add(identity)
        entries = source.get('entries')
        if entries is None: entries = parse_markdown(source['content'])
        if not entries: raise ValueError('Empty history source: '+source['source_path'])
        prepared.append((source, entries))
    counts = dict(documents=0, entries=0, issue_links=0, attempt_links=0)
    conn.execute('BEGIN IMMEDIATE')
    try:
        ensure_schema(conn)
        for source, entries in prepared:
            if conn.execute('SELECT 1 FROM issue_history_documents WHERE source_path=? AND content_sha256=?',
                            (source['source_path'], source['content_sha256'])).fetchone(): continue
            cursor = conn.execute('''INSERT INTO issue_history_documents
                (source_path,source_kind,release_version,git_revision,content_sha256,content,imported_at)
                VALUES (?,?,?,?,?,?,?)''', (source['source_path'], source['source_kind'],
                source.get('release_version'), source.get('git_revision'), source['content_sha256'],
                source['content'], dt.datetime.now(dt.timezone.utc).isoformat()))
            document_id = cursor.lastrowid; counts['documents'] += 1
            for entry in entries:
                links, attempts, unresolved = links_for(entry, known, app_id, package)
                if 'issue_links' in entry: links = entry['issue_links']
                if 'attempt_links' in entry: attempts = entry['attempt_links']
                cursor = conn.execute('''INSERT INTO issue_history_entries
                    (document_id,start_line,end_line,kind,section,title,body,fields_json,unresolved_ids_json)
                    VALUES (?,?,?,?,?,?,?,?,?)''', (document_id, entry['start_line'], entry['end_line'],
                    entry['kind'], entry['section'], entry['title'], entry['body'],
                    json.dumps(entry['fields'], ensure_ascii=False), json.dumps(unresolved)))
                entry_id = cursor.lastrowid; counts['entries'] += 1
                for (provider, scope, issue), basis in links.items():
                    conn.execute('INSERT INTO issue_history_issue_links VALUES (?,?,?,?,?)',
                                 (entry_id, provider, scope, issue, basis)); counts['issue_links'] += 1
                for attempt, role in attempts.items():
                    conn.execute('INSERT INTO issue_history_attempt_links VALUES (?,?,?)',
                                 (entry_id, attempt, role)); counts['attempt_links'] += 1
        conn.commit()
    except BaseException:
        conn.rollback(); raise
    return counts


def document(path):
    file = path.resolve(); relative = file.relative_to(ROOT).as_posix()
    content = file.read_bytes().decode('utf-8'); revision = None
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    stored = subprocess.run(['git', 'show', f'{head}:{relative}'], cwd=ROOT, capture_output=True)
    if stored.returncode == 0 and stored.stdout == content.encode(): revision = head
    version = re.search(r'release-(\d+)', file.name)
    return dict(source_path=relative, source_kind='markdown', content=content,
        content_sha256=digest(content), git_revision=revision,
        release_version=int(version[1]) if version else None)


def from_manifest(path):
    manifest = json.loads(path.read_text())
    if manifest.get('schema_version') != 1 or not manifest.get('sources'):
        raise ValueError('Unsupported or empty source manifest')
    sources = []
    for item in manifest['sources']:
        relative = Path(item['source_path'])
        if relative.is_absolute() or '..' in relative.parts:
            raise ValueError('Manifest source must be relative to the repository')
        file = ROOT / relative
        content = file.read_bytes().decode() if file.is_file() else ''
        if digest(content) != item['content_sha256']:
            revision = item.get('git_revision')
            if not revision or not re.fullmatch(r'[0-9a-f]{40}', revision):
                raise ValueError('Original source unavailable: '+str(relative))
            content = subprocess.check_output(['git', 'show', f'{revision}:{relative.as_posix()}'], cwd=ROOT).decode()
        if digest(content) != item['content_sha256']:
            raise ValueError('Manifest checksum mismatch: '+str(relative))
        sources.append(dict(item, content=content))
    return sources


def record(path):
    data = json.loads(path.read_text())
    if not re.fullmatch(r'[A-Za-z0-9_.-]+', data.get('record_id', '')):
        raise ValueError('record_id must be a stable filename-safe identifier')
    if type(data.get('version_code')) is not int or data['version_code'] <= 0 or not data.get('title'):
        raise ValueError('Record needs version_code and title')
    attempts = {}
    for key in data.get('prior_attempts', []):
        if not ATTEMPT.fullmatch(key): raise ValueError('Invalid prior attempt ID')
        attempts[key] = 'mention'
    if data.get('attempt_id'):
        if not ATTEMPT.fullmatch(data['attempt_id']): raise ValueError('Invalid attempt ID')
        attempts[data['attempt_id']] = 'definition'
    links = {}
    for issue in data.get('issues', []):
        if issue.get('provider') not in {'firebase', 'play'} or not issue.get('scope_id') or not issue.get('issue_id'):
            raise ValueError('Each issue needs provider, scope_id and issue_id')
        links[(issue['provider'], issue['scope_id'], issue['issue_id'])] = 'explicit_record'
    content = json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True)+'\n'
    entry = dict(start_line=1, end_line=len(content.splitlines()), kind='record',
        section=data['title'], title=data['title'], body=content, fields=data,
        issue_links=links, attempt_links=attempts)
    return dict(source_path='record:'+data['record_id'], source_kind='record',
        release_version=data['version_code'], git_revision=None,
        content_sha256=digest(content), content=content, entries=[entry])


def history_references(conn, provider, scope_id):
    """References only; a matching ID is not a root-cause or resolution assertion."""
    result = defaultdict(list)
    if not has_history(conn): return result
    conn.row_factory = sqlite3.Row
    for row in conn.execute('''SELECT issue_id,id,document_id,source_path,release_version,
            section,title,start_line,end_line,content_sha256,basis
        FROM issue_review_history WHERE provider=? AND scope_id=?
        ORDER BY release_version,source_path,start_line,id''', (provider, scope_id)):
        item = dict(row); issue = item.pop('issue_id'); result[issue].append(item)
    return result


def telemetry_notes(conn, issue_id, provider=None, scope_id=None):
    """Keep existing per-version decisions visible, without copying raw payloads."""
    conn.row_factory = sqlite3.Row
    result = []
    columns = {'version_code', 'version_name', 'issue_id', 'title', 'status', 'notes',
        'resolved_at', 'events', 'period_start', 'period_end', 'present_in_latest_import',
        'blame_symbol', 'event_time', 'sample_event_id', 'sample_report_id', 'vcs_information'}
    for table, name, scope in [('issues', 'play', 'package_name'),
                               ('crashlytics_issues', 'firebase', 'firebase_app_id')]:
        if provider and provider != name: continue
        available = {r[1] for r in conn.execute('PRAGMA table_info('+table+')')}
        if not available: continue
        selected = sorted(columns & available)
        where = ' WHERE issue_id=?'; values = [issue_id]
        if scope_id: where += ' AND '+scope+'=?'; values.append(scope_id)
        for row in conn.execute('SELECT '+','.join(selected)+','+scope+' AS scope_id FROM '+table+
                                where+' ORDER BY version_code,'+scope, values):
            result.append(dict(row, provider=name))
    return result


def query(conn, filters, limit=20):
    conn.row_factory = sqlite3.Row
    clauses, values = [], []
    for name, column in [('entry', 'h.id'), ('version', 'h.release_version'), ('source', 'h.source_path')]:
        value = filters.get(name)
        if value is not None: clauses.append(column+'=?'); values.append(value)
    if filters.get('section'):
        clauses.append('instr(lower(h.section),lower(?))>0'); values.append(filters['section'])
    if filters.get('search'):
        clauses.append('instr(lower(h.section||char(10)||h.body),lower(?))>0'); values.append(filters['search'])
    if filters.get('attempt'):
        clauses.append('EXISTS(SELECT 1 FROM issue_history_attempt_links a WHERE a.entry_id=h.id AND a.attempt_id=?)')
        values.append(filters['attempt'])
    if any(filters.get(key) for key in ['issue', 'provider', 'scope']):
        predicate = ['l.entry_id=h.id']
        for key, column in [('issue', 'issue_id'), ('provider', 'provider'), ('scope', 'scope_id')]:
            if filters.get(key): predicate.append('l.'+column+'=?'); values.append(filters[key])
        clauses.append('EXISTS(SELECT 1 FROM issue_history_issue_links l WHERE '+' AND '.join(predicate)+')')
    where = ' WHERE '+' AND '.join(clauses) if clauses else ''
    total = conn.execute('SELECT count(*) FROM issue_history h'+where, values).fetchone()[0]
    suffix = ' ORDER BY h.release_version,h.source_path,h.start_line,h.id LIMIT ?'
    rows = conn.execute('SELECT h.* FROM issue_history h'+where+suffix, [*values, limit]).fetchall()
    entries = []
    for row in rows:
        item = dict(row)
        item['fields'] = json.loads(item.pop('fields_json'))
        item['unresolved_ids'] = json.loads(item.pop('unresolved_ids_json'))
        item['issues'] = [dict(r) for r in conn.execute('SELECT provider,scope_id,issue_id,basis FROM issue_history_issue_links WHERE entry_id=?', (item['id'],))]
        item['attempts'] = [dict(r) for r in conn.execute('SELECT attempt_id,role FROM issue_history_attempt_links WHERE entry_id=? ORDER BY attempt_id', (item['id'],))]
        entries.append(item)
    report = dict(total=total, shown=len(entries), entries=entries)
    if filters.get('issue'):
        report['telemetry_history'] = telemetry_notes(conn, filters['issue'], filters.get('provider'), filters.get('scope'))
    return report


def summary(conn):
    conn.row_factory = sqlite3.Row
    result = dict(history_available=has_history(conn))
    if result['history_available']:
        result['documents'] = conn.execute('SELECT count(*) FROM issue_history_documents').fetchone()[0]
        result['entries'] = conn.execute('SELECT count(*) FROM issue_history_entries').fetchone()[0]
        result['attempts'] = [r[0] for r in conn.execute('SELECT DISTINCT attempt_id FROM issue_history_attempt_links ORDER BY attempt_id')]
        result['linked_issues'] = conn.execute('SELECT count(*) FROM (SELECT DISTINCT provider,scope_id,issue_id FROM issue_history_issue_links)').fetchone()[0]
        result['sources'] = [dict(r) for r in conn.execute('''SELECT id,source_path,release_version,content_sha256,git_revision
            FROM issue_history_documents ORDER BY release_version,source_path,id''')]
    for table in ['issues', 'crashlytics_issues']:
        if conn.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (table,)).fetchone():
            scope = 'package_name' if table == 'issues' else 'firebase_app_id'
            result[table] = [dict(r) for r in conn.execute(f'''SELECT {scope} AS scope_id,
                version_code,count(*) AS retained_groups,
                sum(present_in_latest_import) AS current_groups,
                sum(CASE WHEN present_in_latest_import=1 THEN events ELSE 0 END) AS current_events
                FROM {table} GROUP BY {scope},version_code ORDER BY {scope},version_code''')]
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', type=Path, default=ROOT/'play_reporting.db')
    commands = parser.add_subparsers(dest='command', required=True)
    load = commands.add_parser('import-docs', help='Archive Markdown originals and extract issue/fix references')
    load.add_argument('paths', nargs='*', type=Path)
    load.add_argument('--manifest', type=Path)
    load.add_argument('--write-manifest', type=Path)
    load.add_argument('--dry-run', action='store_true')
    add = commands.add_parser('record', help='Append a structured historical update from JSON')
    add.add_argument('--input', type=Path, required=True)
    add.add_argument('--dry-run', action='store_true')
    commands.add_parser('summary')
    show = commands.add_parser('show')
    for name in ['issue', 'attempt', 'source', 'section', 'search', 'scope']:
        show.add_argument('--'+name)
    show.add_argument('--provider', choices=['firebase', 'play'])
    show.add_argument('--version', type=int)
    show.add_argument('--entry', type=int)
    show.add_argument('--limit', type=int, default=20)
    export = commands.add_parser('source', help='Read an exact archived original, including its old headings')
    export.add_argument('--path', required=True)
    export.add_argument('--document-id', type=int)
    export.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.command in {'import-docs', 'record'}:
        if args.command == 'record': sources = [record(args.input)]
        else:
            if bool(args.paths) == bool(args.manifest): parser.error('Choose paths or --manifest')
            sources = from_manifest(args.manifest) if args.manifest else [document(p) for p in args.paths]
        if args.dry_run:
            print(json.dumps([dict(source_path=s['source_path'], content_sha256=s['content_sha256'],
                entries=len(s.get('entries', parse_markdown(s['content'])))) for s in sources], indent=2)); return
        # Require an existing database; typos must not create an empty reporting DB.
        with closing(sqlite3.connect(args.database.resolve().as_uri()+'?mode=rw', uri=True)) as conn:
            conn.execute('PRAGMA foreign_keys=ON')
            pending = not has_history(conn) or any(not conn.execute('''SELECT 1 FROM issue_history_documents
                WHERE source_path=? AND content_sha256=?''', (s['source_path'],s['content_sha256'])).fetchone() for s in sources)
            backup = None
            if pending:
                stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
                backup = args.database.with_name(args.database.name+'.backup-issue-history-'+stamp)
                with closing(sqlite3.connect(backup)) as recovery:
                    conn.backup(recovery)
                    if recovery.execute('PRAGMA quick_check').fetchone()[0] != 'ok': raise ValueError('Backup failed verification')
            counts = import_sources(conn, sources)
        if args.command == 'import-docs' and args.write_manifest:
            manifest = {'schema_version': 1, 'sources': [{k:v for k,v in s.items() if k != 'content'} for s in sources]}
            args.write_manifest.write_text(json.dumps(manifest, indent=2)+'\n')
        print(json.dumps(dict(counts, backup=str(backup) if backup else None), indent=2)); return
    with closing(sqlite3.connect(args.database.resolve().as_uri()+'?mode=ro', uri=True)) as conn:
        if args.command == 'summary': print(json.dumps(summary(conn), indent=2)); return
        if not has_history(conn): parser.error('Issue history is not installed; see scripts/README-play-reporting.md')
        if args.command == 'show':
            if args.limit <= 0: parser.error('--limit must be positive')
            print(json.dumps(query(conn, vars(args), args.limit), ensure_ascii=False, indent=2)); return
        row = conn.execute('''SELECT content,content_sha256 FROM issue_history_documents WHERE source_path=?'''+
            (' AND id=?' if args.document_id else '')+' ORDER BY id DESC LIMIT 1',
            (args.path,args.document_id) if args.document_id else (args.path,)).fetchone()
        if row is None: parser.error('Archived source not found')
        if digest(row[0]) != row[1]: raise ValueError('Archived source checksum mismatch')
        if args.output: args.output.write_bytes(row[0].encode('utf-8'))
        else: print(row[0], end='')


if __name__ == '__main__':
    main()
