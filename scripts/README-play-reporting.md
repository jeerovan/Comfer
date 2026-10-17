# Play issue reporting

`play_reporting.db` is the canonical local store for telemetry, historical issue
reviews, attempted fixes, validation and production follow-up. Release documents
retain artifact/acceptance summaries and link here for issue details. Answers
about current app implementation must still be checked against current code.

## Issue and fix history

Run these read-only commands from the repository root:

```sh
python3 scripts/issue_history.py summary
python3 scripts/issue_history.py show --issue 159e25351be7042db517f3af684684db --provider firebase
python3 scripts/issue_history.py show --attempt 56-R03
python3 scripts/issue_history.py show --version 57 --search certificate
```

`show` returns archived entries, explicit attempt definitions/mentions and issue
references. An issue-ID query also returns existing per-version telemetry notes,
including decisions absent from the documents. Provider and app/package scope
remain separate; use `--scope` when needed. A reference means that a report mentions
an ID, not proof that its events share a cause or were fixed.

`--version` filters historical document/record context. It need not be the affected
or shipped version: 56-R01 was prepared for the next release after 56. The issue
query's `telemetry_history` retains all observed versions. `--entry`, `--source`,
`--section` and `--search` narrow results. The default limit is 20; `total` and
`shown` expose truncation. Increase `--limit` explicitly.
`summary` separates retained historical groups from groups/events present in the
latest import for each provider scope and version.

The additive schema leaves provider tables, notes and status fields unchanged:

| Table/view | Purpose |
| --- | --- |
| `issue_history_documents` | Immutable source revisions, complete original text, SHA-256 and Git revision |
| `issue_history_entries` | Individual sections/table rows, original line ranges and verbatim labeled fields |
| `issue_history_issue_links` | Provider/app-scoped issue references with their matching basis |
| `issue_history_attempt_links` | Stable attempt IDs, distinguishing definitions from mentions |
| `issue_history_schema` | History schema version, independent of legacy `PRAGMA user_version` |
| `issue_history` / `issue_fix_history` / `issue_review_history` | SQL views for entry, attempt and provider-issue queries |

For example:

```sql
SELECT attempt_id, role, release_version, source_path, section, body
FROM issue_fix_history WHERE attempt_id = '56-R03'
ORDER BY release_version, source_path, start_line;
```

Line ranges refer to the **archived original**, not the shortened document now
in Git. Retrieve a full original, including its old headings, with:

```sh
python3 scripts/issue_history.py source --path docs/release-56-review-2026-10-15.md \
  --output /tmp/release-56-review-original.md
```

The [source manifest](issue_history_sources.json) records originals migrated from
releases 50–57, the development ledger and this guide. After restoring telemetry,
this idempotent command reconstructs the history from recorded Git revisions when
the working documents have been shortened:

```sh
python3 scripts/issue_history.py import-docs --manifest scripts/issue_history_sources.json
```

Hashes are verified before import. A changed document becomes another immutable
revision; identical imports add nothing. The importer takes a verified SQLite
backup before changing writes and commits schema/data together. It never edits
telemetry, old triage or remote issue states. Ambiguous references are retained
as unresolved rather than assigned a guessed provider.

Append findings with `record --input /tmp/issue-update.json`. JSON requires a stable
`record_id`, positive `version_code` (review context), and `title`. Include
`attempt_id`, `prior_attempts`, `issues` (objects containing `provider`, `scope_id`
and `issue_id`), evidence, hypothesis, change, validation, target version, exact
commit/artifact identities, production outcome and limitations when available.
All fields are preserved. Identical content is a no-op; changed content appends a
revision, preserving earlier decisions and failures. Record production confirmation
only when supported. Documented claims are imported verbatim, never promoted to
verified fixes by the migration.

The database and backups remain ignored by Git. Preserve verified recovery copies:
Git reconstruction restores migrated reports, not subsequently appended records
or raw telemetry. Future issue details belong in the database instead of another
duplicated release ledger. The [release requirements](../DEVELOPMENT-PROGRESS.md#crash-remediation-and-release-requirements)
remain applicable.

## Google Play import

Examples below use historical version 56. Select the actual provider version and
interval instead of treating example numbers as current release state.

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

### Recurrence comparison

For a read-only exact-ID comparison after a Firebase import:

```sh
venv/bin/python scripts/analyze_crashlytics_recurrence.py --version 56 \
  --output /tmp/comfer-recurrence-v56.json
```

This retains current/prior sample signatures and build stamps. Use `issue_history.py`
alongside it for documented attempts, outcomes and limitations.

### Verification rules

Follow every continuation token using the currently available API/tool schema.
A first page is not a complete export when more pages remain. Reconcile totals
independently by error type and retain the reporting interval. Historical fetches,
interface limitations, sample replacements and build checks are preserved in the
database's archived reports instead of repeated as current instructions here.

Obtain the exact version display name from Firebase before filtering. Validate
every selected event's app, package, version, issue, error type and timestamp;
replace unsuitable defaults with version-filtered events. API errors are failed
requests, never evidence of an empty result. Retain full raw event payloads and
missing-stack limitations. A version can include candidate/manual builds; match
the mapping and signed artifact before attributing samples to shipped code.

Keep stable attempt IDs, actual target/shipped versions, commit/artifact identities,
validation and limitations in appended history. Local validation, absent groups
and repeated IDs do not justify production resolution or remote state changes.

### Evidence retention and cleanup

Keep the current normalized export, raw reconciliation reports, import and
verification results, recurrence output, archived issue history and meaningful
fix/test evidence. The ignored `crashlytics_*` tables retain raw selected events,
issue payloads, snapshots and local triage. After verifying these and comparing
historical rows, redundant fetch intermediates and superseded exports/backups
can be removed. Retain one verified SQLite recovery backup. Preserve unique older
row variants in a verified archive before removing superseded backups. Record
exact removed paths and bytes in the refresh's `cleanup-manifest.json`.

Per-run totals, cleanup manifests and evidence paths belong in the database's
historical records. Preserve both providers' histories without adding their
counts or per-issue user totals together.

Reporting regressions:

```sh
venv/bin/python -m unittest discover -s scripts/tests -p 'test_import_firebase_crashlytics.py' -v
venv/bin/python -m unittest discover -s scripts/tests -p 'test_analyze_crashlytics_recurrence.py' -v
python3 -m unittest discover -s scripts/tests -p 'test_issue_history.py' -v
```
