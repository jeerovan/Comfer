import csv
import importlib.util
import io
import sqlite3
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("sync_play_reviews", Path(__file__).parents[1] / "sync_play_reviews.py")
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)


class ReviewTests(unittest.TestCase):
    def test_unicode_and_invalid_export(self):
        row = {"Package Name": sync.PACKAGE, "Star Rating": "5",
               "Review Submit Date and Time": "2026-09-01T00:00:00Z", "Review Text": "अच्छा\napp"}
        output = io.StringIO()
        writer = csv.DictWriter(output, fieldnames=list(row))
        writer.writeheader()
        writer.writerow(row)
        for encoding in ("utf-16", "utf-8-sig"):
            self.assertEqual(sync.parse_export(output.getvalue().encode(encoding), sync.PACKAGE), [row])
        with self.assertRaises(ValueError):
            sync.parse_export(output.getvalue().encode(), "wrong.package")
        with self.assertRaises(ValueError):
            sync.parse_export(b"invalid,headers\n", sync.PACKAGE)

    def test_repeat_update_empty_and_rollback(self):
        with tempfile.TemporaryDirectory() as directory:
            db = Path(directory) / "reviews.db"
            review = {"reviewId": "one", "comments": [{"userComment": {"text": "good", "starRating": 5}}]}
            export = ({"name": "monthly.csv", "generation": "1"}, [
                {"Star Rating": "4", "Review Text": "fine"}])
            sync.save(db, sync.PACKAGE, [review], [export])
            review["comments"][0]["userComment"]["text"] = "updated"
            sync.save(db, sync.PACKAGE, [review], [export])
            with sqlite3.connect(db) as c:
                self.assertEqual(c.execute("SELECT count(*),text FROM reviews").fetchone(), (1, "updated"))
                self.assertEqual(c.execute("SELECT count(*) FROM historical_reviews").fetchone()[0], 1)
            bad = ({"name": "monthly.csv", "generation": "2"}, [{"Star Rating": "0"}])
            with self.assertRaises(sqlite3.IntegrityError):
                sync.save(db, sync.PACKAGE, [], [bad])
            with sqlite3.connect(db) as c:
                self.assertEqual(c.execute("SELECT generation FROM exports").fetchone()[0], "1")
                self.assertEqual(c.execute("SELECT text FROM historical_reviews").fetchone()[0], "fine")
                self.assertEqual(c.execute("SELECT count(*) FROM sync_runs").fetchone()[0], 2)
            sync.save(db, sync.PACKAGE, [], [(export[0], [])])
            with sqlite3.connect(db) as c:
                self.assertEqual(c.execute("SELECT count(*) FROM reviews").fetchone()[0], 1)
                self.assertEqual(c.execute("SELECT count(*) FROM historical_reviews").fetchone()[0], 0)
                self.assertEqual(c.execute("PRAGMA integrity_check").fetchone()[0], "ok")

    def test_pagination_and_repeated_token(self):
        class Response:
            def raise_for_status(self):
                pass
            def json(self):
                return next(results)
        class Session:
            def get(self, url, params, timeout):
                calls.append(dict(params))
                return Response()
        calls = []
        results = iter([{"next": "page2"}, {}])
        self.assertEqual(len(list(sync.pages(Session(), "url", {}, "token", lambda p: p.get("next")))), 2)
        self.assertEqual(calls, [{}, {"token": "page2"}])
        results = iter([{"next": "same"}, {"next": "same"}])
        with self.assertRaises(ValueError):
            list(sync.pages(Session(), "url", {}, "token", lambda p: p.get("next")))


if __name__ == "__main__":
    unittest.main()
