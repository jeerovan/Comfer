# Version 42 Baseline, Version 45 Findings, and Version 46 Fix Plan

- Last updated: 2026-09-03
- Source: `play_reporting.db`, table `issues`
- Baseline release: `com.jeerovan.comfer` versionCode `42`, versionName `42.0`, Git revision `af587ec056af7bee024895b63a77e95b7d1d6594`
- Current release under investigation: versionCode `45`, versionName `45.0`, Git revision `6b38c3d384e31253bef8fa40d423254b29323310`
- Candidate compatibility release: versionCode `46`, versionName `46.0`
- Baseline reporting window: 2026-07-25 14:00 UTC through 2026-08-24 14:00 UTC
- Version-45 issue window: 2026-08-31 00:00 UTC through 2026-09-03 02:00 UTC

## Goal and interpretation

Fix deterministic first-party failures immediately, reduce credible shared causes, and avoid speculative changes for stacks that show an idle main thread, a dead Android system, an OEM/driver failure, or an omitted root-cause class name.

`affected_users` is stored per issue signature. Sums below are **issue-affected-user counts**, not deduplicated people; one person can occur in several issues. `events` is likewise summed from issue rows. Improvements must be confirmed with a new version rather than inferred from these historical totals.

## Database and scope checks

The `issues` table has one row per `(package_name, version_code, issue_id)` and records type, impact, title/cause/location, full stack, representative device/API, time range, report JSON, status, and import-presence state. Indexes cover `(version_code, status, type)` and latest events.

- The database now contains versions 42, 44, and 45. Version 45 is the latest import without ambiguity: 11 issue rows, comprising 9 ANR and 2 crash signatures.
- The version-42 baseline remains 1,414 rows: 1,252 ANR signatures and 162 crash signatures.
- Three imports contain the same 1,414-issue snapshot. The `issues` primary key prevents those repeated imports from tripling the analysis.
- Version 44 contributes one import with 11 ANR signatures and no crash signatures. Version 45 contributes one import with 9 ANR and 2 crash signatures; its 11 representative issue reports have stack text, and the reports search returned 15 current samples across those issues.
- Baseline stack coverage is 1,246/1,252 ANRs and 161/162 crashes. A package name appearing anywhere in a multi-thread ANR dump does not prove that first-party code blocked the main thread, so ANRs are classified using only the captured `main` thread block.

## Baseline

| Type | Signatures | Issue-affected-user count | Events |
|---|---:|---:|---:|
| ANR | 1,252 | 4,315 | 4,910 |
| Crash | 162 | 703 | 3,971 |

### Crash families

| Family | Signatures | User count | Events | Assessment |
|---|---:|---:|---:|---|
| OEM widget code | 13 | 212 | 1,411 | Mostly asynchronous Honor Calendar code running inside the host process; app can prevent inflation of a proven-bad provider. |
| Missing activity class / protected build | 1 | 200 | 2,065 | Highest-volume crash. Report omits the missing class name; local minified APK contains every manifest component. Requires Play-generated artifact inspection. |
| Compose/View draw-layout | 57 | 128 | 155 | Framework-only draw/layout stacks. Version 42 packaged an inconsistent Compose dependency graph, now aligned as a shared-cause fix. |
| Dead Android system | 18 | 44 | 56 | Generally not preventable; individual nonessential calls can be guarded. |
| Native runtime/graphics driver | 39 | 43 | 52 | ART, HWUI, Skia, EGL/GLES, or vendor driver termination; monitor by API/device after dependency alignment. |
| Unsafe activity/URI/framework launch | 6 | 27 | 34 | Deterministic first-party boundaries; guarded in Phase 1. |
| Other | 21 | 23 | 63 | Review after the high-volume families move in the next release. |
| WorkManager startup | 1 | 13 | 112 | Deterministic missing-initializer path; fixed in Phase 1. |
| Widget bounds | 2 | 7 | 8 | Inverted `coerceIn` ranges for oversized widgets; fixed and unit-tested. |
| Device storage full | 3 | 5 | 13 | WorkManager database cannot commit because the device filesystem is full; app cannot manufacture free space. |
| Wallpaper API | 1 | 1 | 2 | Android 16 OPPO wallpaper service rejects its own description; failure is now contained and retryable. |

Highest individual crash clusters:

1. `7eed1be...`: `BaseDexClassLoader.findClass`, 200 user count / 2,065 events, Honor 200 Smart API 34.
2. `c1a42ef...` and `659578...`: Honor Calendar `SecurityException`, 191 user count / 1,322 events across the two dominant signatures.
3. `7d378a...`: `AndroidComposeView.dispatchDraw` NPE, 22 / 23.
4. `949936...`: `ComferApp.setupImageWorker` before WorkManager initialization, 13 / 112.
5. `7623e9...`: missing `OPEN_DOCUMENT_TREE` activity, 9 / 13 in the largest of two matching signatures.

### ANR attribution

| Captured main-thread evidence | Signatures | User count | Events |
|---|---:|---:|---:|
| No first-party frame on main | 1,067 | 4,026 | 4,615 |
| First-party frame on main | 185 | 289 | 295 |

93.3% of ANR user count and 94.0% of ANR events have no first-party frame on the captured main thread. The dominant `nativePollOnce` / “No focused window” report has an idle main looper in its representative dump, so it is not evidence for a CPU or I/O fix in a particular app method.

The largest actionable first-party main-thread groups are activity-launch Binder waits in `CommonUtil.handleStartActivity` (61 user count across line variants), accent normalization during search (10 / 13), startup/activity creation frames, and smaller Compose input/layout frames. Activity launch itself crosses a system Binder boundary and must not be moved to a background thread speculatively; search filtering can safely move off main and has been changed.

## Version 45 production evidence (2026-09-03)

The Play Developer Reporting API was queried with `versionCode = 45`. The issue search covers 2026-08-31 00:00 UTC through 2026-09-03 02:00 UTC; normalized hourly metrics are currently fresh only through 2026-09-02 14:00 UTC.

| Type | Signatures | Issue-user count | Events |
|---|---:|---:|---:|
| ANR | 9 | 22 | 22 |
| Crash | 2 | 3 | 16 |

`issue-user count` is still the sum of per-signature distinct-user values, not a deduplicated audience. The reports search returned 15 current samples: 10 Honor, 2 TECNO, 2 Samsung, and 1 Redmi. Hourly normalized rows show v45 activity across 13 brands; the rounded Honor normalization denominator reaches 200 active users in one hour. This is meaningful Honor exposure, not the absence-of-exposure problem that limited the v44 canary.

| Issue | Type | Users / events | Evidence and disposition |
|---|---|---:|---|
| `7eed1be...` | Crash | 2 / 15 | `ClassNotFoundException` during `AppComponentFactory.instantiateActivity`; reproduced on Honor X7c API 34 and Honor X6c API 35. **Rollout blocker.** |
| `df0e1dd...` | ANR | 12 / 12 | Dominant no-focused-window cluster; available main-thread samples are idle in `MessageQueue.nativePollOnce`. Recurs from v42 and v44, but does not identify app work to move. |
| `a6de15e...` | ANR | 2 / 2 | Input timeout with idle main-loop sample; recurs from v42/v44. |
| `a07df57...` | ANR | 2 / 2 | Input timeout with idle main-loop samples on Honor API 36; recurs from v42/v44. |
| `c002f2c...` | ANR | 1 / 1 | No-focused-window timeout with idle main-loop sample; recurs from v42/v44. |
| `db1f7de...` | ANR | 1 / 1 | Main thread is in framework vsync scheduling / ART stack capture; no first-party frame; recurs from v42. |
| `db888cf...` | ANR | 1 / 1 | Main thread waits in `HardwareRenderer.setStopped`; no first-party frame. New, monitor. |
| `97dbd8f...` | ANR | 1 / 1 | Tagged as `SystemJobService`, but the main thread is blocked in window relayout and the worker pool is not blocked in Comfer code. This is not a recurrence of the fixed WorkManager-initializer crash. |
| `50ef88a...` | ANR | 1 / 1 | Compose snapshot application during recomposition; no first-party main frame. New single event, monitor. |
| `59680af...` | ANR | 1 / 1 | Android/Compose BiDi text measurement; no first-party main frame. New single event, monitor and correlate with the displayed text/screen. |
| `62bcb23...` | Crash | 1 / 1 | API-36 Samsung `FrameLayout.onMeasure` NPE through Compose `AndroidViewHolder`. This resembles the v42 AndroidView interop family, but the sample omits the widget provider and composable site. |

All nine v45 ANR representative `main` blocks contain zero `com.jeerovan.comfer` frames. No current sample points to the U-shaped drawer, weather/location, backup/restore, search normalization, or an app method doing synchronous I/O. The old WorkManager startup crash `949936...` is absent.

### Evidence sufficiency decision

- **Enough to enforce the rollout gate:** yes. The pre-publish gate said to halt on any v45 crash or recurrence of `7eed1be...`; both conditions occurred. Do not expand v45 beyond the current cohort while the launch crash is open.
- **Enough to disprove Automatic Protection as a required cause:** yes. The Play-generated v45 universal APK and the API-32+ base APK applicable to the affected devices both name `com.jeerovan.comfer.ComferApp`, contain no Pairip application wrapper, and pass the 30-component manifest-to-DEX check.
- **Enough to identify a targeted compatibility candidate:** yes. Git and artifact history prove that v33 deleted `SubscriptionActivity` while leaving it declared through v35, and v32 could put that activity on the launcher's task stack. This makes an old-name compatibility activity a justified candidate for an upgrade-path fix. It is not yet proven to be the class requested by v38-v45 because Play redacts the exception message and those versions' current manifests/DEX are internally consistent.
- **Enough to fix the AndroidView crash safely:** not yet. The stack proves View interop but does not distinguish the cached `AppWidgetHostView` path from the notification-icon `ImageView` path or identify a widget provider. The cached-host-view reparenting path is the leading source hypothesis because it explicitly removes a live view from its previous parent, but changing it trades against widget reinflation/ANR risk and needs a targeted overlapping-enter/exit regression test first.
- **Enough for another generic ANR optimization:** no. The captured main stacks are framework/idle stacks. The next useful evidence is lifecycle/focus timing and provider/screen correlation, not speculative dispatcher changes.

## Phase status

| Phase | Objective | Status |
|---|---|---|
| 0 | Establish version-42 database baseline and reproduce build graph | DONE |
| 1 | Apply deterministic first-party crash and ANR fixes | IMPLEMENTED; full local verification complete |
| 2 | Resolve missing-activity-class crash | COMPATIBILITY TOMBSTONE IMPLEMENTED; local/API-24 upgrade validation passed, Honor telemetry pending |
| 3 | Attribute no-focus and remaining first-party ANRs | INSTRUMENTATION/DEVICE EVIDENCE REQUIRED; all v45 representative main stacks lack first-party frames |
| 4 | Reduce residual OEM, Compose/View, native, and resource failures | ONE ANDROIDVIEW CRASH CANDIDATE; targeted lifecycle regression required before code change |
| 5 | Stage rollout and close against version-42 baseline | STOP GATE TRIGGERED; do not expand current v45 cohort |

## Phase 1 — Implemented cumulative fixes

### 1.1 WorkManager initialization fallback

- Evidence: issue `949936...`, 13 user count / 112 events. `WorkManager.getInstance()` throws during `ComferApp.onCreate()` because the Play-protected `com.pairip.application.Application` path has no initialized WorkManager.
- Change: preserve AndroidX's normal manifest initializer. If and only if `getInstance()` reports that initialization is absent, initialize with a default `Configuration` and then enqueue the unique periodic worker.
- Why this shape: implementing `Configuration.Provider` while retaining the default initializer is an invalid dual-initialization setup; release lint explicitly rejected it during validation.
- Acceptance: cold start and post-update start work with both the normal local manifest and Play-protected internal-test APK; only one unique `ImageWorker` exists.

### 1.2 Block the proven-crashing Honor Calendar widget provider

- Evidence: six Honor Calendar signatures total 197 user count / 1,339 events. The dominant exceptions occur later on the provider's own `HandlerThread`, after widget creation has returned, so a `try/catch` around `AppWidgetHost.createView()` cannot contain them.
- Change: reject `com.hihonor.calendar/*` before using a cached view or inflating RemoteViews, evict any cached host view, and show a non-retryable compatibility message.
- Tradeoff: the affected OEM Calendar widget is unavailable in Comfer. This is preferable to repeatedly terminating the launcher process.
- Acceptance: configure/reopen the Calendar widget on representative Honor API 33, 34, and 36 devices; Comfer remains alive and shows the compatibility message.

### 1.3 Make external activity boundaries non-fatal

- Handle missing or forbidden document-tree pickers and restore the setting to off.
- Route the FiFe/privacy/browser fallback links through the existing safe URL launcher.
- Guard the package-replaced receiver's background activity start.
- Guard `reportFullyDrawn()` on OEM multi-user builds that reject its internal broadcast.
- Guard nonessential UI sound playback when Android's audio service is dead.
- Directly addressed database families: six launch/report signatures, 27 user count / 34 events, plus small dead-system sound signatures.

### 1.4 Repair widget drag/resize range invariants

- Evidence: two `WidgetInstance` `IllegalArgumentException` signatures, 7 / 8, at `coerceIn` calls whose maximum becomes smaller than the minimum when a saved widget is wider/taller than the current window or grid.
- Change: clamp the maximum to the minimum before coercion and clamp oversized spans to the first legal grid column.
- Regression tests: oversized pixel and grid-span cases in `WidgetGeometryTest`.

### 1.5 Align the Compose runtime

- Version 42 declared BOM `2025.10.00` but also directly forced UI `1.11.3`, animation `1.11.2`, foundation `1.10.0`, and runtime/text pieces `1.10.0`, while Material stayed on the BOM's `1.9.3` family.
- Change: remove the five direct version overrides and update the single stable BOM to `2026.08.00`. This officially targets Compose 1.12 for compileSdk 37 and requires AGP 9.1.2+, which the project satisfies with compileSdk 37 / AGP 9.2.1.
- Dependency verification: post-fix `dependencyInsight` resolves the UI family to `1.12.0` through BOM `2026.08.00`; the app compiles and minifies against the aligned graph.
- References: [Android Compose BOM guidance](https://developer.android.com/develop/ui/compose/bom) and [Compose August 2026 stable release](https://developer.android.com/blog/posts/what-s-new-in-the-jetpack-compose-august-26-release).
- Expected impact: addresses a credible shared cause for 57 Compose/View draw-layout signatures (128 user count / 155 events). It is not counted as proven until new release telemetry confirms the reduction.

### 1.6 Move search normalization/filtering off main

- Evidence: `CommonUtil.removeAccents` is the captured first-party main frame for 10 ANR user count / 13 events.
- Change: app/contact projections now run on `Dispatchers.Default` through key-cancelled `produceState` work rather than synchronously during composition.
- Acceptance: rapid typing with a 300+ app inventory and 10k-contact fixture produces no app-main slice over 100 ms and never publishes results from an older query after a newer key restarts the producer.

### 1.7 Contain wallpaper service rejection

- Evidence: API 36 OPPO issue `4375d5...`, 1 / 2, where `WallpaperManager.setBitmap()` throws `IllegalArgumentException` for a null OEM `WallpaperDescription` component.
- Change: convert wallpaper application to a Boolean outcome, log `IOException`/runtime service rejection, do not mark a failed image as applied, and let worker flows return retry rather than crash.

### 1.8 Replace mutable Accompanist drawable rendering

- Evidence: five `DrawablePainter` signatures total 9 issue-user count / 19 events, within the wider Compose/View family.
- Change: remove `accompanist-drawablepainter`; snapshot app, icon-pack, and widget-preview drawables into owned ARGB bitmaps before Compose renders them. Invalid intrinsic sizes are clamped to `1x1`, the largest edge is capped at 512 px while preserving aspect ratio, original bounds are restored, and rasterization failures fall back to a transparent painter.
- Why this shape: the old painter retained mutable OEM drawables and could execute their drawing code repeatedly during Compose frames. An owned bounded snapshot isolates Compose from later drawable mutation/recycling and prevents unexpectedly large allocations.
- Regression tests: invalid, normal, and oversized dimension cases in `DrawableExtTest`.

### 1.9 Block additional proven asynchronous OEM widget failures

- Evidence: Honor Gallery contributes 5 signatures / 13 issue-user count / 70 events; Huawei weather contributes 2 / 2 / 2. Generic Huawei/Honor calendar package paths also recur. Like the Honor Calendar failures, the exceptions run asynchronously after widget inflation returns.
- Change: reject `com.hihonor.gallery/*`, `com.huawei.android.totemweather/*`, and `com.android.calendar/*` at the same pre-inflation boundary as `com.hihonor.calendar/*`.
- Tradeoff: those OEM widgets are unavailable inside Comfer; ordinary widgets and other providers remain unaffected.
- Regression test: all known unsafe package prefixes are rejected, while an unrelated provider remains allowed.

### 1.10 Tolerate malformed OEM system proxy configuration

- Evidence: one crash signature / 1 issue-user count / 4 events reaches OkHttp `RouteSelector` after the platform `DefaultProxySelector` exposes an invalid proxy port.
- Change: wrap the platform proxy selector for the Ktor/OkHttp client. Runtime failures or an empty proxy list fall back to `Proxy.NO_PROXY`; valid platform selections are preserved.
- Regression tests: throwing and valid proxy-selector behavior in `SafeProxySelectorTest`.

## Phase 2 — Missing-activity `ClassNotFoundException`

This is still the highest-volume unresolved production crash. The earlier database snapshot recorded 200 issue-user count / 2,065 events on v42; the wider API history below shows that it spans several installed versions.

### Version history

Play issue `7eed1be192b4c2a320968e14c6d5116e` appears in the following per-version searches. Counts from adjacent windows are shown separately because distinct users cannot be safely deduplicated across windows.

| Version | Available issue evidence |
|---:|---|
| 33-34 | No matching issue returned from monthly searches covering 2026-03-01 through 2026-09-03. This is absence in available telemetry, not proof that the builds could not fail. |
| 35 | 1 user / 7 events; last event 2026-07-08. |
| 36-37 | No matching issue in the searched history. |
| 38 | 18 / 177 through 2026-07-23, then 1 / 32 through 2026-08-01. The earliest sampled report available is 2026-07-06 on an Honor X6a/API 34; this predates the sampled v35 event because both versions were installed concurrently. |
| 39 | 2 / 9; last event 2026-07-31. |
| 40 | 12 / 133 through 2026-08-26, then 1 / 12 through 2026-09-01. |
| 41 | No matching issue in the searched history. |
| 42 | 263 / 2,792 through 2026-08-28, then 96 / 966 through 2026-09-02. |
| 43 | No matching issue; this version had limited/rejected release exposure. |
| 44 | 2 / 28 through 2026-09-02. This supersedes the earlier zero-crash 48-hour checkpoint. |
| 45 | 2 / 15 through 2026-09-02. |

Every sampled device for this issue is Honor, across API 34-36 and multiple models (X5b Plus, X5d, X6a, X6c, X7c, X7d, Honor 200 Lite, and Honor 200 Smart). Event volume is approximately ten or more launches per affected user in the larger cohorts, consistent with the launcher repeatedly trying to resume the same bad activity/task state.

### Source and artifact history

Confirmed evidence:

- The crash occurs while `AppComponentFactory.instantiateActivity()` launches an activity, but both the stored report and live Play samples omit the missing class name.
- The stored v42 representative row is Honor 200 Smart API 34. Version-45 samples now reproduce the same issue ID on Honor X7c API 34 and Honor X6c API 35: 2 users and 15 launch events in the current window.
- Commit `0d982a9` (2026-03-12) removed the 575-line `SubscriptionActivity`, `BillingRepository`, and billing dependencies while bumping version 32 to 33, but did not remove `<activity android:name=".SubscriptionActivity">` from the manifest. Version 32 had a real Settings action that launched this activity in the same task.
- The stale manifest declaration remained in v33, v34, and v35. Commit `21ebf7b` removed it on 2026-05-24, before the v36 release.
- The Play-generated v35 protected universal, protected API-29+ base, and unprotected API-29+ base APKs all fail the component check with exactly one omission: `com.jeerovan.comfer.SubscriptionActivity`. This proves a release defect independent of Automatic Protection and strongly fits the v35 crash, although the redacted report prevents a byte-for-byte class-name match.
- The same check passes every protected/unprotected artifact inspected for v38, v39, v40, and v42, as well as the unprotected v44/v45 artifacts. Protected builds contain one additional Pairip component, hence 31 checked components versus 30 in their unprotected counterparts.
- The protected artifact has a single `classes.dex`; this is not a missing feature-split or secondary-DEX case.
- Play protection replaces the manifest application with a Pairip application subclass. That class is present, extends `ComferApp`, and invokes the Pairip license client. This is the meaningful protected/unprotected startup difference.

### Cause assessment

The most coherent explanation is an upgrade/task-restoration compatibility failure:

1. v32 could leave `SubscriptionActivity` at the top of Comfer's activity task.
2. v33 removed that class without retaining a compatibility implementation. Because Comfer is a HOME/launcher app, the system repeatedly resumes its task rather than treating it like an occasional app screen.
3. The stale manifest made v33-v35 directly inconsistent. Even after the manifest was corrected, an Honor device upgrading with an already-live or OEM-restored task/component record can continue asking the new APK to instantiate the deleted class. That explains recurrence in internally consistent v38-v45 APKs and the unusually high event count per affected user.

This is a high-confidence hypothesis, not yet a proven class-name match for v38-v45. AOSP's persisted-task restore path normally resolves activities against the current package, so recurrence across reboot/clean install would instead point to a stale/partial installed split or an Honor PackageManager/class-loader defect. The clean-install-versus-upgrade distinction is therefore decisive.

Platform references: Android documents that a normal Recents task resumes the last activity the user invoked ([Recents screen](https://developer.android.com/guide/components/activities/recents)); AOSP's disk-persisted activity restore resolves the saved intent against the currently installed package and rejects it when no `ActivityInfo` exists ([`ActivityRecord.restoreFromXml`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android13-security-release/services/core/java/com/android/server/wm/ActivityRecord.java#9184)). These support the task-state lead while also explaining why a reboot/clean-install recurrence would weaken it.

The v44/v45 unprotected experiment establishes one boundary: Automatic Protection is **not required**. The issue occurs in protected v35/v38/v39/v40/v42 and unprotected v44/v45. Likewise, v38-v45 artifact checks provide no evidence for a current R8 keep-rule failure, ordinary bundle-packaging omission, missing feature split, or secondary-DEX problem.

Implemented release guard: `scripts/verify_apk_components.py` compares every merged-manifest application component with the APK's defined DEX classes and fails on any omission.

Local compatibility verification (2026-09-03):

- Added `SubscriptionActivity` under its exact historical package/class name. It contains no subscription or billing code. When restored above another activity it immediately finishes; when restored as the task root it calls `finishAndRemoveTask()`.
- The manifest registers it as non-exported, excluded from Recents, and `noHistory`. The API-24 focused instrumentation suite passes both the root-task removal and underlying-Settings restoration branches (2/2 tests).
- Reproduced a v32 debug build from commit `c02ee17`, launched its real subscription screen above `SettingsActivity`, confirmed both records in the task, and performed an in-place update to the current debug APK. The update completed without a crash; on this AOSP emulator, package replacement restarted `MainActivity` and discarded the obsolete task before the tombstone was needed. This validates the normal upgrade path but does not reproduce Honor's suspected stale-record behavior.
- `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest`, and the focused connected suite pass.
- The signed minified v46 release APK builds successfully. Its merged manifest retains the exact `com.jeerovan.comfer.SubscriptionActivity` name and required restrictions, R8 keeps the class and `onCreate`, and the component verifier passes all 31 declared components. APK SHA-256: `a0ee9fba0679f8d3c9bb1791806b2fe8aff88a414eceba3080da36eac7c96f05`.
- The signed v46 AAB is `app/build/outputs/bundle/release/app-release.aab`, SHA-256 `e074e8a66b2bf8ad5741a47d54d60790bda6a4af440c335e7d57d50e82334ecf`; JAR verification reports `jar verified`. The packaged APK reports versionCode `46` / versionName `46.0`, APK Signature Scheme v2 verification passes with the configured upload certificate, and it contains none of the prohibited broad storage/media permissions.

Play-generated internal artifact verification (2026-09-03):

- The Publisher API reports internal release `46 (46.0)` with status `completed`; its release notes identify the compatibility activity change.
- Automatic Protection artifacts are absent. The generated APK group contains the normal split/universal set only and is signed with the expected Play app-signing certificate, SHA-256 `7de810dc8919178d0642d7636f94e88abfead2d9416f6bd929981aaef32a4f4b`.
- The Play-generated universal and highest-targeted API-32+ base APKs both pass the 31-component manifest-to-DEX check. Their manifests retain `SubscriptionActivity` as non-exported, excluded from Recents, and `noHistory`; the universal DEX defines the exact class, constructor, and `onCreate(Bundle)` method.
- Both artifacts report versionCode `46` / versionName `46.0` and contain none of `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, or `READ_MEDIA_AUDIO`. Universal SHA-256: `89283467ffa707083a367b4c0ee3babb65160ee192edded213c38e3fb2b9084b`; API-32+ base SHA-256: `985ed4ff4361b03bf738ccda93f39c042d34468908a6de1912a27388caa40f11`.
- The Play-generated universal APK clean-installed on the API-24 emulator, cold-launched `MainActivity` in 771 ms, remained focused/resumed, and emitted no inspected `AndroidRuntime`, `ActivityManager`, `ComferApp`, or `BackupRestore` error.
- Production has not yet received v46. The API currently reports v45 halted for Sudan (`SD`) alongside completed v42, so the next production action is a replacement v46 staged release rather than expansion of v45.

Next actions, in order:

1. Keep v45 from expanding; preserve issue `7eed1be...`, its v45 samples, affected models/API levels, Git revision, and APK hashes in the escalation packet.
2. **Implemented:** restore a minimal `com.jeerovan.comfer.SubscriptionActivity` compatibility/tombstone and register it non-exported, excluded from Recents, and without history. It finishes immediately to reveal a valid underlying activity, or removes the task when the obsolete activity is its root. No billing behavior is restored. Keep the old class name for several releases.
3. **Completed for internal v46:** inspect the Play-generated artifacts, verify signing/version/permissions, confirm the compatibility activity remains in the manifest and DEX after Play processing, and clean-install/cold-launch the universal APK.
4. **Partially validated:** the API-24 v32 in-place upgrade with the real subscription activity at the top of its task passes, as do direct root and above-Settings tombstone tests. Still test v35, HOME/Recents relaunch, process death, reboot, repeated launch, and an Honor configuration when available.
5. Ask Google Play support for the unredacted missing activity class and installed-split state. If it is not `SubscriptionActivity`, retain the shim as upgrade hardening but do not call the production issue fixed.
6. If an affected user can cooperate, collect `pm path com.jeerovan.comfer`, `dumpsys package com.jeerovan.comfer`, installer source, task/activity state, and a launch logcat before reinstalling. Then clean-install from Play and compare.
7. If the shim does not close the issue, add a narrowly scoped `AppComponentFactory` diagnostic/fallback only after verifying AppCompat/Core component instantiation and every declared activity. Do not broadly redirect arbitrary missing components in the first attempt.
8. Do not add blanket `-keep` rules, force multidex, or disable app-bundle splits without evidence naming a missing current class or an incomplete delivered split.

Exit criteria: the v32/v35 upgrade regression is covered, the exact requested/missing class or a reproducible installation-state failure is identified, a targeted mitigation is verified on an affected Honor configuration, and the issue remains absent through meaningful Honor/API 34-35 exposure. Zero recurrence on a small cohort alone is no longer sufficient.

## Phase 3 — Remaining ANRs

Do not optimize an arbitrary method from the dominant idle/no-focus dumps. Use a new release and capture evidence closer to the timeout:

1. Add release-safe timestamps/counters for process start, `MainActivity` create/start/resume, first layout, first focus, startup-gate readiness, app-inventory completion, and widget inflation provider/duration.
2. Run cold HOME selection, app update, screen unlock, rotation/configuration, and rapid external-app launch/return scenarios on the dominant low-end/Honor/TECNO/Samsung API/device groups.
3. Capture Perfetto/system traces for any >1 s first-focus or launch Binder wait. Correlate “No focused window” with activity/window-manager state rather than the later idle stack.
4. Recheck `CommonUtil.handleStartActivity` after unsafe launch guards. If Binder stalls remain, reproduce the exact target package/OEM policy before changing launch semantics.
5. Run the existing startup, search, configured-widget, and app-drawer macrobenchmarks with large fixtures.

Exit criteria for the ANR phase:

- No app-main slice over 100 ms in the named scenarios.
- No startup path reaches the input timeout.
- A post-v45 diagnostic release's ANR rate and each dominant no-focus cluster improve against version 42/v45 over the same window and device mix.

Current source disposition: search normalization is moved off main, deterministic unsafe launches are guarded, and no remaining high-volume ANR stack identifies a safe first-party operation that can be moved or removed. Activity-start Binder waits must stay on the main thread by Android contract. Version 45 confirms that the dominant samples are still idle/framework-only at capture time. The remaining work in this phase is measurement and device/production evidence, not another speculative code change.

## Phase 4 — Residual crash work

Completed source work:

1. Compose/View dependencies are aligned, and all old Accompanist mutable-drawable paths now use bounded owned bitmap snapshots.
2. All OEM widget package families directly evidenced by asynchronous vendor `SecurityException` stacks are blocked before inflation.
3. Malformed system proxy selection is contained at the HTTP client boundary.

Monitor-only residuals:

1. **Storage full:** WorkManager cannot commit while the device filesystem is full. Avoid unnecessary app-owned cache growth, but do not claim these 3 signatures / 5 issue-user count / 13 events are app-fixable.
2. **Dead system/native/driver:** monitor by device/API after Compose alignment. Do not catch fatal VM/graphics corruption globally or suppress process corruption signals.
3. **OEM widget detach internals:** `ViewFlipper`/`TextClock` receiver-unregistration failures happen inside vendor/system child views. The known crashing providers are blocked; there is no safe general app-level catch for an arbitrary child view's asynchronous detach work.
4. **Profile installer/system resource failure:** the observed Choreographer/display event receiver failure is a system resource condition. Disabling profile installation would trade away startup performance without evidence that it fixes the device state.
5. **Unknown/low-volume:** re-rank after one stable version-44 window and promote only recurring first-party roots.

New version-45 candidate:

1. **Cached widget host view reparenting:** issue `62bcb23...` is a `FrameLayout.onMeasure` NPE reached through `AndroidViewHolder`. Version 42 had 27 AndroidViewHolder-on-measure signatures totaling 49 issue-user count / 54 events, while v45 has one current event. `WidgetInstance` caches an `AppWidgetHostView` and its `AndroidView.factory` removes that same view from any old parent, which can overlap an `AnimatedVisibility` exit/entry and mutate the old view hierarchy during measurement. Before changing production behavior, add a regression test that keeps the outgoing widget composition alive while the incoming composition requests the same widget ID. The intended fix is single-owner cache acquisition/release (or fresh inflation only for overlap), never unconditional live-parent removal. Verify reopen latency and no widget-inflation ANR regression.

## Phase 5 — Verification and rollout

Prepared locally:

- Version is bumped to versionCode `43`, versionName `43.0`.
- Signed minified release APK: `app/build/outputs/apk/release/app-release.apk` (`SHA-256 1765791bfc587874bcbe0aa468260babd9089eb8b87f880b4c1c4c69fbf52e52`).
- Signed release bundle: `app/build/outputs/bundle/release/app-release.aab` (`SHA-256 be52a839b61e9bea48dccf1f16fe6efd7f7e2b4cb10b7a9aab2210f9aa1d125c`).
- The APK reports versionCode `43` / versionName `43.0` and passes the 30-component manifest-to-DEX check.
- Release signing loads `storeFile`, `storePassword`, `keyAlias`, and `keyPassword` from the ignored root `jks-key.properties` file, with private Gradle properties or `COMFER_UPLOAD_*` environment variables as fallbacks. Partial configuration fails the build instead of silently producing an unsigned release.
- `jks-key.properties` is Git-ignored and restricted to owner-only filesystem permissions (`0600`).
- Gradle `validateSigningRelease`, APK Signature Scheme v2 verification, and AAB JAR-signature verification pass. The APK and AAB use the same certificate (`SHA-256 52ef555d772b43fbd473a4e4dc03d7211fd146d3b0e06bca14db7a46206431a0`).

Verified after Play processing:

- Google Play reports version 43 completed on the internal track; production remains version 42.
- The Play-generated universal APK is versionCode `43` / versionName `43.0`, is signed with Play's app-signing certificate (`SHA-256 7de810dc8919178d0642d7636f94e88abfead2d9416f6bd929981aaef32a4f4b`), and has APK v2/v3 plus Source Stamp verification.
- The universal APK (`SHA-256 05a7125affb8d4c60b3d9c7abd31e16562006856ca4f272da60140c6b6ca0f45`) and API-24, API-29, and API-32+ base variants all pass manifest-to-DEX verification.
- Its manifest names `com.jeerovan.comfer.ComferApp` directly and neither its manifest nor DEX definitions contain Pairip classes. This confirms Automatic Protection is absent from the delivered artifact.
- The Play-generated universal APK clean-installed on the API-24 emulator, cold-launched `MainActivity` in 883 ms, remained resumed, and emitted no inspected process exception/error.

Production-policy correction:

- Play rejected the v43 production submission because active artifacts requested broad photo/video permissions. Despite that review result, the Publisher API still reports v43 as an `inProgress` 2% production release alongside completed v42; do not assume the rejected review removed it from the track.
- Version 44 removes `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and `READ_MEDIA_AUDIO` at manifest-merge time. Wallpaper-folder access already uses `OpenDocumentTree()` with a persisted URI grant and does not require broad media access.
- The signed v44 release APK (`SHA-256 0fd880cc5da072bc618631f40f67d5de07804dec5dbf45d09687e6b47cf541a7`) and AAB (`SHA-256 b294719402fd5d70d659c2db87871294c24ea36fe9ed8fd6a33618e23e2756af`) build successfully. The packaged APK reports versionCode `44` / versionName `44.0`, contains none of those broad storage/media permissions, and passes APK Signature Scheme v2 verification with the expected upload certificate.
- On 2026-08-26 the API reported internal v44 completed, production v43 at 2% plus completed v42, and an obsolete completed alpha v3 release. Deactivate alpha v3 unless it is intentionally needed, and keep Automatic Protection disabled for v44.
- The rejected v43 canary is superseded by v44. Play subsequently accepted v44 as a staged production release while v42 remains the fallback, so continue evidence-gated expansion of v44 rather than treating v42 as a demonstrated policy blocker.

Verified after Play processing for version 44:

- Google Play reports `44 (44.0)` completed on the internal track.
- The Play-generated universal APK is versionCode `44` / versionName `44.0`, contains none of `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, or `READ_MEDIA_AUDIO`, and names `com.jeerovan.comfer.ComferApp` directly. Its DEX contains `ComferApp` and no Pairip class was found, confirming Automatic Protection is absent.
- The universal APK (`SHA-256 49e2bdb02469fb255719d3820370c0a293a8d54d1be66242019dc6b52e0d83f6`) is signed with Play's app-signing certificate (`SHA-256 7de810dc8919178d0642d7636f94e88abfead2d9416f6bd929981aaef32a4f4b`), passes APK v2/v3 and Source Stamp verification, and passes the 30-component manifest-to-DEX check.
- It clean-installed on the available API-35 x86_64 emulator, cold-launched `MainActivity` in 400 ms, remained the resumed activity with a live process, and produced no inspected app exception, missing-class failure, fatal crash, or ANR. The only error-level launch lines were emulator `eglCodecCommon` parameter warnings.

Post-48-hour production checkpoint (2026-08-31):

- Publisher API state: v44 is an `inProgress` 5% production release; v42 remains the completed fallback release. Internal v44 remains completed.
- The complete v44 error-issue window from 2026-08-26 00:00 UTC through 2026-08-31 04:00 UTC contains 11 ANR signatures, 14 issue-user counts, 14 events, and **zero crash signatures**.
- At this time-bounded checkpoint, neither missing-class crash `7eed1be192b4c2a320968e14c6d5116e` nor WorkManager startup crash `949936057a675872942624878f1a41d2` had recurred, and there was no v44 first-party crash group. A later search through 2026-09-03 superseded the first conclusion: `7eed1be...` reached 2 users / 28 events on v44.
- Fresh normalized daily vitals through August 28 report v44 user-perceived crash rate `0.0000` on all three available days. On August 28, v44 user-perceived ANR rate was `0.0060` (0.60%) versus v42 `0.0066` (0.66%); this is not evidence of a regression.
- The daily v44 normalization counts were approximately 60, 200, and 300 active users. These are rounded daily observations and must not be summed as unique people.
- Device-brand breakdown provides approximately 160 Honor daily-user observations. Six Honor/API 34-36 ANR signatures account for 9 issue-user counts/events, across HONOR X5b Plus, Magic7 Lite, and X9d samples; there were no Honor crashes and no missing-class recurrence.
- Nine of the 11 v44 ANR signatures were already present in v42. Only three representative main-thread blocks contain first-party frames: the known `UshapedAppList` Compose recomposition and `CommonUtil.handleStartActivity` Binder launch groups, plus one new `EffectTextBlock` native text-measurement wait. Each first-party group has one user/event. The other eight samples are idle main-loop, OEM instrumentation, or native renderer waits.
- Gate decision: the 5% checkpoint passes. Advance to 25%, hold at least another 48 hours, and re-fetch the same metrics. Halt expansion for any v44 crash, recurrence of either critical startup issue, or a sustained normalized user-perceived ANR rate materially above the v42 comparison cohort.

## Version 45 pre-publish audit (2026-08-31)

Scope and result:

- Reindexed the complete patched repository: 2,059 code nodes and 7,088 relationships, with zero skipped source files. Source review covered the v44-to-v45 feature delta, especially backup/restore, weather/location, landscape layouts, gesture guides, app-title settings, widget positions, and the U-shaped app drawer.
- Version 45 adds a substantial feature delta over v44. The backup/restore path keeps archive parsing, hashing, bitmap-bounds checks, Room snapshot replacement, preference replacement, journaling, and rollback on `Dispatchers.IO`; size, entry-count, duplicate-entry, identifier, package-list, widget, checksum, and wallpaper limits are enforced. No synchronous main-thread archive/database work was found.
- The release manifest is versionCode `45` / versionName `45.0`. Its merged permissions contain `ACCESS_COARSE_LOCATION` for weather, but none of `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, or `READ_MEDIA_AUDIO`.

Pre-release fixes applied cumulatively:

1. **U-shaped drawer index and key safety:** normalize indexes with overflow-safe `Long` modular arithmetic, so a long negative/positive fling cannot address `apps[-1]` or another out-of-range slot. Key each visible occurrence by its unwrapped logical index: the key follows the app as it moves between slots, while remaining unique when a small app list repeats around the U-shape.
2. **Drawer animation continuity:** restore the proven per-event `snapTo` drag behavior. The first v45 hardening attempt cancelled and restarted the pending update for every pointer event and used fixed slot keys; together those changes made icons jump between positions. Logical occurrence keys preserve visual identity without reintroducing duplicate package-name keys.
3. **Weather location Binder isolation:** provider discovery, last-known-location reads, registration, and cleanup now run in the flow's `Dispatchers.IO` context. Location callbacks still use the main looper as required by the UI consumer.
4. **Weather network bounds:** Ktor request, connect, and socket timeouts are each 15 seconds, preventing a stalled provider/network from holding the refresh coroutine indefinitely.
5. **Normal text fast path:** unrotated, uncurved clock/date/battery/weather text uses Compose `Text` and its normal cached layout pipeline. The custom Android `Paint.measureText`/path renderer remains only for an enabled rotation or curve, reducing exposure to the v44 single-event native text-measurement ANR without removing the visual effect feature.
6. **Regression coverage:** `UShapedAppListLayoutTest` now covers negative, positive, `Long.MIN_VALUE`, `Long.MAX_VALUE`, maximum app-count, and empty-list index behavior.

Verification:

- `testDebugUnitTest`, `lintDebug`, and `:app:assembleDebugAndroidTest`: passed after the fixes.
- Focused connected suite: 12/12 passed on the Android 7 `Small_Phone` emulator and 12/12 passed on the Samsung SM-A305F / Android 11 physical device. This includes refresh-burst stress, widget-inflation guards, settings smoke, setting sliders, and the new landscape/gesture layout tests.
- The full connected invocation ran those same 12 tests successfully and failed only the opt-in `Wallpaper8kStressTest`, whose explicit external `stress_wallpaper_8k.jpg` fixture was not installed. This is recorded as missing test data, not an app failure.
- Samsung cold start: the first debug launch after install and initialization completed in 5,513 ms; the repeat force-stop/cold-start completed in 1,095 ms. `MainActivity` remained resumed and the inspected `AndroidRuntime`, `ActivityManager`, `ComferApp`, and `BackupRestore` error streams were empty.
- U-shaped drawer regression check: repeated slow and fast bidirectional swipes were recorded on the Samsung SM-A305F after restoring the drag path and logical keys. Intermediate icon positions remained continuous, the activity remained focused/resumed, and the inspected crash/error streams were empty.
- `:app:bundleRelease`: passed with release-vital lint, R8, upload-keystore validation, bundle packaging, and signing. The signed post-drawer-fix AAB is `app/build/outputs/bundle/release/app-release.aab`, SHA-256 `1e3ac8c147690e29c0af5364c6dc374c75d3641effbe01107ee11b5c98cb7518`; JAR signature verification reports `jar verified`.
- `git diff --check`: passed.

Pre-release decision (superseded by production evidence below):

- **Ready for a controlled production rollout**, subject to reviewing and committing the audit changes. Local evidence cannot prove the absence of OEM-only or statistically rare ANRs, and no Honor hardware was available; staged Play telemetry remains the final verification layer.
- A 50% rollout restricted to one country is acceptable as the data-gathering cohort if rollback is kept available. Check v45 issues and normalized crash/ANR rates after 24 hours and again after 48 hours before adding countries or raising exposure.
- Halt or roll back for any v45 crash, recurrence of missing-class issue `7eed1be192b4c2a320968e14c6d5116e`, recurrence of WorkManager issue `949936057a675872942624878f1a41d2`, or a sustained normalized user-perceived ANR regression against the v42/v44 comparison cohorts. Track the U-shaped drawer, `EffectTextBlock`, weather/location, and backup/restore stacks separately so new feature regressions are not hidden inside the aggregate rate.

Post-release decision (2026-09-03):

- **The stop gate failed.** Version 45 has two crash signatures, including recurrence of `7eed1be...` with 15 launch events. Do not expand the current 50%/single-country cohort. Prefer halting the staged rollout while the support/escalation packet is prepared; roll back if the affected-user/event count continues rising.
- No current ANR sample identifies a first-party v45 feature regression. In particular, the v44 `UshapedAppList` first-party issue is absent, and no representative main stack points to weather, backup/restore, search, or drawer code.
- Play-generated v45 universal APK: SHA-256 `4183fb698300d71da03566a36f80fcf4a13ddc3d26a9529626f1d91210a936e3`; API-32+ base APK: SHA-256 `d9f643910ba1dcc167ac26a290f8dbab77d29ff7647ad715f633afe2a2e977e0`. Both pass the 30-component manifest-to-DEX check and name `ComferApp` directly.
- Normalized hourly metrics alone must not overrule the issue feed: the available rows contain no nonzero crash-rate bucket even though the issue API has 16 crash events. Treat this as aggregation/rounding/freshness behavior, not proof that the launch crash is harmless.

Historical unprotected-release setup status:

1. **Done:** upload signed v44 AAB to internal with Automatic Protection disabled.
2. **Done:** download and verify the Play-generated unprotected v44 universal APK.
3. **Partial:** v44 clean-install/cold-launch emulator smoke passed. HOME selection, upgrade from v42/v43, package replacement, worker scheduling, compatibility-boundary scenarios, and physical-device checks remain.
4. **Manual release required:** the service account can inspect artifacts/tracks and construct a production edit, but Play rejects edit validation/commit with `PERMISSION_DENIED`. The initial production cohort is therefore started manually in Play Console.
5. Because no Honor device is available and launcher apps are a poor fit for generic pre-launch automation, use a very small production cohort as the device-coverage experiment rather than pretending local testing covers the failing OEMs.

Rollout gates:

1. Version-45 artifact identity/component verification: **passed**.
2. Version-45 crash gate: **failed**; freeze expansion and monitor issue `7eed1be...` daily.
3. Validate the narrowly targeted `SubscriptionActivity` upgrade-compatibility tombstone; still obtain the exact missing class/installed-split evidence from Google or an affected Honor device before claiming root-cause closure.
4. Reproduce and regression-test overlapping cached widget-view ownership before changing the `AndroidView` path for `62bcb23...`.
5. A replacement release may restart with a small Honor-bearing cohort only after its targeted fix or diagnostic has passed local component checks, clean install, upgrade install, every-activity launch, widget reopen/overlap stress, and available physical-device tests.
6. Close only after the missing-class cluster remains absent with meaningful Honor/API 34-35 exposure and the WorkManager/first-party clusters remain absent or materially reduced.

## Local verification recorded for version 43

- `testDebugUnitTest`: passed, including drawable sizing, proxy fallback, and widget geometry regression tests.
- `lintDebug`: passed.
- `:app:assembleDebugAndroidTest`: passed; instrumentation sources compile against the aligned Compose test API.
- `:app:assembleRelease`: passed with R8/resource shrinking and release-vital lint.
- `:app:bundleRelease`: passed.
- Production signing: Gradle keystore validation, APK v2 signature, AAB JAR signature, and matching APK/AAB certificate checks passed.
- Compose dependency check: UI family resolves to `1.12.0` under BOM `2026.08.00`; no library-specific overrides remain.
- Component checker: local v43 release APK, Play v42 protected universal/base APKs, and Play v42 unprotected base APK all passed.
- Signed-release emulator smoke: clean install passed; `MainActivity` cold-launched successfully in 581 ms, remained the resumed activity, and emitted no process exception/error in the inspected launch logs.
- Play-generated unprotected artifact smoke: clean install and 883 ms cold launch passed with no inspected process exception/error.
- `git diff --check`: passed.

Not yet run: connected Android tests, the full emulator/physical-device smoke matrix, Honor reproduction, or production staged rollout.

## Repeatable database checks

```sql
SELECT MAX(version_code) FROM issues;

SELECT type, COUNT(*) AS signatures,
       SUM(affected_users) AS issue_user_count,
       SUM(events) AS events
FROM issues
WHERE version_code = (SELECT MAX(version_code) FROM issues)
GROUP BY type;

SELECT issue_id, type, affected_users, events, title,
       os_api_level, device_brand, device_model
FROM issues
WHERE version_code = (SELECT MAX(version_code) FROM issues)
ORDER BY affected_users DESC, events DESC;
```

Re-run the same classification against the next version and an equal reporting window. Do not mark a family resolved solely because its old version stops receiving exposure.
