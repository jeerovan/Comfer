#!/usr/bin/env python3
"""Refresh crash/ANR issues in the existing play_reporting.db schema."""

import argparse
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import sqlite3
import sys

from google.oauth2 import service_account
from google.auth.exceptions import GoogleAuthError
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from httplib2 import HttpLib2Error

ROOT = Path(__file__).resolve().parents[1]
SCOPE = "https://www.googleapis.com/auth/playdeveloperreporting"


def utc_hour(value):
    """Accept ISO dates/timestamps; require explicit hour alignment in UTC."""
    dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    dt = dt.astimezone(timezone.utc)
    if dt.minute or dt.second or dt.microsecond:
        raise ValueError("start/end must be aligned to a UTC hour")
    return dt


def interval_params(start, end):
    params = {}
    for key, dt in (("startTime", start), ("endTime", end)):
        for field, value in (("year", dt.year), ("month", dt.month),
                             ("day", dt.day), ("hours", dt.hour),
                             ("timeZone_id", "UTC")):
            params[f"interval_{key}_{field}"] = value
    return params


def search_pages(resource, result_key, params):
    params = dict(params)
    seen_tokens = set()
    while True:
        response = resource.search(**params).execute(num_retries=3)
        yield from response.get(result_key, [])
        token = response.get("nextPageToken")
        if not token:
            return
        if token in seen_tokens:
            raise ValueError("API repeated a pagination token; aborting incomplete import")
        seen_tokens.add(token)
        params["pageToken"] = token


def fetch_issues(service, package, version, start, end):
    errors = service.vitals().errors()
    common = {"parent": f"apps/{package}", **interval_params(start, end)}
    items = list(search_pages(errors.issues(), "errorIssues", {
        **common, "filter": f"versionCode = {version} AND "
        "(errorIssueType = CRASH OR errorIssueType = ANR)",
        "pageSize": 1000, "sampleErrorReportLimit": 1,
    }))
    results = []
    seen = set()
    for index, issue in enumerate(items, 1):
        issue_id = issue["name"].rsplit("/", 1)[-1]
        if issue_id in seen or not issue["name"].startswith(f"apps/{package}/"):
            raise ValueError("Duplicate issue or unexpected package in API response")
        seen.add(issue_id)
        for key in ("firstAppVersion", "lastAppVersion"):
            if str(issue.get(key, {}).get("versionCode", version)) != str(version):
                raise ValueError("API returned an issue outside the requested version")
        # Search by issue AND version: samples must also belong to this release.
        reports = search_pages(errors.reports(), "errorReports", {
            **common, "filter": f"versionCode = {version} AND errorIssueId = {issue_id}",
            "pageSize": 1,
        })
        report = next(reports, None)
        if report and (str(report.get("appVersion", {}).get("versionCode")) != str(version)
                       or report.get("issue", "").rsplit("/", 1)[-1] != issue_id
                       or not report.get("name", "").startswith(f"apps/{package}/")):
            raise ValueError("Sample report does not match the requested issue/version")
        results.append((issue, report))
        print(f"Fetched issue {index}/{len(items)} ({issue.get('type')})", file=sys.stderr)
    return results


def issue_row(issue, report, package, version, version_name, start, end, now):
    types = {"CRASH": "crash", "ANR": "anr", "APPLICATION_NOT_RESPONDING": "anr"}
    report_data = report or {}
    device = report_data.get("deviceModel", {})
    api_level = report_data.get("osVersion", {}).get("apiLevel")
    percent = issue.get("distinctUsersPercent", {}).get("value")
    return {
        "package_name": package, "version_code": version, "version_name": version_name,
        "issue_id": issue["name"].rsplit("/", 1)[-1], "type": types[issue["type"]],
        "affected_users": int(issue.get("distinctUsers", 0)),
        "events": int(issue.get("errorReportCount", 0)),
        "affected_users_percent": float(percent) if percent is not None else None,
        "title": ": ".join(filter(None, (issue.get("cause"), issue.get("location"))))
        or issue["name"],
        "cause": issue.get("cause"), "location": issue.get("location"),
        "stack_trace": report_data.get("reportText"),
        "play_console_uri": issue.get("issueUri"),
        "sample_report_id": report_data.get("name", "").rsplit("/", 1)[-1] or None,
        "last_event_at": issue.get("lastErrorReportTime"),
        "first_seen_at": now, "last_synced_at": now, "present_in_latest_import": 1,
        "os_api_level": int(api_level) if api_level is not None else None,
        "device_brand": device.get("deviceId", {}).get("buildBrand"),
        "device_model": device.get("marketingName") or device.get("deviceId", {}).get("buildDevice"),
        "vcs_information": report_data.get("vcsInformation"),
        "annotations_json": json.dumps(issue.get("annotations", []), sort_keys=True),
        "period_start": start.isoformat(), "period_end": end.isoformat(),
        "raw_issue_json": json.dumps(issue, sort_keys=True),
        "raw_report_json": json.dumps(report, sort_keys=True) if report is not None else None,
    }


def save_import(conn, rows, package, version, version_name, start, end, now):
    """Apply one complete refresh atomically; never infer that absent issues resolved."""
    report_columns = {"stack_trace", "sample_report_id", "os_api_level", "device_brand",
                      "device_model", "vcs_information", "raw_report_json"}
    with conn:
        run_id = conn.execute(
            "INSERT INTO import_runs (package_name,version_code,version_name,period_start,"
            "period_end,imported_at,issue_count,crash_count,anr_count) VALUES (?,?,?,?,?,?,?,?,?)",
            (package, version, version_name, start.isoformat(), end.isoformat(), now,
             len(rows), sum(r["type"] == "crash" for r in rows),
             sum(r["type"] == "anr" for r in rows)),
        ).lastrowid
        conn.execute("UPDATE issues SET present_in_latest_import=0 "
                     "WHERE package_name=? AND version_code=?", (package, version))
        for row in rows:
            columns = list(row)
            updates = []
            for column in columns:
                if column in {"package_name", "version_code", "issue_id", "first_seen_at"}:
                    continue
                value = f"excluded.{column}"
                if column in report_columns:
                    value = (f"CASE WHEN excluded.raw_report_json IS NULL THEN issues.{column} "
                             f"ELSE excluded.{column} END")
                updates.append(f"{column}={value}")
            conn.execute(
                f"INSERT INTO issues ({','.join(columns)}) VALUES "
                f"({','.join('?' for _ in columns)}) "
                "ON CONFLICT(package_name,version_code,issue_id) DO UPDATE SET "
                + ",".join(updates), list(row.values()),
            )
            conn.execute(
                "INSERT INTO issue_snapshots (import_run_id,package_name,version_code,issue_id,"
                "affected_users,events,last_event_at) VALUES (?,?,?,?,?,?,?)",
                (run_id, package, version, row["issue_id"], row["affected_users"],
                 row["events"], row["last_event_at"]),
            )
    return run_id


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", default="com.jeerovan.comfer")
    parser.add_argument("--version-code", type=int, default=46)
    parser.add_argument("--version-name", help="Local display label (default: existing label or CODE.0)")
    parser.add_argument("--database", type=Path, default=ROOT / "play_reporting.db")
    parser.add_argument("--credentials", type=Path, default=ROOT / "service-account.json")
    parser.add_argument("--days", type=int, default=30, help="Lookback when --start is omitted")
    parser.add_argument("--start", help="Inclusive ISO UTC date/hour")
    parser.add_argument("--end", help="Exclusive ISO UTC date/hour (default: current UTC hour)")
    parser.add_argument("--dry-run", action="store_true", help="Fetch and summarize without changing SQLite")
    args = parser.parse_args(argv)
    try:
        end = utc_hour(args.end) if args.end else datetime.now(timezone.utc).replace(
            minute=0, second=0, microsecond=0)
        start = utc_hour(args.start) if args.start else end - timedelta(days=args.days)
        if args.version_code <= 0 or args.days <= 0 or start >= end:
            raise ValueError("Require a positive version/days and start before end")
        # mode=rw avoids silently creating an empty database after a path typo.
        with sqlite3.connect(args.database.resolve().as_uri() + "?mode=rw", uri=True) as conn:
            conn.execute("PRAGMA foreign_keys=ON")
            for table in ("issues", "import_runs", "issue_snapshots"):
                conn.execute(f"SELECT * FROM {table} LIMIT 0")
            existing = conn.execute("SELECT version_name FROM issues WHERE package_name=? "
                                    "AND version_code=? LIMIT 1",
                                    (args.package, args.version_code)).fetchone()
            version_name = args.version_name or (existing[0] if existing else f"{args.version_code}.0")
            credentials = service_account.Credentials.from_service_account_file(
                str(args.credentials), scopes=[SCOPE])
            service = build("playdeveloperreporting", "v1beta1", credentials=credentials,
                            cache_discovery=False)
            print(f"Fetching {args.package} version {args.version_code}: "
                  f"{start.isoformat()} to {end.isoformat()}", file=sys.stderr)
            try:
                fetched = fetch_issues(service, args.package, args.version_code, start, end)
            finally:
                service.close()
            now = datetime.now(timezone.utc).isoformat()
            rows = [issue_row(i, r, args.package, args.version_code, version_name, start, end, now)
                    for i, r in fetched]
            summary = {"package": args.package, "version_code": args.version_code,
                       "period_start": start.isoformat(), "period_end": end.isoformat(),
                       "issues": len(rows), "sample_reports": sum(r is not None for _, r in fetched),
                       "crash_issues": sum(r["type"] == "crash" for r in rows),
                       "anr_issues": sum(r["type"] == "anr" for r in rows),
                       "events": sum(r["events"] for r in rows), "dry_run": args.dry_run}
            if not args.dry_run:
                backup = args.database.with_name(args.database.name + ".backup-" +
                                                datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
                with sqlite3.connect(backup) as destination:
                    conn.backup(destination)
                summary["backup"] = str(backup)
                summary["import_run_id"] = save_import(
                    conn, rows, args.package, args.version_code, version_name, start, end, now)
            print(json.dumps(summary, indent=2))
        return 0
    except (ValueError, KeyError, OSError, sqlite3.Error, HttpError,
            GoogleAuthError, HttpLib2Error) as exc:
        print(f"Sync failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
