# Play statistics

```sh
venv/bin/python scripts/sync_play_stats.py
```

Creates or refreshes root `play_stats.db`, using root `service-account.json` and
the existing virtual environment. Defaults to all versions of
`com.jeerovan.comfer`, account bucket `pubsite_prod_5435940062993501423`, and
30 available days. No additional Python dependencies
are needed beyond the existing Google API client and authentication packages.

## Available data

`vitals_daily` holds daily active-user estimates, crash rates, user-perceived
crash rates, ANR rates, and user-perceived ANR rates. Each metric set has four
independently queried breakdowns:

- `version`: app version only.
- `country_version`: country and app version.
- `device_type_version`: device form factor and app version.
- `country_device_type_version`: country, device form factor, and app version.

These are Android vitals statistics, not total installations. The API provides
rounded active-user estimates. Never sum users across dates, breakdowns, or
metric sets as a unique audience total, and do not sum rates. Missing rows or
metrics are not interpreted as zero. Fine-grained results can differ from
coarser queries. The raw API row retains decimal values and any confidence
intervals and labels. See Google's
[metric definitions](https://developers.google.com/play/developer/reporting/reference/rest/v1beta1/vitals.crashrate).

Daily dates use `America/Los_Angeles`. Start is inclusive and end exclusive.
The script reads freshness separately for each metric set and caps the end
date to available data. With no explicit start, the lookback ends at that cap.
The selected cohort is `OS_PUBLIC` (public Android versions).

```sh
venv/bin/python scripts/sync_play_stats.py --days 14
venv/bin/python scripts/sync_play_stats.py --version-code 46
venv/bin/python scripts/sync_play_stats.py --start 2026-08-01 --end 2026-09-01
```

## Install, rating, and language reports

The account bucket is built into the script, so the default command includes CSV
reports. To override it, use `--bucket`:

```sh
venv/bin/python scripts/sync_play_stats.py --bucket gs://pubsite_prod_5435940062993501423/stats/
```

Alternatively set `PLAY_REPORTS_BUCKET`; `--bucket` takes precedence. Both a bucket name and a full report
URI are accepted; only its bucket component is used. Use the exact URI copied
from this app's Console; no `_rev` prefix is required. The service account
needs access to bulk reports. Google requires global “View app information”
permission for that access. See Google's
[CSV export documentation](https://support.google.com/googleplay/android-developer/answer/6135870?hl=en).

The script lists only this app's monthly `stats/installs` and `stats/ratings`
objects and fetches available country, device, language, and app-version files.
UTF-16 and UTF-8 CSVs are supported. Whole monthly files intersecting the chosen
date range are imported; filter dates when querying. `--version-code` applies
only to vitals, since most CSV breakdowns contain no version column.

`csv_reports` records object names, generations, and dimensions. `csv_stats`
retains the date, dimension value, metrics as JSON, and original row JSON.
The `language_stats` view exposes imported language rows. Empty metric cells
remain empty; they are not fabricated as zeros. A device CSV identifies device
models, while vitals `device_type` identifies form factors.

Each CSV has its own breakdown. A country-only or language-only CSV cannot
establish country/language by app version. The script does not join separate
marginal reports into invented combinations.

An explicitly empty bucket (`--bucket ''`) disables CSV fetching and records
the source as `not_configured`. Missing files
are `not_available`; API permission failures and other errors are recorded
separately. This distinction prevents absent access from looking like zero users.

## Refresh behavior and inspection

Existing databases receive a timestamped SQLite backup before a run. Each
complete vitals slice or CSV object is replaced transactionally after fetching
and validation. Repeating a query updates rows without duplicates. An empty
successful response clears the corresponding slice; failures preserve its old
data. Other dates, versions, and sources remain intact. `sync_runs` and `fetches`
retain audit history, freshness metadata, errors, and per-query row counts.

Successful datasets remain available if another dataset fails; the run is
marked `partial` and exits nonzero. A `complete` run means configured fetches
succeeded; inspect `fetches` for unconfigured or missing optional exports.
Credentials, database files, and backups are excluded from Git.

Example: country/device breakdown for a single date and version:

```sql
SELECT country_code, device_type, distinct_users, crash_rate
FROM vitals_daily
WHERE metric_set = 'crashRateMetricSet'
  AND breakdown = 'country_device_type_version'
  AND version_code = '45' AND date = '2026-09-02'
ORDER BY distinct_users DESC;
```

Check freshness and optional-source availability:

```sql
SELECT dataset, status, period_start, period_end, row_count, detail
FROM fetches WHERE run_id = (SELECT max(id) FROM sync_runs);
```

Initial fetch on September 7, 2026: **15,114 vitals rows**, August 4–September 2,
covering **79 countries**, **PHONE/TABLET**, and **10 versions** (34–40, 42, 44,
45). The API reported September 3 as its exclusive daily freshness boundary.
No version 46 statistics were returned. The CSV source was not configured
because a report bucket had not been supplied.

The subsequent run **2** successfully used
`gs://pubsite_prod_5435940062993501423/stats/`. It imported 12 CSV files:
August installs and August/September ratings, each with country, language,
device-model, and app-version breakdowns. September install files were not
available. All configured requests completed without errors.

Tests:

```sh
venv/bin/python -m unittest discover -s scripts/tests -v
```
