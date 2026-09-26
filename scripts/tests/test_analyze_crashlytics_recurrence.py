import importlib.util
from pathlib import Path
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / (name + '.py'))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result

analysis = module('analyze_crashlytics_recurrence')
importer = module('import_firebase_crashlytics')


class RecurrenceTest(unittest.TestCase):
    def test_empty_completed_import_is_not_confused_with_missing_data(self):
        with sqlite3.connect(':memory:') as conn:
            importer.ensure_schema(conn)
            conn.execute('''INSERT INTO crashlytics_import_runs
                (source, firebase_project_id, firebase_app_id, package_name,
                 version_code, version_name, period_start, period_end, imported_at,
                 issue_count, crash_count, anr_count, event_count, complete)
                VALUES ('firebase','project','app','package',54,'54.0',
                        '2026-09-01','2026-09-26','2026-09-26',0,0,0,0,1)''')
            report = analysis.analyze(conn, 'app', 54)
            self.assertEqual([], report['inventory'])
            self.assertEqual(0, report['events'])
            self.assertEqual(1, report['import_run']['id'])

    def test_build_stamp_plain_and_yaml_escaped_and_missing(self):
        revision = 'a' * 40
        for text in [f'revision: "{revision}"', f'revision: \\"{revision}\\"']:
            self.assertEqual(revision, analysis.build_revision(text))
        self.assertIsNone(analysis.build_revision(''))

    def test_samples_override_retained_titles_and_anr_thread_names(self):
        self.assertEqual('java.lang.SecurityException: different cause',
                         analysis.sample_signature('java.lang.SecurityException: different cause\nat frame'))
        self.assertEqual('at binder', analysis.sample_signature('Thread: main\nat binder\nat caller'))

    def test_recurrence_is_bounded_by_provider_app_version_and_current_snapshot(self):
        with sqlite3.connect(':memory:') as conn:
            importer.ensure_schema(conn)
            fields = [r[1] for r in conn.execute('pragma table_info(crashlytics_issues)')
                      if r[3] and r[4] is None]
            def insert(app, version, issue, events, present=1):
                row = {f: 'sample' for f in fields}
                row.update(firebase_app_id=app, version_code=version, issue_id=issue,
                           type='anr', affected_users=1, events=events, sessions=events,
                           stack_trace='Thread: main\nat current', present_in_latest_import=present)
                conn.execute(f"INSERT INTO crashlytics_issues ({','.join(row)}) VALUES ({','.join('?' for _ in row)})", list(row.values()))
            insert('app', 52, 'seen', 100)
            insert('other-app', 52, 'fresh', 200)
            insert('app', 54, 'fresh', 300)
            insert('app', 53, 'seen', 2)
            insert('app', 53, 'fresh', 3)
            insert('app', 53, 'absent', 50, 0)
            report = analysis.analyze(conn, 'app', 53)
            self.assertEqual((2, 5, 1, 2), tuple(report[k] for k in
                ('issues', 'events', 'recurring_issues', 'recurring_events')))
            self.assertEqual([52], [p['version_code'] for p in report['inventory'][1]['prior']])
            self.assertEqual([], report['inventory'][0]['prior'])
            with self.assertRaises(ValueError): analysis.analyze(conn, 'missing', 53)


if __name__ == '__main__': unittest.main()
