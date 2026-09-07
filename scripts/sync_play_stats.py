#!/usr/bin/env python3
"""Fetch daily Play vitals and optional Play Console statistics CSV exports."""

import argparse
import csv
from datetime import date, datetime, timedelta, timezone
import io
import json
import os
from pathlib import Path
import sqlite3
import sys

from google.auth.exceptions import GoogleAuthError
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from httplib2 import HttpLib2Error

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BUCKET = "pubsite_prod_5435940062993501423"
ZONE = "America/Los_Angeles"
SETS = {
    "crashrate": ("crashRateMetricSet", ["distinctUsers", "crashRate", "userPerceivedCrashRate"]),
    "anrrate": ("anrRateMetricSet", ["distinctUsers", "anrRate", "userPerceivedAnrRate"]),
}
BREAKDOWNS = {
    "version": ["versionCode"],
    "country_version": ["countryCode", "versionCode"],
    "device_type_version": ["deviceType", "versionCode"],
    "country_device_type_version": ["countryCode", "deviceType", "versionCode"],
}
CSV_DIMENSIONS = {
    "country": ["Country", "Country/region"], "language": ["Language"],
    "device": ["Device"], "app_version": ["App Version Code", "App version code"],
}
SCHEMA = """
CREATE TABLE IF NOT EXISTS sync_runs (
 id INTEGER PRIMARY KEY, package_name TEXT NOT NULL, started_at TEXT NOT NULL,
 finished_at TEXT, status TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS fetches (
 id INTEGER PRIMARY KEY, run_id INTEGER NOT NULL REFERENCES sync_runs(id),
 source TEXT NOT NULL, dataset TEXT NOT NULL, status TEXT NOT NULL,
 period_start TEXT, period_end TEXT, row_count INTEGER NOT NULL DEFAULT 0, detail TEXT
);
CREATE TABLE IF NOT EXISTS vitals_daily (
 package_name TEXT NOT NULL, metric_set TEXT NOT NULL, breakdown TEXT NOT NULL,
 date TEXT NOT NULL, timezone TEXT NOT NULL, version_code TEXT NOT NULL,
 country_code TEXT NOT NULL DEFAULT '', device_type TEXT NOT NULL DEFAULT '',
 distinct_users REAL, crash_rate REAL, user_perceived_crash_rate REAL,
 anr_rate REAL, user_perceived_anr_rate REAL,
 raw_json TEXT NOT NULL, fetch_id INTEGER NOT NULL REFERENCES fetches(id),
 PRIMARY KEY(package_name,metric_set,breakdown,date,version_code,country_code,device_type)
);
CREATE TABLE IF NOT EXISTS csv_reports (
 bucket TEXT NOT NULL, object_name TEXT NOT NULL, package_name TEXT NOT NULL,
 report_type TEXT NOT NULL, dimension TEXT NOT NULL, generation TEXT,
 fetch_id INTEGER NOT NULL REFERENCES fetches(id),
 PRIMARY KEY(bucket,object_name)
);
CREATE TABLE IF NOT EXISTS csv_stats (
 bucket TEXT NOT NULL, object_name TEXT NOT NULL, date TEXT NOT NULL,
 dimension_value TEXT NOT NULL, metrics_json TEXT NOT NULL, raw_json TEXT NOT NULL,
 PRIMARY KEY(bucket,object_name,date,dimension_value),
 FOREIGN KEY(bucket,object_name) REFERENCES csv_reports(bucket,object_name)
);
CREATE INDEX IF NOT EXISTS vitals_date_version ON vitals_daily(date,version_code);
CREATE VIEW IF NOT EXISTS language_stats AS
 SELECT r.package_name,s.date,s.dimension_value AS language,r.report_type,s.metrics_json
 FROM csv_stats s JOIN csv_reports r USING(bucket,object_name) WHERE r.dimension='language';
"""


def dump(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False)


def api_date(day):
    return {"year": day.year, "month": day.month, "day": day.day, "timeZone": {"id": ZONE}}


def read_date(value):
    return date(value["year"], value["month"], value["day"])


def query_rows(resource, name, body):
    body = dict(body)
    tokens = set()
    while True:
        response = resource.query(name=name, body=body).execute(num_retries=3)
        yield from response.get("rows", [])
        token = response.get("nextPageToken")
        if not token:
            return
        if token in tokens:
            raise ValueError("Repeated pagination token")
        tokens.add(token)
        body["pageToken"] = token


def vitals_values(row, dimensions, start, end, version):
    day = read_date(row["startTime"])
    dims = {v["dimension"]: str(v.get("stringValue", v.get("int64Value", "")))
            for v in row.get("dimensions", [])}
    if set(dims) != set(dimensions) or not start <= day < end:
        raise ValueError("API returned unexpected dimensions or date")
    if version is not None and dims["versionCode"] != str(version):
        raise ValueError("API returned a different app version")
    metrics = {m["metric"]: float(m["decimalValue"]["value"])
               for m in row.get("metrics", []) if "decimalValue" in m}
    return (day.isoformat(), ZONE, dims["versionCode"], dims.get("countryCode", ""),
            dims.get("deviceType", ""), *(metrics.get(m) for m in
            ("distinctUsers", "crashRate", "userPerceivedCrashRate", "anrRate", "userPerceivedAnrRate")), dump(row))


def record_fetch(conn, run_id, source, dataset, status, start=None, end=None, count=0, detail=None):
    return conn.execute("INSERT INTO fetches (run_id,source,dataset,status,period_start,"
                        "period_end,row_count,detail) VALUES (?,?,?,?,?,?,?,?)",
                        (run_id, source, dataset, status, str(start) if start else None,
                         str(end) if end else None, count, detail)).lastrowid


def save_vitals(conn, run_id, package, metric_set, breakdown, start, end, version, rows, metadata):
    # Validate the entire response before replacing the requested slice.
    values = [vitals_values(row, BREAKDOWNS[breakdown], start, end, version) for row in rows]
    with conn:
        fetch_id = record_fetch(conn, run_id, "vitals", f"{metric_set}/{breakdown}",
                                "ok", start, end, len(values), dump(metadata))
        sql = "DELETE FROM vitals_daily WHERE package_name=? AND metric_set=? AND breakdown=? AND date>=? AND date<?"
        args = [package, metric_set, breakdown, str(start), str(end)]
        if version is not None:
            sql += " AND version_code=?"
            args.append(str(version))
        conn.execute(sql, args)
        conn.executemany("INSERT INTO vitals_daily VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                         [(package, metric_set, breakdown, *value, fetch_id) for value in values])


def fetch_vitals(conn, run_id, service, args):
    failures = 0
    for resource_name, (metric_set, metrics) in SETS.items():
        resource = getattr(service.vitals(), resource_name)()
        name = f"apps/{args.package}/{metric_set}"
        try:
            metadata = resource.get(name=name).execute(num_retries=3)
            daily = next((f for f in metadata.get("freshnessInfo", {}).get("freshnesses", [])
                          if f["aggregationPeriod"] == "DAILY"), None)
            if daily is None:
                raise ValueError("No daily freshness information available")
            latest = read_date(daily["latestEndTime"])
            end = min(args.end, latest) if args.end else latest
            start = args.start or end - timedelta(days=args.days)
            if start >= end:
                raise ValueError(f"No available interval: latest exclusive end is {latest}")
        except (HttpError, ValueError) as exc:
            with conn:
                record_fetch(conn, run_id, "vitals", metric_set, "unavailable", detail=str(exc))
            failures += 1
            continue
        for breakdown, dimensions in BREAKDOWNS.items():
            print(f"Fetching {metric_set}/{breakdown}: {start} to {end}", file=sys.stderr)
            body = {"timelineSpec": {"aggregationPeriod": "DAILY", "startTime": api_date(start),
                                     "endTime": api_date(end)}, "dimensions": dimensions,
                    "metrics": metrics, "pageSize": 10000, "userCohort": "OS_PUBLIC"}
            if args.version_code is not None:
                body["filter"] = f"versionCode = {args.version_code}"
            try:
                rows = list(query_rows(resource, name, body))
                save_vitals(conn, run_id, args.package, metric_set, breakdown,
                            start, end, args.version_code, rows, metadata)
            except (HttpError, ValueError) as exc:
                failures += 1
                with conn:
                    record_fetch(conn, run_id, "vitals", f"{metric_set}/{breakdown}",
                                 "failed", start, end, detail=str(exc))
    return failures


def parse_csv(payload, package, dimension):
    encoding = "utf-16" if payload.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig"
    reader = csv.DictReader(io.StringIO(payload.decode(encoding)))
    headers = reader.fieldnames or []
    dim_header = next((h for h in CSV_DIMENSIONS[dimension] if h in headers), None)
    package_header = next((h for h in ("Package Name", "Package name") if h in headers), None)
    if "Date" not in headers or not dim_header or not package_header:
        raise ValueError(f"Unrecognized {dimension} CSV headers: {headers}")
    results = []
    for row in reader:
        if None in row or any(v is None for v in row.values()):
            raise ValueError("Malformed CSV row")
        if row[package_header] != package:
            raise ValueError("CSV contains another package")
        day = date.fromisoformat(row["Date"])
        metrics = {k: v for k, v in row.items() if k not in {"Date", package_header, dim_header}}
        results.append((str(day), row[dim_header], dump(metrics), dump(row)))
    return results


def months_between(start, end):
    current = start.replace(day=1)
    while current < end:
        yield current.strftime("%Y%m")
        current = (current.replace(day=28) + timedelta(days=4)).replace(day=1)


def list_objects(storage, bucket, prefix):
    params = {"bucket": bucket, "prefix": prefix}
    tokens = set()
    while True:
        response = storage.objects().list(**params).execute(num_retries=3)
        yield from response.get("items", [])
        token = response.get("nextPageToken")
        if not token:
            return
        if token in tokens:
            raise ValueError("Repeated storage pagination token")
        tokens.add(token)
        params["pageToken"] = token


def save_csv(conn, run_id, bucket, obj, package, report_type, dimension, rows):
    with conn:
        fetch_id = record_fetch(conn, run_id, "csv", obj["name"], "ok", count=len(rows))
        conn.execute("INSERT INTO csv_reports VALUES (?,?,?,?,?,?,?) ON CONFLICT(bucket,object_name) "
                     "DO UPDATE SET generation=excluded.generation,fetch_id=excluded.fetch_id",
                     (bucket, obj["name"], package, report_type, dimension, obj.get("generation"), fetch_id))
        conn.execute("DELETE FROM csv_stats WHERE bucket=? AND object_name=?", (bucket, obj["name"]))
        conn.executemany("INSERT INTO csv_stats VALUES (?,?,?,?,?,?)",
                         [(bucket, obj["name"], *row) for row in rows])


def fetch_csv(conn, run_id, storage, args):
    end = args.end or date.today() + timedelta(days=1)
    start = args.start or end - timedelta(days=args.days)
    failures = 0
    for month in months_between(start, end):
        for report_type in ("installs", "ratings"):
            prefix = f"stats/{report_type}/{report_type}_{args.package}_{month}_"
            try:
                objects = list(list_objects(storage, args.bucket, prefix))
            except HttpError as exc:
                with conn:
                    record_fetch(conn, run_id, "csv", prefix, "unavailable", detail=str(exc))
                failures += 1
                continue
            for dimension in CSV_DIMENSIONS:
                name = prefix + dimension + ".csv"
                obj = next((o for o in objects if o["name"] == name), None)
                if obj is None:
                    with conn:
                        record_fetch(conn, run_id, "csv", name, "not_available")
                    continue
                print(f"Fetching {name}", file=sys.stderr)
                try:
                    payload = storage.objects().get_media(bucket=args.bucket, object=name,
                                                          generation=obj["generation"]).execute(num_retries=3)
                    rows = parse_csv(payload, args.package, dimension)
                    save_csv(conn, run_id, args.bucket, obj, args.package, report_type, dimension, rows)
                except (HttpError, ValueError) as exc:
                    failures += 1
                    with conn:
                        record_fetch(conn, run_id, "csv", name, "failed", detail=str(exc))
    return failures


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", default="com.jeerovan.comfer")
    parser.add_argument("--database", type=Path, default=ROOT / "play_stats.db")
    parser.add_argument("--credentials", type=Path, default=ROOT / "service-account.json")
    parser.add_argument("--days", type=int, default=30)
    parser.add_argument("--start", type=date.fromisoformat)
    parser.add_argument("--end", type=date.fromisoformat, help="Exclusive date; vitals capped at API freshness")
    parser.add_argument("--version-code", type=int, help="Filter vitals only; default: all versions")
    parser.add_argument("--bucket", default=os.environ.get("PLAY_REPORTS_BUCKET", DEFAULT_BUCKET),
                        help=f"Play reports bucket name or gs:// URI (default: {DEFAULT_BUCKET}; "
                        "overridable with PLAY_REPORTS_BUCKET)")
    args = parser.parse_args(argv)
    if args.days <= 0 or (args.version_code is not None and args.version_code <= 0):
        parser.error("days and version-code must be positive")
    if args.start and args.end and args.start >= args.end:
        parser.error("start must precede end")
    if args.bucket:
        args.bucket = args.bucket.removeprefix("gs://").split("/", 1)[0]
    conn = None
    run_id = None
    try:
        conn = sqlite3.connect(args.database)
        conn.execute("PRAGMA foreign_keys=ON")
        conn.executescript(SCHEMA)
        if conn.execute("SELECT count(*) FROM sync_runs").fetchone()[0]:
            backup = args.database.with_name(args.database.name + ".backup-" +
                      datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
            with sqlite3.connect(backup) as destination:
                conn.backup(destination)
        with conn:
            run_id = conn.execute("INSERT INTO sync_runs(package_name,started_at,status) VALUES (?,?,?)",
                                  (args.package, datetime.now(timezone.utc).isoformat(), "running")).lastrowid
        scopes = ["https://www.googleapis.com/auth/playdeveloperreporting"]
        if args.bucket:
            scopes.append("https://www.googleapis.com/auth/devstorage.read_only")
        credentials = service_account.Credentials.from_service_account_file(str(args.credentials), scopes=scopes)
        service = build("playdeveloperreporting", "v1beta1", credentials=credentials, cache_discovery=False)
        try:
            failures = fetch_vitals(conn, run_id, service, args)
        finally:
            service.close()
        if args.bucket:
            storage = build("storage", "v1", credentials=credentials, cache_discovery=False)
            try:
                failures += fetch_csv(conn, run_id, storage, args)
            finally:
                storage.close()
        else:
            with conn:
                record_fetch(conn, run_id, "csv", "installs/ratings/languages", "not_configured",
                             detail="Supply --bucket or PLAY_REPORTS_BUCKET from Play Console Download reports.")
        with conn:
            conn.execute("UPDATE sync_runs SET finished_at=?,status=? WHERE id=?",
                         (datetime.now(timezone.utc).isoformat(), "partial" if failures else "complete", run_id))
        print(dump({"database": str(args.database), "run_id": run_id, "failed_fetches": failures,
                    "fetches": [dict(zip(("source", "dataset", "status", "rows"), r)) for r in
                                conn.execute("SELECT source,dataset,status,row_count FROM fetches WHERE run_id=?", (run_id,))]}))
        return 1 if failures else 0
    except (OSError, ValueError, KeyError, sqlite3.Error, GoogleAuthError, HttpLib2Error, HttpError) as exc:
        if conn is not None and run_id is not None:
            with conn:
                conn.execute("UPDATE sync_runs SET finished_at=?,status='failed' WHERE id=?",
                             (datetime.now(timezone.utc).isoformat(), run_id))
        print(f"Stats sync failed: {exc}", file=sys.stderr)
        return 1
    finally:
        if conn is not None:
            conn.close()


if __name__ == "__main__":
    sys.exit(main())
