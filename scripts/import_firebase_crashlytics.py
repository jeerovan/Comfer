#!/usr/bin/env python3
"""Validate a Firebase MCP Crashlytics export and import it into play_reporting.db.

The Firebase MCP server supplies the report; this script provides a repeatable,
transactional bridge from that JSON export to provider-specific SQLite tables.
"""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import sqlite3
import sys


ROOT = Path(__file__).resolve().parents[1]
ISSUE_ID_RE = re.compile(r"^[0-9a-f]{32}$")
ERROR_TYPES = {"FATAL": "crash", "ANR": "anr"}

SCHEMA = (
    """
    CREATE TABLE IF NOT EXISTS crashlytics_import_runs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        source TEXT NOT NULL,
        firebase_project_id TEXT NOT NULL,
        firebase_app_id TEXT NOT NULL,
        package_name TEXT NOT NULL,
        version_code INTEGER NOT NULL,
        version_name TEXT NOT NULL,
        period_start TEXT NOT NULL,
        period_end TEXT NOT NULL,
        imported_at TEXT NOT NULL,
        issue_count INTEGER NOT NULL CHECK(issue_count >= 0),
        crash_count INTEGER NOT NULL CHECK(crash_count >= 0),
        anr_count INTEGER NOT NULL CHECK(anr_count >= 0),
        event_count INTEGER NOT NULL CHECK(event_count >= 0),
        complete INTEGER NOT NULL CHECK(complete = 1)
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS crashlytics_issues (
        firebase_app_id TEXT NOT NULL,
        version_code INTEGER NOT NULL,
        issue_id TEXT NOT NULL,
        firebase_project_id TEXT NOT NULL,
        package_name TEXT NOT NULL,
        version_name TEXT NOT NULL,
        type TEXT NOT NULL CHECK(type IN ('crash', 'anr')),
        affected_users INTEGER NOT NULL CHECK(affected_users >= 0),
        events INTEGER NOT NULL CHECK(events >= 0),
        sessions INTEGER NOT NULL CHECK(sessions >= 0),
        title TEXT NOT NULL,
        subtitle TEXT,
        stack_trace TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'pending'
            CHECK(status IN ('pending', 'investigating', 'fixed', 'resolved', 'ignored')),
        notes TEXT,
        resolved_at TEXT,
        firebase_state TEXT,
        console_uri TEXT,
        sample_event_name TEXT NOT NULL,
        sample_event_id TEXT NOT NULL,
        event_time TEXT NOT NULL,
        received_time TEXT NOT NULL,
        device_brand TEXT NOT NULL,
        device_model TEXT NOT NULL,
        device_marketing_name TEXT NOT NULL,
        device_architecture TEXT NOT NULL,
        os_version TEXT NOT NULL,
        process_state TEXT NOT NULL,
        app_orientation TEXT NOT NULL,
        device_orientation TEXT NOT NULL,
        blame_symbol TEXT,
        blame_file TEXT,
        blame_line TEXT,
        blame_owner TEXT,
        first_seen_version TEXT,
        last_seen_version TEXT,
        signals_json TEXT NOT NULL,
        first_seen_at TEXT NOT NULL,
        last_synced_at TEXT NOT NULL,
        present_in_latest_import INTEGER NOT NULL DEFAULT 1
            CHECK(present_in_latest_import IN (0, 1)),
        period_start TEXT NOT NULL,
        period_end TEXT NOT NULL,
        raw_issue_text TEXT NOT NULL,
        raw_event_text TEXT NOT NULL,
        PRIMARY KEY(firebase_app_id, version_code, issue_id)
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS crashlytics_issue_snapshots (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        import_run_id INTEGER NOT NULL REFERENCES crashlytics_import_runs(id),
        firebase_app_id TEXT NOT NULL,
        version_code INTEGER NOT NULL,
        issue_id TEXT NOT NULL,
        affected_users INTEGER NOT NULL CHECK(affected_users >= 0),
        events INTEGER NOT NULL CHECK(events >= 0),
        sessions INTEGER NOT NULL CHECK(sessions >= 0),
        sample_event_time TEXT NOT NULL
    )
    """,
    """
    CREATE INDEX IF NOT EXISTS crashlytics_issues_version_type
    ON crashlytics_issues(version_code, type, events DESC)
    """,
    """
    CREATE INDEX IF NOT EXISTS crashlytics_snapshots_issue
    ON crashlytics_issue_snapshots(firebase_app_id, version_code, issue_id)
    """,
)


def _required_string(mapping, key, context):
    value = mapping.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{context}.{key} must be a non-empty string")
    return value


def _nonnegative_int(mapping, key, context):
    value = mapping.get(key)
    if isinstance(value, bool):
        raise ValueError(f"{context}.{key} must be a non-negative integer")
    try:
        value = int(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{context}.{key} must be a non-negative integer") from exc
    if value < 0:
        raise ValueError(f"{context}.{key} must be a non-negative integer")
    return value


def _parse_timestamp(value, context):
    value = _required_string({"value": value}, "value", context)
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ValueError(f"{context} must be an ISO-8601 timestamp") from exc


def validate_payload(payload):
    """Return normalized metadata and rows only after proving export completeness."""
    if not isinstance(payload, dict):
        raise ValueError("Input must be a JSON object")
    if payload.get("complete") is not True:
        raise ValueError("Crashlytics export must be marked complete")

    source = _required_string(payload, "source", "payload")
    project_id = _required_string(payload, "firebaseProjectId", "payload")
    app_id = _required_string(payload, "firebaseAppId", "payload")
    package = _required_string(payload, "packageName", "payload")
    version_code = _nonnegative_int(payload, "versionCode", "payload")
    if version_code <= 0:
        raise ValueError("payload.versionCode must be positive")
    version_name = _required_string(payload, "versionName", "payload")
    period_start = _required_string(payload, "periodStart", "payload")
    period_end = _required_string(payload, "periodEnd", "payload")
    if _parse_timestamp(period_start, "payload.periodStart") >= _parse_timestamp(
        period_end, "payload.periodEnd"
    ):
        raise ValueError("payload.periodStart must be before payload.periodEnd")

    issues = payload.get("issues")
    if not isinstance(issues, list):
        raise ValueError("payload.issues must be an array")
    expected_issue_count = _nonnegative_int(payload, "expectedIssueCount", "payload")
    expected_event_count = _nonnegative_int(payload, "expectedEventCount", "payload")
    if len(issues) != expected_issue_count:
        raise ValueError(
            f"Expected issue count {expected_issue_count}, received {len(issues)}"
        )

    rows = []
    issue_ids = set()
    event_names = set()
    for index, item in enumerate(issues):
        context = f"issues[{index}]"
        if not isinstance(item, dict):
            raise ValueError(f"{context} must be an object")
        issue_id = _required_string(item, "id", context)
        if not ISSUE_ID_RE.fullmatch(issue_id):
            raise ValueError(f"{context}.id is not a Crashlytics issue ID")
        if issue_id in issue_ids:
            raise ValueError(f"Duplicate Crashlytics issue ID: {issue_id}")
        issue_ids.add(issue_id)

        error_type = _required_string(item, "errorType", context)
        if error_type not in ERROR_TYPES:
            raise ValueError(f"{context}.errorType must be FATAL or ANR")
        sample_name = _required_string(item, "sampleEvent", context)
        if sample_name in event_names:
            raise ValueError(f"Duplicate Crashlytics sample event: {sample_name}")
        event_names.add(sample_name)

        event = item.get("event")
        if not isinstance(event, dict):
            raise ValueError(f"{context}.event must be an object")
        event_context = f"{context}.event"
        event_name = _required_string(event, "name", event_context)
        event_issue_id = _required_string(event, "issueId", event_context)
        event_error_type = _required_string(event, "errorType", event_context)
        event_package = _required_string(event, "packageName", event_context)
        event_version = _nonnegative_int(event, "versionCode", event_context)
        if (
            event_name != sample_name
            or event_issue_id != issue_id
            or event_error_type != error_type
            or event_package != package
            or event_version != version_code
        ):
            raise ValueError(f"{context} sample event does not match its issue/package/version")

        rows.append(
            {
                "firebase_app_id": app_id,
                "version_code": version_code,
                "issue_id": issue_id,
                "firebase_project_id": project_id,
                "package_name": package,
                "version_name": version_name,
                "type": ERROR_TYPES[error_type],
                "affected_users": _nonnegative_int(item, "users", context),
                "events": _nonnegative_int(item, "events", context),
                "sessions": _nonnegative_int(item, "sessions", context),
                "title": _required_string(item, "title", context),
                "subtitle": item.get("subtitle"),
                "stack_trace": _required_string(event, "stackTrace", event_context),
                "firebase_state": item.get("state"),
                "console_uri": item.get("uri"),
                "sample_event_name": event_name,
                "sample_event_id": _required_string(event, "eventId", event_context),
                "event_time": _required_string(event, "eventTime", event_context),
                "received_time": _required_string(event, "receivedTime", event_context),
                "device_brand": _required_string(event, "deviceBrand", event_context),
                "device_model": _required_string(event, "deviceModel", event_context),
                "device_marketing_name": _required_string(
                    event, "deviceMarketingName", event_context
                ),
                "device_architecture": _required_string(
                    event, "deviceArchitecture", event_context
                ),
                "os_version": _required_string(event, "osVersion", event_context),
                "process_state": _required_string(event, "processState", event_context),
                "app_orientation": _required_string(
                    event, "appOrientation", event_context
                ),
                "device_orientation": _required_string(
                    event, "deviceOrientation", event_context
                ),
                "blame_symbol": event.get("blameSymbol"),
                "blame_file": event.get("blameFile"),
                "blame_line": event.get("blameLine"),
                "blame_owner": event.get("blameOwner"),
                "first_seen_version": item.get("firstSeenVersion"),
                "last_seen_version": item.get("lastSeenVersion"),
                "signals_json": json.dumps(item.get("signals", []), sort_keys=True),
                "period_start": period_start,
                "period_end": period_end,
                "raw_issue_text": _required_string(item, "rawGroup", context),
                "raw_event_text": _required_string(event, "rawEventText", event_context),
            }
        )

    event_count = sum(row["events"] for row in rows)
    if event_count != expected_event_count:
        raise ValueError(
            f"Expected event count {expected_event_count}, received {event_count}"
        )
    return {
        "source": source,
        "firebase_project_id": project_id,
        "firebase_app_id": app_id,
        "package_name": package,
        "version_code": version_code,
        "version_name": version_name,
        "period_start": period_start,
        "period_end": period_end,
        "issue_count": len(rows),
        "crash_count": sum(row["type"] == "crash" for row in rows),
        "anr_count": sum(row["type"] == "anr" for row in rows),
        "event_count": event_count,
        "rows": rows,
    }


def ensure_schema(conn):
    for statement in SCHEMA:
        conn.execute(statement)


def save_import(conn, report, now):
    """Save one validated, complete Crashlytics refresh in a single transaction."""
    with conn:
        ensure_schema(conn)
        run_id = conn.execute(
            """
            INSERT INTO crashlytics_import_runs (
                source, firebase_project_id, firebase_app_id, package_name,
                version_code, version_name, period_start, period_end, imported_at,
                issue_count, crash_count, anr_count, event_count, complete
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
            """,
            (
                report["source"],
                report["firebase_project_id"],
                report["firebase_app_id"],
                report["package_name"],
                report["version_code"],
                report["version_name"],
                report["period_start"],
                report["period_end"],
                now,
                report["issue_count"],
                report["crash_count"],
                report["anr_count"],
                report["event_count"],
            ),
        ).lastrowid
        conn.execute(
            """
            UPDATE crashlytics_issues
            SET present_in_latest_import=0
            WHERE firebase_app_id=? AND version_code=?
            """,
            (report["firebase_app_id"], report["version_code"]),
        )
        for source_row in report["rows"]:
            row = dict(source_row)
            row.update(
                first_seen_at=now,
                last_synced_at=now,
                present_in_latest_import=1,
            )
            columns = list(row)
            updates = [
                f"{column}=excluded.{column}"
                for column in columns
                if column
                not in {"firebase_app_id", "version_code", "issue_id", "first_seen_at"}
            ]
            conn.execute(
                f"INSERT INTO crashlytics_issues ({','.join(columns)}) "
                f"VALUES ({','.join('?' for _ in columns)}) "
                "ON CONFLICT(firebase_app_id,version_code,issue_id) DO UPDATE SET "
                + ",".join(updates),
                list(row.values()),
            )
            conn.execute(
                """
                INSERT INTO crashlytics_issue_snapshots (
                    import_run_id, firebase_app_id, version_code, issue_id,
                    affected_users, events, sessions, sample_event_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    run_id,
                    row["firebase_app_id"],
                    row["version_code"],
                    row["issue_id"],
                    row["affected_users"],
                    row["events"],
                    row["sessions"],
                    row["event_time"],
                ),
            )
    return run_id


def _load_json(path):
    if str(path) == "-":
        return json.load(sys.stdin)
    with path.open(encoding="utf-8") as stream:
        return json.load(stream)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path, help="MCP export JSON, or - for stdin")
    parser.add_argument("--database", type=Path, default=ROOT / "play_reporting.db")
    parser.add_argument("--dry-run", action="store_true", help="Validate without changing SQLite")
    args = parser.parse_args(argv)
    try:
        report = validate_payload(_load_json(args.input))
        summary = {
            "firebase_project_id": report["firebase_project_id"],
            "firebase_app_id": report["firebase_app_id"],
            "package": report["package_name"],
            "version_code": report["version_code"],
            "period_start": report["period_start"],
            "period_end": report["period_end"],
            "issues": report["issue_count"],
            "crash_issues": report["crash_count"],
            "anr_issues": report["anr_count"],
            "events": report["event_count"],
            "dry_run": args.dry_run,
        }
        if not args.dry_run:
            database = args.database.resolve()
            with sqlite3.connect(database.as_uri() + "?mode=rw", uri=True) as conn:
                conn.execute("PRAGMA foreign_keys=ON")
                backup = database.with_name(
                    database.name
                    + ".backup-"
                    + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
                )
                with sqlite3.connect(backup) as destination:
                    conn.backup(destination)
                summary["backup"] = str(backup)
                summary["import_run_id"] = save_import(
                    conn, report, datetime.now(timezone.utc).isoformat()
                )
        print(json.dumps(summary, indent=2))
        return 0
    except (ValueError, KeyError, OSError, json.JSONDecodeError, sqlite3.Error) as exc:
        print(f"Crashlytics import failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
