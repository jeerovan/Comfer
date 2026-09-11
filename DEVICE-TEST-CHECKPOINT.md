# Device-testing checkpoint

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
