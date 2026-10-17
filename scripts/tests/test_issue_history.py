import importlib.util
from contextlib import closing
import json
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('issue_history', Path(__file__).resolve().parents[1]/'issue_history.py')
history = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(history)
ISSUE = '159e25351be7042db517f3af684684db'
OTHER = '6380853658a02626929b323100e0fc52'


def source(content, path='docs/release-56-review.md', version=56):
    return dict(source_path=path, source_kind='markdown', release_version=version,
                content=content, content_sha256=history.digest(content), git_revision='a'*40)


class HistoryTest(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(':memory:')
        self.conn.execute('PRAGMA foreign_keys=ON')
        self.conn.executescript('''CREATE TABLE crashlytics_issues (
            firebase_app_id TEXT, version_code INTEGER, issue_id TEXT, notes TEXT, status TEXT);
            CREATE TABLE issues (package_name TEXT,version_code INTEGER,issue_id TEXT,notes TEXT,status TEXT);''')
        self.conn.executemany('INSERT INTO crashlytics_issues VALUES (?,?,?,?,?)', [
            (history.APP, 51, ISSUE, 'original early attempt', 'investigating'),
            (history.APP, 56, ISSUE, 'append only; production unverified', 'investigating'),
            (history.APP, 56, OTHER, 'provider failure', 'investigating')])
        self.conn.commit()

    def tearDown(self):
        self.conn.close()

    def test_archives_exact_source_and_separates_inventory_rows(self):
        text = f'''# Version 56 review

Original scope: different windows; production unverified.

## Earlier attempts

| Earlier attempt | Issue | Decision |
| --- | --- | --- |
| 50-04 / 51-06 | `{ISSUE}` | Partial mitigation; see 56-R03. |
| Other | `{OTHER}` | Keep cached contacts. |

## 56-R03 — management list

- Change: Guard duplicate package keys.
  Preserve order and selection.
- Verification: Failed before the guard; local pass afterward.
- Status: implemented locally; production pending.
'''
        before = self.conn.execute('SELECT * FROM crashlytics_issues ORDER BY version_code,issue_id').fetchall()
        history.import_sources(self.conn, [source(text)])
        self.assertEqual(text, self.conn.execute('SELECT content FROM issue_history_documents').fetchone()[0])
        result = history.query(self.conn, {'issue': OTHER})
        self.assertEqual(1, result['total'])
        self.assertNotIn(ISSUE, result['entries'][0]['body'])
        attempt = history.query(self.conn, {'attempt': '56-R03'})
        self.assertEqual(2, attempt['total'])
        definition = next(e for e in attempt['entries'] if e['kind'] == 'attempt')
        self.assertIn('production pending', definition['fields']['Status'])
        self.assertIn('Preserve order', definition['fields']['Change'])
        self.assertEqual(before, [tuple(r) for r in self.conn.execute('SELECT * FROM crashlytics_issues ORDER BY version_code,issue_id')])
        self.assertEqual([], self.conn.execute('PRAGMA foreign_key_check').fetchall())

    def test_idempotence_and_new_document_revision_preserve_prior_decisions(self):
        old = source(f'# Review\n\n## 56-R03 — guard\nIssue `{ISSUE}`: hypothesis; no repair yet.\n')
        new = source(f'# Review\n\n## 56-R03 — guard\nIssue `{ISSUE}`: locally tested; production pending.\n')
        self.assertEqual(1, history.import_sources(self.conn, [old])['documents'])
        self.assertEqual(0, history.import_sources(self.conn, [old])['documents'])
        self.assertEqual(1, history.import_sources(self.conn, [new])['documents'])
        report = history.query(self.conn, {'issue': ISSUE})
        self.assertEqual(2, report['total'])
        self.assertEqual({old['content'], new['content']}, {r[0] for r in self.conn.execute('SELECT content FROM issue_history_documents')})

    def test_provider_and_app_scope_are_never_merged(self):
        self.conn.execute('INSERT INTO issues VALUES (?,?,?,?,?)', (history.PACKAGE,56,ISSUE,'Play note','pending'))
        self.conn.execute('INSERT INTO crashlytics_issues VALUES (?,?,?,?,?)', ('other-app',56,ISSUE,'other app','pending'))
        self.conn.commit()
        text = f'''# Review\n## Firebase inventory\n| Issue | Decision |\n| --- | --- |\n| `{ISSUE}` | Firebase context |\n
## Play inventory\n| Issue | Decision |\n| --- | --- |\n| `{ISSUE}` | Play context |\n'''
        history.import_sources(self.conn, [source(text)])
        firebase = history.query(self.conn, {'issue': ISSUE, 'provider': 'firebase', 'scope': history.APP})
        play = history.query(self.conn, {'issue': ISSUE, 'provider': 'play'})
        self.assertEqual(1, firebase['total']); self.assertEqual(1, play['total'])
        self.assertNotEqual(firebase['entries'][0]['id'], play['entries'][0]['id'])
        self.assertEqual({history.APP}, {r['scope_id'] for r in firebase['telemetry_history']})
        self.assertEqual({'play'}, {r['provider'] for r in play['telemetry_history']})
        self.assertEqual({}, history.history_references(self.conn, 'firebase', 'other-app'))

    def test_existing_notes_remain_queryable_without_any_document_mention(self):
        history.import_sources(self.conn, [source('# General review\nNo issue ID in this document.\n')])
        report = history.query(self.conn, {'issue':ISSUE,'provider':'firebase','scope':history.APP})
        self.assertEqual(0, report['total'])
        self.assertEqual(['original early attempt','append only; production unverified'],
                         [r['notes'] for r in report['telemetry_history']])

    def test_ambiguous_issue_is_retained_without_guessing_its_provider(self):
        self.conn.execute('INSERT INTO issues VALUES (?,?,?,?,?)', (history.PACKAGE,56,ISSUE,'Play note','pending'))
        self.conn.commit()
        history.import_sources(self.conn, [source(f'# Mixed provider review\n\nIssue `{ISSUE}`; source provider omitted.\n')])
        result = history.query(self.conn, {})
        self.assertEqual([], result['entries'][0]['issues'])
        self.assertEqual(ISSUE, result['entries'][0]['unresolved_ids'][0]['issue_id'])

    def test_fences_escaped_pipes_and_build_hashes_do_not_create_false_links(self):
        text = f'''# Review\n```md\n## 56-R99 is example text inside a fence\n| Not | A table |\n| --- | --- |\n```\n
## Decisions\n| Issue | Detail |\n| --- | --- |\n| `{ISSUE}` | keep `a|b` and escaped a\\|b |\n
Build revision: {'a'*40}; mapping resource ID: {'b'*32}.\n'''
        entries = history.parse_markdown(text)
        rows = [e for e in entries if e['kind'] == 'table_row']
        self.assertEqual(1, len(rows))
        self.assertEqual('keep `a|b` and escaped a\\|b', rows[0]['fields']['Detail'])
        history.import_sources(self.conn, [source(text)])
        self.assertEqual(1, history.query(self.conn, {'issue': ISSUE})['total'])
        self.assertEqual(1, self.conn.execute('SELECT count(*) FROM issue_history_issue_links').fetchone()[0])
        # Code examples can mention attempts, but never define headings inside fences.
        self.assertEqual([], self.conn.execute("SELECT * FROM issue_history_attempt_links WHERE attempt_id='56-R99' AND role='definition'").fetchall())

    def test_invalid_batch_rolls_back_schema_and_all_entries(self):
        good = source(f'# Review\nIssue `{ISSUE}`\n')
        bad = source('native record', 'record:invalid')
        bad['entries'] = [dict(start_line=1, end_line=1, kind='record', section='bad', title='bad',
            body='bad', fields={}, issue_links={('unsupported', 'app', ISSUE): 'explicit_record'})]
        with self.assertRaises(sqlite3.IntegrityError): history.import_sources(self.conn, [good,bad])
        self.assertFalse(history.has_history(self.conn))
        self.assertEqual(3, self.conn.execute('SELECT count(*) FROM crashlytics_issues').fetchone()[0])

    def test_bad_source_hash_and_duplicates_rejected_before_schema_changes(self):
        item = source('# Review\n')
        with self.assertRaisesRegex(ValueError, 'Duplicate'): history.import_sources(self.conn, [item,item])
        item['content_sha256'] = '0'*64
        with self.assertRaisesRegex(ValueError, 'checksum'): history.import_sources(self.conn, [item])
        self.assertFalse(history.has_history(self.conn))

    def test_json_records_preserve_versions_and_explicit_attempt_relationships(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'followup.json'
            data = dict(record_id='v57-duplicate-keys-followup',version_code=57,title='Partial management-list repair',
                attempt_id='56-R03',prior_attempts=['50-04','51-06'],
                issues=[dict(provider='firebase',scope_id=history.APP,issue_id=ISSUE)],
                evidence='Local reproducer only',change='Package identity guard',
                validation='Local pass',production_outcome='unverified',target_version=57)
            path.write_text(json.dumps(data)); first = history.record(path)
            history.import_sources(self.conn, [first])
            data['production_outcome'] = 'Same ID observed later; signature comparison still required'
            path.write_text(json.dumps(data)); history.import_sources(self.conn, [history.record(path)])
            report = history.query(self.conn, {'issue': ISSUE, 'version':57})
            self.assertEqual(2, report['total'])
            self.assertEqual('unverified', report['entries'][0]['fields']['production_outcome'])
            self.assertEqual(2, history.query(self.conn, {'attempt':'50-04'})['total'])
            self.assertEqual({'explicit_record'}, {e['issues'][0]['basis'] for e in report['entries']})

    def test_limit_reports_omitted_rows_and_search_is_literal(self):
        history.import_sources(self.conn, [source('# Review\n## One\n100% locally tested\n## Two\nNo percentage\n')])
        limited = history.query(self.conn, {}, limit=1)
        self.assertGreater(limited['total'], limited['shown'])
        self.assertEqual(1, history.query(self.conn, {'search':'100%'})['total'])
        self.assertEqual(0, history.query(self.conn, {'search':"' OR 1=1 --"})['total'])

    def test_manifest_uses_verified_git_original_after_docs_are_shortened(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); (root/'docs').mkdir()
            (root/'docs/release-56-review.md').write_text('Short database pointer\n')
            original='# Version 56 original\nImportant old evidence\n'
            item=source(original); item.pop('content')
            manifest=root/'manifest.json'; manifest.write_text(json.dumps({'schema_version':1,'sources':[item]}))
            with patch.object(history, 'ROOT', root), patch.object(history.subprocess, 'check_output', return_value=original.encode()):
                self.assertEqual(original, history.from_manifest(manifest)[0]['content'])
            with patch.object(history, 'ROOT', root), patch.object(history.subprocess, 'check_output', return_value=b'wrong content'):
                with self.assertRaisesRegex(ValueError, 'checksum'): history.from_manifest(manifest)

    def test_manifest_rejects_unknown_formats_empty_sources_and_path_escape(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'manifest.json'
            item=source('# Original\n'); item.pop('content')
            for data in [{'schema_version':2,'sources':[item]},
                         {'schema_version':1,'sources':[]},
                         {'schema_version':1,'sources':[dict(item,source_path='../outside.md')]}]:
                with self.subTest(manifest=data):
                    path.write_text(json.dumps(data))
                    with self.assertRaises(ValueError): history.from_manifest(path)

    def test_cli_backup_and_repeat_record_are_safe(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); database=root/'reporting.db'; input_file=root/'update.json'
            with closing(sqlite3.connect(database)) as conn: self.conn.backup(conn)
            input_file.write_text(json.dumps(dict(record_id='local-followup',version_code=57,
                attempt_id='57-01',title='Local validation; production unknown')))
            command=[sys.executable,str(SPEC.origin),'--database',str(database),'record','--input',str(input_file)]
            first=subprocess.run(command,text=True,capture_output=True,check=True)
            report=json.loads(first.stdout)
            self.assertEqual(1,report['documents'])
            with closing(sqlite3.connect(report['backup'])) as recovery:
                self.assertFalse(history.has_history(recovery))
                self.assertEqual(3,recovery.execute('SELECT count(*) FROM crashlytics_issues').fetchone()[0])
            repeated=json.loads(subprocess.run(command,text=True,capture_output=True,check=True).stdout)
            self.assertEqual(0,repeated['documents']);self.assertIsNone(repeated['backup'])
            self.assertEqual(1,len(list(root.glob('reporting.db.backup-*'))))

    def test_read_cli_rejects_missing_database_without_creating_it(self):
        with tempfile.TemporaryDirectory() as directory:
            database=Path(directory)/'missing.db'
            result=subprocess.run([sys.executable,str(SPEC.origin),'--database',str(database),'summary'],capture_output=True)
            self.assertNotEqual(0,result.returncode)
            self.assertFalse(database.exists())


if __name__ == '__main__':
    unittest.main()
