# Version 48 crash/ANR findings and cumulative fix plan

## Inbox home gesture implementation — 2026-09-12

Implemented the one-finger down-and-return gesture in QuickListOverlay's existing
handler. A single release-time classification opens Notification Inbox; it does
not also dispatch up/down. Incomplete deliberate returns cancel. Existing
left/right, L/circle, tap, long-press and child-scroll behaviors passed the focused
regressions. NotificationIconRow remains an entry point.

Validation: 113 JVM tests pass; debug lint and isolated APK builds pass; eight
gesture/navigation tests pass on each of Samsung API 30 and emulator API 37.
See [feature evidence](validation-artifacts/device-checkpoint/2026-09-12-inbox-gesture/RESULTS.md).
Human gesture comfort/false-positive trials and accessibility/orientation review
remain distinct from automated coverage. Guide text follows the existing English
app-guide fallback; translation review remains pending before release.

The earlier device gate describes the pre-feature build. This feature's affected
checks pass, but signed/minified release artifacts must be rebuilt and checked
after feature completion. VersionCode stays 48; no production verification or
upload is implied by these results.

## Controlled namespace compatibility check — 2026-09-12

**PASS: the remaining controlled missing-`forNamespace` check is complete.**
The existing API-37 emulator passed both the healthy control and an isolated
build whose reflective method lookup deliberately fails. The missing-method
run leaves WorkManager uninitialized with no scheduled jobs while application
data initialization, MainActivity focus/recreation, and a subsequent ordinary
cold launch succeed. Both crash buffers are empty, with no fatal/Comfer ANR
signatures and only user-requested exits. The expected fallback diagnostic
appears only in the injected run.

Reproduce using `scripts/test_workmanager_compatibility.py`; see
[controlled-run evidence](validation-artifacts/device-checkpoint/2026-09-12-namespace/RESULTS.md).
Injection occurs only in a disposable copy using the isolated test package;
production application source and the installed user app are unchanged.
The local device gate stays PASS with its documented limits. Actual affected
Honor firmware, the separate post-preflight LinkageError path, and production
telemetry are not verified by this simulation. VersionCode remains 48; signed
release preparation and upload have not started.

## Latest gate update — 2026-09-12 follow-up

**PASS — local device gate, with the documented coverage limitations.**
Samsung restore now passes end to end: locate the ZIP, **long-press to select**
(a 4-second injected press succeeded), tap **Select**, then confirm **Restore**
in Comfer. Tapping the ZIP opens it and is not the selection procedure.

The original 44 focused device/method combinations passed. This follow-up adds
15 passing combinations: 12 Samsung (notification recovery, 4 integration,
4 navigation, 3 folder/transfer/drag) and 3 emulator folder/transfer/drag.
Historical failed/interrupted attempts are retained, not counted as passes.
Three test-only corrections address the reproduced viewport precondition,
Samsung background fixture-broadcast congestion, and a stale notification-open
assertion; application code is unchanged. Instrumentation builds and affected
reruns pass. See [follow-up results](validation-artifacts/device-checkpoint/2026-09-12-followup/RESULTS.md)
for exact runs, failures, reproduction steps, skipped cases and evidence.

Samsung pre-test data was restored and contacts/notification access verified.
After USB reconnection, all seven final evidence captures succeeded. The crash
buffer is empty, all 16 recorded Comfer exits are USER REQUESTED, and all seven
tracked crash signatures have zero matches in main/system logcat. MainActivity
has window focus; JobScheduler retains Comfer work. Evidence and file hashes
are in `RZ8M80E8ZPZ/final-capture-summary.json` under the follow-up directory.
All local gate requirements now have a passing result or explicit skipped-case
disposition. This is a local device-validation pass, not production verification.
Unavailable Honor firmware/work-profile/provider cases and unexecuted DND,
history, real-contact/manual-folder and perceptual smoothness coverage remain
limitations, not verified claims. Honor-specific production verification still
requires affected firmware and meaningful later-release telemetry. VersionCode
remains 48; no production build/upload is prepared by this follow-up.

- Last updated: 2026-09-12
- Package: `com.jeerovan.comfer`
- Current published evidence: versionCode 46 and 48
- Current source version: versionCode 48 / versionName 48.0
- Sources: `play_reporting.db.issues` (Google Play) and
  `play_reporting.db.crashlytics_issues` (Firebase Crashlytics)
- Crashlytics window: 2026-08-13 00:00 UTC through 2026-09-11 23:59:59 UTC
- Code baseline before this pass: Git `8961f06`

## Reading the evidence

Play and Crashlytics use different grouping, sampling, freshness, and reporting
windows. Their counts must not be added. `affected_users` is unique only inside
one issue group, so sums are issue-user counts rather than app-wide unique users.
A source fix is not a production-verified fix until a later version receives
meaningful exposure on the affected devices.

The Crashlytics MCP result is complete for fatal crashes and ANRs in the stated
version-48 window: the 206 unique issue groups and their sample events reconcile
exactly with the version report's 1,182 events. Crashlytics returned no version-46
data. Google Play remains the version-46 evidence source.

| Source/version | Crash groups | Crash events | ANR groups | ANR events |
|---|---:|---:|---:|---:|
| Play 46 | 11 | 56 | 54 | 140 |
| Play 48 | 9 | 10 | 54 | 103 |
| Crashlytics 48 | 30 | 296 | 176 | 886 |

## Current priority and disposition

| Priority | Evidence | Impact | Disposition |
|---|---|---:|---|
| P0 | Crashlytics `ca8f7f21...`: Comfer's class loader is asked to instantiate `com.hihonor.android.launcher.powersavemode.PowerSaveModeLauncher` | 208 events / 7 issue-users | Exact-name component-factory redirect implemented; production verification required |
| P0 | Crashlytics `7c080f7c...`: API-34 framework lacks `JobScheduler.forNamespace` during automatic WorkManager initialization | 14 / 2 | Automatic initializer removed; reflective platform preflight and contained manual initialization implemented |
| P1 | AndroidView/ViewGroup mutation family, led by `b9243dd5...` | At least 12 groups / 43 events | Live cached widget views are no longer removed from an outgoing parent; only detached views are reused |
| P1 | `578a53df...`: 81 MB launcher-icon allocation OOM | 3 / 2 | Icon density capped, concurrency reduced, OOM contained per app, cache evicted |
| P1 | `42e44a72...`: missing `OPEN_DOCUMENT` handler | 2 / 2 | Backup and restore picker launches now contain missing/forbidden handlers |
| P1 | `packageChanges` callback registration on main | 5 events across 3 ANR groups | Binder registration moved upstream to serialized IO while callbacks remain on main |
| P1 | Vivo clock/cleaner and alternate Honor Gallery widget packages | 3 crash groups / 3 events | Proven unsafe provider package prefixes blocked before inflation |
| P2 | Launcher two-pane measurement blamed while Minikin/ICU lays out text | 4 groups / 16 events | Monitor and reproduce with exact text/locale; no speculative layout rewrite |
| P2 | Activity launch Binder waits in `CommonUtil.handleStartActivity` | 3 groups / 4 events | Monitor; activity start must remain on main by Android contract |
| P3 | Idle main loop, HWUI/GLES/ART/libc, GMS Dynamite, dead-system failures | Majority of ANR groups | Device/OS monitoring only unless a first-party causal frame is captured |

## Corrected missing-class diagnosis

The earlier Play reports redacted the class name, so the version-46
`BaseDexClassLoader.findClass` recurrence was provisionally attributed to a
deleted `SubscriptionActivity`. The version-48 Crashlytics sample now supplies
the missing value:

`com.hihonor.android.launcher.powersavemode.PowerSaveModeLauncher`

It also shows `ComponentInfo{com.hihonor.android.launcher/...}` being loaded
from Comfer's APK path on HONOR NIC-LX2 / Android 15. Therefore:

- the retained `SubscriptionActivity` tombstone is still valid upgrade
  hardening for old Comfer tasks, but it does not close the current Honor issue;
- Automatic Protection, R8 removal, multidex, and a missing current Comfer
  manifest class do not explain this sample;
- the new `ComferAppComponentFactory` redirects only that exact OEM class name
  to a no-UI activity which immediately finishes/removes the malformed task;
- arbitrary missing activities are never redirected.

This is a targeted mitigation of an OEM cross-package/class-loader mismatch. It
cannot be called verified until the issue is absent on the affected Honor
firmware in a later release.

## Phase 1 — implemented source fixes

### 1. Honor power-save activity compatibility

- Register `ComferAppComponentFactory` in the application manifest.
- Preserve normal platform instantiation for every component except the exact
  telemetry-proven Honor power-save class.
- Return `HonorPowerSaveCompatibilityActivity` for that class only.
- Finish immediately; remove the task when it is the root.
- Pure regression tests prove near-match, unrelated, and historical Comfer
  activities are not redirected.

### 2. WorkManager startup compatibility

Crashlytics shows the process dying in AndroidX Startup before
`Application.onCreate`, so the old fallback in `ComferApp` could never catch
this failure.

- Remove only `androidx.work.WorkManagerInitializer` from the merged AndroidX
  Startup provider. Other Startup initializers remain.
- Before manual initialization, require API <34 or reflection evidence that the
  framework exposes `JobScheduler.forNamespace(String)`.
- On broken API-34 firmware, keep the launcher alive and disable only periodic
  wallpaper work for that process.
- Catch a remaining `LinkageError` around initialization for partially updated
  firmware.

AndroidX WorkManager 2.10+ intentionally uses a namespaced JobScheduler on API
34+, and current upstream code still calls the platform method. Downgrading the
whole dependency would discard unrelated fixes, so the application boundary is
guarded instead.

References:

- [WorkManager release notes](https://developer.android.com/jetpack/androidx/releases/work)
- [AndroidX namespaced JobScheduler implementation](https://android.googlesource.com/platform/frameworks/support/+/79381d77d7faeef6dd5ba316a259ab3ef42da615/work/work-runtime/src/main/java/androidx/work/impl/background/systemjob/JobSchedulerExt.kt)

### 3. Widget ownership and unsafe providers

The cached-widget path previously called `removeView` on a host view still
owned by an outgoing Compose `AndroidViewHolder`. That can mutate the old
`ViewGroup` while Android is measuring, dispatching visibility/insets, or
collecting attributes—the exact family seen in the version-48 stacks.

- Reuse a cached `AppWidgetHostView` only when `parent == null`.
- If an enter/exit overlap still owns the cached view, inflate a separate view;
  never steal the live instance.
- Never call `removeView` from the new `AndroidView.factory`.
- Continue serializing `createView` and tracking slow providers.
- Add `com.android.gallery3d`, `com.vivo.doubletimezoneclock`, and
  `com.vivo.cleanwidget` to the evidence-based provider denylist.

Tradeoff: an overlapping transition may inflate one extra RemoteViews instance.
This is preferable to corrupting a live hierarchy. The existing mutex, frame
yield, slow-provider quarantine, and detached-view cache remain.

### 4. Launcher icon memory/resource pressure

- Cap requested launcher-icon density to 160–320 dpi; a launcher icon does not
  need an xxxhdpi vendor asset merely because the display uses that density.
- Reduce complete concurrent icon jobs from four to two.
- Catch only `OutOfMemoryError` at the per-app icon boundary, clear icon cache
  state, and omit the offending app entry instead of terminating the launcher.
- Preserve cancellation and normal exception behavior.
- Keep package resource acquisition serialized.

### 5. Document picker availability

- Wrap both `CreateDocument` and `OpenDocument` launches.
- Contain `ActivityNotFoundException` and `SecurityException`.
- Show a short user-facing message and leave backup/restore state unchanged.

### 6. LauncherApps callback registration

- Apply `flowOn(packageManagerDispatcher)` to the callback flow.
- Register with a main-looper callback handler so UI-facing callback semantics
  are preserved.
- Perform the synchronous `addOnAppsChangedListener` and unregister Binder
  operations on the serialized IO dispatcher.

## Phase 2 — local verification

Resumed on 2026-09-12 on an API-37 Google emulator and API-30 Samsung SM-A305F.
All five focused instrumentation classes passed on each device. Additional
drawer/contact tests passed; device evidence covers repeated startup/focus,
API-37 namespaced ImageWorker scheduling, real-widget overlap/resize/deletion,
rotation (with an API-30 command correction), icon loading, fixture package
changes, and emulator backup/restore. See the ignored
[`device results`](validation-artifacts/device-checkpoint/2026-09-12/RESULTS.md)
for exact counts, evidence and limits.

The device decision gate is **HOLD** for the recorded coverage gaps. The user
clarified Samsung's restore procedure: locate the backup, **long-press to select
it**, then proceed. Tapping opens the archive and is not a selection failure.
The earlier tap-based result is therefore not a demonstrated restore defect;
no new end-to-end run was performed for this documentation correction.
Samsung DocumentsUI search separately recorded a `DirectoryLoader`
`DeadObjectException`. Its generated ZIP passes integrity checks; emulator
restore succeeds. No deterministic first-party code
failure was established, so no speculative source fix was made. Unexecuted
cases are recorded in the device report. VersionCode remains 48; Honor firmware
and later-release production telemetry are still required.

The resumable emulator/physical-device procedure is recorded in
[`DEVICE-TEST-CHECKPOINT.md`](DEVICE-TEST-CHECKPOINT.md). Continue there when
devices are available.

Completed on 2026-09-11:

- `testDebugUnitTest`: passed, including five new mitigation-policy tests.
- `assembleNotificationTest`: passed; the Firebase-isolated variant compiles
  and packages (this project does not define a separate unit-test task for that
  custom build type).
- `:app:assembleDebugAndroidTest`: passed; all instrumentation sources compile.
- `lintDebug`: passed with the custom factory preserving AndroidX's component
  compatibility wrapper behavior.
- Merged debug/notification-test manifests contain
  `ComferAppComponentFactory`, retain `InitializationProvider`, and omit only
  `WorkManagerInitializer`.
- No Android device was connected, so instrumentation behavior has not been
  executed in this pass.
- Signed/minified version-48 validation artifacts build successfully. The APK
  uses APK Signature Scheme v2, its 45 manifest-declared application/component
  classes all exist in DEX, and the AAB reports `jar verified`.
- R8 output inspection confirms the exact Honor class-name comparison remains
  in `ComferAppComponentFactory` and the renamed compatibility activity retains
  its finish/remove-task `onCreate` behavior.
- Validation artifact SHA-256 values: APK
  `cce19060727dd63fb5fe13279c9c3f77e11c901759d2687486c6e8a74e1ca4e2`;
  AAB `1194250bc644185a081165fa2b507f2b0e4d290976fbbd29e24dc9bc9bc4334d`.
  These artifacts remain version 48 and cannot be uploaded over the existing
  version-48 release.

Release checklist after device and controlled compatibility validation:

1. **Complete:** focused connected tests, widget overlap/edit/rotation/HOME,
   and normal API-34+ wallpaper scheduling (API-37 emulator).
2. **Complete:** isolated missing-method lookup during Application.onCreate
   skips WorkManager without preventing startup, focus, or activity recreation.
   See the controlled namespace report above; this is simulated compatibility
   evidence, not affected-OEM or production verification.
3. **Next:** increment to a new version code, rebuild signed/minified APK/AAB,
   and repeat signature, permission, component and manifest-to-DEX checks.
4. Confirm the component factory and compatibility behavior again after Play
   bundle processing before the staged release.

## Phase 3 — next release telemetry

Use a new version code; do not infer success from version 48 after source changes.

1. Start with a staged rollout that retains meaningful Honor exposure.
2. Fetch Play and Crashlytics after 24 hours and 48 hours using the same filters.
3. Track these issues/families separately:
   - `ca8f7f21...` / Play `7eed1be...`: Honor missing power-save activity;
   - `7c080f7c...`: WorkManager `forNamespace`;
   - `b9243dd5...` and the ViewGroup/AndroidView family;
   - `578a53df...` and getAppInfo resource/OOM groups;
   - `42e44a72...`: document picker;
   - `packageChanges` Binder-registration ANRs.
4. Halt expansion for any repeat of the two P0 startup/launch crashes.
5. Compare rates over equal windows and device mixes; do not compare raw totals
   from unlike exposure.

Exit criteria:

- zero P0 recurrence with meaningful exposure on the previously affected
  Honor/API-34 devices;
- no repeat of the WorkManager pre-`Application` crash;
- material reduction of AndroidView/ViewGroup and app-icon families;
- no regression in normalized user-perceived ANR/crash rate;
- widget reopen and periodic wallpaper behavior remain functional.

## Phase 4 — evidence-gated residuals

Do not add broad catches, disable Compose features, or move Android activity
launch calls off main for these stacks:

- 445 `nativePollOnce` events are grouped across seven ANR signatures. The
  sample frame is often an idle looper and does not identify the earlier work
  that caused a no-focused-window timeout.
- HWUI, GLES, ART, libc, OEM instrumentation, and dead-system stacks generally
  lack a safe first-party intervention.
- Four LauncherTwoPaneLayout groups (16 events) are actually blocked in
  Minikin/ICU text breaking/layout. Capture locale, displayed text, and a system
  trace before changing the two-pane measure policy.
- `CommonUtil.handleStartActivity` groups wait in the platform's activity-task
  Binder call. Android requires activity launch from the UI boundary.
- Compose snapshot, vector painter, pointer input, dialog transition, and
  lifecycle cleanup groups are currently isolated one/two-event samples.

Next diagnostics:

1. Add Crashlytics keys for active screen, widget provider, app count, locale,
   time-to-first-frame, and time-to-focus.
2. Capture Perfetto traces for startup/focus over 1 second and for custom widget
   inflation over 500 ms.
3. Re-rank only after a fixed build has equivalent exposure.

## Previously implemented fixes retained

These remain part of the cumulative solution and are documented in
`play_reporting.md`:

- historical `SubscriptionActivity` tombstone;
- missing WorkManager-initializer fallback (superseded by guarded manual init);
- broad storage/media permission removal;
- unsafe external activity/URI/reportFullyDrawn/sound boundaries;
- oversized widget geometry clamps;
- aligned Compose BOM;
- off-main search normalization;
- wallpaper service failure containment;
- owned/bounded bitmap rendering instead of mutable drawable painters;
- Honor/Huawei Calendar, Gallery, and weather widget guards;
- malformed proxy fallback;
- weather Binder isolation and network timeouts;
- U-shaped drawer index/key/animation fixes.
