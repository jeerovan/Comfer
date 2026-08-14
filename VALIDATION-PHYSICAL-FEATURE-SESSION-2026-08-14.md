# Physical broad feature-session validation — 2026-08-14

## Outcome

The user-driven session completed on a Samsung SM-A305F running Android 11/API 30 with about 4 GB RAM. Comfer remained the default HOME and its process (`pid 22493`) survived the full session. No Comfer ANR, fatal exception, OOM, process death, or app-owned StrictMode disk/network operation was found.

The trace found one actionable responsiveness defect: concurrent third-party icon/resource loading blocked main-thread input for 104.322 ms through Android's process-global `ResourcesManager` lock. ANR-015/P2-06 is now fixed and passes the physical release-like regression described below.

## Capture

- Perfetto: `validation-artifacts/api30-feature-session-20260814/comfer_feature_session_api30_20260814.perfetto-trace` (268,423,815 bytes)
- Logcat: `validation-artifacts/api30-feature-session-20260814/comfer_feature_session_api30_20260814.log` (12,857,757 bytes)
- SQL used for analysis is stored beside those files.
- Device remained connected after capture; current task state contained one Comfer `MainActivity` record and no accumulated Comfer activity instances.

Capture limitation: Perfetto stopped after 184.286 seconds when the configured 256 MiB file ceiling was reached. Logcat and post-run `gfxinfo` cover the full roughly ten-minute interaction session. The lightweight Perfetto configuration also omitted `atrace_apps: "com.jeerovan.comfer"`, so custom `PerformanceTrace` sections/counters are absent. No claim below assumes full-session trace coverage.

## Results

### Stability

- Comfer ANR: 0
- Comfer fatal exception/crash: 0
- OOM/process death: 0
- Comfer skipped-frame log entries: 0 (26 such messages belonged to other processes)
- Security exceptions attributed to Comfer: 0 (26 entries belonged to Phone, Samsung launcher, or Google Play services)
- Activity accumulation: none observed

Four `SurfaceControl` finalizer warnings had framework-only stacks during activity/IME transitions. Repeated `libEGL disconnect failed`, inactive input-connection, Play Core death-recipient, and Samsung view-root warnings lacked an app-owned failure stack. They are recorded as device/framework noise, not Comfer defects.

### Perfetto frame and main-thread evidence

The retained 184.286-second window contained 3,089 `Choreographer#doFrame` slices:

| Measure | Result |
|---|---:|
| Average / maximum frame duration | 9.895 / 457.981 ms |
| Frames at least 16 ms | 336 |
| Frames at least 32 ms | 94 |
| Frames at least 100 ms | 24 |
| Main thread running / runnable | 27,147.819 / 334.751 ms |
| Main thread sleeping | 155,923.988 ms |

The worst frames clustered around first visits to search, app selection, Settings, gesture selection, and app-list management. Among the 24 frames at least 100 ms:

- JIT code-cache contention affected 5 frames: 278.704 ms total, 139.661 ms maximum.
- Binder transactions appeared in 11 frames: 259.077 ms total, 26.081 ms maximum individual transaction.
- First verification of Compose `AndroidTileMode` cost 140.786 ms.
- First verification of `ManageAppListActivityKt` and Material 3 `AppBarKt` cost 22.931 ms and 21.664 ms.

The installed package is a debug build. Large first-use JIT/class-verification costs are debug-runtime evidence and possible baseline-profile coverage gaps, not proof of release performance. Confirm them with a release-like/profileable build before changing UI code.

### ANR-015: resource loading indirectly blocks input

At trace time 152.320 seconds, main-thread input delivery waited 104.322 ms on `ResourcesManager.createResources()`. The owner, `DefaultDispatcher-worker-2`, spent 371.351 ms in `ResourcesManager#getResources()` while loading Google keyboard APK resources and icon assets; it itself waited 131.966 ms on another concurrent worker. At 152.937 seconds, a second main-thread ResourcesManager wait lasted 53.887 ms while another third-party app icon and language splits loaded.

Code correlation:

- `packageManagerDispatcher = Dispatchers.IO.limitedParallelism(4)`
- `iconProcessingDispatcher = Dispatchers.Default.limitedParallelism(4)`
- `iconLoadSemaphore = Semaphore(4)`
- `refreshAppLists()` starts a deferred job per package under that four-job cap.
- `getAppInfo()` calls `LauncherActivityInfo.getBadgedIcon()` and reads the label through the package dispatcher.

Thus work is off main, but four resource loads compete on Android global resource locks that main also needs. Proposed correction: independently serialize third-party resource/icon and label loading (initial parallelism 1), retain bounded CPU parallelism only after the drawable is loaded, and replace per-package fan-out with a small worker pool or equivalent bounded mapping. Tune using total refresh duration plus main-thread lock-wait metrics.

Acceptance for the fix: a release-like physical cold-inventory and repeated visual-refresh trace has no app-induced main ResourcesManager wait above 16 ms, no input slice above 100 ms, correct component/profile icons, and no ANR/crash/OOM.

### ANR-015 post-fix validation

The fix changed third-party resource acquisition from parallelism four to one and combined each icon/label read into one serialized PackageManager block. CPU drawable/theme processing remains on the four-way Default dispatcher.

Five minified/profileable physical cold-start benchmark iterations passed. Initial display was 606.3–1,265.1 ms, median 727.2 ms. Maximum main ResourcesManager wait by iteration was 10.672, 10.568, 9.590, 10.113, and 12.153 ms: all below the 16 ms acceptance boundary and far below the pre-fix 104.322 ms wait.

A separate 30-second trace retained the whole inventory refresh:

- 85/85 launcher icons loaded in 3,045.451 ms.
- `activeAppRefresh` returned from one to zero after 3,419.674 ms.
- Main-thread ResourcesManager contention slices: 0.
- Frames: 344; at least 16 ms: 61; at least 100 ms: 0; maximum: 40.658 ms.
- ANR, crash, OOM: 0.

This closes ANR-015/P2-06. The frame distribution is cold full-inventory stress, not a steady-state release KPI.

### ANR-011 configured-widget navigation regression

The physical fixture contains Samsung Calendar month and Digital clock widgets. A release-like five-iteration Macrobenchmark swiped from QuickListOverlay to the widget screen, verified Digital clock provider content, returned, and verified Search. `CompilationMode.Ignore` deliberately preserves the app-private widget host IDs.

Before the cache fix, all five openings reinflated both widgets on main because `AnimatedVisibility` disposed the side-screen Compose tree. The maximum `widgetInflate:*` duration by iteration was 163.446, 119.216, 106.415, 128.260, and 100.286 ms. None crossed the existing 500 ms warning boundary, but the repeated work was unnecessary and consumed visible frame budget.

The fix caches each successful `AppWidgetHostView` under a weak host key and reattaches it when the side screen returns. Widget removal evicts its entry. In the post-fix run, iteration 0 performed the expected first inflation for Calendar and Digital clock (107.712 and 104.800 ms); iterations 1-4 performed zero widget inflations. Frame CPU P50/P90/P95/P99 was 24.76/36.85/42.42/65.03 ms. No ANR, crash, OOM, provider error, or 500 ms inflation occurred.

All ten pre/post traces and `widget_navigation_validation.sql` are stored in `validation-artifacts/api30-feature-session-20260814/`. This closes the repeated-inflation refinement of ANR-011; pathological first inflation remains a framework-required residual risk handled by attribution and quarantine.

### Settings slider stress

A benchmark-build-only activity hosts the production `SettingSlider` without reading or writing launcher preferences. Five physical Macrobenchmark iterations performed 20 alternating slider gestures each. Frame CPU P50/P90/P95/P99 was 20.42/27.13/30.50/39.28 ms, and SQL analysis found zero app-main slices at least 100 ms in every trace.

The first fixture used 100 UI Automator steps per gesture and took about 40 seconds per iteration on this Samsung device. The retained benchmark now uses 25 steps, about ten seconds of rapid dragging, while a separate Compose instrumentation test provides event-volume coverage: 1,000 move events produced exactly one durable commit at gesture completion and passed in 6.166 seconds.

The five Perfetto traces are stored beside the other session artifacts under names beginning `LauncherInteractionBenchmark_settingsSliderDragFrames`. This completes P0-01.

### 8K wallpaper and refresh-burst stress

An instrumentation fixture copied a 7680 x 4320 JPEG (1,151,551 bytes) into app-private storage and invoked the production `WallpaperWorkCoordinator` and bounded wallpaper application path. Baseline PSS was 185,734 KiB; peak sampled PSS was 202,958 KiB, a 17,224 KiB (16.82 MiB) increase under the 32 MiB budget. The applied marker matched the fixture. The test restored the original wallpaper and removed the fixture in `finally`.

A physical `AppInfoViewModel` test issued 100 immediate `reloadList()` calls after initial readiness. Exactly one refresh executed, maximum concurrent refreshes was one, the active counter returned to zero, and completion took 1,019 ms.

Both tests passed without ANR, crash, OOM, or stale state. They satisfy the 8K and 100-refresh portions of the Phase 2 exit gate.

### Widget quarantine and deterministic failure coverage

On-device widget-health instrumentation verified that sub-second inflation creates no strike, the first one-second inflation creates one strike, the second quarantines the provider, and explicit retry clears quarantine. The connected user layout exposed three center widgets plus two side widgets, so it does not satisfy the planned ten-widget restoration fixture. The layout was not modified.

Local deterministic tests additionally cover valid/malformed legacy folder migration, ImageWorker success/retry/cancellation, and notification 100-event debounce plus pending-stop cancellation. The migration test exposed and fixed a correctness risk: malformed folder JSON had been swallowed before legacy preferences were deleted. The error now aborts migration and preserves its source for retry.

### StrictMode and activity launches

StrictMode reported main-thread disk stacks during framework `startActivity()` calls. Complete stacks resolve to Samsung PackageManager/Knox SQLite work in system server, propagated across Binder. Observed call durations were generally 18–76 ms, with one 162 ms accessibility-settings launch. These are OEM intent-resolution costs, not direct Comfer file access; no StrictMode allowance should hide them.

Settings first creation remained measurable: system displayed times were 363–486 ms across observed creations; `setContent()` returned in 37–68 ms and first layout took 235–329 ms. Current task state showed no leaked Settings record. Whether later openings reused or recreated Settings depends on whether the user used HOME or Back; this broad session did not annotate that action sequence, so it cannot serve as the dedicated ANR-014 reuse regression.

### Memory and aggregate gfxinfo

Perfetto counters in its retained window showed:

- Java heap size: 9,295–41,845 KiB
- HWUI all memory: 10.11–75.74 MB
- HWUI texture memory maximum: 49.13 MB

After the session, package RSS was about 306.7 MB immediately and about 283.4 MB after additional idle/collection. No valid pre-session package baseline was captured, so no retained-memory delta is claimed.

Post-run aggregate debug `gfxinfo` reported 9,430 frames, 4,231 janky (44.87%), P50/P90/P95/P99 of 15/34/57/150 ms, 273 missed vsyncs, 789 slow-UI-thread frames, and 374 slow draw-command frames. This combines many screens, external activity transitions, debug JIT, and simultaneous capture. It is useful stress evidence, not a scenario-specific release KPI.

## Follow-up

1. Extend the release-like physical macrobenchmark to search first entry, app selection, Settings, gesture selection, and list management.
2. Keep `atrace_apps: "com.jeerovan.comfer"` in future Perfetto configs and avoid the 256 MiB early-stop ceiling. Prefer short targeted cycles so custom app traces and user actions remain attributable.
3. Keep P5-01 open for full-store process-restart migration and framework-bound WorkManager/notification-service integration.
4. Keep P5-03 open for a real ten-widget restore including deliberately slow-provider UX, 300+ apps, and 10k contacts. The 8K wallpaper and 100-refresh requirements now pass.

Operational note: Gradle's connected macrobenchmark lifecycle uninstalled the previous target package, clearing app-private configuration. The debug APK, default HOME role, contacts permission, and notification-listener access were restored afterward. Prior private widget/layout data could not be recovered without a backup.
