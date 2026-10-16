# Play issue reporting

The current triage/fix ledger is maintained in
[the production reporting section](../DEVELOPMENT-PROGRESS.md#production-reporting-and-issue-ledger); the phased engineering plan is in
[the remediation section](../DEVELOPMENT-PROGRESS.md#crash-remediation-and-release-requirements).

## Google Play import

Latest Firebase snapshot: [version 56](../docs/release-56-issues.md), with the
complete inventory and recurrence evidence in
`validation-artifacts/firebase-v56-20261015/`. Historical fix attempts and
outcomes and new prepared attempts **56-R01–56-R05** are recorded in the
[version-56 review](../docs/release-56-review-2026-10-15.md); earlier evidence remains
in the [version-55 review](../docs/release-55-review-2026-10-02.md),
[version-54 review](../docs/release-54-recurrence.md) and
[version-53 review](../docs/release-53-recurrence.md).
For a repeatable read-only comparison after a future import:

```sh
venv/bin/python scripts/analyze_crashlytics_recurrence.py --version 56 \
  --output validation-artifacts/firebase-v56-20261015/recurrence.json
```

Substitute the actual version. This compares exact IDs within the Firebase app,
retains current/prior sample signatures and build stamps, and never treats
different release windows as comparable rates or overwrites existing triage.

From the repository root, using the existing virtual environment:

```sh
venv/bin/python scripts/sync_play_issues.py --version-code 56 --version-name 56.0
```

The script's legacy default version is `46`; explicitly pass the current version
as above. Other defaults: package `com.jeerovan.comfer`, root
`service-account.json`, root `play_reporting.db`, and the 30 days ending at
the current complete UTC hour. The database must already contain the reporting
schema. The environment needs `google-api-python-client` and `google-auth`.
The service account needs Play Console access to the app and the Play Developer
Reporting API enabled. Credentials are used only for Google API authentication.

To preview a fetch without writing the database:

```sh
venv/bin/python scripts/sync_play_issues.py --version-code 56 --version-name 56.0 --dry-run
```

To select a release and an explicit interval:

```sh
venv/bin/python scripts/sync_play_issues.py --version-code 56 --version-name 56.0 \
  --start 2026-10-01T00:00:00Z --end 2026-10-15T13:00:00Z
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

Use Firebase MCP for version discovery and reports. Save a complete,
version-filtered result in the JSON envelope accepted by the importer, then
validate and store it transactionally:

```sh
venv/bin/python scripts/import_firebase_crashlytics.py \
  --input crashlytics-export-v56-20261015.json --dry-run
venv/bin/python scripts/import_firebase_crashlytics.py \
  --input crashlytics-export-v56-20261015.json
```

The importer rejects incomplete exports, duplicate issue/sample IDs, mismatched
package/version/error types, and count mismatches before changing SQLite. It
uses provider-specific `crashlytics_*` tables, preserves local triage fields on
refresh, marks groups absent from the newest same-version import without
deleting history, and writes snapshots in one transaction.

Crashlytics and Play counts must stay separate: their issue grouping, sampling,
reporting windows, and user-count semantics differ.

### Latest Crashlytics verification: 15 October 2026

Import **10** stores `56.0 (56)` for **15 September 13:44:30–15 October
13:44:30 UTC**: **965 groups / 4,793 events**, comprising 30 fatal groups / 193
crashes and 935 ANR groups / 4,600 ANRs. Independent error-type top-version totals
reconcile exactly; non-fatal queries returned no version-56 events.

The complete top-issues report spans **10 pages**. The installed MCP interface
does not expose continuation requests and the service capped pages at 100 groups
despite a requested size of 1,000. Use the existing authenticated Firebase CLI API
client with `page_token` for continuation. Inspect every returned token; a first
page is not a complete export. Fetch scripts and raw pages are retained with
this refresh's evidence.

All earlier Firebase and Play rows, snapshots and triage fields are preserved.
The subsequent v56 review appends decisions to all 965 current rows and leaves
their status `investigating`, including locally prepared fixes. Keep the stable
attempt ID, actual next release/artifact identity, validation and limitations in
notes so a later import can compare attempts without erasing history. Local
validation does not justify `resolved` or changing Firebase's remote state.

Obtain the exact version display name from Firebase before filtering. Default
`sampleEvent` references are not reliably version-filtered: 16 samples in this
refresh were unsuitable for the requested release/window. Replace mismatches with
`crashlytics_list_events` using the same interval, version display name and issue
ID. Validate every selected event's app, package, version, issue, error type and
timestamp. API errors are failed requests, never evidence of an empty result.

The [version-56 ledger](../docs/release-56-issues.md) records sample/build limits.
One selected event has no supplied stack frames; full raw event payloads are
retained rather than relying on the MCP's shortened displayed traces. A version
code can include candidate/manual builds too. Do not infer a production regression
or a root cause from a retained issue title alone.

### Evidence retention and cleanup

Keep the current normalized export, raw reconciliation reports, import and
verification results, recurrence output, historical issue ledgers and meaningful
fix/test evidence. The ignored `crashlytics_*` tables retain raw selected events,
issue payloads, snapshots and local triage. After verifying these and comparing
historical rows, redundant fetch intermediates and superseded exports/backups
can be removed. Retain one verified SQLite recovery backup. Preserve unique older
row variants in a verified archive before removing superseded backups. Record
exact removed paths and bytes in the refresh's `cleanup-manifest.json`.

Older per-run totals belong in the historical ledgers/database, rather than
being repeated as current instructions here. The current refresh evidence is in
`validation-artifacts/firebase-v56-20261015/`. Google Play was not queried during
this Firebase-only refresh.

Reporting regressions (six importer and four recurrence tests):

```sh
venv/bin/python -m unittest discover -s scripts/tests -p 'test_import_firebase_crashlytics.py' -v
venv/bin/python -m unittest discover -s scripts/tests -p 'test_analyze_crashlytics_recurrence.py' -v
```
