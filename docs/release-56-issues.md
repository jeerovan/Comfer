# Version 56 Firebase issues — 15 October 2026

Comfer **56.0 (56)** is the current version. Firebase import **10** in
`play_reporting.db` contains **965 issue groups / 4,793 events** for
**15 September 13:44:30–15 October 13:44:30 UTC**.

| Type | Issue groups | Events |
| --- | ---: | ---: |
| Fatal crashes | 30 | 193 |
| ANRs | 935 | 4,600 |
| Non-fatal exceptions | 0 | 0 |

The Firebase project is `comfer-db710`, Android app
`1:141154670700:android:3463921d29d4a97bee2049`, package
`com.jeerovan.comfer`. The version display name was obtained from Firebase before
filtering. The complete inventory, sample metadata and console links are in
`validation-artifacts/firebase-v56-20261015/inventory.csv` and SQLite.

The subsequent [recurrence and fix review](release-56-review-2026-10-15.md)
records attempts **56-R01–56-R05**, scoped validation and remaining evidence
requests. All 965 v56 rows now have appended review notes and remain
`investigating`; no production resolution is inferred. This import ledger retains
the original telemetry-refresh evidence below.

## Verification and limits

- All **10** issue pages were fetched. The installed MCP tool exposes no page
  token, and Firebase returned only 100 groups despite a requested page size of
  1,000. Continuation requests used the same authenticated Firebase CLI API
  client and followed `page_token` until no continuation remained.
- Independent fatal and ANR top-version reports reconcile exactly with the
  issue totals. Firebase rejected version-filtered `topVersions` requests;
  these checks queried each error type and selected `56.0 (56)` from the result.
  The initial discovery report had one fewer fatal event; the subsequent
  complete report and reconciliation agree on 193. Reporting data can arrive late.
- Every issue has a sample checked for Firebase app, package, version, issue,
  error type and event timestamp. **16** unsuitable default samples were replaced
  with version-filtered events. Four timed-out reads were retried successfully.
- Full selected event payloads are retained losslessly in SQLite and the current
  normalized export, including threads outside the displayed stack summary.
  Issue `51ab3f8f0c820211f368dbd7527432fc` has a selected sample without supplied
  stack frames and is explicitly marked as such. Some other frames remain
  obfuscated or unsymbolized.
- **183** exact IDs / **3,738** events also appear in earlier local Firebase
  releases; **782** IDs / **1,055** events are newly observed in this local history.
  An ID match does not establish the same root cause, and a new ID does not prove
  a newly introduced defect. Earlier windows and exposure differ; these counts
  are not comparative crash rates. Per-issue user counts must not be summed.
- Existing Play and older Firebase rows, snapshots, local triage and notes were
  verified unchanged. Firebase issue states were not modified, and no production
  resolution is claimed.

## Evidence and cleanup

- Current export: `crashlytics-export-v56-20261015.json` (ignored by Git).
- Reports, import validation, database checks, recurrence analysis, inventory,
  cleanup manifest and historical backup variants:
  `validation-artifacts/firebase-v56-20261015/` (ignored by Git).
- One verified version-56 SQLite recovery backup is retained. Superseded backups
  are compared with the current database; differing historical row variants are
  archived losslessly before removal. Exact paths, hashes and bytes are in
  `cleanup-manifest.json`.
- Cleanup removed 16 redundant files (1,226,591,763 bytes), including seven
  superseded SQLite backups. The verified archive preserves 243 unique older
  row variants; historical ledgers and meaningful fix/test evidence remain.
- Six importer tests and four recurrence tests passed. All 965 imported rows
  match the export; SQLite integrity and foreign-key checks passed. This reporting
  refresh requires no Android build, device tests or layout changes.

Earlier investigations remain in the [version-55 review](release-55-review-2026-10-02.md)
and the [version-54 ledger](release-54-issues.md).
