# Device-testing checkpoint

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

## Resumed device pass — 2026-09-12

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

## Original checkpoint — 2026-09-11

- Created: 2026-09-11
- Resume state: source fixes are implemented; JVM tests, lint, instrumentation
  compilation, signed/minified release builds, signatures, and manifest-to-DEX
  verification pass.
- Blocker at checkpoint: `adb devices` reported no connected devices.
- Test artifacts are still versionCode 48 and are validation-only. Do not upload
  them over the published version 48.

## Goal when resuming

Validate the current crash/ANR mitigations on one emulator and one physical
device before assigning a new version code and preparing the next staged
release. A non-Honor physical device proves general compatibility only; it
cannot production-verify the HONOR NIC-LX2 OEM class-loader workaround.

## 1. Device preflight

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

## 2. Re-run the release gate if source changed

Skip this section only if the worktree is unchanged from this checkpoint.

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

## 3. Focused connected tests on each device

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

## 4. Manual regression matrix

Clear logcat before each device's run:

```sh
/home/pi/Android/Sdk/platform-tools/adb -s SERIAL logcat -c
```

### Startup, Honor factory, and WorkManager

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

### Widget ownership and OEM providers

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

### App icons and package changes

1. Repeatedly cold-load the complete app drawer with themed icons both enabled
   and disabled.
2. Switch icon packs if available and test a work profile if configured.
3. While the drawer is open, install/update/uninstall several apps in quick
   succession.
4. Confirm labels/icons eventually converge to installed package state and the
   UI remains responsive.

Pass condition: no OOM, native resource crash, missing app list, or input ANR.
One malformed app icon may be omitted rather than terminating Comfer.

### Backup and restore picker

1. Create a backup, cancel once, then create one successfully.
2. Open restore, cancel once, then select a valid backup.
   On the connected Samsung SM-A305F, locate the backup file, **long-press it
   to select it**, then use the picker's action to proceed and confirm the
   restore in Comfer. **Do not tap the file to select it**: tapping opens the
   archive. This device-specific procedure was supplied by the user.
3. If a managed physical device naturally blocks document providers, confirm
   Comfer shows `No compatible file picker is available` and remains usable.

Do not disable a system DocumentsUI package merely to force this branch.

### General UX regression

Verify the U-shaped drawer's slow drag, fling interruption, high sensitivity,
wraparound settling, app launch, long press, folders, and portrait/landscape
behavior. Also smoke-test Settings, search/contact selection, widgets,
notifications, and returning HOME.

## 5. Capture evidence before disconnecting

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

## 6. Decision gate

Proceed to a new version code only when:

- focused tests pass on both devices;
- no first-party crash or ANR appears in the manual matrix;
- normal API-34+ firmware schedules periodic wallpaper work;
- widgets survive repeated overlap/rotation without hierarchy corruption;
- app loading/package refresh stays responsive without memory failure;
- any skipped case and its reason are recorded.

After passing, increment the version code, rebuild the signed/minified APK and
AAB, repeat signature/permission/component checks, inspect the Play-generated
artifact, then use the staged telemetry gates in `FIX-PLAN.md`.

If a test fails, preserve its logs and exact reproduction steps before changing
source. Update `FIX-PLAN.md` and `play_reporting.md`; do not mark an issue
production verified from local device testing alone.
