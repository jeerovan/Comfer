# Physical Device Validation — 2026-08-13

Device: Samsung SM-A305F, Android 11/API 30 production `user` build, 3,835,944 KiB RAM, four CPU cores. Battery was 72% and charging; thermal status was 0 before measurement.

## Automated results

| Gate | Result | Notes |
|---|---:|---|
| App instrumentation | PASS, 1/1 | Physical device, debug APK |
| Cold startup | PASS, 5/5 | No benchmark error suppression |
| Warm startup | PASS, 5/5 | No benchmark error suppression |
| Drawer open/close frames | PASS, 5/5 | No benchmark error suppression |
| Search type/delete frames | PASS, 5/5 | No benchmark error suppression |
| Debug startup without contacts permission | PASS | Final cold `am start -W`: 4,383 ms; no fatal exception |
| Default HOME resolution | PASS | `isDefault=true`; resolves and resumes `com.jeerovan.comfer/.MainActivity` |
| Granted contacts startup | PASS | `READ_CONTACTS: granted=true`; cold HOME launch remained healthy |
| Notification listener | PASS | Service present in active NotificationManager listener bindings |
| Manual interaction stress | PASS | 20 drawer/HOME and 30 search type/delete cycles; no crash or ANR |
| Notification update stress | PASS | 30 updates to one temporary notification; process survived and listener remained bound |
| Configured widget flow | PASS | Bind, configure, add, drag, resize, exit edit mode, cold restore, then 10 reopen cycles |
| Local wallpaper rapid replacement | PASS | 119.68 s captured flow; several replacements and final HOME render; no crash, ANR, OOM, or process death |
| Settings main-thread regression | PASS, 1/1 | Focused `SettingsActivitySmokeTest`; default-launcher lookup absent from post-fix StrictMode stacks |
| Network wallpaper rapid replacement | PASS after fix | Initial 5/5 exposed one stale duplicate apply; capture-free fixed 5/5 produced exactly five unique applies |
| Settings-only capture-free cycles | PASS after reuse fix | Baseline median 798 ms; five reuse cycles kept one ActivityRecord and completed resume refresh at median 237 ms, with no repeated composition |

## Measurements

| Scenario | Measurement | Result |
|---|---|---:|
| Cold startup | initial display min / median / max | 1,534.68 / 1,631.45 / 2,880.14 ms |
| Cold startup | five runs | 2,880.14, 1,691.74, 1,631.45, 1,534.68, 1,575.45 ms |
| Warm startup | initial display min / median / max | 107.33 / 358.28 / 1,783.49 ms |
| Warm startup | five runs | 205.68, 1,783.49, 440.23, 107.33, 358.28 ms |
| Drawer open/close | frame CPU P50 / P90 / P95 / P99 | 28.22 / 39.58 / 51.46 / 119.65 ms |
| Search type/delete | frame CPU P50 / P90 / P95 / P99 | 28.41 / 45.05 / 63.16 / 119.99 ms |

Startup tests now measure `StartupTimingMetric` only. Frame timing belongs to the interaction scenarios; combining the metrics caused AndroidX Benchmark 1.4.1 to reject an otherwise valid Samsung API 30 startup trace when it contained no RenderThread slices.

The interaction results fail the proposed 60 Hz frame budget and remain optimization evidence. They did not produce an ANR or crash.

## Defects exposed and fixed

1. `ContactsRepository` registered a contacts provider observer in its constructor before runtime permission was granted. Fresh/no-permission launch crashed with `SecurityException`. Observer registration now follows permission state, unregisters after revocation, and queries continue to handle revocation races.
2. Debug `StrictMode.penaltyDeath()` killed every activity on API 30 because AppCompat reads its locale-storage file during `attachBaseContext`, before app code can scope an allowance. Debug now logs violations without making the build unusable.
3. Logged StrictMode data identified Coil's file-key `lastModified()` on the main thread (~220 ms) from `AnimatedBackground`. The wallpaper request now supplies explicit versioned memory/disk cache keys.
4. APK replacement auto-launch from `AppUpdateReceiver` raced controlled benchmark launch. The benchmark build removes only that receiver through a source-set manifest overlay; production behavior is unchanged.
5. Search benchmark previously built a cumulative query (`AB`) that could change keyboard availability based on installed apps. It now types and deletes each letter independently.
6. Wallpaper-flow recording exposed synchronous `isDefaultLauncher(context)` calls from settings composition. Samsung PackageManager/Knox resolver work blocked one call for 131 ms. Settings now refreshes default-launcher state on IO whenever its lifecycle resumes; both composition-time queries use that state.

Final no-permission debug startup produced no fatal exception and no StrictMode stack containing app code, `AnimatedBackground`, or Coil `FileKeyer`. Remaining logged locale reads originate in AppCompat before activity attachment.

## Default-launcher and granted-permission smoke

After setting Comfer as default launcher and granting contacts plus notification-listener access:

- Android reported HOME `isDefault=true` and resolved it to `com.jeerovan.comfer/.MainActivity`.
- A controlled force-stop followed by HOME resumed Comfer successfully.
- Package state reported `android.permission.READ_CONTACTS: granted=true`.
- NotificationManager reported `MyNotificationListenerService` as an active bound listener.
- 20 drawer/HOME cycles and 30 custom-keyboard type/delete cycles completed.
- 30 updates to one temporary shell notification exercised listener burst handling. The temporary notification was dismissed afterward.
- Comfer remained alive. Filtered logcat contained no app ANR, fatal exception, process death, `SecurityException`, or crash event.

Opening an installed app from Comfer logged a 62 ms `DiskReadViolation`. Its complete origin was Samsung Knox `EdmStorageProvider` SQLite work in system server during `PackageManagerService.resolveIntent`, propagated back through the required `startActivity` Binder call. It is not application-owned disk I/O and is far below ANR thresholds, so no `StrictMode` suppression was added.

## Configured widget flow — 2026-08-14

Physical flow exercised the full external-provider lifecycle:

1. Swiped from QuickList into the configured right widget host.
2. Long-pressed the empty host, verified Add Widget appeared, and opened the provider picker.
3. Selected Samsung Dual clock, accepted binding, chose New Delhi, kept default provider options, and saved.
4. Verified Comfer resumed with widget rendered and edit mode still active.
5. Dragged the widget, resized it from 3 to 4 grid columns, then tapped empty space to leave edit mode.
6. Force-stopped and relaunched Comfer. Configured content, placement, and 4-column width restored.
7. Completed ten widget-host close/reopen cycles. Comfer remained alive and responsive.

No `Slow widget inflation` warning was emitted by the 500 ms guard. No quarantine placeholder or widget setup error appeared. Filtered logs contained no app ANR, fatal exception, process death, crash event, or permission exception. Widget ID 370 remains configured as a repeatable physical fixture.

The bind and configuration activity launches produced 76–86 ms StrictMode disk-read reports. Full stacks originate in Samsung Knox `EdmStorageProvider` SQLite reads within system server intent resolution and return through the required activity-launch Binder call. No application-owned disk I/O was present, so no allowance or masking change was made.

## Local wallpaper rapid replacement — 2026-08-14

User performed local wallpaper apply followed by rapid replacements while screen recording and logcat capture ran. Recording duration was 119.68 seconds. It shows several backgrounds being applied, settings remaining usable, and latest selected wallpaper rendered after returning HOME.

`WallpaperManager.setBitmap()` ran from a background app thread with a 554 x 1200 bitmap, consistent with bounded decode/apply behavior. Largest observed app GC pause was 8.2 ms; concurrent GC total was 441 ms. Comfer process stayed alive and logs contained no app ANR, fatal exception, OOM, or process death. A pre-cycle memory baseline was not captured successfully, so this run makes no peak-heap or heap-delta claim.

Perfetto collection did not produce evidence: interrupting its client left a zero-byte trace. Results above use screen recording and logcat only.

During wallpaper-driven settings refresh, StrictMode found an application-owned 131 ms main-thread call from `SettingsScreen` to `CommonUtil.isDefaultLauncher()`. Fix moves lookup to lifecycle-aware IO work and publishes Boolean state to composition. Focused `SettingsActivitySmokeTest` passed 1/1 on this device after APK replacement. Post-fix filtered logs contain no `Settings.kt`, `CommonUtil.isDefaultLauncher`, app ANR, crash, or OOM stack. Remaining 298–299 ms StrictMode reports are AppCompat locale-storage reads during `attachBaseContext`, before activity code executes; this known debug-only framework behavior is already logged rather than fatal.

## Network wallpaper rapid replacement — 2026-08-14

Capture covered 82.63 seconds and produced an 80.21 MiB screen recording plus valid 33.47 MiB Perfetto trace. User triggered five network changes and repeatedly moved between Settings and HOME. Each request completed; final HOME showed latest wallpaper. Applied bitmap sizes were 1638 x 2048, 1280 x 2048, 1362 x 2047, 1365 x 2048, and 1366 x 2048. Request-to-update durations were 4.80, 2.64, 2.04, 2.35, and 5.10 seconds.

Old managed images were removed correctly. Only final 687,194-byte `comfer_18.jpg` remained after five replacements.

No Comfer ANR, fatal exception, OOM, or process death occurred. App concurrent-GC pauses ranged from 0.13 to 2.25 ms. PSS was 240,708 KiB before, 323,955 KiB immediately after capture, and 261,993 KiB after idle/detailed collection. Retained delta was 21,285 KiB, within the 32 MiB budget; immediate PSS is not equivalent to peak heap and includes transient graphics/capture residency.

One correctness/performance race remains. Image ID 12 called `setBitmap()` with the same 1365 x 2048 bitmap at 07:56:33.660 and 07:56:38.905. HOME resumed while first apply remained in flight; `reloadImagePath()` observed the prior applied marker and queued `reapplyWallpaper()`. Coordinator serialization delayed rather than eliminated stale duplicate work. Tracked as ANR-013/P2-05.

Four Settings openings logged 32–63 skipped frames. Corresponding Samsung Knox resolver StrictMode reports were only 30–60 ms; remaining launch cost requires a capture-free settings benchmark because simultaneous screen recording and Perfetto materially load this API 30 device. Stress `gfxinfo` reported 4,092 frames, 96.19% janky, with P50/P95/P99 of 32/65/150 ms; this is not used as release frame performance. No fixed `isDefaultLauncher()` composition stack returned.

### ANR-013 fixed regression

`reapplyWallpaper()` now re-checks desired and applied paths only after acquiring the process-wide wallpaper coordinator. A reapply queued while another generation is applying therefore sees the completed marker and exits without decoding or Binder work.

Updated APK passed unit tests, lint, assembly, and installation. User then performed five capture-free network changes with immediate HOME transitions. Logs contain five requests and exactly five unique `setBitmap()` calls: 1366 x 2048, 1150 x 2048, 1366 x 2048, 1536 x 2048, and 1637 x 2047. No duplicate apply, ANR, fatal exception, OOM, or process death occurred. Maximum app GC pause was 14.10 ms.

PSS before/immediate/idle was 291,786/341,335/297,618 KiB; idle retained delta was 5,832 KiB. RSS before/immediate/idle was 353,796/411,068/367,064 KiB; idle retained delta was 13,268 KiB.

## Settings-only capture-free baseline — 2026-08-14

Five cycles opened Settings from QuickListOverlay, waited two seconds, returned HOME, and waited two seconds. No wallpaper or setting value changed. Activity-request-to-window-focus values were 798, 972, 805, 744, and 634 ms; median was 798 ms.

`gfxinfo` recorded 2,961 frames with 85.07% marked janky. P50/P95/P99 was 48/73/250 ms; 1,386 vsyncs were missed. No screen recording, Perfetto, download, or wallpaper apply ran during this baseline.

Each activity launch carried Samsung Knox PackageManager StrictMode reports of 27–40 ms. These explain only a small share of total latency. No app-owned StrictMode disk/network path, `Settings.kt` synchronous query, ANR, fatal exception, OOM, or process death appeared. SettingsActivity detached after every HOME return, rebuilding its activity-scoped ViewModel and Compose tree on the next open. Tracked as ANR-014/P3-06 pending trace attribution and optimization.

### ANR-014 fixed regression

Instrumentation separated `setContent`, first layout, and full settings snapshot publication. Starting the load before composition and suppressing the inactive guide animation improved the automated warm first-layout median from about 567 ms to 492 ms. In the following user-driven capture, the five final first-layout samples were 344, 392, 505, 520, and 688 ms (median 505 ms); system Displayed median was 675 ms. No ANR, crash, OOM, or app-owned StrictMode path appeared, but recreating the full tree still narrowly missed the first-layout target.

SettingsActivity now uses a dedicated, excluded-from-recents `singleTask`. HOME backgrounds this task instead of destroying Settings above the launcher's `singleTask`. A second synthetic open reused the same `ActivityRecord` and emitted no new `setContent` or first-layout event. Five subsequent HOME/long-press cycles continued using that record; start-request-to-resume-refresh completion was 188–249 ms, median 237 ms. Back continues to finish Settings normally, while reused opens run the existing `onResume()` state refresh.

The final reuse capture had no Comfer ANR or fatal exception. `gfxinfo` reported 1,144 frames, 86.71% janky, and P50/P95/P99 24/101/150 ms. Those aggregate Samsung process metrics still fail the proposed broad frame budget and remain Phase 5 evidence; they do not show repeated Settings composition because no recreation or first-layout event occurred.

## Evidence paths

- App instrumentation report: `app/build/reports/androidTests/connected/debug/index.html`
- Macrobenchmark report: `macrobenchmark/build/reports/androidTests/connected/benchmark/index.html`
- Generated data (latest filtered scenario replaces this file): `macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/SM-A305F - 11/com.jeerovan.comfer.macrobenchmark-benchmarkData.json`

## Remaining physical gates

- Minimum API 24 and target API 36 emulator validation are complete; this report remains the physical API 30 baseline.
- 300-app/work-profile, 10,000-contact, notification-burst, ten-widget, package-burst, and 8K-wallpaper fixtures.
- Scheduled automatic wallpaper flow passed on the API 24 emulator. The 8K low-RAM fixture remains; local and manual network rapid-replacement captures, including the ANR-013 fixed regression, are complete.
- Play Console rollout and Android-vitals comparison.
