# API 36 Emulator Validation — 2026-08-14

Device: `sdk_gphone64_x86_64`, Android 16, API 36 user build, six virtual CPU cores, about 2.4 GiB RAM. Fingerprint: `google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.D1/13818094:user/release-keys`.

## Results

| Gate | Result | Evidence |
|---|---:|---|
| App instrumentation | PASS, 2/2 | `app/build/reports/androidTests/connected/debug/index.html` |
| Release-like macrobenchmark | PASS, 4/4 | Minified non-debuggable target; five iterations per scenario |
| Fresh permission states | PASS | Startup without contacts, then with contacts and notification-listener access |
| Debug drawer/lifecycle smoke | PASS | 20 drawer open/close cycles plus five completed cold HOME launches |
| Scheduled wallpaper | PASS | Network worker downloaded, applied, and persisted matching desired/applied state |
| Failure scan | PASS | No Comfer ANR, fatal exception, OOM, process death, or `SecurityException` |

## Harness compatibility found and fixed

The first interaction run was blocked by Android 16's one-time immersive-mode confirmation. Both interaction setup blocks now dismiss the System UI confirmation outside the measured region.

The original search benchmark then entered `B`, which uniquely matched SIM Toolkit on this emulator. Comfer correctly auto-launched the single search result, but that made the benchmark depend on installed-app inventory and caused the later `C` lookup to fail. The scenario now repeats the known multi-result `A` key and backspace three times. This retains the search/recomposition workload without allowing a singleton result to leave the measured app.

Cold startup must run while Comfer is not the selected HOME because Android restarts the default launcher after force-stop. Pixel Launcher was selected only for the macrobenchmark, and Comfer was restored as default HOME before functional validation.

## Release-like metrics

These emulator values validate the target-API harness and provide a repeatable comparison point; they are not physical-device release thresholds.

| Scenario | Metric | Result |
|---|---|---:|
| Cold startup | time to initial display, median | 292.02 ms |
| Warm startup | time to initial display, median | 66.88 ms |
| Drawer open/close | frame CPU P50 / P95 / P99 | 18.49 / 19.16 / 24.02 ms |
| Search type/delete/close | frame CPU P50 / P95 / P99 | 18.48 / 32.52 / 33.18 ms |

Every scenario completed five iterations. Search measured 270–278 frames per iteration; drawer measured 43–45.

Raw benchmark data:

`macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/Medium_Phone_API_36(AVD) - 16/com.jeerovan.comfer.macrobenchmark-benchmarkData.json`

## Functional and worker evidence

After reinstalling and clearing the debug app, no-contacts startup completed in 941 ms. Startup with contacts granted and the notification listener enabled completed in 808 ms. The process remained alive through 20 drawer cycles. Five force-stop/HOME launches completed at 997, 980, 951, 1,019, and 1,013 ms (median 997 ms). These debug-build values include fresh launcher initialization and are not compared to the minified macrobenchmark target.

The initial periodic WorkManager execution satisfied battery and validated-network constraints and completed in 8.107 seconds. It downloaded `comfer_1088.jpg` (890,155 bytes), applied it as static system wallpaper, persisted the same file in `background_image` and `applied_wallpaper_image`, and returned `image_available` to false.

## Commands

```bash
./gradlew :app:connectedDebugAndroidTest
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

## Remaining external gates

- Populated 300-app, 10,000-contact, package-burst, notification-burst, widget-provider, and 8K-wallpaper fixtures.
- Play Console staged rollout and Android-vitals comparison.
