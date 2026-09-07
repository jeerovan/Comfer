import copy
from pathlib import Path
import sqlite3
import sys
import unittest
from unittest.mock import MagicMock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import sync_play_issues as sync

PACKAGE = "com.jeerovan.comfer"
START = sync.utc_hour("2026-08-08")
END = sync.utc_hour("2026-09-07")
ISSUE = {
    "name": f"apps/{PACKAGE}/abc", "type": "CRASH", "cause": "test cause",
    "location": "test location", "distinctUsers": "2", "errorReportCount": "3",
    "firstAppVersion": {"versionCode": "46"}, "lastAppVersion": {"versionCode": "46"},
}
REPORT = {
    "name": f"apps/{PACKAGE}/sample", "issue": f"apps/{PACKAGE}/errorIssues/abc",
    "appVersion": {"versionCode": "46"}, "reportText": "sample stack",
}


class ImportTests(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.conn.row_factory = sqlite3.Row
        self.conn.execute("PRAGMA foreign_keys=ON")
        self.conn.executescript((Path(__file__).parent / "fixtures/play_reporting.sql").read_text())
        self.addCleanup(self.conn.close)

    def row(self, issue_id="abc", version=46, report=REPORT):
        issue = {**ISSUE, "name": f"apps/{PACKAGE}/{issue_id}"}
        return sync.issue_row(issue, report, PACKAGE, version, f"{version}.0", START, END, "first")

    def save(self, rows, version=46):
        return sync.save_import(self.conn, rows, PACKAGE, version, f"{version}.0", START, END, "now")

    def test_refresh_preserves_triage_and_other_versions(self):
        self.save([self.row(version=45)], version=45)
        self.save([self.row(), self.row("absent")])
        self.conn.execute("UPDATE issues SET status='resolved', notes='keep', resolved_at='fixed' "
                          "WHERE version_code=46")
        self.conn.commit()
        row = self.row(report=None)
        row.update(events=7, first_seen_at="later", last_synced_at="later")
        self.save([row])
        saved = dict(self.conn.execute("SELECT * FROM issues WHERE version_code=46 AND issue_id='abc'").fetchone())
        self.assertEqual((saved["status"], saved["notes"], saved["resolved_at"], saved["first_seen_at"]),
                         ("resolved", "keep", "fixed", "first"))
        self.assertEqual((saved["events"], saved["stack_trace"], saved["sample_report_id"]),
                         (7, "sample stack", "sample"))
        self.assertEqual(self.conn.execute("SELECT count(*) FROM issues").fetchone()[0], 3)
        self.assertEqual(self.conn.execute("SELECT present_in_latest_import FROM issues WHERE version_code=45").fetchone()[0], 1)
        self.assertEqual(self.conn.execute("SELECT present_in_latest_import FROM issues WHERE issue_id='absent'").fetchone()[0], 0)
        self.assertEqual([r[0] for r in self.conn.execute("SELECT events FROM issue_snapshots WHERE version_code=46 AND issue_id='abc' ORDER BY id")], [3, 7])

    def test_failed_import_rolls_back_every_table(self):
        self.save([self.row()])
        before = list(self.conn.iterdump())
        invalid = self.row("invalid")
        invalid["type"] = "unsupported"
        with self.assertRaises(sqlite3.IntegrityError):
            self.save([self.row("new"), invalid])
        self.assertEqual(list(self.conn.iterdump()), before)

    def test_empty_refresh_keeps_history_and_status(self):
        self.save([self.row()])
        self.save([])
        row = self.conn.execute("SELECT status,present_in_latest_import,events FROM issues").fetchone()
        self.assertEqual(tuple(row), ("pending", 0, 3))
        self.assertEqual(self.conn.execute("SELECT issue_count FROM import_runs ORDER BY id DESC LIMIT 1").fetchone()[0], 0)


class FetchTests(unittest.TestCase):
    def test_pagination_including_empty_page(self):
        resource = MagicMock()
        resource.search.return_value.execute.side_effect = [
            {"nextPageToken": "next"}, {"errorIssues": [ISSUE]},
        ]
        self.assertEqual(list(sync.search_pages(resource, "errorIssues", {"filter": "versionCode = 46"})), [ISSUE])
        self.assertEqual(resource.search.call_args.kwargs, {"filter": "versionCode = 46", "pageToken": "next"})

    def test_repeated_token_aborts(self):
        resource = MagicMock()
        resource.search.return_value.execute.return_value = {"nextPageToken": "same"}
        with self.assertRaises(ValueError):
            list(sync.search_pages(resource, "errorIssues", {}))

    def test_issue_and_sample_filters_and_wrong_version_rejection(self):
        service = MagicMock()
        errors = service.vitals.return_value.errors.return_value
        errors.issues.return_value.search.return_value.execute.return_value = {"errorIssues": [ISSUE]}
        errors.reports.return_value.search.return_value.execute.return_value = {"errorReports": [REPORT]}
        self.assertEqual(sync.fetch_issues(service, PACKAGE, 46, START, END), [(ISSUE, REPORT)])
        params = errors.issues.return_value.search.call_args.kwargs
        self.assertIn("versionCode = 46", params["filter"])
        self.assertEqual(params["interval_startTime_timeZone_id"], "UTC")
        self.assertEqual(errors.reports.return_value.search.call_args.kwargs["filter"],
                         "versionCode = 46 AND errorIssueId = abc")
        wrong = copy.deepcopy(REPORT)
        wrong["appVersion"]["versionCode"] = "45"
        errors.reports.return_value.search.return_value.execute.return_value = {"errorReports": [wrong]}
        with self.assertRaises(ValueError):
            sync.fetch_issues(service, PACKAGE, 46, START, END)

    def test_interval_validation(self):
        self.assertEqual(sync.utc_hour("2026-09-07T05:30:00+05:30"), END)
        with self.assertRaises(ValueError):
            sync.utc_hour("2026-09-07T00:01:00Z")


if __name__ == "__main__":
    unittest.main()
