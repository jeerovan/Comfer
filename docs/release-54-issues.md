# Version 54 Firebase issues — 29 September 2026

Current telemetry ledger for **Comfer 54.0 (54)**. Firebase MCP import **7** is stored in `play_reporting.db`. This refresh collects and triages reports; it does not implement crash fixes or change Firebase issue states.

Subsequent [recurrence review and safe fixes](release-54-recurrence.md) documents
past attempts, all new issue dispositions, and locally tested attempts 54-03/54-04.
The counts below remain the original fixed import window.

## Scope and reconciliation

- Firebase project: `comfer-db710`; Android app: `1:141154670700:android:3463921d29d4a97bee2049`; package: `com.jeerovan.comfer`.
- Fixed query window: **2026-09-01 00:00:00–2026-09-29 13:34:10 UTC**. The API supplied the version display name `54.0 (54)` before it was used as a filter.
- **127 groups / 368 events**: **10 fatal groups / 25 crash events**, **117 ANR groups / 343 ANR events**. A separate NON_FATAL report returned no groups.
- The top-issues report returned 127 of a requested maximum 1,000 rows with no continuation token. Its 368 events exactly reconcile with the independent top-versions report for the same window.
- **45 exact IDs / 271 events** also appear in earlier stored Firebase releases; **82 IDs / 97 events** are newly observed in this local history. New IDs do not prove newly introduced defects, and retained IDs do not guarantee matching root causes.
- All 127 selected events match the app, package, version, issue ID, error type and interval. **16 default samples from older releases were replaced** with version-filtered samples.

The [complete 127-row inventory](release-54-issues-inventory.md) links every issue to Firebase. Per-issue user counts are not additive. Counts across versions have different windows and release exposure and must not be compared as crash rates. Google Play data was not refreshed or mixed into these totals.

## Findings requiring follow-up

| Evidence | Current observation | Next investigation |
| --- | --- | --- |
| `c644da91eaa6ccd96baca2a6548b2eef` — 1 crash | Duplicate Compose key for Facebook LoginActivity/profile 0. | Revisit 50-04/51-06 deduplication against the matching R8 mapping. The obfuscated sample does not identify the exact affected list. |
| `c48a27e73027d11418e2a4fb30f13084` — 2 crashes | Honor Gallery widget attachment tries to write a privileged setting. | Check provider containment on the affected Honor device; the prior weather-widget guard is not evidence that this provider is covered. |
| `6e290e48f0fc0a35f868bfe7a0eb734c` — 3 crashes | GoogleApiHandler certificate SecurityException; selected device Pixel 7a / Android 17. | Correlate Google Play services, certificate configuration and the exact release artifact before assigning app ownership. |
| `ca8f7f21e3ec633d0d1dca453409435b` — 1 crash | Group title still says Honor PowerSaveModeLauncher, but the version-54 sample is a missing **MlKitInitProvider** on Infinix X650 / Android 8.1.0. It carries older revision `adbaffe`. | Verify delivered split APK/provider packaging and build identity. Do not count this sample as recurrence of the Honor workaround. |
| `a89c9c2b1a150d99df5dfae045acc2de` — 2 crashes | Selected event is the WorkManager initialization failure on **Android SDK Built For Arm64 / Android 7.0**, at 26 September 10:55:18 UTC. | Consistent with the initial candidate failure documented in [54-01](release-54-readiness.md#54-01-cold-scheduled-job-startup-race). Keep the raw count, but confirm artifact/variant identity before calling either event a production regression or a failure of the final fix. |
| `63049dac6e878123ae9f7c0dbb4747e0` and `6fc8014a98d42f5ebc8c8ba76cad8597` — 4 ANRs | Selected stacks contain AppWidgetHost.startListening Binder waits during onStart. | The previously open widget-start risk remains visible under newly observed IDs. Preserve the pending-RemoteViews main-thread constraint when investigating alternatives. |
| `4d05f9e74e77520b418eac3a355108f1` — 120 ANRs | Largest group, but the selected event has **no returned thread/exception stack**. | Fetch full ANR reason/variants. The retained nativePollOnce title does not identify the cause. |

Four fatal groups / **15 events** show system-server death. One additional fatal event is an OEM Process.getProcessGroup/LooperMessageSuperviser failure. These samples do not establish an app-owned cause. Other ANRs include framework/runtime, rendering, service, initialization and obfuscated app stacks; they remain pending investigation rather than being marked resolved or assigned speculative fixes.

## Build identity and evidence limits

**126 selected samples** report revision `4486d0fd399486c6dd5a48d4d35760bfdd460b89`; the ML Kit provider sample reports `adbaffef671af0bb3bc5d79527776611c77a1c77` despite version 54. One source revision/version can cover multiple local or release artifacts. The WorkManager sample has a different R8 map ID from the common current samples. Version, build stamp, R8 mapping and signed artifact identity must be considered together.

This is one selected event per issue, not every event or variant. Some returned traces explicitly omit frames, and one has no stack. The total includes telemetry with an emulator sample; it is not a certified count of production-user incidents. The [26 September release-readiness record](release-54-readiness.md) remains historical build/test evidence, not proof that these reports are resolved. Prior attempts and unsuccessful outcomes remain in the [version-53 recurrence review](release-53-recurrence.md) and older ledgers.

## Stored evidence, validation and cleanup

- Current normalized export: `crashlytics-export-v54-20260929.json` (ignored by Git).
- Raw reports, sample-verification details, recurrence comparison, import result and database checks: `validation-artifacts/firebase-v54-20260929/` (ignored by Git).
- Database: `play_reporting.db`, with version-54 snapshots and issue-specific local review notes. Existing Play rows and every older Firebase row, snapshot and triage field are preserved.
- Import 7 used `play_reporting.db.backup-20260929T133820213781Z`. The subsequent
  recurrence review verified and rotated it to the newer recovery copy
  `play_reporting.db.backup-v54-review-20260929T140708866333Z`, which also includes
  the imported v54 reports and initial triage.
- Validation: importer dry run and event-total reconciliation passed; six importer tests and four recurrence tests passed. SQLite integrity/foreign-key checks and logical comparison of all historical rows are recorded with the evidence.
- Cleanup removed **19 superseded issue-database backups** and **55 redundant export/fetch files** after verification: **74 files / 2,803,178,090 bytes (2.80 GB)**. Historical ledgers, prior fix evidence, the current export, reconciliation reports and the fresh recovery backup remain. The exact removed paths and bytes are recorded in `cleanup-manifest.json`.

No Android build, device tests, Play upload or Firebase state mutation was needed for this reporting refresh.
