#!/usr/bin/env python3
"""Compare one Firebase release with stored earlier releases (read-only).

Counts are issue/event counts, not exposure-adjusted rates. An issue ID is not
a root-cause fingerprint; preserve the actual current/prior sample signatures.
"""
import argparse
import json
from pathlib import Path
import re
import sqlite3


def build_revision(raw):
    match = re.search(r'revision:\s*\\?"([0-9a-f]{40})', raw or '')
    return match.group(1) if match else None


def sample_signature(stack):
    # Firebase ANRs begin with a thread name; take the first frame instead.
    return next((line.strip() for line in stack.splitlines()
                 if line.strip() and not line.startswith('Thread:')), '')


def analyze(conn, app_id, version):
    conn.row_factory = sqlite3.Row
    run = conn.execute('''SELECT id, period_start, period_end, imported_at
        FROM crashlytics_import_runs WHERE firebase_app_id=? AND version_code=?
        ORDER BY id DESC LIMIT 1''', (app_id, version)).fetchone()
    current = conn.execute('''SELECT * FROM crashlytics_issues
        WHERE firebase_app_id=? AND version_code=? AND present_in_latest_import=1
        ORDER BY events DESC, issue_id''', (app_id, version)).fetchall()
    if not current and run is None:
        raise ValueError('No Firebase import for this app/version')
    result = []
    for row in current:
        prior = conn.execute('''SELECT * FROM crashlytics_issues
            WHERE firebase_app_id=? AND issue_id=? AND version_code<?
            ORDER BY version_code DESC''', (app_id, row['issue_id'], version)).fetchall()
        result.append({
            'issue_id': row['issue_id'], 'type': row['type'], 'events': row['events'],
            'title': row['title'], 'blame_symbol': row['blame_symbol'],
            'sample_signature': sample_signature(row['stack_trace']),
            'sample_revision': build_revision(row['raw_event_text']),
            'sample_time': row['event_time'], 'sample_device': row['device_marketing_name'],
            'sample_os': row['os_version'],
            'recurrence': 'exact_id_seen' if prior else 'newly_observed_id',
            'prior': [{
                'version_code': p['version_code'], 'events': p['events'],
                'period_start': p['period_start'], 'period_end': p['period_end'],
                'sample_signature': sample_signature(p['stack_trace']),
                'sample_revision': build_revision(p['raw_event_text']),
                'status': p['status'], 'notes': p['notes'],
            } for p in prior],
        })
    recurring = [r for r in result if r['prior']]
    return {'firebase_app_id': app_id, 'version_code': version,
            'import_run': dict(run) if run else None,
            'issues': len(result), 'events': sum(r['events'] for r in result),
            'recurring_issues': len(recurring),
            'recurring_events': sum(r['events'] for r in recurring), 'inventory': result}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', type=Path, default=Path('play_reporting.db'))
    parser.add_argument('--app-id', default='1:141154670700:android:3463921d29d4a97bee2049')
    parser.add_argument('--version', type=int, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    with sqlite3.connect(args.database.resolve().as_uri() + '?mode=ro', uri=True) as conn:
        report = analyze(conn, args.app_id, args.version)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({k: v for k, v in report.items() if k != 'inventory'}, indent=2))


if __name__ == '__main__':
    main()
