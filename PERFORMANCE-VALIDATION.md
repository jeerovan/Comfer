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
