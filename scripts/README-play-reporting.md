# Play issue reporting

The current triage/fix ledger is maintained in
[the production reporting section](../DEVELOPMENT-PROGRESS.md#production-reporting-and-issue-ledger); the phased engineering plan is in
[the remediation section](../DEVELOPMENT-PROGRESS.md#crash-remediation-and-release-requirements).

## Google Play import

From the repository root, using the existing virtual environment:

```sh
venv/bin/python scripts/sync_play_issues.py
```

Defaults: package `com.jeerovan.comfer`, version code `46`, root
`service-account.json`, root `play_reporting.db`, and the 30 days ending at
the current complete UTC hour. The database must already contain the reporting
schema. The environment needs `google-api-python-client` and `google-auth`.
The service account needs Play Console access to the app and the Play Developer
Reporting API enabled. Credentials are used only for Google API authentication.

To preview a fetch without writing the database:

```sh
venv/bin/python scripts/sync_play_issues.py --dry-run
```

To select a release and an explicit interval:

```sh
venv/bin/python scripts/sync_play_issues.py --version-code 46 --version-name 46.0 \
  --start 2026-09-03T00:00:00Z --end 2026-09-07T05:00:00Z
```

Dates without a timezone are interpreted as UTC. Start is inclusive, end is
exclusive; both must align to UTC hours. `--days` changes the default lookback.
`--database` and `--credentials` override the root paths. `--version-name` is a
local display label; otherwise an existing label is reused or `CODE.0` is used.
API filtering always uses the numeric version code.

The script follows all issue pages and retrieves one version-filtered sample
report per issue when available. Crash and ANR counts are fetched from the
[Reporting API issue search](https://developers.google.com/play/developer/reporting/reference/rest/v1beta1/vitals.errors.issues/search).
Stack traces and device details come from
[error reports](https://developers.google.com/play/developer/reporting/reference/rest/v1beta1/vitals.errors.reports).
Non-fatal issues are excluded because the existing database supports crash/ANR
types only. Fetching does not change anything in Play Console.

All API fetching and row conversion finish before database updates begin. A
timestamped `play_reporting.db.backup-*` SQLite backup is created before each
write. The import then runs in one transaction:

- Upsert issues using `(package_name, version_code, issue_id)` without duplicating them.
- Preserve `status`, `notes`, `resolved_at`, and `first_seen_at` on existing issues.
- Retain an older sample if no sample is available during the new fetch.
- Set `present_in_latest_import=0` for missing issues of this package/version;
  keep their history and resolution state. Their retained counts refer to their
  stored interval, not the latest interval.
- Record `import_runs` and one `issue_snapshots` row per fetched issue.
  An empty successful fetch records a zero-issue import run.

Repeated runs replace the current interval counts and add historical snapshots;
they do not add overlapping counts together. `crash_count` and `anr_count` in
`import_runs` count issue clusters, while `issues.events` counts reports.
`affected_users` is per issue and must not be summed as an app-wide unique-user
total. `affected_users_percent` describes the share of users affected by issues,
not a crash rate among all active users.

Credentials, database files, backups, and the virtual environment are already
excluded by the root `.gitignore`.

## Firebase Crashlytics import

The Firebase MCP server is the data source. Save its complete, version-filtered
result in the JSON envelope accepted by the importer, then validate and store it
transactionally:

```sh
venv/bin/python scripts/import_firebase_crashlytics.py \
  --input crashlytics-export.json --dry-run
venv/bin/python scripts/import_firebase_crashlytics.py \
  --input crashlytics-export.json
```

The importer rejects incomplete exports, duplicate issue/sample IDs, mismatched
package/version/error types, and count mismatches before changing SQLite. It
uses provider-specific `crashlytics_*` tables, preserves local triage fields on
refresh, marks groups absent from the newest same-version import without
deleting history, and writes snapshots in one transaction.

Crashlytics and Play counts must stay separate: their issue grouping, sampling,
reporting windows, and user-count semantics differ.

### Crashlytics verification on 2026-09-11

Import run 1 stored a complete version-48 interval from 2026-08-13 00:00 UTC
through 2026-09-11 23:59:59 UTC:

| Version | Crash issues | ANR issues | Crash events | ANR events |
|---|---:|---:|---:|---:|
| 48 | 30 | 176 | 296 | 886 |

All 206 groups / 1,182 events reconciled with the Firebase version report.
Crashlytics returned no version-46 data.

## Earlier Play verification on 2026-09-07

The database initially contained 1,436 issues and five import runs:

| Version | Crash issues | ANR issues | Crash reports | ANR reports | Stored interval (UTC) |
| --- | ---: | ---: | ---: | ---: | --- |
| 42 | 162 | 1,252 | 3,971 | 4,910 | July 25 14:00–August 24 14:00 |
| 44 | 0 | 11 | 0 | 14 | August 26 00:00–August 31 04:00 |
| 45 | 2 | 9 | 16 | 22 | August 31 00:00–September 3 02:00 |

All existing issues had local `pending` status. Different time windows and
unknown active-user totals prevent a direct release-quality comparison.
Version 45's largest stored crash cluster was `ClassNotFoundException`
(15 reports, 2 affected users); its largest ANR cluster was an input-dispatch
timeout with no focused window (12 reports, 12 affected users).

Version 46 had no stored issues before the refresh. The live API returned **zero
crash/ANR issues** for August 8 05:00 through September 7 05:00 UTC. Import run
**6** records this successful empty result. An independent query with only
`versionCode = 46` also returned zero issues. The release-filter endpoint listed
`46 (46.0)` in both production and internal testing. This establishes what the
API returned for the queried window; it does not establish that no errors have
occurred outside the available reporting data.

The older versions were preserved. SQLite integrity and foreign-key checks
passed. Tests cover pagination, version matching, repeat imports, triage
preservation, empty imports, and transaction rollback:

```sh
venv/bin/python -m unittest discover -s scripts/tests -v
```
