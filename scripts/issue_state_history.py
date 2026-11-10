"""Compact, transactional issue-state revisions inside the reporting database."""

PROVIDERS = (
    ('firebase', 'crashlytics_issues', 'firebase_app_id', 'crashlytics_issue_state_history'),
    ('play', 'issues', 'package_name', 'play_issue_state_history'),
)
STATE_FIELDS = ('status', 'notes', 'resolved_at', 'present_in_latest_import')


def ensure_state_history(conn):
    """Install linked audit tables/triggers without committing the caller's transaction.

    Existing state is explicitly a baseline at installation time, not a claim
    about when a historical status was originally set. No external backup files.
    """
    views = []
    for provider, source, scope, history in PROVIDERS:
        columns = {row[1] for row in conn.execute(f'PRAGMA table_info({source})')}
        if not columns:
            continue
        fields = [field for field in STATE_FIELDS if field in columns]
        conn.execute(f'''CREATE TABLE IF NOT EXISTS {history} (
            revision_id INTEGER PRIMARY KEY,
            scope_id TEXT NOT NULL, version_code INTEGER NOT NULL, issue_id TEXT NOT NULL,
            recorded_at TEXT NOT NULL, change_kind TEXT NOT NULL,
            status TEXT, notes TEXT, resolved_at TEXT, present_in_latest_import INTEGER,
            FOREIGN KEY(scope_id,version_code,issue_id)
                REFERENCES {source}({scope},version_code,issue_id))''')
        conn.execute(f'''CREATE INDEX IF NOT EXISTS {history}_issue
            ON {history}(scope_id,version_code,issue_id,revision_id)''')
        values = ','.join(f'NEW.{field}' if field in columns else 'NULL' for field in STATE_FIELDS)
        for event in ('INSERT', 'UPDATE'):
            when = '' if event == 'INSERT' else 'WHEN ' + ' OR '.join(f'OLD.{field} IS NOT NEW.{field}' for field in fields)
            conn.execute(f'''CREATE TRIGGER IF NOT EXISTS {history}_{event.lower()}
                AFTER {event} ON {source} {when} BEGIN
                INSERT INTO {history}(scope_id,version_code,issue_id,recorded_at,change_kind,
                    {','.join(STATE_FIELDS)})
                VALUES(NEW.{scope},NEW.version_code,NEW.issue_id,
                    strftime('%Y-%m-%dT%H:%M:%fZ','now'),'{event.lower()}',{values}); END''')
        baseline = ','.join(f's.{field}' if field in columns else 'NULL' for field in STATE_FIELDS)
        conn.execute(f'''INSERT INTO {history}(scope_id,version_code,issue_id,recorded_at,
            change_kind,{','.join(STATE_FIELDS)})
            SELECT s.{scope},s.version_code,s.issue_id,strftime('%Y-%m-%dT%H:%M:%fZ','now'),
                'baseline',{baseline} FROM {source} s
            WHERE NOT EXISTS (SELECT 1 FROM {history} h WHERE h.scope_id=s.{scope}
                AND h.version_code=s.version_code AND h.issue_id=s.issue_id)''')
        for event in ('UPDATE', 'DELETE'):
            conn.execute(f'''CREATE TRIGGER IF NOT EXISTS {history}_immutable_{event.lower()}
                BEFORE {event} ON {history} BEGIN
                SELECT RAISE(ABORT,'append-only issue state history'); END''')
        views.append(f"SELECT '{provider}' AS provider,* FROM {history}")
    if views:
        conn.execute('DROP VIEW IF EXISTS issue_state_history')
        conn.execute('CREATE VIEW issue_state_history AS ' + ' UNION ALL '.join(views))
