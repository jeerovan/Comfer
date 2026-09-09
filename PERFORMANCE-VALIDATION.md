# Comfer Performance Validation

## Automated harness

Build benchmark APKs:

```bash
./gradlew :app:assembleBenchmark :macrobenchmark:assembleBenchmark
```

Run app instrumentation independently (the aggregate command also selects the macrobenchmark debug variant):

```bash
./gradlew :app:connectedDebugAndroidTest
```

Run connected macrobenchmarks on an unlocked device where Comfer can be launched:

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

For harness smoke validation only on an emulator, suppress the emulator accuracy guard. Never use those numbers as release performance evidence:

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

Implemented scenarios:

- five cold startups with startup and frame metrics;
- five warm startups with startup and frame metrics;
- five app-drawer open/close frame measurements.
- five search open/type/delete/close frame measurements using stable accessibility selectors.

Startup scenarios intentionally use only `StartupTimingMetric`; frame metrics are captured by interaction scenarios. Some OEM/API traces omit RenderThread slices during startup, which makes a combined frame metric reject valid startup timing data.

Perfetto exposes these app trace tracks:

- `startupInitialization`;
- `appRefresh:<generation>` and `activeAppRefresh`;
- `wallpaperPipeline` and `activeWallpaper`;
- `notificationSync` and `activeNotificationSync`;
- `contactsQuery` and `activeContactQuery`;
- `widgetInflate:<provider>`.

## Stress matrix

Run on minimum-supported API 24, target API 36, and one physical 2–4 GB low-RAM device.

1. Install at least 300 launcher activities, including work-profile duplicates.
2. Restore ten third-party widgets. Include one deliberately slow RemoteViews provider.
3. Import 10,000 contacts with phone numbers.
4. Post/remove 100 notifications within one second.
5. Apply an 8K image below 25 MiB, then attempt one source above 25 MiB.
6. Drag each settings slider continuously for 10 seconds.
7. Trigger 50 package add/change/remove callbacks.

Capture:

- Perfetto `sched`, `freq`, `binder_driver`, `view`, `wm`, `am`, `dalvik`, and app trace tracks;
- startup time-to-initial-display and time-to-full-display;
- slow/frozen frame percentages;
- peak Java/native/graphics heap and GC pause totals;
- active-operation counters, which must never exceed one;
- Android vitals ANR/crash/OOM rates after staged rollout.

## Pass gates

- No application-owned main-thread slice above 100 ms during steady interaction.
- No application-owned startup/widget-restoration slice above 500 ms.
- Active refresh, wallpaper, contact query, and notification sync counters stay at one.
- At most two notification system queries for a one-second 100-event burst.
- Wallpaper peak heap delta stays at or below 32 MiB.
- 60 Hz flows have at most 5% slow frames and 1% frozen frames.
- Oversized wallpaper fails cleanly; no partial source remains.
- Slow widget becomes quarantined after two ≥1 s inflations and can be manually retried.

## Rollout gate

Use an internal track first, then 5%, 25%, 50%, and 100%. Stop rollout if user-perceived
ANR rate, crash rate, or OOM rate rises by more than 10% relative to the previous stable
version, or if any migration/data-loss regression appears. Roll back to the previous APK
and retain Perfetto/Android-vitals artifacts with release annotations.


## U-shaped drawer gesture correction — 2026-09-09

### Samsung native icon-loading crash — 2026-09-09

The Samsung API 30 crash buffer recorded SIGSEGV in `AssetManager2::FindEntryInternal`, reached through `PackageItemInfo.loadIcon` → `LauncherActivityInfo.getBadgedIcon(0)` → `getAppInfo`. This occurred in background icon acquisition. The loader now supplies display density, preferring direct density-specific resource loading instead of forcing the default-icon fallback. Profile badging and serialized acquisition are retained. Android may still fall back if a density-specific resource cannot be loaded; this is a targeted workaround for the observed native path, not proof that every firmware resource crash is eliminated.

Validation: debug build passed. On Samsung, the added AppIconLoadingTest passed three cold-cache passes over installed launcher activities, in batches of four, checking non-null icons, labels, components and users. Installed the updated APK in place, preserving data. This stress test did not reproduce a native crash.

The old drawer launched independent stop, per-move snap and fling coroutines against one Animatable. Its extra 4 px per-event axis threshold could also ignore a slow drag after Android touch slop had already been crossed. The replacement UShapeScrollState owns one settling job; touch cancellation and drag updates are synchronous. Initial-pass touch observation also brakes motion when a child icon receives the tap, without consuming the tap. Axis selection uses accumulated movement at touch slop, current speed applies to drag and release velocity, and the release timestamp prevents a stale fling after a pause. Offset normalization happens only at rest, preserving icon occurrence identities during wraparound drags.

Validation: six existing UShapedAppListLayoutTest JVM tests passed. Seven instrumentation checks (UShapeScrollGestureTest and SensitivityDialogLayoutTest) passed on API 24 emulator and Samsung API 30: slow one-pixel moves, catching/reversing a fling, live speed changes, icon taps/double taps/long press/vertical dismissal, folder/dialog interruption, paused release and final snapping/wrapping. Build and lint passed. The updated debug APK was installed on Samsung; repeated reversals and a slow swipe were also checked in the actual drawer. Temporary test APKs were removed and the wake preference restored. These checks establish gesture correctness; they do not constitute a new measured frame-time/Perfetto benchmark.


### Sensitivity controls speed and coasting duration

The existing 0.1–3.0 sensitivity setting now scales both drag/release speed and coasting duration. Exponential-decay friction is divided by sensitivity, preserving the baseline at 1x, increasing deceleration below 1x and reducing it above 1x. Final icon snapping and immediate touch braking remain unchanged. A controlled-clock regression compares initial travel and total settling time at 0.5x, 1x and 3x, then interrupts a high-sensitivity fling. All eight drawer/speed-dialog instrumentation tests passed on emulator API 24 and Samsung API 30; build and lint passed. Installed the updated debug app on Samsung and removed temporary instrumentation.


### Current drawer motion: bounded speed for identifying apps

This profile supersedes the earlier uncapped sensitivity/friction experiment. The objective is selecting the desired center app while visually following nearby icons. Inspired by Apple's guidance for brief, precise and purposeful motion (https://developer.apple.com/design/human-interface-guidelines/motion), the chosen ceiling is an experimental product tuning, not a universal human-perception threshold.

One app position is 20 logical units. User-selected tuning uses a global speed multiplier of `8f`, a drag gain of `.05f`, and a release-velocity gain of `.3f`. Dragging therefore moves 0.4 logical units per horizontal pixel at 1x sensitivity, while a fling retains the stronger release response. Sensitivity scales both inputs. The absolute speed ceiling is 480 units/second (24 app positions/second), with 1x capped at 320 units/second. At 2.99x the ceiling is 479.2 units/second.

Coast duration is `1.4f + 1.0f * setting` seconds: 2.4 seconds at 1x and 4.39 seconds at 2.99x. Non-coasting centering retains its `.22f` minimum and existing timing limit. These values supersede the earlier three-times-speed experiment. For future tuning, change the drag gain independently of release velocity to preserve precise thumb positioning without weakening flings.

Validation: the earlier full nine-test gesture suite passed on the API 24 emulator. After reducing drag gain to `.05f`, debug and instrumentation builds passed and three affected tests passed on the same emulator: one-pixel precision dragging (0.4 logical units/pixel at 1x), live sensitivity changes, and touch braking/reversing a fling. Fling speed and duration remain unchanged. The user-selected production values were preserved.

The cap applies during finger movement, coasting and centering. Drag input is consumed per display frame; excess is discarded, with no queued catch-up after the finger stops. Frame stalls cannot accumulate a large catch-up step.

The glide chooses a reachable icon-aligned target before starting and follows monotone Hermite easing into it, without bounce or overshoot. Duration bounds its maximum derivative, enforcing the speed ceiling through final centering. Touch still cancels motion immediately. The nearest center icon now updates throughout both directions of travel, while its title uses the restored fade transition: outgoing text fades out over 100 ms, and incoming text fades in over 200 ms after a 100 ms delay. The nearest-icon selection remains accurate throughout scrolling.

Previous speed-only retuning validation (before the user-selected values above): debug build passed and all eleven drawer/center-selection/speed-dialog instrumentation tests passed on Samsung API 30. The added 2.99x regression verifies increased initial travel and completion within the unchanged roughly 3.2-second glide; extreme-input checks enforce the new nine-position/second cap. Installed in place, preserving user data.

Validation: all ten drawer/center-selection/speed-dialog instrumentation checks passed on emulator API 24 and Samsung API 30, including per-frame movement at extreme input in both directions and across sensitivity settings, no queued catch-up, low-speed precision, touch braking, and correct center identity mid-transition. The slow-drag test explicitly crosses device touch slop before one-pixel events, avoiding host injection delays accidentally generating a long press. Six existing layout/index JVM tests and lint passed. Updated debug app installed on Samsung; temporary test packages removed and wake preference restored. The numerical ceiling is verified, but subjective comfort still requires user feedback.
