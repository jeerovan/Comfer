import copy
import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import import_firebase_crashlytics as importer


APP_ID = "1:141154670700:android:3463921d29d4a97bee2049"
PACKAGE = "com.jeerovan.comfer"


def issue(issue_id="a" * 32, error_type="FATAL", events=3):
    event_name = f"projects/141154670700/apps/{APP_ID}/events/sample-{issue_id}"
    return {
        "id": issue_id,
        "events": events,
        "users": 2,
        "sessions": 2,
        "title": "Example failure",
        "subtitle": "ExampleException",
        "errorType": error_type,
        "sampleEvent": event_name,
        "uri": "https://console.firebase.google.com/example",
        "firstSeenVersion": "48.0",
        "lastSeenVersion": "48.0",
        "state": "OPEN",
        "signals": [{"signal": "SIGNAL_FRESH", "description": "New"}],
        "rawGroup": "raw issue",
        "event": {
            "name": event_name,
            "eventId": f"event-{issue_id}",
            "eventTime": "2026-09-11T05:19:18Z",
            "receivedTime": "2026-09-11T05:20:24Z",
            "issueId": issue_id,
            "errorType": error_type,
            "packageName": PACKAGE,
            "versionCode": 48,
            "versionName": "48.0",
            "deviceBrand": "HONOR",
            "deviceModel": "NIC-LX2",
            "deviceMarketingName": "NIC-LX2",
            "deviceArchitecture": "ARM64",
            "osVersion": "15",
            "processState": "FOREGROUND",
            "appOrientation": "PORTRAIT",
            "deviceOrientation": "PORTRAIT",
            "blameSymbol": "example.Symbol",
            "blameFile": "Example.kt",
            "blameLine": "42",
            "blameOwner": "DEVELOPER",
            "stackTrace": "ExampleException\n at example.Symbol (Example.kt:42)",
            "rawEventText": "raw event",
        },
    }


def payload(issues=None):
    issues = issues if issues is not None else [issue()]
    return {
        "source": "firebase-crashlytics-mcp",
        "firebaseProjectId": "comfer-db710",
        "firebaseAppId": APP_ID,
        "packageName": PACKAGE,
        "versionCode": 48,
        "versionName": "48.0",
        "periodStart": "2026-08-13T00:00:00Z",
        "periodEnd": "2026-09-11T23:59:59Z",
        "complete": True,
        "expectedIssueCount": len(issues),
        "expectedEventCount": sum(item["events"] for item in issues),
        "issues": issues,
    }


class ValidationTests(unittest.TestCase):
    def test_valid_payload_is_normalized(self):
        normalized = importer.validate_payload(payload())
        self.assertEqual(normalized["issue_count"], 1)
        self.assertEqual(normalized["event_count"], 3)
        self.assertEqual(normalized["rows"][0]["type"], "crash")

    def test_incomplete_or_mismatched_payload_is_rejected(self):
        incomplete = payload()
        incomplete["complete"] = False
        with self.assertRaisesRegex(ValueError, "complete"):
            importer.validate_payload(incomplete)

        mismatch = payload()
        mismatch["issues"][0]["event"]["versionCode"] = 47
        with self.assertRaisesRegex(ValueError, "sample event"):
            importer.validate_payload(mismatch)

        wrong_total = payload()
        wrong_total["expectedEventCount"] += 1
        with self.assertRaisesRegex(ValueError, "event count"):
            importer.validate_payload(wrong_total)

    def test_duplicate_issue_is_rejected(self):
        duplicate = issue()
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            importer.validate_payload(payload([issue(), duplicate]))


class ImportTests(unittest.TestCase):
    def setUp(self):
        self.tempdir = tempfile.TemporaryDirectory()
        self.addCleanup(self.tempdir.cleanup)
        self.database = Path(self.tempdir.name) / "play_reporting.db"
        sqlite3.connect(self.database).close()

    def connect(self):
        conn = sqlite3.connect(self.database)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys=ON")
        return conn

    def save(self, data):
        normalized = importer.validate_payload(data)
        with self.connect() as conn:
            return importer.save_import(conn, normalized, "2026-09-11T12:00:00+00:00")

    def test_refresh_preserves_triage_and_marks_absent_issue(self):
        second = issue("b" * 32, "ANR", 4)
        self.save(payload([issue(), second]))
        with self.connect() as conn:
            conn.execute(
                "UPDATE crashlytics_issues SET status='resolved', notes='keep', "
                "resolved_at='fixed', first_seen_at='first' WHERE issue_id=?",
                ("a" * 32,),
            )
            conn.commit()

        refreshed = issue(events=7)
        refreshed["title"] = "Updated failure"
        self.save(payload([refreshed]))
        with self.connect() as conn:
            saved = dict(conn.execute(
                "SELECT * FROM crashlytics_issues WHERE issue_id=?", ("a" * 32,)
            ).fetchone())
            absent = conn.execute(
                "SELECT present_in_latest_import FROM crashlytics_issues WHERE issue_id=?",
                ("b" * 32,),
            ).fetchone()[0]
            snapshots = conn.execute(
                "SELECT events FROM crashlytics_issue_snapshots WHERE issue_id=? ORDER BY id",
                ("a" * 32,),
            ).fetchall()

        self.assertEqual(
            (saved["status"], saved["notes"], saved["resolved_at"], saved["first_seen_at"]),
            ("resolved", "keep", "fixed", "first"),
        )
        self.assertEqual((saved["events"], saved["title"]), (7, "Updated failure"))
        self.assertEqual(absent, 0)
        self.assertEqual([row[0] for row in snapshots], [3, 7])

    def test_database_failure_rolls_back_import_and_snapshots(self):
        self.save(payload())
        with self.connect() as conn:
            before = list(conn.iterdump())
            normalized = importer.validate_payload(payload([issue("c" * 32)]))
            normalized["rows"][0]["type"] = "unsupported"
            with self.assertRaises(sqlite3.IntegrityError):
                importer.save_import(conn, normalized, "later")
            self.assertEqual(list(conn.iterdump()), before)

    def test_cli_validation_failure_does_not_modify_database(self):
        bad = payload()
        bad["expectedIssueCount"] = 2
        input_path = Path(self.tempdir.name) / "bad.json"
        input_path.write_text(json.dumps(bad))
        before = self.database.read_bytes()
        result = importer.main(["--input", str(input_path), "--database", str(self.database)])
        self.assertEqual(result, 1)
        self.assertEqual(self.database.read_bytes(), before)


if __name__ == "__main__":
    unittest.main()
