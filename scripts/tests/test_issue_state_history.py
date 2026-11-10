import sqlite3
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from issue_state_history import ensure_state_history


class StateHistoryTest(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(':memory:')
        self.conn.execute('PRAGMA foreign_keys=ON')
        for table, scope in [('crashlytics_issues', 'firebase_app_id'), ('issues', 'package_name')]:
            self.conn.execute(f'''CREATE TABLE {table} ({scope} TEXT, version_code INTEGER,
                issue_id TEXT, status TEXT, notes TEXT, resolved_at TEXT, present_in_latest_import INTEGER,
                PRIMARY KEY({scope},version_code,issue_id))''')
            self.conn.execute(f"INSERT INTO {table} VALUES ('scope',58,'same-id','pending',NULL,NULL,1)")
        self.conn.commit()
        with self.conn:
            ensure_state_history(self.conn)
        self.addCleanup(self.conn.close)

    def test_baseline_changes_and_noops_keep_provider_relations(self):
        with self.conn:
            self.conn.execute("UPDATE crashlytics_issues SET status='investigating',notes='observed locally'")
            self.conn.execute("UPDATE crashlytics_issues SET status='investigating',notes='observed locally'")
            self.conn.execute('UPDATE crashlytics_issues SET present_in_latest_import=0')
            ensure_state_history(self.conn)
        rows = self.conn.execute('''SELECT change_kind,status,notes,present_in_latest_import
            FROM issue_state_history WHERE provider='firebase' ORDER BY revision_id''').fetchall()
        self.assertEqual([('baseline','pending',None,1),
                          ('update','investigating','observed locally',1),
                          ('update','investigating','observed locally',0)], rows)
        self.assertEqual(1,self.conn.execute("SELECT count(*) FROM issue_state_history WHERE provider='play'").fetchone()[0])
        self.assertEqual([],self.conn.execute('PRAGMA foreign_key_check').fetchall())

    def test_new_issue_has_linked_initial_revision(self):
        with self.conn:
            self.conn.execute("INSERT INTO issues VALUES ('scope',59,'same-id','pending','new version',NULL,1)")
        self.assertEqual(('insert',59,'new version'),self.conn.execute(
            "SELECT change_kind,version_code,notes FROM play_issue_state_history WHERE version_code=59").fetchone())

    def test_failed_transaction_rolls_back_state_and_revision(self):
        with self.assertRaisesRegex(RuntimeError,'abort'):
            with self.conn:
                self.conn.execute("UPDATE issues SET status='resolved',resolved_at='test-time'")
                raise RuntimeError('abort')
        self.assertEqual(('pending',None),self.conn.execute('SELECT status,resolved_at FROM issues').fetchone())
        self.assertEqual(1,self.conn.execute('SELECT count(*) FROM play_issue_state_history').fetchone()[0])

    def test_history_is_append_only_and_parent_relation_is_enforced(self):
        for sql in ["UPDATE play_issue_state_history SET status='resolved'",
                    'DELETE FROM play_issue_state_history', 'DELETE FROM issues']:
            with self.assertRaises(sqlite3.IntegrityError):
                with self.conn: self.conn.execute(sql)
        self.assertEqual(1,self.conn.execute('SELECT count(*) FROM issues').fetchone()[0])
        self.assertEqual(1,self.conn.execute('SELECT count(*) FROM play_issue_state_history').fetchone()[0])


if __name__ == '__main__':
    unittest.main()
