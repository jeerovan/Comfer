# Release 58 implementation plan

Requirements: [PRD-RELEASE-58.md](PRD-RELEASE-58.md).

| ID | Task and completion criteria | Status |
| --- | --- | --- |
| R58-01 | Fetch/reconcile the latest Firebase 57 issues; import into `play_reporting.db` with previous evidence preserved. | completed |
| R58-02 | Compare recurring/new issues with current code and prior attempts; implement reliable fixes with meaningful regressions; archive decisions in the database. | completed |
| R58-03 | Pass host checks, affected regressions and full release layout/localization checks, including system/app locale sources, LTR/RTL and portrait/landscape; restore device configuration/data. | completed |
| R58-04 | After the decision gate passes, assign 58.0 (58), build/validate signed minified APK/AAB and prepare release notes and artifact identities. | completed |

Current context: source base `a9e2b04`; Samsung A30 and one emulator are connected.
The existing `PRD-SYNC.md` belongs to separate work and is unchanged.

Validation: Firebase import 15 reconciles 254 groups / 900 events (38 fatal,
862 ANR), ending 22 October 2026 01:10:32 UTC; all raw events archived and prior
history/triage verified unchanged. Auxiliary trend reports contain late ingestion
differences recorded in the database. Missing accessibility field regression
reproduced `NoSuchFieldError`; the narrow 58-R01 fallback passes build-logic tests.
App host tests: 272 passed. Translation check: 34 locales, 1,036 resources, no
errors. Build-logic tests: 21 passed; Python checks: 57 passed. Release lint: no
errors. The pre-candidate gate passed on 22 October at 01:47 UTC, covering Samsung
A30/API 30, API 24 and API 37/16 KB emulators. Real English/Arabic system and app
language sources passed all four orientation cells; focused landscape, notification,
wallpaper, sensor, widget, startup and live cloud-asset checks passed. Samsung's
regular APK/data, language, rotation and window-animation settings were preserved;
Home/Recents return was verified. Temporary test grants/fixtures were removed.

Limitations and initial failures are archived in the database/evidence. English-only
fixtures were routed to English; locale-aware tests cover real Arabic. Strict
one-minute timers pass with precise-alarm access; the existing warned inexact
fallback can be delayed by Android. The extended Doze probe is now opt-in.
Real local ML-model download and the missing-namespace mutation harness were not
rerun. Production resolution and Google Play delivery remain unverified.

Version 58 artifacts are in `app/release/58/`, with checksums, source identity,
mapping and native symbols. Signatures match version 57; manifest permissions are
unchanged, all components are in DEX, the minified field guard has a real caller,
APK/AAB code and native libraries match, and all 17 native libraries support 16 KB
alignment. The exact signed APK passed a fresh API 37 install, Home-to-module
navigation, and an in-place API 24 upgrade preserving UID and saved Arabic locale.
Emulator Home defaults were restored. Nothing was uploaded to Google Play.
