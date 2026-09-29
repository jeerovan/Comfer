# Version 54 recurrence review — 29 September 2026

Two current-source ANR risks are fixed and locally tested: **54-03** gates and
backgrounds tilt-service work; **54-04** backgrounds wallpaper environment
registration, reads and cleanup. Source baseline: `3cd860a` plus the changes
recorded here. The review was performed at **54 / 54.0**; the two mitigations
are now included in the [version 55 candidate](release-55-readiness.md). They
are not shipped or production-verified resolutions.

## Scope and recurrence

Review covers every group in Firebase import 7, **54.0 (54)**, for
1 September 00:00–29 September 13:34:10 UTC. The
[fetch ledger](release-54-issues.md) records validation of all selected samples.

| Exact-ID history | Groups | Events | Fatal groups / events | ANR groups / events |
| --- | ---: | ---: | ---: | ---: |
| Seen in earlier stored releases | 45 | 271 | 7 / 19 | 38 / 252 |
| Newly observed IDs | 82 | 97 | 3 / 6 | 79 / 91 |
| Total | 127 | 368 | 10 / 25 | 117 / 343 |

These are different reporting windows and unknown exposure, not comparable
release crash rates. New IDs can represent old failure families. A retained ID
can have a different exception: the Honor launcher group now samples ML Kit
provider loading. Each group has one selected event; neither its sample nor its
title represents every event/variant. The [existing inventory](release-54-issues-inventory.md)
now includes each group's prior attempts and review disposition.

## What earlier fixes did, and what is visible now

| Earlier work | Applied behavior | Version-54 observation and decision |
| --- | --- | --- |
| 50-01 / 50-02 / 50-03 / 50-05 | Bound notification drawable conversion, reject invalid drawer scale, fall back from missing dynamic palette resources, contain external URL launch failures. | Original target IDs absent in this snapshot. Retain safeguards; absence is not proof of resolution. |
| 50-04 / 51-06 | Deduplicate search using component/profile identity; capture one drawer-list snapshot for count/key/content; deduplicate restored folder entries. | New `c644da91eaa6ccd96baca2a6548b2eef`, 1 crash, has the same duplicate-key failure family and Facebook component/profile key. The current search still deduplicates. Obtain the matching mapping and a reproducer before changing key identity or Compose behavior. |
| OEM widget guard / 51-03 | Block known unsafe vendor packages, add the exact Honor weather package and filter the picker; retain an unsupported state for saved widgets. | `c48a27e73027d11418e2a4fb30f13084`, 2 crashes, repeats the v48 Honor Gallery privileged-setting exception. Both Gallery package boundaries are already blocked in revision `4486d0f` and current source. The actual bound provider/entry path is omitted. Keep the guard; do not invent another package blacklist from class names. |
| 51-01 | Keep a class at the exact Honor activity name for firmware that bypasses the component factory. | The same ID, `ca8f7f21e3ec633d0d1dca453409435b`, now has 1 missing **MlKitInitProvider** sample on Infinix/Android 8.1. It does not demonstrate recurrence of the Honor fix. |
| 51-02 / 53-08 | Apply pending widget views on Main; serialize lifecycle transitions; move stopListening to IO with per-host failure/retry handling. | Original hierarchy and four targeted stop IDs are absent. Two new startListening IDs have 4 ANRs. This remains the documented Main/Binder tradeoff. Moving the entire start to IO would reopen the view-mutation race. |
| 51-04 / 51-05 | Stream and cap wallpaper downloads; bound and serialize lazy widget-image decoding. | Target allocation failures absent. Generic GPU/GC/resource waits do not establish recurrence of those OOM defects. Retain both safeguards. |
| 51-07 | Defer/cache dictation language labels and compute outside composition. | Target ICU comparator ID absent. Keep the optimization. |
| 53-01 / 53-02 / 53-03 | Journal schema migrations/encryption conversion, exact missing-framework-method bridges, generated Room build/packaging checks. | None of the current fatal samples has the targeted migration/missing-method/Room signature. The ML Kit sample needs its delivered split APKs; it is a different packaging question. |
| 53-07 / 54-01 | Start routine WorkManager setup on IO; provide synchronized on-demand configuration for cold service entry. | New `a89c9c2b1a150d99df5dfae045acc2de`, 2 crashes, samples an API-24 emulator during initial candidate validation. The documented 54-01 fix is already in current source. Do not reapply it or call both events final-release regressions from one sample. A cold service may still initialize on Main as a correctness fallback. |
| 53-09 / 53-10 / 53-11 | Declare VIBRATE; defer launcher-service lookup; queue bounded, stale-dropping drawer sound work on IO. | The targeted IDs are absent. Retained Samsung permission, service-load, sound and lifecycle regressions pass. New sensor and receiver waits expose separate paths addressed below. |

Original attempts, failed approaches and outcomes remain in the
[v50](release-50-issues.md), [v51](release-51-issues.md),
[v53 fixes](release-53-issues.md), [v53 recurrence](release-53-recurrence.md) and
[v54 candidate](release-54-readiness.md) records. No historical attempt is erased
or silently called production verified.

## New issue review and remaining limits

The **three new fatal IDs / six events** are duplicate search keys (1), Google
certificate rejection on GoogleApiHandler (3), and cold WorkManager startup (2).
The Google certificate family was investigated in v51/v52 under a different ID;
its current obfuscated caller, Google Play services version and certificate/device
context are needed before assigning app ownership.

The **79 new ANR IDs / 91 events** include:

- **1 sensor initialization group / 1 event** and **3 receiver-registration
  groups / 3 events**: current-source mitigations below.
- **2 widget-start groups / 4 events**: retain the documented Main-thread
  RemoteViews constraint; obtain a supported separation of IPC and view application.
- **55 groups / 63 events** with obfuscated frames and no matching local mapping.
  Preserve raw evidence; do not assign those frames guessed source names.
- **3 idle native-poll groups / 3 events** and **15 framework/runtime/vendor/SDK
  groups / 17 events**: sampled stacks alone do not establish a safe app-owned repair.

Across all 127 groups, four recurring system-server-death groups account for
15 crashes, and an OEM looper/process-group failure accounts for one. The largest
ANR group has 120 events and **no returned stack**. Other traces include omitted
frames, rendering waits, resource/GC locks, provider reads, licensing and SDK
startup. A generic catch, timeout, dependency bump or moving view operations to
IO would not be an evidence-backed fix for these reports.

The common sample map ID is `acd399db1cc0985b8577e63cbee8cc4b54a5c0f6dbc3b399f09eb3c6b82dbda5`;
the WorkManager candidate sample has a different ID. Neither matches the
available local mappings inspected before this change. Most samples carry
`4486d0f`; one carries `adbaffe`. Build stamps can cover working-tree artifacts
and do not substitute for the exact mapping/signed APK. The source review and
regressions below prove the current code weaknesses; attribution of the four
obfuscated sensor/receiver samples to these exact functions remains an inference.

The existing local minified APK passed manifest-to-DEX verification: **54 manifest
classes and one dynamically loaded Honor class**, including MlKitInitProvider.
This verifies that local artifact only. It does not certify the failing user's
Play-generated splits, and it is not a release build of these new fixes.

## 54-03 — gated, asynchronous tilt sensor lifetime

- Related sample: `e2ce0f7870798b54b97ce8731fd94a2b` (1 ANR),
  SystemSensorManager initialization during a Main-thread service lookup.
- Verified defect: `rememberSpatialTilt(false, ...)` still acquired SensorManager
  and enumerated sensors. Enabled setup, listener registration and cleanup also
  ran synchronously during composition/lifecycle callbacks.
- Implementation in `spatial/SpatialTilt.kt`: acquire/register only when enabled,
  resumed and focused; perform service work on IO; explicitly deliver sensor
  callbacks to Main. Session cancellation and the current focus/lifecycle gate
  reject stale events immediately. Cleanup stays with the session, including when
  cancellation arrives during a blocking service call. The next session waits
  for prior cleanup within that lifetime; different effect lifetimes own distinct
  listeners. Use the attached view's display rotation without another service lookup.
- Missing sensors or runtime service failures leave tilt inactive. Main remains
  responsive while service work is delayed; a stuck service may delay cleanup,
  but cannot resume a disposed session or block UI work. No sensor timeout is
  presented as a mechanism for interrupting an uninterruptible Binder call.

## 54-04 — wallpaper environment work off Main, with ordered cleanup

- Related samples: `00baac71e41e37f8c8792a12ce8d8a8e`,
  `7c5f00b78e169161733a66571ec4d659`, `a831744124e312ad2849ed21680bd903`
  (1 ANR each), Main-thread receiver registration through obfuscated callers.
- Verified defect: `rememberWallpaperActive` acquired power state, registered a
  content observer and screen receiver, read animator settings and unregistered
  them synchronously in composition/lifecycle callbacks. Earlier battery and
  clock registration changes did not cover this wallpaper path.
- Implementation in `spatial/WallpaperMotion.kt`: a callback flow performs setup,
  service/settings reads and teardown on IO. Callbacks enqueue conflated refresh
  requests. Lifecycle/focus state and Compose state remain on Main. Motion starts
  conservatively inactive until the environment is known; pause cancels the
  subscription and resume reads fresh state.
- A single try/finally owns successful registrations, so disposal during slow
  setup cannot unregister too early and leak a late receiver. Partial setup is
  cleaned up; cancellation propagates. Runtime service failure leaves motion off,
  and the next subscription can recover. Reduced-motion behavior is retained.

Android's [Compose performance guidance](https://developer.android.com/codelabs/jetpack-compose-performance)
recommends moving receiver registration off Main. The
[SensorManager handler overload](https://developer.android.com/reference/android/hardware/SensorManager#registerListener(android.hardware.SensorEventListener,android.hardware.Sensor,int,android.os.Handler))
allows registration elsewhere while delivering callbacks to the Main handler.

## Validation and follow-up

- **Pre-fix Samsung:** two regressions failed for the intended reasons: disabled
  motion initialized sensors, and wallpaper receiver registration ran on Main.
- **246 JVM tests passed**, zero failures/errors/skips; isolated test APK and
  instrumentation APK builds passed. No version bump or production-app reinstall.
- **Samsung SM-A305F / Android 11 only:** 32 distinct instrumentation tests passed
  across the service, local/cloud wallpaper, v50/v51/v53 and widget-guard suites.
  Service cases cover disabled/missing sensors, a blocked service with responsive
  Main, disable/dispose during setup, cancellation of late receiver registration,
  failure/recovery, and off-main setup/cleanup. The six focused service tests are
  repeated against the final cancellation guards; see retained results.
- Motion gate, pause/resume and live reduced-motion changes were explicitly
  exercised in **English/LTR and Arabic/RTL**. The original animator setting is
  restored. Existing localized wallpaper controls/rendering checks also pass.
  There are no label or geometry changes; system-language/app-picker equivalence
  was not re-run for this service-lifetime change.
- The isolated `.notificationtest` app preserves production data. No emulator
  tests were run. Actual Honor/Infinix/Tecno firmware, failing Play splits, exact
  production ANR duration and final minified/signed rollout remain unverified.
- `play_reporting.db` appends review notes to all 127 v54 rows and promotes pending
  rows to investigating. Earlier rows/triage, all telemetry fields, snapshots and
  import runs are preserved. Nothing is marked resolved in Firebase.
- Evidence: `validation-artifacts/v54-recurrence-20260929/`, including baseline
  failures, final test logs, per-issue `triage.json`, source/APK hashes and database
  verification. Recovery backup: `play_reporting.db.backup-v54-review-20260929T140708866333Z`.

Before release, record the new version, exact signed artifact/mapping and source
revision. After rollout, compare matching variants/signatures at 24/48 hours and
seven days with device/OS exposure. These two fixes address current code paths
related to **four groups / four events**; that is not a count of incidents proven
prevented, and the remaining 123 groups / 364 events have no new fix claimed here.
