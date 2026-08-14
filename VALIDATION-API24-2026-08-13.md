# API 24 Emulator Validation — 2026-08-13

Device: `Android SDK built for x86`, Android 7.0, API 24, four virtual CPU cores, about 1 GiB RAM.

## Results

| Gate | Result | Evidence |
|---|---:|---|
| App instrumentation | PASS, 2/2 | `app/build/reports/androidTests/connected/debug/index.html` |
| Release-like macrobenchmark | PASS, 4/4 | Current minSdk-24 build; five iterations per scenario |
| Debug drawer/lifecycle smoke | PASS | 20 drawer open/close cycles plus 5 completed cold HOME launches |
| Debug failure scan | PASS | No app StrictMode death, fatal exception, or ANR in post-run logcat |

## Minimum-SDK upgrade revalidation — 2026-08-14

After raising both application and macrobenchmark `minSdk` values to 24 and removing pre-24 branches, fresh `Small_Phone_API_24` sessions accepted the rebuilt app and instrumentation APKs. `ExampleInstrumentedTest` and the five-launch `SettingsActivitySmokeTest` both passed. A later Gradle-managed rerun again passed 2/2, followed by all four release-like macrobenchmark scenarios.

Generated debug, release, and macrobenchmark manifests declare `minSdkVersion="24"` and `targetSdkVersion="36"`.

The aggregate `connectedDebugAndroidTest` command also selects the macrobenchmark module's debug variant. That variant correctly rejects benchmarking a debuggable target and emulator. Use the module-scoped app command below and the `benchmark` variant for measurements.

API 24 initially exposed an unsigned macrobenchmark test APK. `macrobenchmark/build.gradle.kts` now signs the benchmark test build with the debug signing configuration; the measured app remains the non-debuggable, minified `benchmark` build.

## Smoke metrics

These values prove harness execution and provide a repeatable emulator baseline. They are not representative physical-device performance measurements.

| Scenario | Metric | Result |
|---|---|---:|
| Cold startup | time to initial display, median | 210.19 ms |
| Warm startup | time to initial display, median | 32.75 ms |
| Drawer open/close | frame CPU P50 / P95 / P99 | 16.94 / 17.26 / 18.68 ms |
| Search type/delete/close | frame CPU P50 / P95 / P99 | 17.00 / 17.37 / 20.10 ms |

The latest startup scenarios intentionally collect `StartupTimingMetric` only; frame timing is reported by interaction scenarios. Although each scenario requested five iterations, API 24 supplied three usable cold and two usable warm startup samples to the metric report. The instrumentation run itself completed all four tests without skip or failure.

Additional debug smoke used a cleared app-data state. Cold startup without contacts permission completed in 423 ms with no `SecurityException`; after granting contacts it completed in 338 ms. Five subsequent force-stop/HOME launches completed at 322, 323, 339, 337, and 342 ms (median 337 ms). Comfer remained the resolved default HOME and its process was alive after the run.

The 20-cycle debug interaction capture produced no ANR, fatal exception, OOM, `SecurityException`, StrictMode violation, or skipped-frame report. Aggregate `gfxinfo` counted 2,176 frames with P50/P95/P99 of 17/17/18 ms and only six missed vsyncs. Its 98.94% "janky" percentage is an API 24 emulator quantization artifact: frames rounded to 17 ms cross the 16.67 ms threshold. Scenario-specific Macrobenchmark percentiles above are the usable performance evidence.

The fresh debug install also exercised the real scheduled automatic-wallpaper path. With default hourly frequency, network and battery constraints satisfied, the initial periodic WorkManager execution downloaded and installed managed image `comfer_1040.jpg` at 09:54:01. The bounded managed file was 198,351 bytes; persisted desired and applied paths both matched it, `image_available` returned to false, and system plus lock wallpaper state was present. The next periodic job was scheduled with an approximately 20-minute delay. Forcing that future JobScheduler entry early correctly made WorkManager retain it as ENQUEUED instead of bypassing its periodic delay.

Raw benchmark data (the latest filtered run replaces this generated file):

`macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/Small_Phone_API_24(AVD) - 7.0/com.jeerovan.comfer.macrobenchmark-benchmarkData.json`

## Commands

```bash
./gradlew :app:connectedDebugAndroidTest
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

## Remaining external gates

- Target API 36 emulator validation is complete in `VALIDATION-API36-2026-08-14.md`; API 24 remains the minimum-supported baseline.
- Physical low-RAM device measurements without benchmark error suppression.
- Populated 300-app, 10,000-contact, notification-burst, widget, package-burst, and 8K-wallpaper fixtures.
- User-granted launcher-default, notification-listener, and widget-host flows.
- Play Console staged rollout and Android-vitals comparison.
