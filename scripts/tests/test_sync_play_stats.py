from datetime import date
from pathlib import Path
import sqlite3
import sys
import unittest
from unittest.mock import MagicMock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import sync_play_stats as stats

PACKAGE = "com.jeerovan.comfer"
START, END = date(2026, 8, 1), date(2026, 9, 1)


def row(version="46", users="20"):
    return {"startTime": {"year": 2026, "month": 8, "day": 31},
            "dimensions": [{"dimension": "versionCode", "int64Value": version}],
            "metrics": [{"metric": "distinctUsers", "decimalValue": {"value": users}}]}


class StatsTests(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.conn.execute("PRAGMA foreign_keys=ON")
        self.conn.executescript(stats.SCHEMA)
        self.conn.execute("INSERT INTO sync_runs VALUES (1,?,'now',NULL,'running')", (PACKAGE,))
        self.conn.commit()
        self.addCleanup(self.conn.close)

    def save(self, rows, version=None):
        stats.save_vitals(self.conn, 1, PACKAGE, "crashRateMetricSet", "version",
                          START, END, version, rows, {})

    def test_refresh_replaces_counts_preserves_other_versions(self):
        self.save([row("45"), row()])
        self.save([row(users="30")], version=46)
        self.assertEqual(self.conn.execute("SELECT version_code,distinct_users FROM vitals_daily ORDER BY version_code").fetchall(),
                         [("45", 20), ("46", 30)])
        self.save([], version=46)
        self.assertEqual(self.conn.execute("SELECT version_code FROM vitals_daily").fetchall(), [("45",)])

    def test_bad_response_does_not_replace_existing_data(self):
        self.save([row()])
        before = list(self.conn.iterdump())
        with self.assertRaises(ValueError):
            self.save([row("45")], version=46)
        self.assertEqual(list(self.conn.iterdump()), before)

    def test_transaction_rolls_back_duplicate_api_rows(self):
        self.save([row()])
        before = list(self.conn.iterdump())
        with self.assertRaises(sqlite3.IntegrityError):
            self.save([row(), row()])
        self.assertEqual(list(self.conn.iterdump()), before)

    def test_utf16_csv_language_and_missing_values(self):
        payload = ("Date,Package Name,Language,Daily Average Rating,Total Average Rating\n"
                   f"2026-08-31,{PACKAGE},en,,4.25\n").encode("utf-16")
        rows = stats.parse_csv(payload, PACKAGE, "language")
        obj = {"name": "stats/ratings/example_language.csv", "generation": "1"}
        stats.save_csv(self.conn, 1, "bucket", obj, PACKAGE, "ratings", "language", rows)
        stats.save_csv(self.conn, 1, "bucket", obj, PACKAGE, "ratings", "language", rows)
        self.assertEqual(self.conn.execute("SELECT count(*) FROM csv_stats").fetchone()[0], 1)
        self.assertEqual(self.conn.execute("SELECT language FROM language_stats").fetchone()[0], "en")
        self.assertIn('"Daily Average Rating": ""', rows[0][2])

    def test_csv_rejects_wrong_package_and_malformed_rows(self):
        for text in ["Date,Package Name,Language\n2026-08-31,wrong,en\n",
                     f"Date,Package Name,Language\n2026-08-31,{PACKAGE}\n"]:
            with self.assertRaises(ValueError):
                stats.parse_csv(text.encode(), PACKAGE, "language")

    def test_pagination_keeps_filters_and_empty_pages(self):
        resource = MagicMock()
        resource.query.return_value.execute.side_effect = [{"nextPageToken": "p2"}, {"rows": [row()]}]
        self.assertEqual(list(stats.query_rows(resource, "name", {"filter": "versionCode = 46"})), [row()])
        self.assertEqual(resource.query.call_args.kwargs["body"], {"filter": "versionCode = 46", "pageToken": "p2"})

    def test_repeated_token_fails(self):
        resource = MagicMock()
        resource.query.return_value.execute.return_value = {"nextPageToken": "same"}
        with self.assertRaises(ValueError):
            list(stats.query_rows(resource, "name", {}))

    def test_months_exclude_end_boundary(self):
        self.assertEqual(list(stats.months_between(date(2025, 12, 20), date(2026, 2, 1))),
                         ["202512", "202601"])


if __name__ == "__main__":
    unittest.main()
