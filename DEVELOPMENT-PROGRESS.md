# Development progress

This is the single development document for ongoing progress, verification
procedures, remediation details, release gates and production evidence. Record
new work here; do not create separate checkpoint, fix-plan or reporting-status
files. Superseded outcomes remain labelled history.

- [Current state](#current-state)
- [Device verification procedure](#device-verification-procedure)
- [Crash remediation and release requirements](#crash-remediation-and-release-requirements)
- [Production reporting and issue ledger](#production-reporting-and-issue-ledger)

Detailed reports under `validation-artifacts/` retain exact runs and evidence.
They are ignored local artifacts and may be unavailable in a fresh checkout.
Historical validation reports, feature guides, script instructions, the project
README and license retain their separate purposes.

## Current state

As of 2026-09-12, more features are being completed before the next release.
VersionCode remains 48; no production upload is prepared and no local result
marks an issue production-verified. The pre-feature local device gate passed
with recorded limitations; that historical pass does not verify the final
feature-complete release artifact.

Next: review gesture comfort/false positives, accessibility/orientation and guide
translations; assess launch animation behavior and the untested cases in its
report. After feature completion, rerun affected checks and the release decision
gate, then rebuild/verify signed and minified artifacts before release preparation.
Affected Honor firmware and meaningful later-release telemetry remain required
for Honor-specific production verification.

## App launch expansion — 2026-09-12

Icon taps and the U-shaped drawer's centre-app double tap request Android's
scale-up transition from the rendered icon bounds. Window/source coordinates and
the centre-point offset are corrected; invalid/detached geometry falls back to
a normal launch. A full live-window reverse-to-icon animation is not implemented:
Android restricts remote animation control to privileged/system Recents components.
Return animations remain system/OEM controlled.

Build and lint passed; 116 JVM tests passed. All 11 focused drawer tests passed
on the API-37 emulator. Samsung API 30 initially passed nine; two lost their
Compose hierarchy when the screen timed out and passed unchanged after waking.
This is 11 distinct passing Samsung cases, not a clean single suite run.
The updated build is installed on both devices. Camera launches on the emulator
and Clock launches on Samsung were visually checked, including Back/HOME returns.
Both crash buffers were empty; captured logs showed no Comfer ANR.

Warm-task animation, actual centre-double-tap visuals, all placements,
rotation/split screen, navigation modes, disabled animations and OEM overrides
remain unverified in this pass. See [exact animation results and limitations](validation-artifacts/device-checkpoint/2026-09-12-app-launch-animation/RESULTS.md).

## Inbox slide-up appearance — 2026-09-12

The shared Inbox opening method supplies a 300 ms decelerating bottom-to-top
animation with a stationary outgoing window. Row and gesture entry both use it.
Debug build/lint and instrumentation compilation passed; the real home-gesture
→ Inbox → Back test passed on Samsung (1/1). The in-place Samsung update preserved
data. Row-tap recording verified movement; existing FLAG_SECURE blacked out the
Inbox content. An earlier slow shell gesture opened settings and is not counted
as a pass. See [slide-up evidence](validation-artifacts/device-checkpoint/2026-09-12-inbox-gesture/RESULTS.md#follow-up-inbox-slide-up-appearance).

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

## Pre-feature device gate — 2026-09-12 follow-up (historical)

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

## Initial device pass — 2026-09-12 (superseded by follow-up)

The focused tests were executed successfully on `emulator-5554` (Google
sdk_gphone16k_arm64, API 37) and `RZ8M80E8ZPZ` (Samsung SM-A305F, API 30).
Detailed results, reproduction steps, reports, screenshots, and system evidence
are in the ignored local report:
[`validation-artifacts/device-checkpoint/2026-09-12/RESULTS.md`](validation-artifacts/device-checkpoint/2026-09-12/RESULTS.md).

The device decision gate remains **HOLD** for the recorded coverage gaps.
User clarification: on this Samsung, locate the backup, **long-press to select
it**, then proceed. Tapping opens the archive; it does not select it for restore.
The earlier tap-based result is not evidence of a restore defect. No new
end-to-end restore run was performed when recording this clarification.
No application source changed, no issue became production verified, and
versionCode remains 48. The original procedure below remains the resume guide;
do not repeat completed checks without a new change or unresolved concern.

Follow-up: the user enabled Samsung notification access, and adb confirmed
Comfer's listener is enabled at 2026-09-12 03:56:32 UTC. Live notification/DND
validation remains outstanding; this permission check does not change the gate.

## Original source/build checkpoint — 2026-09-11 (historical)

- Created: 2026-09-11
- Resume state: source fixes are implemented; JVM tests, lint, instrumentation
  compilation, signed/minified release builds, signatures, and manifest-to-DEX
  verification pass.
- Blocker at checkpoint: `adb devices` reported no connected devices.
- Test artifacts are still versionCode 48 and are validation-only. Do not upload
  them over the published version 48.

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

Additional initial-pass observations: manual coverage included startup/focus,
API-37 namespaced ImageWorker scheduling, widget overlap/resize/deletion, rotation
(with an API-30 command correction), icon loading, fixture package changes and
emulator backup/restore. Samsung DocumentsUI search logged a DirectoryLoader
DeadObjectException; the generated ZIP passed integrity checks, and no deterministic
Comfer defect was established. The reporting database and production evidence
were not refreshed or modified during device testing. Later successful Samsung
restore and final evidence capture are recorded in the follow-up above.

## Device verification procedure

### Goal when resuming

Validate the current crash/ANR mitigations on one emulator and one physical
device before assigning a new version code and preparing the next staged
release. A non-Honor physical device proves general compatibility only; it
cannot production-verify the HONOR NIC-LX2 OEM class-loader workaround.

### 1. Device preflight

Start the emulator, connect/unlock the physical device, authorize USB debugging,
and keep both awake. Then record their serials and properties:

```sh
/home/pi/Android/Sdk/platform-tools/adb devices -l
/home/pi/Android/Sdk/platform-tools/adb -s SERIAL shell getprop ro.product.manufacturer
/home/pi/Android/Sdk/platform-tools/adb -s SERIAL shell getprop ro.product.model
/home/pi/Android/Sdk/platform-tools/adb -s SERIAL shell getprop ro.build.version.sdk
/home/pi/Android/Sdk/platform-tools/adb -s SERIAL shell getprop ro.build.fingerprint
```

Preferred coverage:

- emulator: API 34 or newer, because of the WorkManager compatibility change;
- physical device: the user's normal device with real installed apps/widgets;
- if either device is Honor/Huawei, record its exact firmware and prioritize the
  Honor checks below.

Run tests on one serial at a time. If Gradle sees both devices, set
`ANDROID_SERIAL=SERIAL` for that command.

### 2. Re-run the release gate if source changed

Use DEVELOPMENT-PROGRESS.md to identify the last validated source state.
Rerun these checks after relevant source changes.

```sh
venv/bin/python -m unittest discover -s scripts/tests -v
./gradlew :app:testDebugUnitTest :app:assembleNotificationTest \
  :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease \
  :app:bundleRelease
venv/bin/python scripts/verify_apk_components.py \
  app/build/outputs/apk/release/app-release.apk \
  --apkanalyzer /home/pi/Android/Sdk/cmdline-tools/latest/bin/apkanalyzer
```

Expected: every command passes. The current release APK verifier should report
45 manifest-declared application/component classes.

### 3. Focused connected tests on each device

Start with the tests nearest to the fixed issue families:

```sh
ANDROID_SERIAL=SERIAL ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jeerovan.comfer.WidgetInflationGuardTest
ANDROID_SERIAL=SERIAL ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jeerovan.comfer.AppIconLoadingTest
ANDROID_SERIAL=SERIAL ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jeerovan.comfer.AppRefreshBurstStressTest
ANDROID_SERIAL=SERIAL ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jeerovan.comfer.SubscriptionActivityCompatibilityTest
ANDROID_SERIAL=SERIAL ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jeerovan.comfer.Wallpaper8kStressTest
```

`Wallpaper8kStressTest` changes the wallpaper temporarily and restores the
original; run it only where that is acceptable. After the focused tests pass,
run the full connected suite if the device permissions and notification-test
fixtures are configured:

```sh
ANDROID_SERIAL=SERIAL ./gradlew :app:connectedDebugAndroidTest
```

Record pass/fail, device serial/model/API, and the HTML report under
`app/build/reports/androidTests/connected/`. Do not treat a compiled test as an
executed test.

### 4. Manual regression matrix

Clear logcat before each device's run:

```sh
/home/pi/Android/Sdk/platform-tools/adb -s SERIAL logcat -c
```

#### Startup, Honor factory, and WorkManager

1. Cold-start Comfer at least 20 times, alternating launcher launch, HOME, and
   force-stop/relaunch. There must be no startup crash or blank/no-focus window.
2. On API 34+, confirm logs do not contain
   `Periodic wallpaper work disabled` on a normal framework and verify automatic
   wallpaper work remains scheduled/functional.
3. Check JobScheduler state:

   ```sh
   /home/pi/Android/Sdk/platform-tools/adb -s SERIAL shell dumpsys jobscheduler
   ```

4. On Honor/Huawei only, exercise power-saving mode, HOME/Recents restoration,
   reboot, and upgrade-from-published-version flows. The exact
   `PowerSaveModeLauncher` crash must not recur. A standard emulator cannot prove
   this OEM behavior.

#### Widget ownership and OEM providers

1. Add real widgets on the left and right custom-widget screens.
2. Open/close each screen rapidly at least 20 times so enter/exit animations
   overlap.
3. Rotate during transitions at least 10 times, enter/exit edit mode, resize and
   reposition widgets, press HOME, and relaunch.
4. Delete a widget and confirm it does not reappear from the cache.
5. Confirm supported widget content still updates. Known blocked Honor/Vivo
   providers should show the unsupported-provider UI rather than inflate.

Pass condition: no `FrameLayout`, `ViewGroup`, `AndroidViewHolder`, visibility,
insets, or measurement NPE; no widget-inflation ANR; no view duplicated between
screens.

#### App icons and package changes

1. Repeatedly cold-load the complete app drawer with themed icons both enabled
   and disabled.
2. Switch icon packs if available and test a work profile if configured.
3. While the drawer is open, install/update/uninstall several apps in quick
   succession.
4. Confirm labels/icons eventually converge to installed package state and the
   UI remains responsive.

Pass condition: no OOM, native resource crash, missing app list, or input ANR.
One malformed app icon may be omitted rather than terminating Comfer.

#### Backup and restore picker

1. Create a backup, cancel once, then create one successfully.
2. Open restore, cancel once, then select a valid backup.
   On the connected Samsung SM-A305F, locate the backup file, **long-press it
   to select it**, then use the picker's action to proceed and confirm the
   restore in Comfer. **Do not tap the file to select it**: tapping opens the
   archive. This device-specific procedure was supplied by the user.
3. If a managed physical device naturally blocks document providers, confirm
   Comfer shows `No compatible file picker is available` and remains usable.

Do not disable a system DocumentsUI package merely to force this branch.

#### General UX regression

Verify the U-shaped drawer's slow drag, fling interruption, high sensitivity,
wraparound settling, app launch, long press, folders, and portrait/landscape
behavior. Also smoke-test Settings, search/contact selection, widgets,
notifications, and returning HOME.

### 5. Capture evidence before disconnecting

For each serial, retain these outputs under an ignored
`validation-artifacts/device-checkpoint/DEVICE/` directory:

```sh
/home/pi/Android/Sdk/platform-tools/adb -s SERIAL logcat -b crash -d
/home/pi/Android/Sdk/platform-tools/adb -s SERIAL logcat -b main -b system -d
/home/pi/Android/Sdk/platform-tools/adb -s SERIAL shell dumpsys activity exit-info com.jeerovan.comfer
/home/pi/Android/Sdk/platform-tools/adb -s SERIAL shell dumpsys jobscheduler
```

Also retain the connected-test report and note which manual cases were actually
run. Search logs for `FATAL EXCEPTION`, `ANR in com.jeerovan.comfer`,
`PowerSaveModeLauncher`, `forNamespace`, `OutOfMemoryError`, `AndroidViewHolder`,
and `Periodic wallpaper work disabled`.

### 6. Decision gate

Proceed to a new version code only when:

- focused tests pass on both devices;
- no first-party crash or ANR appears in the manual matrix;
- normal API-34+ firmware schedules periodic wallpaper work;
- widgets survive repeated overlap/rotation without hierarchy corruption;
- app loading/package refresh stays responsive without memory failure;
- any skipped case and its reason are recorded.

After passing, increment the version code, rebuild the signed/minified APK and
AAB, repeat signature/permission/component checks, inspect the Play-generated
artifact, then use the [staged telemetry gates](#phase-3--next-release-telemetry).

If a test fails, preserve its logs and exact reproduction steps before changing
source. Record the result in the progress section above. Update the remediation
and production-evidence sections only when their underlying findings change.
Do not mark an issue production verified from local device testing alone.

## Crash remediation and release requirements

- Last updated: 2026-09-12
- Package: `com.jeerovan.comfer`
- Current published evidence: versionCode 46 and 48
- Current source version: versionCode 48 / versionName 48.0
- Sources: `play_reporting.db.issues` (Google Play) and
  `play_reporting.db.crashlytics_issues` (Firebase Crashlytics)
- Crashlytics window: 2026-08-13 00:00 UTC through 2026-09-11 23:59:59 UTC
- Code baseline before this pass: Git `8961f06`

### Reading the evidence

Play and Crashlytics use different grouping, sampling, freshness, and reporting
windows. Their counts must not be added. `affected_users` is unique only inside
one issue group, so sums are issue-user counts rather than app-wide unique users.
A source fix is not a production-verified fix until a later version receives
meaningful exposure on the affected devices.

The Crashlytics MCP result is complete for fatal crashes and ANRs in the stated
version-48 window: the 206 unique issue groups and their sample events reconcile
exactly with the version report's 1,182 events. Crashlytics returned no version-46
data. Google Play remains the version-46 evidence source.

See the [stored production evidence](#evidence-currently-stored) for provider totals.

### Current priority and disposition

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

### Corrected missing-class diagnosis

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

### Phase 1 — implemented source fixes

#### 1. Honor power-save activity compatibility

- Register `ComferAppComponentFactory` in the application manifest.
- Preserve normal platform instantiation for every component except the exact
  telemetry-proven Honor power-save class.
- Return `HonorPowerSaveCompatibilityActivity` for that class only.
- Finish immediately; remove the task when it is the root.
- Pure regression tests prove near-match, unrelated, and historical Comfer
  activities are not redirected.

#### 2. WorkManager startup compatibility

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

#### 3. Widget ownership and unsafe providers

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

#### 4. Launcher icon memory/resource pressure

- Cap requested launcher-icon density to 160–320 dpi; a launcher icon does not
  need an xxxhdpi vendor asset merely because the display uses that density.
- Reduce complete concurrent icon jobs from four to two.
- Catch only `OutOfMemoryError` at the per-app icon boundary, clear icon cache
  state, and omit the offending app entry instead of terminating the launcher.
- Preserve cancellation and normal exception behavior.
- Keep package resource acquisition serialized.

#### 5. Document picker availability

- Wrap both `CreateDocument` and `OpenDocument` launches.
- Contain `ActivityNotFoundException` and `SecurityException`.
- Show a short user-facing message and leave backup/restore state unchanged.

#### 6. LauncherApps callback registration

- Apply `flowOn(packageManagerDispatcher)` to the callback flow.
- Register with a main-looper callback handler so UI-facing callback semantics
  are preserved.
- Perform the synchronous `addOnAppsChangedListener` and unregister Binder
  operations on the serialized IO dispatcher.

### Phase 2 — local verification

Use [current progress](#current-state) for executed results, artifact
identities and outstanding work. Follow the [device verification procedure](#device-verification-procedure)
for test order, manual coverage and the decision gate.

Release requirements after feature completion:

1. Pass the device decision gate and document skipped cases and limitations.
2. Validate normal API-34+ scheduling and controlled missing-method startup.
3. Once the gate passes, assign the next version code, rebuild signed/minified
   APK/AAB, and repeat signature, permission, component and manifest-to-DEX checks.
4. Confirm component-factory compatibility after Play bundle processing before
   the staged release. Local validation does not establish production verification.

### Phase 3 — next release telemetry

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

### Phase 4 — evidence-gated residuals

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

### Previously implemented fixes retained

These remain part of the cumulative solution; see the
[retained-fix ledger](#previously-fixed-and-retained):

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

## Production reporting and issue ledger

Database: local, ignored `play_reporting.db`.

### Evidence currently stored

The database keeps Play and Crashlytics in separate tables because the two
systems group and sample failures differently. Their event and user counts must
not be added together.

| Provider/version | Crash groups | Crash events | ANR groups | ANR events |
|---|---:|---:|---:|---:|
| Google Play 46 | 11 | 56 | 54 | 140 |
| Google Play 48 | 9 | 10 | 54 | 103 |
| Crashlytics 48 | 30 | 296 | 176 | 886 |

The complete Crashlytics import covers 2026-08-13 through 2026-09-11 and
contains 206 issue groups / 1,182 events. Crashlytics returned no version-46
data, so Play remains the evidence source for that release. `affected_users`
is unique only within an issue group; summing it does not produce a unique
app-wide audience.

### Fix ledger

Status meanings:

- **Production verified**: a later release has meaningful affected-device
  exposure without recurrence, or a directly equivalent production check.
- **Source fixed**: code and local tests exist, but the fix needs telemetry from
  a new version.
- **Mitigated/monitor**: the app contains a safe boundary, but the sampled stack
  does not support calling the whole family resolved.

#### Source fixed in the current worktree

| Issue/family | Evidence | Implemented change | Verification still needed |
|---|---|---|---|
| Honor `PowerSaveModeLauncher` class not found | Crashlytics `ca8f7f21...`, 208 events / 7 issue-users; overlaps Play `7eed1be...` | Exact-name `AppComponentFactory` redirect to a no-UI task tombstone; unrelated names still fail normally | A new version on HONOR NIC-LX2/affected firmware |
| WorkManager `JobScheduler.forNamespace` missing | Crashlytics `7c080f7c...`, 14 / 2 | Disable only WorkManager-backed wallpaper scheduling when API-34 firmware lacks the method; remove automatic initializer so the check runs first | Normal API-34 scheduling plus affected/broken firmware startup |
| AndroidView/ViewGroup NPE family | At least 12 Crashlytics groups / 43 events, led by `b9243dd5...` | Cached widget views are reused only after detach and are never stolen from an outgoing parent | Connected overlap/rotation/reopen test and new-version telemetry |
| Launcher-icon OOM/resource pressure | `578a53df...`, 3 / 2; sample requests about 81 MB | Density clamp, two-job limit, cache eviction and per-app OOM containment | Large/vendor icon stress test and telemetry |
| Missing/blocked document picker | `42e44a72...`, 2 / 2 | Backup/restore launcher contains `ActivityNotFoundException` and `SecurityException` | Device without a documents provider/device-policy test |
| LauncherApps callback registration ANRs | Three groups / 5 events | Listener Binder registration/unregistration moved off main; callbacks explicitly delivered on main | Package-install/remove stress test |
| Proven unsafe OEM widget providers | Honor Gallery plus Vivo clock/cleaner one-event groups | Added exact provider-package guards before RemoteViews inflation | Affected OEM/device telemetry |

These rows are intentionally not marked production verified: the fixes have not
shipped under a new version code yet.

#### Previously fixed and retained

| Area | Retained fix | Current interpretation |
|---|---|---|
| Historical `SubscriptionActivity` removal | Non-exported, no-history tombstone retained for upgrade/restored-task compatibility | Useful hardening, but it is **not** the current Honor fix; Crashlytics revealed the missing name is the OEM power-save activity |
| Broad media permissions | `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` removed from all merged manifests/artifacts | Resolved the v43 Play policy rejection; keep artifact checks in release verification |
| Manifest/DEX mismatch risk | `scripts/verify_apk_components.py` validates application, component factory, activities, services, receivers and providers against defined DEX classes | Release gate, not evidence that an OEM can never supply a malformed component record |
| Widget geometry | Invalid/inverted bounds and oversized dimensions clamped and tested | No current v48 sample justifies reopening this fix |
| External intents, URIs and framework boundaries | Unsafe launch, URI, `reportFullyDrawn`, sound and malformed-proxy paths guarded | Retain; absence from a finite report window is supporting evidence, not proof |
| Wallpaper/background work | Service failures contained; missing automatic initialization previously had a fallback | The new pre-initialization API check supersedes the older WorkManager fallback for `forNamespace` failures |
| Images/drawables | App-owned bounded bitmaps replace unsafe mutable drawable painting paths | Retained; the new launcher-icon allocation limit is additional protection |
| OEM widgets/weather | Honor/Huawei Calendar, Gallery and weather guards plus Binder/network isolation | Retained and expanded using the v48 provider evidence |
| Search and drawer | Search normalization moved off main; U-shaped drawer occurrence keys and per-event drag animation restored | No current report identifies these paths as a new first-party regression |

#### Mitigated or awaiting better evidence

- Seven `nativePollOnce` ANR groups contain 445 events, but representative main
  stacks are commonly idle and do not identify preceding first-party work.
- Launcher two-pane/Minikin text measurement has four groups / 16 events. Log
  locale and displayed text and capture a system trace before changing layout.
- Activity-launch Binder waits have three groups / 4 events. Android activity
  launch belongs on the UI boundary; moving it to a worker would not be a valid
  fix.
- HWUI/GLES/ART/libc, GMS Dynamite, OEM instrumentation and dead-system samples
  remain monitoring items unless a first-party causal frame is captured.

### Repeatable database checks

```sql
SELECT version_code, type, COUNT(*) AS groups, SUM(events) AS events
FROM issues
WHERE version_code IN (46, 48)
GROUP BY version_code, type;

SELECT version_code, type, COUNT(*) AS groups, SUM(events) AS events
FROM crashlytics_issues
WHERE version_code IN (46, 48)
GROUP BY version_code, type;
```

Operational import instructions are in
[`scripts/README-play-reporting.md`](scripts/README-play-reporting.md).
