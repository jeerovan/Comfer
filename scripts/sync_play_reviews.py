#!/usr/bin/env python3
"""Read Comfer's recent Play reviews and all available monthly review exports."""
import argparse
import csv
import io
import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.jeerovan.comfer"
BUCKET = "pubsite_prod_5435940062993501423"
SCHEMA = """
CREATE TABLE IF NOT EXISTS sync_runs (
 id INTEGER PRIMARY KEY, fetched_at TEXT NOT NULL, package_name TEXT NOT NULL,
 api_count INTEGER NOT NULL, export_count INTEGER NOT NULL, export_rows INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS reviews (
 package_name TEXT NOT NULL, review_id TEXT NOT NULL, author_name TEXT,
 star_rating INTEGER CHECK(star_rating BETWEEN 1 AND 5), text TEXT,
 language TEXT, device TEXT, version_code INTEGER, version_name TEXT,
 modified_seconds INTEGER, developer_reply TEXT, raw_json TEXT NOT NULL,
 fetched_at TEXT NOT NULL, PRIMARY KEY(package_name, review_id)
);
CREATE TABLE IF NOT EXISTS exports (
 object_name TEXT PRIMARY KEY, generation TEXT NOT NULL, row_count INTEGER NOT NULL,
 fetched_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS historical_reviews (
 object_name TEXT NOT NULL REFERENCES exports(object_name), row_number INTEGER NOT NULL,
 package_name TEXT NOT NULL, star_rating INTEGER NOT NULL CHECK(star_rating BETWEEN 1 AND 5),
 title TEXT, text TEXT, language TEXT, device TEXT, version_code TEXT, version_name TEXT,
 submitted_at TEXT, updated_at TEXT, developer_reply TEXT, developer_reply_at TEXT,
 review_link TEXT, raw_json TEXT NOT NULL, PRIMARY KEY(object_name, row_number)
);
CREATE INDEX IF NOT EXISTS historical_reviews_date ON historical_reviews(submitted_at);
"""


def json_text(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True)


def parse_export(blob, package):
    encoding = "utf-16" if blob.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig"
    reader = csv.DictReader(io.StringIO(blob.decode(encoding)))
    required = {"Package Name", "Star Rating", "Review Submit Date and Time", "Review Text"}
    if not required.issubset(reader.fieldnames or []):
        raise ValueError("Review export is missing required columns")
    rows = list(reader)
    for row in rows:
        if None in row or row["Package Name"] != package:
            raise ValueError("Malformed row or wrong package in review export")
        if not 1 <= int(row["Star Rating"]) <= 5:
            raise ValueError("Invalid star rating")
    return rows


def pages(session, url, params, token_field, response_token):
    params = dict(params)
    seen = set()
    while True:
        response = session.get(url, params=params, timeout=60)
        response.raise_for_status()
        data = response.json()
        yield data
        token = response_token(data)
        if not token:
            break
        if token in seen:
            raise ValueError("Repeated pagination token")
        seen.add(token)
        params[token_field] = token


def fetch(session, package, bucket):
    reviews = {}
    for page in pages(session,
                      f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{package}/reviews",
                      {"maxResults": 100}, "token",
                      lambda p: p.get("tokenPagination", {}).get("nextPageToken")):
        for review in page.get("reviews", []):
            reviews[review["reviewId"]] = review
    base = f"https://storage.googleapis.com/storage/v1/b/{bucket}/o"
    objects = []
    for page in pages(session, base,
                      {"prefix": f"reviews/reviews_{package}_", "maxResults": 1000},
                      "pageToken", lambda p: p.get("nextPageToken")):
        objects.extend(o for o in page.get("items", []) if o["name"].endswith(".csv"))
    exports = []
    for obj in sorted(objects, key=lambda o: o["name"]):
        response = session.get(base + "/" + quote(obj["name"], safe=""),
                               params={"alt": "media", "generation": obj["generation"]}, timeout=60)
        response.raise_for_status()
        rows = parse_export(response.content, package)
        exports.append((obj, rows))
        print(f"Fetched {obj['name']}: {len(rows)} rows", flush=True)
    return list(reviews.values()), exports


def save(database, package, reviews, exports):
    now = datetime.now(timezone.utc).isoformat()
    database = Path(database)
    if database.exists():
        backup = str(database) + ".backup-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
        with sqlite3.connect(database) as source, sqlite3.connect(backup) as target:
            source.backup(target)
    connection = sqlite3.connect(database)
    try:
        connection.execute("PRAGMA foreign_keys=ON")
        connection.executescript(SCHEMA)
        with connection:
            for review in reviews:
                users = [c["userComment"] for c in review.get("comments", []) if "userComment" in c]
                replies = [c["developerComment"] for c in review.get("comments", []) if "developerComment" in c]
                latest = lambda c: (int(c.get("lastModified", {}).get("seconds", 0)), int(c.get("lastModified", {}).get("nanos", 0)))
                user = max(users, key=latest) if users else {}
                reply = max(replies, key=latest) if replies else {}
                connection.execute("""INSERT OR REPLACE INTO reviews VALUES
                    (?,?,?,?,?,?,?,?,?,?,?,?,?)""", (
                    package, review["reviewId"], review.get("authorName"), user.get("starRating"),
                    user.get("text"), user.get("reviewerLanguage"), user.get("device"),
                    user.get("appVersionCode"), user.get("appVersionName"),
                    user.get("lastModified", {}).get("seconds"), reply.get("text"), json_text(review), now))
            for obj, rows in exports:
                name = obj["name"]
                connection.execute("""INSERT INTO exports VALUES (?,?,?,?)
                    ON CONFLICT(object_name) DO UPDATE SET generation=excluded.generation,
                    row_count=excluded.row_count,fetched_at=excluded.fetched_at""",
                    (name, obj["generation"], len(rows), now))
                connection.execute("DELETE FROM historical_reviews WHERE object_name=?", (name,))
                for number, row in enumerate(rows, 1):
                    connection.execute("""INSERT INTO historical_reviews VALUES
                        (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", (
                        name, number, package, int(row["Star Rating"]), row.get("Review Title"),
                        row.get("Review Text"), row.get("Reviewer Language"), row.get("Device"),
                        row.get("App Version Code"), row.get("App Version Name"),
                        row.get("Review Submit Date and Time"), row.get("Review Last Update Date and Time"),
                        row.get("Developer Reply Text"), row.get("Developer Reply Date and Time"),
                        row.get("Review Link"), json_text(row)))
            connection.execute("""INSERT INTO sync_runs
                (fetched_at,package_name,api_count,export_count,export_rows) VALUES (?,?,?,?,?)""",
                (now, package, len(reviews), len(exports), sum(len(rows) for _, rows in exports)))
    finally:
        connection.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", type=Path, default=ROOT / "play_reviews.db")
    parser.add_argument("--credentials", type=Path, default=ROOT / "service-account.json")
    parser.add_argument("--package", default=PACKAGE)
    parser.add_argument("--bucket", default=BUCKET)
    args = parser.parse_args()
    from google.oauth2 import service_account
    from google.auth.transport.requests import AuthorizedSession
    credentials = service_account.Credentials.from_service_account_file(str(args.credentials), scopes=[
        "https://www.googleapis.com/auth/androidpublisher",
        "https://www.googleapis.com/auth/devstorage.read_only"])
    with AuthorizedSession(credentials) as session:
        reviews, exports = fetch(session, args.package, args.bucket)
    save(args.database, args.package, reviews, exports)
    print(f"Saved {len(reviews)} recent API reviews and {sum(len(r) for _, r in exports)} "
          f"historical export rows from {len(exports)} files to {args.database}")


if __name__ == "__main__":
    main()
