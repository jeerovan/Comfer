# Comfer ANR and Responsiveness Fix Plan

Last audit: 2026-08-14  
Scope: all 41 Kotlin production files (plus 2 template test files), Android manifest, Gradle configuration, Room/DataStore paths, Compose screens, services, worker, widget host, image pipeline, and lifecycle entry points.

## Goal

Remove known main-thread stalls and bound background work that can indirectly freeze the process through Binder contention, garbage collection, memory pressure, or work storms. Preserve launcher behavior while making fixes measurable and independently releasable.

This is a static risk audit, not proof that every item already produces an ANR on every device. Risk ratings combine call-site context, work size, lifecycle frequency, and whether work can reach or stall the main thread.

## Tracking

Status values: `TODO`, `IN PROGRESS`, `BLOCKED`, `DONE`, `WON'T FIX`.

| Phase | Objective | Status | Done |
|---|---|---|---:|
| 0 | Measurement and regression harness | IN PROGRESS | 3/4 |
| 1 | Stop startup and refresh work storms | DONE | 4/4 |
| 2 | Bound wallpaper and icon memory/CPU | DONE | 6/6 |
| 3 | Remove remaining main-thread and Compose hot work | DONE | 6/6 |
| 4 | Serialize storage and service event work | DONE | 4/4 |
| 5 | Stress validation and rollout | IN PROGRESS | 1/5 |

When completing a task, change `[ ]` to `[x]`, set `Status`, add PR/commit reference, and record before/after measurements.

## Audit baseline

- Code graph: 1,461 nodes, 4,713 edges; largest UI file is `MainActivity.kt` at roughly 6,100 lines.
- `./gradlew lintDebug`: successful; 0 errors, 125 warnings, 2 hints. Lint found no direct threading error, but it does not model the cross-coroutine work storms below.
- Existing protections: no production `runBlocking`, no `Thread.sleep`, no `allowMainThreadQueries`; Room DAO functions are suspend; app-list and icon loading are mostly off main; widget inflation is serialized; StrictMode is enabled for debug builds.
- Unit coverage includes startup barriers, inventory refresh tracking, wallpaper source limits, and bounded log persistence. Macrobenchmark harness now covers cold/warm startup and app-drawer frames; connected migration, worker/service, Compose, and large-data tests still require device execution/fixtures.

## Ranked findings

### Critical/high risk

#### ANR-001 — Full app refresh has multiple producers and no shared single-flight guard

- Status: DONE
- Risk: High
- Confidence: High
- Evidence:
  - `AppInfoViewModel.observePackageChanges()` immediately emits and invokes `refreshAppLists()`; every package callback invokes it again (`AppInfoViewModel.kt:434-464`).
  - `reloadList()` independently launches another refresh (`AppInfoViewModel.kt:466-470`).
  - `LauncherScreen` calls `reloadList()` when six state keys change (`MainActivity.kt:3554-3562`).
  - One refresh queries `LauncherApps` for every profile, performs Room writes, creates one async task per package, loads/mutates icons, generates folder bitmaps, and publishes four UI snapshots (`AppInfoViewModel.kt:471-648`).
  - `collectLatest` only cancels its own producer. `reloadList()` jobs can overlap it. Binder calls and drawable work are not promptly cancellable.
- Failure mode: duplicate PackageManager/Binder calls, icon allocations, Room writes, garbage collection, and repeated whole-screen recomposition. Low-end devices or package-install bursts can become unresponsive despite work originating off main.
- Fix:
  - Route initial load, package callbacks, preference/theme changes, and explicit reloads through one conflated refresh request flow.
  - Debounce package bursts (proposed 250–500 ms), use `mapLatest`, and protect the refresh body with one `Mutex` or owned `Job`.
  - Separate package inventory from icon rendering. Rebuild only inventory affected by package events; retheme cached models for theme-only events.
  - Publish one immutable `AppInfoUiState` per completed generation, or only quick apps early plus one final snapshot.
  - Rethrow `CancellationException`; never swallow it in broad `catch (Exception)` blocks.
- Acceptance:
  - At most one refresh body active.
  - A burst of 50 package callbacks produces at most two refreshes: leading optional, trailing final.
  - No stale generation can update UI after a newer request.
  - Trace counters prove active refresh count never exceeds one.

#### ANR-002 — Startup migration/snapshot race starts consumers before settings are ready

- Status: DONE
- Risk: High for first launch/upgrade; Medium afterward
- Confidence: High
- Evidence:
  - `ComferApp.onCreate()` launches migration and `PreferenceManager.reload()` asynchronously (`ComferApp.kt:26-45`).
  - `PreferenceManager.snapshot` starts empty (`PreferenceManager.kt:65-82`).
  - Activity-scoped view models immediately read that empty snapshot, start package refresh, load settings, and may write defaults (`AppInfoViewModel.kt:390-425`, `SettingsViewModel.kt:194-406`).
  - The initial app refresh can read/write Room while `PrefMigrator` is still importing legacy app lists and folders.
- Failure mode: duplicate initialization, default values briefly replacing migrated state, conflicting DB work, extra app-list rebuilds, and cold-start I/O contention.
- Fix:
  - Expose one application-level `StateFlow<StartupState>` or deferred readiness result.
  - Complete migration and settings snapshot load in one serialized initialization transaction.
  - Let UI draw a lightweight shell immediately, but do not start settings consumers, app inventory, wallpaper work, or writes until `Ready`.
  - Make migration failure explicit and retryable; never silently continue into default-state writes.
- Acceptance:
  - First launch and upgrade show one migration, one snapshot load, and one app refresh.
  - No `PreferenceManager.write()` occurs before readiness.
  - Migration instrumentation test proves old settings/lists/folders/widgets survive process restart.

#### ANR-003 — Wallpaper pipeline decodes and copies full-size images repeatedly

- Status: DONE
- Risk: High
- Confidence: High
- Evidence:
  - `downloadImage()` lets Coil load without a size bound, converts the drawable to a bitmap, and JPEG-compresses at quality 100 (`CommonUtil.kt:412-460`).
  - `setWallpaper()` decodes the saved file again at full resolution and sends it through `WallpaperManager.setBitmap()` (`CommonUtil.kt:461-486`).
  - Local URI flow copies the original file, extracts colors, then performs the same full decode (`CommonUtil.kt:227-264`).
  - UI background uses a screen-size Coil request, but wallpaper application does not (`MainActivity.kt:3918-3976`).
- Failure mode: large native/Java heap spikes, stop-the-world GC, bitmap allocation failure, expensive JPEG encode, and large Binder transfer. Background dispatch does not prevent GC or Binder pressure from freezing UI.
- Fix:
  - Determine target dimensions from wallpaper desired minimum size/display bounds and enforce a pixel/byte ceiling before decode.
  - Prefer streaming source into a bounded transformed file; avoid drawable-to-bitmap-to-JPEG round trip when source format is reusable.
  - Decode once for wallpaper application and palette extraction where practical.
  - Serialize all wallpaper work across `ImageWorker` and `MainViewModel` with a process-level coordinator/`Mutex`.
  - Remove artificial delays; model stages and failures explicitly.
- Acceptance:
  - 8K input test completes without OOM or ANR on selected low-RAM device.
  - Peak bitmap memory stays within agreed budget; proposed ceiling 32 MiB above steady state.
  - Only one download/copy/palette/apply pipeline runs at once.
  - Failed work returns retry/failure correctly instead of unconditional `Result.success()`.

#### ANR-004 — Preference writes are unbounded, non-atomic, per-key DataStore jobs

- Status: DONE
- Risk: High during sliders/rapid settings changes
- Confidence: High
- Evidence:
  - Every setter copies the snapshot and launches a new application-scope IO coroutine containing `DataStore.edit` (`PreferenceManager.kt:72-101`).
  - Snapshot read-modify-write is not synchronized; concurrent setters can lose unrelated keys.
  - `SettingsViewModel` contains many `viewModelScope.launch(Dispatchers.IO)` setters, which then enqueue another write coroutine (`SettingsViewModel.kt:421-1178`).
  - Multi-key operations such as themed colors issue six separate edits (`PreferenceManager.kt:151-173`).
- Failure mode: rapid slider input queues many serial DataStore transactions, saturates IO, delays other startup/database/image work, and emits excessive downstream updates. Lost snapshot changes trigger incorrect reloads.
- Fix:
  - Replace fire-and-forget writes with one actor/channel or repository-owned serialized update path.
  - Make snapshot updates atomic (`MutableStateFlow.update`, `Mutex`, or immutable repository state).
  - Add batch update API for logically related keys.
  - Keep preview values in Compose state; debounce durable slider writes until idle and flush on gesture end/lifecycle stop.
- Acceptance:
  - 1,000 rapid slider events result in a bounded number of disk edits, proposed <= 20.
  - Concurrent writes to distinct keys never lose data.
  - Callers can await persistence when ordering matters.

### Medium risk

#### ANR-005 — Notification events spawn overlapping full Binder resyncs

- Status: DONE
- Risk: Medium/High under notification bursts
- Confidence: High
- Evidence: connect, periodic timer, every post, and every removal call `syncActiveNotifications()`. Each call launches a new IO job, calls `getActiveNotifications()`, sorts, groups, and publishes (`MyNotificationListenerService.kt:29-73`).
- Fix: one conflated event flow, debounce bursts, single-flight Binder query, `distinctUntilChangedBy` stable keys, cancel scope on disconnect as well as destroy.
- Acceptance: 100 post/remove callbacks in one second create <= 2 system queries and one final correct snapshot.

#### ANR-006 — Contacts reload can overlap; sorting/state replacement returns to main

- Status: DONE
- Risk: Medium
- Confidence: High
- Evidence: every resume calls `fetchContacts()` through a new composition scope job; prior fetch is not retained/cancelled. Queries are on IO, but sorting and `SnapshotStateList.clear/addAll` occur after returning to main (`MainActivity.kt:3640-3745`).
- Fix: move contacts into a lifecycle-aware view model/repository; retain one job; use `flatMapLatest`/single-flight; sort and deduplicate on Default; publish one immutable list; skip reload when provider generation is unchanged.
- Acceptance: resume bursts never overlap queries; 10k-contact fixture causes no main-thread slice above agreed threshold.

#### ANR-007 — Temporary icon-analysis bitmaps are not recycled and refresh repeats work

- Status: DONE
- Risk: Medium; amplifies ANR-001
- Confidence: High
- Evidence:
  - `ThemedIconProcessor.drawableToBitmap()` allocates/scales a 64x64 bitmap for analysis (`AppInfoViewModel.kt:1254-1337`).
  - `applyThemedColor()` and `handleAdaptiveIcon()` do not recycle temporary bitmaps.
  - Folder construction can load/process icons already processed for app lists (`AppInfoViewModel.kt:668-716`).
  - Cache key is package-only, so personal/work profile entries collide (`AppInfoViewModel.kt:112-116`, `AppIconCache.kt:6-25`).
- Fix: recycle analysis-only bitmaps in `finally`; cache immutable rendered results by component, user, icon pack, theme, and size; reuse per-refresh models for folders; byte-size the LRU cache.
- Acceptance: repeated 100 refresh stress reaches stable heap; profile-specific icons remain correct; bitmap allocation count drops materially from baseline.

#### ANR-008 — Synchronous framework/package queries remain in composition or main effects

- Status: DONE
- Risk: Medium
- Confidence: High
- Evidence:
  - Widget label loads synchronously during composition (`MainActivity.kt:1627`).
  - App version calls PackageManager from a `LazyColumn` item (`Settings.kt:835`, `981-986`).
  - Search auto-launch calls `getLaunchIntentForPackage()` from a main `LaunchedEffect` (`MainActivity.kt:2485-2494`).
  - Several `resolveActivity()` and launch-intent queries remain in click/gesture paths (`MainActivity.kt:4707`, `5165-5177`).
- Fix: load immutable app metadata in repositories on bounded IO dispatcher; cache it; make click handlers consume pre-resolved data or perform query off main before returning to main for `startActivity`.
- Acceptance: debug StrictMode/trace shows no disk or blocking PackageManager/Binder query from composition, effects running on main, or input callbacks.

#### ANR-009 — Large Compose functions redo collection transforms and broaden recomposition

- Status: DONE
- Risk: Medium
- Confidence: High
- Evidence:
  - `LauncherScreen` sorts primary apps on every recomposition (`MainActivity.kt:3544-3550`).
  - `AppSelectionScreen` concatenates and sorts all apps on every recomposition (`AppSelectionActivity.kt:84-90`).
  - `AppDrawerScreen` repeats sorting (`ProSettingsActivity.kt:1724-1729`).
  - `SearchListOverlay` uses `remember { derivedStateOf { ... apps/contacts ... } }` without parameter keys, risking stale captured lists (`MainActivity.kt:2419-2448`).
  - `QuickListOverlay`, `SearchListOverlay`, and `AppListOverlay` are 400+ line composables with cognitive complexity 93, 47, and 119; `LauncherScreen` collects four broad flows.
- Fix: compute sorted/search projections in view models or keyed `remember`; split UI state by feature; collect lifecycle-aware state; give lazy items stable keys/content types; split overlays into stable subcomposables; use immutable collections where useful.
- Acceptance: recomposition counts and frame metrics improve in scroll/search/notification scenarios; app/contacts updates never show stale results.

#### ANR-010 — Log viewers parse/layout potentially large files on main

- Status: DONE
- Risk: Medium for diagnostic screens
- Confidence: High
- Evidence:
  - File reads run on IO, but `parseLogToAnnotatedString()` executes in `remember(rawLogs)` during composition and walks every line (`LogcatViewActivity.kt:44-50`, `114-137`).
  - One giant `Text` lays out the full annotated log (`LogcatViewActivity.kt:83-94`).
  - Logcat file is capped at 2 MiB, but crash log appends are not capped (`LogCatRecorder.kt:24-103`, `CrashHandler.kt:46-67`).
- Fix: cap both files, parse on Default, page/tail lines into `LazyColumn`, delete on IO, and keep diagnostic activities disabled or inaccessible when recording is disabled.
- Acceptance: maximum-size log opens and scrolls without ANR; memory and first-frame latency stay bounded.

#### ANR-011 — Third-party widget inflation remains unavoidable main-thread risk

- Status: DONE
- Risk: Medium/High depending on provider
- Confidence: Medium
- Evidence: `AppWidgetHost.createView()` must run on main. Inflation is frame-deferred and serialized, but a physical two-widget navigation benchmark showed that `AnimatedVisibility` disposed the side screen after every exit and caused both RemoteViews to be reinflated on every reopening. Before caching, each of five iterations performed two main-thread inflations; the slowest inflation per iteration was 163.446, 119.216, 106.415, 128.260, and 100.286 ms. Widget host construction also occurs lazily from `MainActivity.onCreate()` (`ComferApp.kt:86-91`, `MainActivity.kt`).
- Fix: initialize only required hosts, inflate only visible widgets, stagger one per frame, record provider/package and duration around each inflation, show a recoverable placeholder after prior slow/crashing provider detection, and allow user to remove/disable the offender. Cache each successfully inflated `AppWidgetHostView` for its weakly referenced host lifetime, detach it from the disposed Compose wrapper, and reattach it on later navigation; evict it when the widget is removed.
- Acceptance: traces identify provider attribution; launcher remains usable with 10 normal widgets; known slow provider can be quarantined on next launch. The physical cache regression inflated Calendar and Digital clock only during iteration 0 (107.712 and 104.800 ms); iterations 1-4 performed zero widget inflations. No inflation reached the 500 ms warning boundary, and no ANR/crash/OOM occurred.
- Residual risk: an individual framework-required main-thread inflation cannot be forcibly timed out once started. Mitigation is deferral, reduced frequency, attribution, and quarantine.

#### ANR-012 — Broad exception catches obscure cancellation and retry state

- Status: DONE
- Risk: Medium
- Confidence: High
- Evidence: suspend paths including app refresh, wallpaper work, icon loading, and notification sync catch `Exception`; this includes `CancellationException` (`AppInfoViewModel.kt:645`, `MainViewModel.kt:172/196`, `IconPackManager.kt:60/79`, `MyNotificationListenerService.kt:68`). Worker helpers swallow failures, so `ImageWorker` always returns success (`ImageWorker.kt:24-30`).
- Fix: rethrow `CancellationException`, return typed failures, add bounded timeouts/retries for network and Binder boundaries, and make WorkManager result reflect outcome.
- Acceptance: cancellation tests stop work promptly; failed network/download does not mark work successful; no stale UI publication after cancellation.

#### ANR-013 — HOME resume can queue a stale duplicate wallpaper apply

- Status: DONE
- Risk: Medium
- Confidence: High
- Evidence: during five successful network changes, image ID 12 completed one 1365 x 2048 `setBitmap()` call at 07:56:33.660. Returning HOME while that Binder call was still completing made `reloadImagePath()` observe the old applied-image marker and enqueue `reapplyWallpaper()`; the same 1365 x 2048 image was applied again at 07:56:38.905. `WallpaperWorkCoordinator` serialized both calls but did not discard the stale queued reapply.
- Failure mode: duplicate bitmap decode and `WallpaperManager` Binder/system color work, transient memory growth, extra launcher/system UI jank, and increased ANR exposure under rapid changes.
- Fix: after acquiring `WallpaperWorkCoordinator`, re-read desired and applied image paths and skip stale reapply requests. Track apply generation/in-flight path so lifecycle resume cannot enqueue work already running.
- Acceptance: rapid network change plus immediate Settings/HOME transitions produces one `setBitmap()` per accepted wallpaper generation; stale generations never apply; final image and applied marker match.

#### ANR-014 — SettingsActivity recreation misses frame and launch budgets

- Status: DONE
- Risk: Medium/Low for ANR; High for visible jank on low-end devices
- Confidence: High for latency, Medium for root attribution
- Evidence: capture-free five-cycle Settings/HOME run on Samsung API 30 measured activity-request-to-window-focus at 634, 744, 798, 805, and 972 ms. `gfxinfo` reported 2,961 frames, 85.07% janky, with P50/P95/P99 48/73/250 ms. Samsung PackageManager/Knox StrictMode work was only 27–40 ms per launch, and no fixed `isDefaultLauncher()` composition stack returned. Each HOME return detached SettingsActivity, so every long press rebuilt its activity, ViewModel, and Compose tree.
- Failure mode: repeated 0.6–1.0 s visible stalls; larger settings state or slower device can push launch work toward frozen-frame and input-timeout territory.
- Fix: added activity/create/load/first-layout trace boundaries and repeated launch coverage. Snapshot loading now starts before composition, the unused guide pulse is not instantiated, and oversized social-vector intrinsic dimensions match their 35 dp render size. Most importantly, Settings now lives in a hidden dedicated `singleTask`, so HOME backgrounds rather than destroys its Compose/ViewModel tree; subsequent launcher long presses reuse the same activity and refresh state on resume.
- Acceptance: five physical reuse cycles retained one ActivityRecord, produced no repeated `setContent`/first-layout work, and completed resume refresh 188–249 ms after each activity start request (median 237 ms). No ANR, crash, OOM, or app-owned disk/network violation occurred. First-ever activity creation and device `gfxinfo` frame-budget work remain rollout measurements under Phase 5 rather than recurring recreation work.

#### ANR-015 — Parallel icon loads indirectly block main input through ResourcesManager

- Status: DONE
- Risk: Medium for ANR; High for visible input latency during inventory/icon refresh
- Confidence: High for observed contention; Medium for best concurrency setting
- Evidence: the API 30 physical feature trace recorded main input delivery waiting 104.322 ms on `ResourcesManager.createResources()`, followed by another 53.887 ms wait. The lock owner was `DefaultDispatcher-worker-2` inside `ResourcesManager#getResources()` while loading third-party app resources and icons; that worker also waited 131.966 ms on another concurrent worker. `refreshAppLists()` permits four complete icon jobs, `packageManagerDispatcher` has parallelism four, and `getAppInfo()` loads each icon and label through those workers (`AppInfoViewModel.kt:115-119`, `127-158`, `678-700`).
- Failure mode: moving PackageManager/resource work off main prevents direct blocking calls, but concurrent `LauncherActivityInfo.getBadgedIcon()`/label loads contend on Android's process-global resource locks. Main-thread resource creation or input work can then wait behind the background fan-out.
- Fix: third-party icon and label acquisition now share one serialized PackageManager dispatcher block. Loaded drawables still use the four-way Default dispatcher for CPU bitmap/theme processing, so the global ResourcesManager boundary is serialized without serializing image computation.
- Acceptance: five physical release-like cold-start traces limited maximum main-thread ResourcesManager waits to 9.590–12.153 ms. A separate 30-second full-refresh trace loaded all 85 icons in 3,045.451 ms and completed the refresh in 3,419.674 ms with zero main-thread ResourcesManager contention, no frame at least 100 ms, and a 40.658 ms maximum frame. Cold initial-display median was 727.2 ms. No ANR/crash/OOM occurred.

### Lower-priority correctness/performance issues found beside ANR audit

- `allActivitiesMap` is keyed only by package, dropping secondary launcher activities and one of personal/work entries (`AppInfoViewModel.kt:477-487`). Use component plus `UserHandle` identity.
- `AppIconCache` previously keyed only by package and stored mutable `Drawable` instances across threads. P2-03 replaced it with component/user keys and cached immutable `ConstantState` entries under an 8 MiB byte budget.
- Wallpaper downloads previously checked the common Coil result base type, making failure handling ineffective. P2-02 replaced this with cancellable bounded streaming and explicit Boolean failure propagation.
- Network fixture returned Unsplash URLs shaped like `?w=2000q=99` rather than separate `w` and `q` parameters. Validate/fix backend URL construction or normalize trusted Unsplash URLs client-side; malformed sizing parameters can waste bandwidth even though the 25 MiB source limit and 2048 px decode bound prevent unbounded memory use.
- App update receiver launches an activity immediately after package replacement from a receiver (`AppUpdateReceiver.kt:11-26`). Review modern background activity-start restrictions and product intent. Not current ANR source.
- Lint reports oversized 512dp vector icons (`reddit_icon.xml`, `telegram_icon.xml`), which can increase draw cost. Resize/rasterize after higher risks.
- Duplicate direct Compose animation dependencies resolve different requested versions in version catalog. Consolidate through BOM to reduce build/runtime uncertainty.
- Legacy folder migration previously swallowed malformed JSON and then deleted the legacy preferences, making the failure non-retryable and risking data loss. Migration now fails atomically, preserves the source preferences, and has valid/malformed fixture coverage.

## Phased execution checklist

### Phase 0 — Measurement and regression harness

- [x] **P0-01** Status: DONE — Added `macrobenchmark` coverage for cold/warm startup, app drawer, accessibility-selector search typing, configured-widget navigation, and a benchmark-only settings slider. The isolated slider scenario preserves launcher data and completed five physical iterations; frame CPU P50/P90/P95/P99 was 20.42/27.13/30.50/39.28 ms with zero app-main slices at least 100 ms. A Compose stress test injected 1,000 drag moves and verified exactly one durable commit at gesture completion.
- [x] **P0-02** Status: DONE — Added async trace sections/counters for startup, active app refresh generations, launcher activity/icon counts, wallpaper pipeline, notification resync, contact query, and widget provider/duration.
- [x] **P0-03** Status: DONE — Added reproducible stress matrix, commands, capture requirements, pass gates, and rollout thresholds in `PERFORMANCE-VALIDATION.md`.
- [ ] **P0-04** Status: IN PROGRESS — Current minSdk-24 build passes connected instrumentation and release-like macrobenchmarks on API 24 and API 36, plus drawer and cold-HOME smoke; API 30 Samsung 4 GB physical baselines also pass. Populated stress fixtures remain. See `VALIDATION-API24-2026-08-13.md`, `VALIDATION-API36-2026-08-14.md`, and `VALIDATION-PHYSICAL-2026-08-13.md`.

Exit gate: reproducible baseline exists. Each later task has a measurable regression test.

### Phase 1 — Stop startup and refresh work storms

- [x] **P1-01 / ANR-002** Status: DONE — Added process-wide startup readiness/failure state, serialized migration plus snapshot load, retry entry point, consumer/write barriers, and startup-gate unit tests.
- [x] **P1-02 / ANR-001** Status: DONE — Replaced package and explicit refresh producers with one merged, immediately cancelling, 300 ms coalescing pipeline.
- [x] **P1-03 / ANR-001, ANR-012** Status: DONE — Added generation IDs, atomic publication guards, cancellation rethrow, and stale-result prevention.
- [x] **P1-04** Status: DONE — Split inventory changes from theme/icon rerender; visual-only refreshes reuse cached launcher inventory, and each generation reuses one deferred app/icon result across lists and folder previews.

Exit gate: one initialization and one refresh on cold start; package burst benchmark passes; migrated data remains correct.

### Phase 2 — Bound wallpaper and icon memory/CPU

- [x] **P2-01 / ANR-003** Status: DONE — Added process-wide IO-dispatched wallpaper coordinator shared by worker and view model; removed artificial pipeline delays.
- [x] **P2-02 / ANR-003** Status: DONE — Stream network/local sources with a 25 MiB limit, bound decode dimensions to 2048 px, reuse one sampled bitmap for encode/palette/apply, clean temporary files, and propagate meaningful worker outcomes.
- [x] **P2-03 / ANR-007** Status: DONE — Recycle owned analysis bitmaps; cache raw immutable icon state by component/user under an 8 MiB byte budget; invalidate affected package/profile entries. Theme output remains generation-scoped and cannot collide in raw cache.
- [x] **P2-04** Status: DONE — Put icon and folder bitmap processing on bounded Default dispatcher; keep package Binder work on bounded IO and wallpaper work on serialized IO.
- [x] **P2-05 / ANR-013** Status: DONE — Lifecycle reapply re-checks desired/applied paths after acquiring the wallpaper coordinator, skipping work completed while it waited. Capture-free physical regression produced five accepted changes and exactly five unique `setBitmap()` calls despite immediate HOME transitions.
- [x] **P2-06 / ANR-015** Status: DONE — Serialized third-party icon/label resource acquisition while retaining four-way CPU drawable processing. Five release-like cold traces reduced maximum main ResourcesManager wait from the observed 104.322 ms to 9.590–12.153 ms; a full 85-icon refresh showed zero such main contention and completed in 3,419.674 ms.

Exit gate: repeated 8K wallpaper and 100 app-refresh loops show stable heap, bounded concurrency, no OOM/ANR, and no stale wallpaper state.

### Phase 3 — Remove remaining main-thread and Compose hot work

- [x] **P3-01 / ANR-008** Status: DONE — Moved widget metadata, app version, default-launcher detection, dialer/alarm/calendar resolution, and remaining launch resolution queries out of composition/input main-thread work. Default-launcher state now refreshes from IO whenever settings resumes.
- [x] **P3-02 / ANR-009** Status: DONE — Memoized app projections/sorts by source inputs, removed stale parameter captures from search derivations, moved search auto-launch resolution to IO, and added profile/component-aware lazy keys.
- [x] **P3-03 / ANR-009** Status: DONE — Added distinct launcher-only app/settings state projections and extracted contacts state/query work into its own lifecycle ViewModel/repository, preventing unrelated settings/inventory fields from recomposing launcher orchestration.
- [x] **P3-04 / ANR-010** Status: DONE — Cap crash logs at 512 KiB, tail-read at most 2 MiB/2,000 lines on IO, render lines lazily, and clear files on IO.
- [x] **P3-05 / ANR-011** Status: DONE — Widget inflation is serialized/frame-deferred, traced by provider/duration, persisted as slow after ≥1 s, quarantined after two strikes across restarts, and recoverable through explicit retry UI. Successfully inflated host views are retained under weak host keys and reattached after side-screen disposal, eliminating repeated RemoteViews inflation during navigation.
- [x] **P3-06 / ANR-014** Status: DONE — Added repeatable settings-launch profiling and activity/composition/state-load traces; started snapshot load before composition, removed an unconditional infinite transition, corrected 512 dp social-vector intrinsic sizes, and retained Settings in a hidden dedicated single-task so HOME/long-press cycles reuse its Compose/ViewModel tree.

Exit gate: no known blocking framework query in composition/input callbacks; frame/recomposition benchmarks meet agreed budget; widget stress remains interactive.

### Phase 4 — Serialize storage and service event work

- [x] **P4-01 / ANR-004** Status: DONE — Replaced per-key coroutine/DataStore fan-out with one conflated, 50 ms coalescing writer that atomically persists latest values per key and retries failed batches.
- [x] **P4-02 / ANR-004** Status: DONE — Slider composables retain local preview values while dragging and commit durable ViewModel/DataStore state only from `onValueChangeFinished`; writer-level coalescing remains a second bound.
- [x] **P4-03 / ANR-005** Status: DONE — Conflate notification events into one 250 ms debounced resync pipeline; cancel it on disconnect and suppress unchanged snapshots.
- [x] **P4-04 / ANR-006** Status: DONE — Extracted lifecycle `ContactsViewModel`/repository with one Mutex-serialized query, provider invalidation observer, dirty-generation skip, cancellable provider queries/cursor loops, off-main dedupe/sort, and immutable StateFlow publication.

Exit gate: storage, notification, and contacts stress tests show bounded jobs/Binder queries and no lost state.

### Phase 5 — Stress validation and rollout

- [ ] **P5-01** Status: IN PROGRESS — Unit tests, lint, release/benchmark compilation, API 24/API 36 and physical API 30 app instrumentation pass. Dedicated tests now cover a 1,000-event Compose slider gesture, valid/malformed legacy folder migration, ImageWorker success/retry/cancellation, notification debounce/stop behavior, app-refresh burst coalescing, and widget quarantine/retry. Full-store migration across process restart and framework-bound WorkManager/notification-service integration remain.
- [x] **P5-02** Status: DONE — Supported-device matrix captured: minimum API 24 emulator, physical Samsung API 30 with 4 GB RAM and no benchmark error suppression, and target API 36 emulator. API 36 passed 2/2 app instrumentation tests, 4/4 release-like macrobenchmarks, both permission-state startups, 20 drawer cycles, and five cold HOME launches without an app failure. See the three validation reports.
- [ ] **P5-03** Status: IN PROGRESS — API 24/API 36 interaction smoke and API 30 fresh/no-contacts-permission cold launch pass. On physical API 30, default-HOME/grants, drawer, search, notifications, configured-widget edit/restore, local/network wallpaper, and broad feature-session coverage complete without crash or ANR. An actual 7680 x 4320 wallpaper passed with a 16.82 MiB peak PSS delta and restored the original wallpaper. A 100-request app-refresh burst produced one refresh with maximum concurrency one. Widget health/quarantine state transitions pass on device. Remaining volume fixtures are a real 10-widget restore including a deliberately slow provider, 300+ apps, and 10k contacts.
- [ ] **P5-04** Status: BLOCKED — Requires access to Play Console Android vitals and release annotations.
- [ ] **P5-05** Status: BLOCKED — Rollout thresholds are defined in `PERFORMANCE-VALIDATION.md`; staged release and migration verification require release authority/devices.

Exit gate: no reproducible ANR in stress matrix, no new lint errors, measurements meet thresholds, rollback plan approved.

## Proposed performance budgets

Confirm these before Phase 0 closes:

- No application-owned main-thread trace slice above 100 ms during steady-state interactions.
- No application-owned main-thread trace slice above 500 ms during startup/widget restoration.
- No input dispatch stall approaching platform ANR threshold.
- One active app refresh, wallpaper pipeline, contacts query, and notification resync maximum.
- 60 Hz target: <= 5% slow frames and <= 1% frozen frames in measured launcher flows; use platform benchmark definitions for final reporting.
- Wallpaper peak heap delta <= 32 MiB on selected low-RAM device.
- Cold start time-to-full-display target: choose after baseline; do not hide deferred work by reporting only first frame.

## Product/engineering decisions

- [x] Minimum supported device profile: API 24 with 2–4 GB RAM.
- [x] Maximum supported wallpaper dimensions/file bytes: 2048 px per dimension and 25 MiB compressed source; destructive downscaling is accepted for app-managed wallpaper copies.
- [ ] Maximum simultaneously restored third-party widgets and slow-provider quarantine UX.
- [ ] Whether crash/log viewer ships in release when `saveCrashes` and `saveLogs` are false.
- [ ] Whether app updates should auto-launch the launcher activity after package replacement.

## Definition of done for each fix

1. Root cause has a regression test or repeatable benchmark.
2. Before/after trace and memory/frame metrics are recorded.
3. Cancellation, process restart, lifecycle recreation, and error paths are tested.
4. No unrelated behavior or migrated user data changes.
5. Task checkbox, status, PR/commit, and measurement link are updated in this file.

## Progress log

### 2026-08-13 — Phase 1 core concurrency fixes

- Added `StartupCoordinator`/`StartupGate`; migration and `PreferenceManager.reload()` now form one readiness boundary.
- Added explicit `Initializing`, `Ready`, and `Failed` states plus idempotent retry through `ComferApp.initializeApplicationData()`.
- Gated app, settings, and wallpaper consumers until migrated data is ready.
- Prevented pre-ready preference writes from mutating/replacing the empty snapshot; made snapshot updates synchronized and DataStore edits mutually exclusive. Full write coalescing/batching remains P4-01/P4-02.
- Unified initial/package/manual app refresh signals. New signals cancel old work immediately, then coalesce for 300 ms.
- Added refresh generation guards and cancellation propagation in `refreshAppLists()`, `getAppInfo()`, and `IconPackManager`.
- Added two `StartupGateTest` cases covering suspension, readiness, explicit failure, and retry.
- Verification: `./gradlew testDebugUnitTest` passed. `git diff --check` passed.
- Phase 1 implementation complete. Migration instrumentation and performance traces remain under Phase 0/5 validation.

### 2026-08-13 — Phase 1 completion and Phase 2 start

- Added monotonic inventory request/completion tracking. Package events remain pending across cancellation; visual-only refreshes skip launcher Binder inventory queries and reconciliation writes.
- Added per-generation deferred app/icon cache shared by normal lists and folder previews. Added cancellation and event-during-refresh tracker tests.
- Added process-wide `WallpaperWorkCoordinator`; worker, startup load, manual change, and reapply paths now serialize on IO.
- Bounded wallpaper requests and file decodes to 2048 px, disabled hardware bitmap output, reused network bitmap for apply, and reduced JPEG quality from 100 to 92.
- Worker now propagates cancellation and returns retry when fetch/download fails.
- Recycled all temporary icon-analysis bitmaps. Replaced package-only mutable drawable cache with component/user-keyed immutable constant-state entries, 8 MiB byte sizing, and targeted package callback invalidation.
- Moved icon analysis and folder bitmap generation to bounded `Dispatchers.Default`; package-manager work remains on bounded IO.
- Added 25 MiB streaming source ceiling for network and local URI wallpaper inputs, including partial-file cleanup and three boundary tests.
- Network wallpaper now uses one bounded decoded bitmap for JPEG encode, palette extraction, and system apply; palette CPU work uses single-parallelism `Dispatchers.Default`.
- Verification: `./gradlew testDebugUnitTest` and `./gradlew lintDebug` passed.
- Phase 2 implementation complete. Real-device 8K peak-heap and ANR validation remains under Phase 0/5.

### 2026-08-13 — Phase 3 start

- Widget label and preview metadata now load together on IO instead of calling PackageManager during composition.
- App version now loads once through `produceState` on IO instead of querying PackageManager from a lazy-list item.
- Dialer, alarm, and calendar intent resolution moved off input/main callbacks using composition-owned coroutine scopes.
- Verification: `./gradlew testDebugUnitTest` and `./gradlew lintDebug` passed.

### 2026-08-13 — Compose, diagnostics, and event serialization

- Memoized launcher, selection, drawer, and search list projections; fixed stale search captures and added stable component/profile lazy keys.
- Added bounded log utility and tests. Crash files cap at 512 KiB; viewers tail at most 2 MiB/2,000 lines and use `LazyColumn` instead of one giant laid-out `Text`.
- Added widget inflation trace sections with provider names/durations and warnings above 500 ms. Persistence/quarantine remains P3-05.
- Replaced notification callback job fan-out with one conflated 250 ms debounce pipeline, one Binder query at a time, disconnect cancellation, and stable-snapshot suppression.
- Contacts resume reload now cancels prior work, observes cancellation while scanning, and deduplicates/sorts off main.
- Replaced preference write fan-out with one bounded-key coalescing writer; related writes share one DataStore transaction and failures retry without overwriting newer values.
- Added `BoundedLogFileTest`; final verification: `./gradlew testDebugUnitTest`, `./gradlew lintDebug`, and `git diff --check` passed.

### 2026-08-13 — Remaining repository implementation

- Added launcher-only distinct state projections so unrelated app/settings fields no longer invalidate launcher orchestration.
- Added persistent widget health tracking: two provider inflations at or above one second quarantine that provider across restarts; retry UI clears quarantine explicitly.
- Slider controls now keep drag preview locally and persist once on gesture finish.
- Extracted contact loading into lifecycle `ContactsViewModel` and repository with provider observation, dirty skip, Mutex single-flight, Android `CancellationSignal`, immutable flow publication, and off-main processing.
- Added startup/app-refresh/wallpaper/notification/contact async traces, concurrency counters, launcher inventory/icon counters, and provider-specific widget inflation traces.
- Added `macrobenchmark` module using AndroidX Benchmark 1.4.1, benchmark build type/profileability, cold/warm startup metrics, and app-drawer frame scenario.
- Added `PERFORMANCE-VALIDATION.md` with device matrix, fixtures, Perfetto tracks, pass budgets, rollout stages, and rollback threshold.
- Final static/build verification passed: `testDebugUnitTest`, `lintDebug`, minified `assembleRelease`, app `assembleBenchmark`, macrobenchmark `assembleBenchmark`, and `git diff --check`.
- Device/Play Console work is explicitly blocked rather than reported as complete without evidence.

### 2026-08-13 — API 24 emulator validation

- Confirmed Android 7.0/API 24 x86 emulator and ran app instrumentation: 1/1 passed.
- Fixed the benchmark test APK signing configuration exposed by API 24 (`INSTALL_PARSE_FAILED_NO_CERTIFICATES`).
- Ran the release-like benchmark target with only the emulator accuracy guard suppressed: cold startup, warm startup, app-drawer, and search typing frame tests passed 4/4.
- Captured smoke-only medians: cold initial display 205.53 ms, warm initial display 31.89 ms, drawer frame CPU P50/P95/P99 16.89/17.11/17.45 ms. Emulator results are not accepted as physical-device performance evidence.
- Search semantics use accessibility selectors, bounds-derived raw taps, and bounded idle waits so custom keyboard animation cannot pollute measurements with hidden UI Automator waits; P50/P95/P99 were 17.00/17.37/19.26 ms.
- Installed debug build, granted contacts, performed 20 drawer swipes and 5 home/resume cycles (30/30 commands), then found no app StrictMode death, fatal exception, or ANR in captured logcat.
- Full report and artifact paths: `VALIDATION-API24-2026-08-13.md`.

### 2026-08-13 — API 30 physical validation

- Validated on a production Samsung SM-A305F (Android 11/API 30, 4 GB RAM, thermal status 0) without benchmark error suppression.
- App instrumentation passed 1/1. Cold startup, warm startup, drawer frames, and search frames each passed five iterations.
- Physical median initial display: cold 1,631.45 ms; warm 358.28 ms.
- Drawer frame CPU P50/P95/P99 was 28.22/51.46/119.65 ms; search was 28.41/63.16/119.99 ms. Both remain above the proposed 60 Hz frame budget.
- Fixed fresh-install crash from registering the contacts observer before `READ_CONTACTS`; verified no-permission debug cold launch completes without fatal exception.
- Replaced debug StrictMode death with logging after AppCompat's unavoidable locale read killed startup before app code. Logged evidence then exposed Coil file-key I/O, removed through explicit versioned wallpaper cache keys.
- Isolated benchmark installs from production update auto-launch using a benchmark-only receiver-removal manifest and separated startup timing from interaction frame timing.
- Full evidence: `VALIDATION-PHYSICAL-2026-08-13.md`.

### 2026-08-13 — Default-launcher and granted-permission physical smoke

- Verified Android resolves HOME to `com.jeerovan.comfer/.MainActivity` with `isDefault=true`; HOME resumed that activity after a controlled force-stop.
- Verified `READ_CONTACTS` remains granted and `MyNotificationListenerService` is present in NotificationManager's active listener bindings.
- Completed 20 drawer/HOME cycles, 30 search type/delete cycles, and 30 updates to one temporary notification. Comfer process remained alive; filtered logs contained no ANR, fatal exception, process death, permission exception, or app crash.
- Removed the temporary notification and device-side hierarchy/screenshot files; default HOME and both grants remain unchanged.
- App launch produced one 62 ms StrictMode disk-read report. Full origin is Samsung Knox `EdmStorageProvider` inside system server during intent resolution, propagated through the required `startActivity` Binder call. This is OEM work, not application-owned I/O; no masking allowance was added.

### 2026-08-14 — Physical configured-widget flow

- Opened the right widget host through the configured QuickList swipe, entered edit mode by long press, and opened the provider picker.
- Added Samsung Dual clock, accepted widget-host binding, completed its New Delhi configuration, saved provider options, and verified Comfer returned with edit mode still active.
- Dragged the widget to another grid cell, resized it from 3 to 4 columns, tapped empty space to exit edit mode, and verified edit controls disappeared while RemoteViews stayed rendered.
- Force-stopped and relaunched Comfer, then verified widget ID, configured content, 4-column size, and placement restored. Ten close/reopen cycles completed afterward.
- No `Slow widget inflation` warning (500 ms threshold), quarantine UI, setup error, ANR, fatal exception, process death, or permission exception appeared. The configured widget remains installed as the physical fixture.
- Binding/configuration launches logged 76–86 ms StrictMode reports whose full origins were Samsung Knox `EdmStorageProvider` reads inside system server intent resolution. No application-owned disk read was found and no suppression was added.

### 2026-08-14 — Local-wallpaper rapid-replacement flow

- Captured 119.68 seconds covering local wallpaper apply, rapid replacement, and final HOME render. Several replacements completed and latest wallpaper remained visible after returning HOME.
- Wallpaper application ran on a background thread with a bounded 554 x 1200 bitmap. No Comfer ANR, fatal exception, OOM, or process death appeared; largest observed app GC pause was 8.2 ms.
- Capture exposed a synchronous `isDefaultLauncher(context)` PackageManager query inside settings composition. Samsung's resolver blocked that main-thread call for 131 ms. Replaced both composition-time calls with lifecycle-aware state refreshed on IO at each resume.
- Added `SettingsActivitySmokeTest`; focused physical run passed 1/1. Post-fix StrictMode logs contain no `Settings.kt`, `CommonUtil.isDefaultLauncher`, ANR, crash, or OOM entry. Known AppCompat locale-storage reads still occur before activity attachment.
- Perfetto client interruption left a zero-byte trace, so no trace claim uses that artifact. Screen recording and logcat provide this cycle's evidence. No valid pre-cycle memory baseline was captured; no heap-delta claim is made.
- Verification: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, focused physical instrumentation, and `git diff --check` passed.

### 2026-08-14 — Network-wallpaper rapid-replacement flow

- Captured 82.63 seconds of screen video, focused logcat, before/after memory, gfxinfo, and a valid 33.47 MiB Perfetto trace while performing five network wallpaper changes with repeated Settings/HOME transitions.
- All five downloads completed and applied bounded bitmaps: widths 1280–1638 px and heights 2047–2048 px. Request-to-update latency was 2.04–5.10 seconds. Final HOME displayed the latest image. No Comfer ANR, fatal exception, OOM, or process death occurred.
- Managed-file cleanup succeeded: only final 687,194-byte `comfer_18.jpg` remained after five replacements.
- App GC pauses remained 0.13–2.25 ms. PSS rose from 240,708 KiB to 323,955 KiB immediately after capture, then fell to 261,993 KiB after idle/detailed collection: retained delta 21,285 KiB, inside the 32 MiB budget. Immediate PSS includes transient bitmap/graphics/capture residency and is not a heap-peak measurement.
- Found one duplicate system apply: image ID 12 was sent to `setBitmap()` at 07:56:33.660 and again at 07:56:38.905 after HOME resumed before the applied marker became visible. Added ANR-013/P2-05; no fix is claimed yet.
- Four Settings launches skipped 32–63 frames. Each follows activity launch and Samsung Knox PackageManager work of 30–60 ms; screen recording plus Perfetto add load, so a capture-free settings benchmark is still required before attributing the full 0.5–1.05 s stalls to app composition.
- `gfxinfo` during simultaneous capture reported 4,092 frames, 96.19% janky, P50/P95/P99 32/65/150 ms. Treat this as stress evidence, not release frame performance, because full-resolution screen recording and Perfetto ran concurrently.
- No regression of fixed settings default-launcher lookup appeared. StrictMode stacks were limited to known Samsung system-server intent resolution propagated through activity launch.

### 2026-08-14 — ANR-013 fix and capture-free regression

- `reapplyWallpaper()` now acquires `WallpaperWorkCoordinator`, then re-reads desired and applied image paths. Work queued during an in-flight apply becomes a no-op once the completed generation publishes its marker.
- Unit tests, lint, and debug assembly passed; updated APK installed with HOME default, contacts grant, notification access, and app data preserved.
- User performed five network changes and pressed HOME immediately after every request. Logs show five requested generations, five successful downloads, and exactly five `setBitmap()` calls with distinct dimensions. No stale duplicate apply, ANR, fatal exception, OOM, or process death occurred.
- Request-to-update time was 1.52–9.18 seconds. Maximum app GC pause was 14.10 ms. PSS was 291,786 KiB before, 341,335 KiB immediately after, and 297,618 KiB after idle collection; retained delta was 5,832 KiB. RSS retained delta was 13,268 KiB.
- Capture-free `gfxinfo` for the combined network/navigation stress run recorded 5,251 frames, 87.75% janky, with P50/P95/P99 23/69/150 ms. Because wallpaper work overlaps navigation, a separate settings-only run follows before attributing launch cost.

### 2026-08-14 — Capture-free settings-only baseline

- Five user-driven cycles opened Settings, waited two seconds, returned HOME, and waited two seconds without wallpaper or setting changes.
- Activity request to Settings window focus was 634–972 ms; sorted values 634/744/798/805/972 ms, median 798 ms. `gfxinfo` recorded 2,961 frames, 85.07% janky, P50/P95/P99 48/73/250 ms.
- Samsung Knox PackageManager resolution contributed 27–40 ms per launch. No application-owned StrictMode disk/network call, synchronous default-launcher query, ANR, fatal exception, OOM, or process death appeared.
- SettingsActivity detached after each HOME return, recreating its activity-scoped `SettingsViewModel` and Compose tree on every opening. Added ANR-014/P3-06; profiling/optimization remains open rather than attributing the full cost without trace evidence.

### 2026-08-14 — ANR-014 settings reuse fix

- Added `settingsLaunch`, `settingsActivityCreate`, settings-load, `setContent`, and first-layout instrumentation plus a five-launch activity smoke regression.
- Starting settings snapshot loading before `setContent` and avoiding the inactive how-to infinite transition improved automated warm first-layout median from about 567 ms to 492 ms. A user-driven intermediate run measured first-layout 344/392/505/520/688 ms (median 505 ms) and system Displayed median 675 ms, proving full recreation remained the dominant recurring cost.
- Moved SettingsActivity into an excluded-from-recents dedicated `singleTask`. HOME now backgrounds Settings instead of clearing it above the launcher `singleTask`; reopening Settings reuses the exact ActivityRecord and existing Compose/ViewModel tree. Back still finishes Settings normally, and `onResume()` refreshes external notification/settings state.
- Five physical automated HOME/long-press cycles reused one ActivityRecord. No later cycle emitted `setContentReturnMs`, `firstLayoutMs`, or an ActivityTaskManager Displayed event. Start-request-to-resume-refresh completion was 188/211/237/237/249 ms (median 237 ms).
- Final cycle capture contained no Comfer ANR or fatal exception. Samsung `gfxinfo` still reports implausibly high aggregate jank for the continuously composed launcher/settings process (86.71%, P50/P95/P99 24/101/150 ms), so broad device frame-budget closure stays in Phase 5 and is not represented as fixed by activity reuse.
- Unit tests, lint, debug APK, and debug-test APK assembly passed. Updated debug APK installed successfully with launcher data and permissions preserved.

### 2026-08-14 — Minimum SDK raised to API 24

- Raised application and macrobenchmark `minSdk` from 23 to 24; `targetSdk` remains 36 and `compileSdk` remains 37. Generated debug, release, and benchmark manifests confirm min/target 24/36.
- Removed the pre-24 SharedPreferences deletion fallback, the pre-24 unflagged wallpaper apply fallback, and the API 24 lock-screen-wallpaper availability check. All remaining runtime SDK gates protect API 26+ functionality and remain required.
- Promoted the launcher foreground vector from `drawable-v24` to the default drawable set because every supported device now implements its API 24 vector features.
- Updated minimum-device and stress-matrix documentation. API 24 is now the tested minimum baseline; target API 36 validation was subsequently completed in the pass recorded below.
- Verification passed: unit tests, debug lint, debug APK, debug-test APK, minified release APK, and macrobenchmark APK. Fresh API 24 installation and both connected instrumentation tests passed, including the five-launch Settings smoke test. `git diff --check` passed.

### 2026-08-14 — Current-build API 24 minimum-device pass

- Gradle-connected instrumentation passed 2/2 on `Small_Phone_API_24`; all four release-like macrobenchmark scenarios then passed with five requested iterations and valid Perfetto artifacts.
- Latest medians: cold initial display 210.19 ms; warm initial display 32.75 ms. Drawer frame CPU P50/P95/P99 was 16.94/17.26/18.68 ms; search was 17.00/17.37/20.10 ms.
- Fresh no-contacts-permission startup completed in 423 ms without `SecurityException`; granted-contact startup completed in 338 ms. Five completed force-stop/HOME launches were 322–342 ms, median 337 ms.
- Twenty drawer open/close cycles and the startup stress left Comfer alive and default HOME. Filtered logs contained no ANR, fatal exception, OOM, `SecurityException`, StrictMode violation, or skipped-frame report.
- Aggregate emulator `gfxinfo` P50/P95/P99 was 17/17/18 ms with six missed vsyncs. Its 98.94% janky label results from 17 ms rounding across the 16.67 ms threshold; release decisions continue to use scenario-specific Macrobenchmark metrics and physical-device traces.
- Initial periodic WorkManager execution satisfied its network/battery constraints, downloaded a bounded 198,351-byte managed wallpaper, applied it to system/lock state, and persisted identical desired/applied paths. A forced early invocation of the next period was correctly left ENQUEUED by WorkManager's delay guard.

### 2026-08-14 — Current-build API 36 target-device pass

- Gradle-connected instrumentation passed 2/2, followed by all four release-like macrobenchmark scenarios with five iterations each.
- Android 16's first-run immersive confirmation was moved out of measured regions, and search input was made independent of installed-app singleton matches. The final macrobenchmark pass recorded cold/warm medians of 292.02/66.88 ms, drawer CPU P50/P95/P99 of 18.49/19.16/24.02 ms, and search CPU P50/P95/P99 of 18.48/32.52/33.18 ms.
- Fresh debug startup passed without contacts permission and after granting contacts plus notification-listener access. Twenty drawer cycles and five cold HOME launches (median 997 ms) left the process alive with no ANR, crash, OOM, process death, or `SecurityException`.
- Initial periodic WorkManager execution completed in 8.107 seconds, downloaded an 890,155-byte managed wallpaper, applied it, and persisted identical desired/applied paths. The supported-device matrix gate P5-02 is complete; fixture-volume and rollout gates remain.

### 2026-08-14 — Physical broad feature-session trace

- Captured user-driven launcher feature coverage on the Samsung API 30/4 GB device. The Comfer process remained alive; logcat contained no Comfer ANR, fatal exception, OOM, process death, or app-owned disk/network violation. Current activity state retained one Comfer `MainActivity` record.
- Perfetto retained the first 184.286 seconds before its 256 MiB file ceiling; full-session logcat and post-run `gfxinfo` continued for the roughly ten-minute session. The trace contains 3,089 main-thread frames: 336 at least 16 ms, 94 at least 32 ms, and 24 at least 100 ms; maximum was 457.981 ms.
- Much first-visit latency was debug-runtime work: JIT code-cache contention affected five >100 ms frames (278.704 ms total, 139.661 ms maximum), and `AndroidTileMode` verification cost 140.786 ms. These are release/baseline-profile candidates, not proven production regressions.
- One actionable app-induced contention was found. Four-way icon/resource loading held Android's global ResourcesManager lock; main input waited 104.322 ms and later 53.887 ms. Added ANR-015/P2-06 and reopened Phase 2 rather than marking the prior bounded-dispatcher change sufficient.
- Post-session aggregate debug `gfxinfo` was 9,430 frames, 44.87% janky, P50/P90/P95/P99 15/34/57/150 ms. It mixes many screens and capture overhead, so it is stress evidence, not a release KPI. Full report: `VALIDATION-PHYSICAL-FEATURE-SESSION-2026-08-14.md`.

### 2026-08-14 — ANR-015 ResourcesManager contention fix

- Reduced `packageManagerDispatcher` parallelism from four to one and combined each app's icon and label acquisition in one serialized block. Four-way CPU-bound drawable/theme processing remains unchanged.
- Unit tests, debug lint, debug assembly, and the minified/profileable benchmark target passed. Five physical cold-start benchmark iterations passed with a 727.2 ms initial-display median; maximum main ResourcesManager waits were 9.590–12.153 ms versus the 104.322 ms pre-fix observation.
- A targeted 30-second physical trace captured the complete refresh: 85 launcher icons loaded in 3,045.451 ms, active refresh returned to zero after 3,419.674 ms, no main ResourcesManager wait appeared, no frame reached 100 ms, and maximum frame duration was 40.658 ms.
- The macrobenchmark test lifecycle uninstalled the prior package before the final validation install, clearing app-private launcher configuration. Debug build, default HOME selection, contacts permission, and notification-listener access were restored; private widget/layout state could not be recovered without a prior backup.

### 2026-08-14 — Configured-widget benchmark preparation

- Added a five-iteration frame scenario that swipes from QuickListOverlay to a configured widget screen, verifies provider content, returns to the launcher, and verifies Search.
- An initial `CompilationMode.Partial` attempt reset the configured package state before measurement and failed rather than producing misleading metrics. Changed only this fixture-dependent scenario to experimental `CompilationMode.Ignore`, preventing benchmark compilation setup from touching package data; benchmark APK compilation now passes.
- Restored debug APK, default HOME, contacts permission, and notification-listener access. The user recreated the Calendar/Digital clock widget fixture for the corrected physical run.

### 2026-08-14 — ANR-011 widget navigation cache regression

- The pre-fix five-iteration physical run passed functionally but reinflated both configured widgets every time the side screen reopened. Ten `widgetInflate:*` main-thread slices measured 100.286-163.446 ms at each iteration's maximum; none reached the 500 ms warning boundary.
- Added a weak-host-keyed `AppWidgetHostView` cache. `AnimatedVisibility` may dispose its Compose wrapper, but reopening now detaches and reattaches the existing framework host view. Widget removal evicts the cache entry; process/activity-host death clears entries naturally.
- The post-fix five-iteration run passed. Iteration 0 inflated Calendar and Digital clock once (107.712 and 104.800 ms); iterations 1-4 emitted zero widget inflation slices. Frame CPU P50/P90/P95/P99 was 24.76/36.85/42.42/65.03 ms. No inflation reached 500 ms and no ANR, crash, or OOM occurred.
- Unit tests, debug lint, and the minified/profileable benchmark target passed. Ten pre/post traces and repeatable SQL are saved in `validation-artifacts/api30-feature-session-20260814/`.

### 2026-08-14 — Slider and large-fixture stress closure

- Added an exported benchmark-build-only slider activity that cannot mutate real launcher settings. Five physical iterations with 20 alternating gestures passed; frame CPU P50/P90/P95/P99 was 20.42/27.13/30.50/39.28 ms and all traces contained zero app-main slices at least 100 ms.
- Added a Compose slider regression that injected 1,000 move events and verified exactly one commit at gesture completion. The focused physical test passed in 6.166 seconds.
- Applied an actual 7680 x 4320 JPEG through the production wallpaper coordinator on the physical device. Peak PSS rose 17,224 KiB (16.82 MiB), within the 32 MiB budget; the applied marker matched and the original wallpaper was restored.
- Sent 100 rapid `reloadList()` requests through a real `AppInfoViewModel`. Coalescing produced one refresh, maximum active refreshes remained one, and the test completed in 1,019 ms.
- Added deterministic coverage for widget strike/quarantine/retry state, ImageWorker success/retry/cancellation, notification burst debounce/stop, and valid/malformed legacy folder migration. Malformed folder JSON is no longer swallowed before legacy preferences are deleted, so migration now remains atomic and retryable.
- Connected layout inspection found five normal widgets, not the required ten. The real 10-widget restoration/slow-provider UX, 300+ app inventory, and 10k-contact volume fixtures remain P5-03 work; user configuration was preserved.
- Sequential verification passed unit tests, debug lint, debug/debug-test assembly, release assembly, app benchmark assembly, and macrobenchmark assembly. Detailed evidence is in `validation-artifacts/api30-feature-session-20260814/stress_fixture_results.txt`.
