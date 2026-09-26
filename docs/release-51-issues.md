# Version 51 recurrence analysis and next-release attempts

20 September 2026. Source baseline: `b62f71af3972b5cbb366a1322be16fb2375a10c8`.
Changes below are uncommitted next-release candidates. Version remains **51 / 51.0**;
building a local APK does not ship these fixes. Firebase issues remain open.

## Evidence

**26 September outcome:** [version-53 recurrence review](release-53-recurrence.md)
finds none of the exact target crash/ANR IDs for attempts 51-01 through 51-07 in
import 6. The separate widget Binder risk from 51-02 persists: six start events
and eight stop events. Attempt 53-08 moves only stop calls off Main while retaining
Main-thread pending-view updates. Keep this earlier attempt and its rationale;
absence of its original hierarchy crashes is not a production-resolution claim.

**Follow-up, 22 September 2026:** version-52 import 4 contains 46 groups / 127
events; all selected samples carry revision `7f7cdb042507893fb2a9bac3eafd5b69dd1e2c56`.
See [the version-52 ledger](release-52-issues.md) for complete evidence. None of
the exact target IDs listed under attempts 51-01 through 51-07 below appears in
that version-52 snapshot; limited exposure and differing windows prevent a
resolution claim. Attempt 51-02's documented main-thread Binder-latency risk is
now represented by new group `1ea5988c748d88947b0a46bb05ff104d` (3 ANRs / 2 users),
with `IAppWidgetService.startListening` in the version-52 sample. This does not
establish recurrence of the old hierarchy exceptions. Keep the original attempts
and triage states. The misleading CLOSE_SYSTEM_DIALOGS group also appears again;
its new sample is still a Google measurement certificate rejection.

Firebase MCP import **3**, `51.0 (51)`, 23 June–20 September 2026 04:33:25 UTC:
**236 groups / 935 events**, comprising 24 crash groups / 268 events and 212 ANR
groups / 667 events. All totals reconcile with the version report. Ten default
samples were replaced with version-filtered samples. These are samples, sometimes
with omitted frames, not exhaustive reproductions or exposure-adjusted rates.

22 exact issue IDs also occur in the version-49 snapshot. Newly observed IDs are
not necessarily new defects: Firebase groups can retain an old title despite a
different current exception. In particular, `8d782ca9e751c6476994a4d4f4ad0075` is
labelled CLOSE_SYSTEM_DIALOGS but its version-51 sample is a Google measurement
certificate rejection on GoogleApiHandler. There is no matching broadcast call
in the current source; no broadcast-permission workaround was added.

235 sample build stamps identify `a283ada0c795dfc4f10c48672215b94c3cdc1fee`
(15 September, "version 51. with scroll fix"). That source contains the prior
Honor factory, widget ownership, search deduplication and version-50 mitigations.
Do not attribute every sample to that revision: one Firebase sessions sample has
no usable stamp, and subsequent same-version builds exist in the workspace. Exact
per-sample revision and dispositions are in the inventory below.

Previous attempts remain in [the version-50 ledger](release-50-issues.md).
Attempts 50-01 (notification drawable), 50-02 (launch scale), 50-03 (dynamic palette),
and 50-05 (external URL launch) have no exact matching group in this import.
Retain them; absent reports do not establish production resolution. Attempt 50-04
(search deduplication) **recurs**, with 8 events. The version-51 sample already
contains the component/profile key that attempt introduced.

## Attempts

### 51-01 — Honor factory bypass

- Issue: `ca8f7f21e3ec633d0d1dca453409435b`, 143 events; Honor ALT-LX2 / Android 14.
- Earlier attempt: `CrashMitigationPolicy.kt` redirects the exact vendor activity
  in `ComferAppComponentFactory`. The recurring sample instead goes through the
  platform `android.app.AppComponentFactory` and cannot find the vendor class.
- New implementation: an `@Keep` class at the exact requested
  `com.hihonor.android.launcher.powersavemode.PowerSaveModeLauncher` name inherits
  the existing finish-only compatibility activity. The original factory remains.
  No new manifest component, permission or vendor behavior is exposed.
- Files: `CrashMitigationPolicy.kt`, new vendor-named `PowerSaveModeLauncher.kt`;
  `scripts/verify_apk_components.py` now also checks this dynamically loaded class.
- Test: platform `Instrumentation.newActivity`, bypassing our factory, failed
  before the fix with ClassNotFoundException. Constructing an Activity must occur
  on Main; the first post-fix test exposed this test-harness error and was corrected.
- Limit: an affected Honor device and Play-generated splits are still needed to
  validate the malformed-task lifecycle and actual production outcome.

### 51-02 — widget updates on the UI thread, in lifecycle order

- Related issues: `b9243dd5994a058da551ec3dcf57b5c3`,
  `29f60304d4be54d1221bf9b5efcc696f`, `99d0eefca6ea8ba065b8cc3f0f814551`,
  `9d621f22b24da46f018caa460844e565`, `8096cf8b7b23346223282ac7afd504d5`,
  `b29c9bb53a0e14455f29d441798770be`, `081d507f42887a933d6a06d8034bf4fc`.
- Earlier attempt: detached-view ownership/cache guards and main-thread createView.
  However both lifecycle callers and WidgetHostManager dispatched start/stop to IO.
  Android's [AppWidgetHost implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/appwidget/AppWidgetHost.java)
  applies pending RemoteViews, provider and adapter updates synchronously inside
  startListening. This left a concrete background mutation path during layout.
- New implementation: Main.immediate manager scope, direct ordered lifecycle calls,
  and per-host failure isolation so one failed host cannot skip the others.
  Existing detached ownership and provider quarantine remain intact.
- Files: `MainActivity.kt` (onStart/onStop and WidgetHostManager).
- Test: the controlled three-host sequence failed before the fix because all six
  callbacks ran off-main. This establishes the threading defect, not reproduction
  of every vendor hierarchy/receiver failure. Do not mark these groups resolved.
- Tradeoff: startListening includes a synchronous Binder call; main-thread Binder
  latency remains a device acceptance/ANR monitoring gate. Moving the entire call
  back to IO would reintroduce unsafe view mutations.

### 51-03 — Honor weather package missing from the provider guard

- Issue: `8644ffc581a1e64cff2cf99bcdf6f5ca`, 23 events; Honor WDY-LX2 / Android 14.
- Earlier attempt blocked Huawei's weather package. This sample runs Honor's
  `com.hihonor.android.totemweather` code on `weather_global_worker` and fails its
  signature-protected content-provider permission asynchronously.
- New implementation: add the exact Honor package boundary to WidgetInflationGuard;
  filter known unsafe providers from the picker before preview/binding. Existing
  saved unsupported widgets still show the existing unsupported-widget state.
- Files: `WidgetInflationGuard.kt`, `MainActivity.kt`.
- Test: Honor weather rejection failed before the fix; similarly named safe package
  remains allowed. Existing Huawei/calendar/gallery guards are retained.
- Limit: package-level mitigation disables these vendor widgets in Comfer; it does
  not repair vendor code. A cold process restart clears any already-running code.

### 51-04 — enforce wallpaper size before Ktor buffers the response

- Issue: `a8ebacfbd4ff93fa3eca3bb2eb56be10`, 1 event, SavedCallKt.save OOM.
- Earlier attempt bounded file copying and bitmap decode. `client.get()` saved
  the entire response before the copy loop could check the 25 MiB source limit.
- New implementation: `prepareGet().execute {}` streaming, reject oversized
  Content-Length, enforce actual bytes for chunked/misreported responses, cancel
  the response on every exit, and delete incomplete staging files. Coroutine
  cancellation propagates; the currently applied wallpaper is untouched.
- Files: `utils/BoundedDownload.kt`, `utils/CommonUtil.kt`.
- Tests: real loopback HTTP server, exact limit, declared oversized response with
  no body, unfinished oversized chunked response, HTTP failure, cancellation.
  The first streaming implementation timed out during cleanup for header-only
  rejection; moving channel cancellation around every response exit fixed it.
- Scope: sample omits the caller; this removes a verified unbounded wallpaper path
  matching Ktor's save stack, not proof that every Ktor OOM comes from wallpapers.
  See [Ktor streaming documentation](https://ktor.io/docs/client-responses.html#streaming).

### 51-05 — widget picker memory pressure

- Issue: `7e7c39ec34af08a023be70280bceeb34`, 1 event; bitmap allocation while
  getGroupedWidgetProviders eagerly loads all package icons.
- Earlier bounded painter copies did not bound the eager source drawables retained
  by every provider group, or preview decode concurrency.
- New implementation: groups retain package names, visible rows load on demand,
  serialize image loading, retain owned bitmaps capped at 128/256 pixels, request
  default-density previews, and use a selectable placeholder after load failure.
  No repeated retry under allocation failure. Cancellation still propagates.
- Files: `MainActivity.kt`, new `WidgetImages.kt`, configurable bitmap bound in
  `DrawableExt.kt`. Other painter callers retain the existing 512-pixel default.
- Test: injected allocation failure followed by successful bounded decode, restored
  permit and cancellation. This is controlled failure injection, not an exhausted
  production heap reproduction. Vendor source decode may still allocate before scaling.

### 51-06 — duplicate-key follow-up; partial mitigation, investigation open

- Issues: `159e25351be7042db517f3af684684db` (8 events) and
  `f26e8107ace4f9542b53486a6a1a42af` (2 events).
- Earlier 50-04 deduplicates search by component/profile identity. The first
  recurring sample uses that identity and the second enters paused precomposition.
  A global Compose flag/dependency change is not justified by these samples alone.
- New bounded changes: AppDrawer captures one list snapshot for count, key and
  content callbacks, instead of reading newer state during deferred key/content
  calls; restored folder package lists are deduplicated during final publication.
  Order, package-based drag identifiers and component/profile search keys survive.
- Files: `ProSettingsActivity.kt`, `AppInfoViewModel.kt`.
- Tests: retained activity/profile deduplication test and grid scroll/inventory
  replacement regression. The actual production duplicate-key crash has not been
  deterministically reproduced; these guards must not be called a confirmed fix.

### 51-07 — Journal language-name work during composition

- Issue: `fc2b18db7801c844b7671c3b4806807d`, 1 ANR sample in ICU getDisplayName
  from the locale sorting comparator inside JournalScreen.
- New issue, no earlier attempt in the ledger. The current screen still did all
  locale enumeration and repeated label lookups during initial composition.
- New implementation: enumerate only when dictation options open, run on Default,
  compute each display label once before sorting, and render cached labels. Keep
  the selected language tag stable and include the current locale even when the
  platform inventory is empty. The language menu is enabled once options are ready.
- Files: `journals/JournalActivity.kt`, new `journals/JournalSpeechLanguages.kt`.
- Tests: injected inventory/label callbacks verify off-caller-thread execution,
  deduplication, one lookup per option, sort order and empty-inventory fallback;
  retained Journal UI tests. The exact device ANR is not reproduced locally.

## Remaining investigations and follow-up

- System-server death, driver/native waits, Google measurement crashes and
  incomplete framework stacks have no established first-party repair. Do not
  suppress uncaught exceptions, disable accessibility or mark these resolved.
- Many nativePollOnce groups report unknown ANR causes. A single idle main-thread
  sample is insufficient to identify the blocking operation. Obtain full ANR/exit
  traces and device exposure before assigning a cause.
- Activity-start Binder waits remain monitored; moving activity calls off-main
  without an Android lifecycle-safe design is not a validated repair.
- Resource-lock icon ANRs retain the existing serialized resource acquisition.
  Widget image concurrency is now separately bounded, but no universal ANR fix
  is claimed.
- Before shipping: affected Honor/widget acceptance, repeated widget resume/update,
  first-frame/Binder timing, low-memory picker behavior, rapid search/reorder and
  dictation language selection. Assign a new release version after acceptance and
  record exact APK/AAB hashes plus Play-generated split checks.
- At 24/48 hours, append outcomes per attempt using comparable release/device
  exposure. Preserve the earlier attempts and record ineffective/reverted changes.

## Validation

20 September 2026 final local checkpoint:

- **196 JVM tests passed**, zero failures/errors/skips, including five real HTTP
  streaming/cancellation checks and two Journal locale-loading checks.
- **33 distinct emulator tests passed across the focused runs**: Release51RegressionTest
  5, Release51DrawerTest 1, retained Release50RegressionTest 4, WidgetInflationGuardTest
  3, WallpaperDecodeTest 3, SubscriptionActivityCompatibilityTest 2, JournalUiTest 15.
  Final new-regression invocation reported **OK (6 tests)**. Original OEM devices,
  actual low-memory exhaustion and the production duplicate-key input remain untested.
- Pre-fix three-case instrumentation failed as expected. Intermediate test-harness
  failures were retained: Activity construction outside Main, and a drawer fixture
  with no rendered icon/accessibility label. Corrected fixtures pass; these were
  not silently counted as successful application reproductions.
- **26 Python reporting tests passed**. SQLite integrity/foreign-key checks passed.
  All 236 version-51 notes and five version-49 attempt outcomes were appended in
  one transaction. Prior notes, event counts, imports, snapshots and resolution
  timestamps were checked against the backup and preserved. Only pending rows
  associated with a candidate attempt were promoted to investigating.
- Database backup: `play_reporting.db.backup-20260920T045918576756Z`.
- Isolated notificationTest builds and minified release build passed. An initial
  lint-worker internal AsyncExecutionService crash was resolved by rebuilding in
  a fresh Gradle process; the successful release build included lint vital checks.
  APK inspection verified **49 manifest classes plus the dynamic Honor class**.
- Local release APK SHA-256:
  `dedfcd20dc25f3609ccff9d0ea6b7f7f82602f81395003a80d5bc931b5559dd8`.
  Still version 51 / 51.0, **validation only**. It was not installed on the physical
  phone or published. Only the separate emulator test package was updated.
- Logs, tracked diff, exact changed source copies/hashes, triage export and local
  validation APK: [checkpoint directory](../validation-artifacts/device-checkpoint/2026-09-20-version51-recurrences/).
  These local artifacts and SQLite backups are ignored by Git; this ledger and
  its full inventory are repository files ready for version control. Fix commit/artifact for a newly numbered shipped
  release: **pending**. Production outcome for every new attempt: **unverified**.

## Complete import-3 inventory

Generated from the local database. `prior` means exact-ID presence in import 2,
not proof of identical cause. Dispositions are triage hypotheses. The database
keeps complete sample traces; this table deliberately omits installation/user IDs.

| Issue ID | Type | Events | V49 prior | Sample revision | Disposition / title |
|---|---|---:|---:|---|---|
| `4d05f9e74e77520b418eac3a355108f1` | anr | 221 | 32 | `a283ada` | ANR; cause unproven from sampled stack: Native method - android.os.MessageQueue.nativePollOnce |
| `ca8f7f21e3ec633d0d1dca453409435b` | crash | 143 | — | `a283ada` | 51-01; candidate, production unverified: dalvik.system.BaseDexClassLoader.findClass |
| `15c1049c4d0bc536c073e5cbbeef1b7c` | anr | 55 | 14 | `a283ada` | ANR; cause unproven from sampled stack: Native method - android.os.MessageQueue.nativePollOnce |
| `7be898be9b6f6bbe3d90f85674200043` | crash | 23 | — | `a283ada` | System-server death; no app fix established: android.app.ActivityClient.activityStopped |
| `8644ffc581a1e64cff2cf99bcdf6f5ca` | crash | 23 | — | `a283ada` | 51-03; candidate, production unverified: a3.a.i |
| `ec94f9d136e7da26d520d47942d46deb` | anr | 20 | 5 | `a283ada` | ANR; cause unproven from sampled stack: [libart.so] art::ConditionVariable::WaitHoldingLocks(art::Thread*) |
| `b9243dd5994a058da551ec3dcf57b5c3` | crash | 19 | 1 | `a283ada` | 51-02; candidate, production unverified: android.widget.FrameLayout.onMeasure |
| `302d6c90af10653f908aac894932b278` | anr | 15 | 3 | `a283ada` | ANR; cause unproven from sampled stack: [libc.so] __ioctl |
| `8d782ca9e751c6476994a4d4f4ad0075` | crash | 13 | — | `a283ada` | Google SDK/system service; investigate full variants: android.os.Parcel.createExceptionOrNull |
| `958ed166f1584b7c22378e82ad6968b1` | anr | 12 | 2 | `a283ada` | ANR; cause unproven from sampled stack: [libart.so] art::ConditionVariable::WaitHoldingLocks |
| `7487bf9aa6a13353cfd84b898fa70a9d` | anr | 11 | 3 | `a283ada` | ANR; cause unproven from sampled stack: Native method - android.os.MessageQueue.nativePollOnce |
| `29f60304d4be54d1221bf9b5efcc696f` | crash | 10 | — | `a283ada` | 51-02; candidate, production unverified: android.view.ViewGroup.dispatchVisibilityAggregated |
| `49e77967e24836ed20e1450247201422` | anr | 10 | 2 | `a283ada` | ANR; cause unproven from sampled stack: [libhwui.so] android::uirenderer::ThreadBase::waitForWork |
| `28051541328b9d04813ec0dbc5ef264a` | anr | 9 | — | `a283ada` | Google SDK/system service; investigate full variants: :com.google.android.gms.dynamite_measurementdynamite@263234029@26.32.34 (190400-0) - m7.es.m |
| `159e25351be7042db517f3af684684db` | crash | 8 | 5 | `a283ada` | 51-06; partial mitigation, cause unresolved: androidx.compose.ui.internal.InlineClassHelperKt.throwIllegalArgumentException |
| `416f1c679b091716c78157591849051c` | anr | 8 | 4 | `a283ada` | ANR; cause unproven from sampled stack: [libhwui.so] android::uirenderer::ThreadBase::waitForWork |
| `4593bd5c8c65cb959be5aea7506dc75f` | anr | 8 | — | `a283ada` | ANR; cause unproven from sampled stack: Native method - android.os.MessageQueue.nativePollOnce |
| `a5c1f855585a55e6a9cf91d97c82b5ea` | anr | 8 | 1 | `a283ada` | ANR; cause unproven from sampled stack: [libart.so] art::ConditionVariable::WaitHoldingLocks |
| `293b2117cdf3ed5f2c38dd6de73a722a` | anr | 7 | — | `a283ada` | Google SDK/system service; investigate full variants: :com.google.android.gms.dynamite_measurementdynamite@263232029@26.32.32 (190400-0) - m7.es.m |
| `901023f312e4c8a37f604ccfb3ec41c6` | anr | 7 | 1 | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivityKt$LauncherTwoPaneLayout$1$1.measure-3p2s80s |
| `2bdaa90e21b71caad2450bff3f478830` | anr | 6 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.utils.CommonUtil.handleStartActivity |
| `967976fa3a49fe618dae41b85c7a49fb` | anr | 6 | — | `a283ada` | ANR; cause unproven from sampled stack: [libhwui.so] android::uirenderer::ThreadBase::waitForWork |
| `4e241f2d06c3a545132bfd9722932912` | anr | 5 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.AppInfoViewModelKt$getAppInfo$2$1.invokeSuspend |
| `71851376d2af5f56955bf728d321d90b` | anr | 5 | — | `a283ada` | ANR; cause unproven from sampled stack: [libc.so] __ioctl |
| `8d148077ccbe90466167a0aa6fc2be1d` | anr | 5 | 1 | `a283ada` | ANR; cause unproven from sampled stack: [libGLES_mali.so] glProgramBinary |
| `b0d42f2bb00829213e59e7f5dd5f0e82` | anr | 5 | — | `a283ada` | ANR; cause unproven from sampled stack: [libc.so] __futex_wait_ex |
| `c87abde51b3c87cd50a2afcce7a8fdb3` | crash | 5 | — | `a283ada` | Framework/native crash; matching reproduction needed: androidx.compose.ui.platform.AndroidComposeView.dispatchDraw |
| `0759651f42c5b565e22114f551d539fa` | anr | 4 | — | `a283ada` | ANR; cause unproven from sampled stack: Native method - android.os.BinderProxy.transactNative |
| `30805a6146c0b33feed724acdf88553d` | crash | 4 | — | `a283ada` | System-server death; no app fix established: android.app.ActivityThread.handleCreateService |
| `462cb6737500d85dbb12137c3f5ecbfd` | anr | 4 | — | `a283ada` | ANR; cause unproven from sampled stack: [linker64] __dl_do_dl_iterate_phdr |
| `57cdeb82a64afd66721fcc7ed02b383e` | anr | 4 | — | `a283ada` | ANR; cause unproven from sampled stack: [libart.so] art::DumpNativeStack |
| `890e086d60a0dac62be2975dc400ec17` | anr | 4 | 2 | `a283ada` | ANR; cause unproven from sampled stack: [libart.so] art::ConditionVariable::WaitHoldingLocks |
| `96de289334e54a302779cb891d83b9bb` | anr | 4 | — | `a283ada` | ANR; cause unproven from sampled stack: [libc.so] __ioctl |
| `ef715e1213c320b2739fbc5b0ead0a5f` | anr | 4 | — | `a283ada` | ANR; cause unproven from sampled stack: [libart.so] art::ConditionVariable::WaitHoldingLocks |
| `207c0b5f261e49ef7069e9ffcae84e75` | anr | 3 | — | `a283ada` | ANR; cause unproven from sampled stack: miui.content.res.ThemeValues.getIdentifier |
| `2fba5703c39d194f1fd5d3b447a00275` | anr | 3 | — | `a283ada` | ANR; cause unproven from sampled stack: [libandroid_runtime.so] android::android_os_MessageQueue_nativePollOnce(_JNIEnv*, _jobject*, long long, int) |
| `348157e030bac748d3d1262e0edc1d81` | anr | 3 | — | `a283ada` | ANR; cause unproven from sampled stack: [libandroid_runtime.so] android::android_os_MessageQueue_nativePollOnce |
| `8cea93e60ac6516390e783687ba4e15e` | anr | 3 | 2 | `a283ada` | ANR; cause unproven from sampled stack: com.pairip.licensecheck.LicenseClient.bindToLicensingService |
| `a5a9aa779a2a249c69052699e89ae21f` | anr | 3 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivity.onCreate |
| `b8f783e754bd002ebf2b47f81ad8f6a4` | anr | 3 | — | `a283ada` | ANR; cause unproven from sampled stack: [libart.so] art::DumpNativeStack |
| `d407e59611040a5624a808c1c021b4e8` | anr | 3 | 1 | `a283ada` | ANR; cause unproven from sampled stack: [libGLES_mali.so] cmpbe_v2_compile_multiple_shaders |
| `e0290b0cb49d3d4f56833dbe36789808` | anr | 3 | — | `a283ada` | ANR; cause unproven from sampled stack: [libc.so] __ioctl |
| `e2d3656b801b549025b8b0115c58aec7` | anr | 3 | — | `a283ada` | ANR; cause unproven from sampled stack: [libart.so] art::ConditionVariable::WaitHoldingLocks |
| `081d507f42887a933d6a06d8034bf4fc` | crash | 2 | 1 | `a283ada` | 51-02; candidate, production unverified: android.app.LoadedApk.forgetReceiverDispatcher |
| `099720056c2b8309e00ed68859e706e3` | anr | 2 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivity.onCreate |
| `1465dc27264264d8080762ce7f3fbc98` | anr | 2 | — | `a283ada` | ANR; cause unproven from sampled stack: kotlinx.coroutines.channels.BufferedChannel.receive$suspendImpl |
| `24c61fb294bb39bfc865c16f9eb4df95` | anr | 2 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivityKt.SensitivityDialog |
| `3fa5d596ef1552217a6200780697888d` | anr | 2 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.AppInfoViewModelKt$getAppInfo$2$1.invokeSuspend |
| `657829503aaa8ba4ee39d01bb858d69e` | anr | 2 | — | `a283ada` | ANR; cause unproven from sampled stack: android.os.StrictMode.setThreadPolicy |
| `6a9edac514b70ea54745e9c9452a02ee` | anr | 2 | — | `a283ada` | ANR; cause unproven from sampled stack: android.app.AppComponentFactory.instantiateActivity |
| `6ab80b425c770d5deb87bc74a144aa59` | anr | 2 | — | `a283ada` | ANR; cause unproven from sampled stack: [libc.so] __futex_wait_ex |
| `973a7a1429c335323bfe72f30dad1df7` | crash | 2 | — | `a283ada` | System-server death; no app fix established: android.app.ActivityClient.activityResumed |
| `9895ddf1cafa99cc926408430cb7c701` | anr | 2 | — | `a283ada` | ANR; cause unproven from sampled stack: [libc.so] __sendto |
| `99d0eefca6ea8ba065b8cc3f0f814551` | crash | 2 | — | `a283ada` | 51-02; candidate, production unverified: android.view.ViewGroup.newDispatchApplyWindowInsets |
| `9adf34285ab3da88085a01985a91265f` | anr | 2 | — | `a283ada` | ANR; cause unproven from sampled stack: Native method - android.os.BinderProxy.transactNative |
| `9d621f22b24da46f018caa460844e565` | crash | 2 | — | `a283ada` | 51-02; candidate, production unverified: android.view.ViewGroup.dispatchWindowVisibilityChanged |
| `a1f83e1f9e992711abf36f9d9f42fd69` | anr | 2 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivity.onCreate |
| `a23c51089a7056be268f45588381635b` | anr | 2 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.DrawableExtKt.toOwnedBitmap |
| `bd45a06d6613f26ae5f66570745cb1cd` | anr | 2 | — | `a283ada` | ANR; cause unproven from sampled stack: com.mediatek.boostfwk.identify.scroll.ScrollIdentify.inputEventCheck |
| `cb9593d762b12a0d8e0246b6e5108f84` | anr | 2 | — | `a283ada` | ANR; cause unproven from sampled stack: [libhwui.so] android::uirenderer::ThreadBase::waitForWork |
| `d3bd7bf3f5d04282117bcbba1b067ba4` | anr | 2 | — | `a283ada` | ANR; cause unproven from sampled stack: [linker64] __dl_do_dl_iterate_phdr |
| `f26e8107ace4f9542b53486a6a1a42af` | crash | 2 | — | `a283ada` | 51-06; partial mitigation, cause unresolved: androidx.compose.runtime.saveable.SaveableStateHolderImpl.SaveableStateProvider$lambda$0$1$0 |
| `f76d00024b6fabfbea2c0368a25ea3af` | anr | 2 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivityKt$LauncherTwoPaneLayout$1$1.measure-3p2s80s |
| `027097ee40ae575a02528df508536f8d` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.compose.ui.unit.Constraints.getMaxHeight-impl |
| `03b29dde7cf2c9aecce5c1136e9b0460` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivity.getSettingsViewModel |
| `041e5d145c0a0534dcff232a077f4a0b` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.content.ContextWrapper.getPackageManager |
| `05babf260a655d1e7a2738eef61f0a2b` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.ComferApp.setupImageWorker |
| `066b6e5ea24b70c86d3b592b03d78c1a` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.collection.MutableOrderedScatterSet.d |
| `0fec9750d5e179415bc510433c5df2a3` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.os.ufw.UltraFrameworkComponentFactoryImpl.makeUFwChoreographer |
| `108ff62355e1cfbb76382a9bcb27a5c7` | crash | 1 | — | `a283ada` | System-server death; no app fix established: android.view.inputmethod.InputMethodManager.removeImeSurface |
| `13635885d117ee6860de887d59f4f63e` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: java.lang.Class.getMethod |
| `140ef0f8a1af38cf23c3553b0835baba` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.android.internal.policy.PhoneWindow.saveHierarchyState |
| `166259043f9de1d65afd87a539fa6bd3` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.google.firebase.encoders.json.JsonDataEncoderBuilder.configureWith |
| `16efc77fdd7e62840bbf72af34f77776` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.HomeGesturesKt.classifyHomeGesture |
| `172df327e7958436eb8fa58bf8efb21a` | anr | 1 | — | `a283ada` | Google SDK/system service; investigate full variants: :com.google.android.gms.dynamite_measurementdynamite@263234022@26.32.34 (150400-0) - m7.es.m |
| `192cdb29b086a4756a38fb55713604eb` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: coil.memory.RealWeakMemoryCache.cleanUp$coil_base_release |
| `1a7b4f30c2e8d37a6a43cb9c79cae44b` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.appcompat.app.AppCompatActivity.getResources |
| `1b1fcdccd5d9fff6746c829a06db609c` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: kotlin.jvm.internal.Intrinsics.g |
| `1b9b9a1021c548bb3c8e6b665f963fe1` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libart.so] art::ConditionVariable::WaitHoldingLocks(art::Thread*) |
| `1d5786b31fd2a47114eb1233553cd34b` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: R8$$SyntheticClass - com.jeerovan.comfer.utils.CommonUtil$$ExternalSyntheticLambda5.m |
| `1f62473dfae416accc2676ee4647022b` | anr | 1 | 1 | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.utils.CommonUtil.handleStartActivity |
| `1fde055aa17a1833dc65226520420c4e` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.collection.ScatterMapKt.d |
| `21f99e923ecad7a6fb8f4491b0fcc7cb` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: Native method - android.os.BinderProxy.transactNative |
| `22b0e9b595c86f346813acd8d18976bb` | crash | 1 | — | `a283ada` | System-server death; no app fix established: android.app.ActivityThread.handleSleeping |
| `25518ad8182128eff24f4f63f899e8a3` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.DrawableExtKt.toOwnedBitmap |
| `26acc5bd82c3f61fbc3ca7380e4a22df` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.compose.runtime.internal.AwaiterQueue.addAwaiter |
| `293fd20e783876c7e8dd1ae5888a4bc8` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: PersistentCompositionLocalMap.kt - androidx.compose.runtime.internal.PersistentCompositionLocalHashMap.get |
| `2dcdef947466b48130ee44eeb076a72c` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivityKt$AnalogClock_mllb_LA$lambda$3$0$$inlined$onDispose$1.dispose |
| `311b51e2500fd005ee2ce29c1d699922` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libGLESv2_powervr.so] glTexSubImage2D |
| `3156d09664880635b36f9d21e0ffc22c` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.compose.ui.unit.ConstraintsKt.a |
| `31c8e4bc4379a85d55863f90c92ccd1e` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivityKt.AppListOverlay |
| `31f69719aa52d2b562f1d9682770d671` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.work.impl.model.WorkTagDao_Impl.get |
| `320b5fa8c6e03e75ff351730b26e125f` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.compose.ui.layout.Placeable.setMeasuredSize-ozmzZPI |
| `339fa265663f51428e457767270f89a7` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.lifecycle.LifecycleRegistry.<init> |
| `36293788e50e498455ee6f557bd0a311` | crash | 1 | — | `a283ada` | System-server death; no app fix established: android.app.ActivityThread.handleUnbindService |
| `3632cfaff7f588f8917a36ab40506f4f` | anr | 1 | — | `a283ada` | Google SDK/system service; investigate full variants: com.google.android.gms:play-services-basement@@18.9.0 - com.google.android.gms.common.wrappers.InstantApps.Y |
| `3711a3ecf16f5a68a23a921718e03b63` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: Native method - java.lang.Class.getNameNative |
| `3855feff86b735a141ab24227207a8d3` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: java.util.Arrays.rangeCheck |
| `38dfbbf39b8c5158fc16c72afdf4d8cd` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: java.util.Locale.equals |
| `393112243cd4ffc8c4e74d141784a9fa` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: kotlin.coroutines.jvm.internal.ContinuationImpl.releaseIntercepted |
| `3a699c49623c75f6b1e2a2f9ec10689e` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libart.so] art::DumpNativeStack |
| `3cea4c09755a2ad9bb663d3a7067954b` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libc.so] fsync |
| `3d0d342f18c4634cffaa1b2dcdd28e67` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libGLES_meow.so] eglGetProcAddress |
| `40a09264f14d4e4b6f32f94d9e0b9ce8` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: kotlin.collections.ArrayDeque.f |
| `40b500c35ba14052009f0e8d01fa80b9` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.ui.theme.ThemeKt.ComferTheme |
| `40cc45d8828e09a9d9d5cdf6fb722390` | crash | 1 | — | `a283ada` | Framework/native crash; matching reproduction needed: android.os.Process.getProcessGroup |
| `43ebc594970d976ec0fe5968e28c48c0` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.transsion.hubcore.internal.ITranInternalView.Instance |
| `448c9e17c2fd6d4a9a913688d8124c31` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [linker64] __dl_syscall |
| `473dd79ba2cc238d496d25a4f926c4ae` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.google.firebase.tracing.ComponentMonitor.lambda$processRegistrar$0 |
| `47929b792926cd22326529bb4ccc5a6c` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libGLES_mali.so] cmpbe_v2_compile_multiple_shaders |
| `4d0b166777d01fd55d94625f117262e0` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libandroid_runtime.so] android::android_os_MessageQueue_nativePollOnce(_JNIEnv*, _jobject*, long long, int) |
| `4e84b1170532150189dff4b212ab1ce9` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libc.so] __ioctl |
| `4eb164efb834896b9969a5069ebea51c` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [linker64] __dl_do_dl_iterate_phdr |
| `4eeadc1f2a9e496eab864233bb0fda01` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libGLESv2_adreno.so] !!!0000!86ef64f2a801c732241ff107020608!3dad7f8ed7! |
| `536f039fb057ba7d96553ee029cda836` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.SettingsViewModel.<init> |
| `538662985f5a3bcf3efef73ef62e3c0a` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: D8$$SyntheticClass - android.view.InsetsController$$ExternalSyntheticLambda12.<init> |
| `565b6f728606f18da6ff0deb6ebd0970` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivityKt.SearchIcon-gSuKmCU |
| `59e5977fd4c13ca76d9d17edc8db242b` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: WorkSpec.kt - androidx.work.impl.model.WorkGenerationalId.toString |
| `5c0c1a7cc2a8b04e59dd287538b33dc2` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.google.firebase.sessions.FirebaseSessionsComponent$MainModule$Companion.createDataStore |
| `5e49a5ad0c3e297edb62f08bc2c698b6` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: kotlin.coroutines.ContinuationInterceptor$DefaultImpls.get |
| `60c8e8601dc2d182023954cc8fbe7468` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libhwui.so] sktext::gpu::StrikeCache::findOrCreateStrike |
| `60e044da562db6147924eb121418c51a` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: R8$$SyntheticClass - kotlinx.serialization.SerializersCacheKt$$ExternalSyntheticLambda3.i |
| `610c96bacbd70fc439ec4740819fab8f` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libart.so] art::ConditionVariable::WaitHoldingLocks |
| `644a7adc0fda1825e83223a6724ef595` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.mediatek.boostfwk.identify.scroll.ScrollIdentify.inputEventCheck |
| `6928bd70aff53e74c689ce73a5ef5d0e` | anr | 1 | 2 | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivityKt.AppListOverlay |
| `6a82cbfff65892f4aa41ef86f4e8ef31` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: Native method - android.os.MessageQueue.nativePollOnce |
| `6bba884050263fdff281a8bc49a85b9b` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: Native method - android.os.MessageQueue.nativePollOnce |
| `6be412d86aee5a9e7ab13a9242213c23` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.ComferApp.setupImageWorker |
| `6be7022fdbc991fa39572cc9de3ba7dd` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: kotlinx.coroutines.CancellableContinuationImpl.getSuccessfulResult$kotlinx_coroutines_core |
| `6d1cca24626aba043b068a531bba8538` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.google.firebase.tracing.ComponentMonitor.lambda$processRegistrar$0 |
| `70673550e65f1b653fd2b5c4bafdbffe` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libft2.so] TT_Load_Glyph_Header |
| `7143923d65b86aafd879de983c99d964` | anr | 1 | 1 | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.utils.CommonUtil.handleStartActivity |
| `72deebd901b17347e2c1e907f759a673` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libc.so] __ioctl |
| `749e794e39e41ae06f54974100115eef` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: unavailable - android.view.InsetsController$InternalAnimationControlListener$$ExternalSyntheticLambda0.onAnimationUpdate |
| `758500a0df036f1fa1d995feb15f3393` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.os.Trace.beginSection |
| `75a0981891a8b3d314353dbd423cf60f` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.work.impl.background.greedy.GreedyScheduler.onExecuted |
| `77182372e143cca4a3c3e6f31cc13711` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.ComferApp.newImageLoader |
| `7791fc65bcc9c2c90cc8e33a27765da8` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libui.so] std::__1::vector<android::Rect, android::InlineStdAllocator<android::Rect, 4ul>>::__destroy_vector::operator()[abi:nn180000]() |
| `779303f416da7f89ac02add5c36eb1e1` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.pairip.licensecheck.LicenseClient.bindToLicensingService |
| `7df15e50e6289199b880a50d0b6642f6` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libart.so] art::ConditionVariable::WaitHoldingLocks |
| `7e7c39ec34af08a023be70280bceeb34` | crash | 1 | — | `a283ada` | 51-05; candidate, production unverified: com.jeerovan.comfer.MainActivityKt$getGroupedWidgetProviders$2.invokeSuspend |
| `7f745b2014d22f78838bc9c72404aedc` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: Native method - android.graphics.HardwareRenderer.nSyncAndDrawFrame |
| `8096cf8b7b23346223282ac7afd504d5` | crash | 1 | — | `a283ada` | 51-02; candidate, production unverified: android.view.ViewGroup.dispatchGetDisplayList |
| `80fe7daaa5c14edec79f00e04df7b87d` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libhwui.so] android::uirenderer::ThreadBase::waitForWork() |
| `824c025fcb6c188845c9f37c16a362b3` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.PerformanceTrace.endAsync |
| `825261bf8d471a64c94a5164f6a83bbf` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libhwui.so] SkTHashTable<GrTextureProxy*, skgpu::UniqueKey, SkTDynamicHash<GrTextureProxy, skgpu::UniqueKey, GrProxyProvider::UniquelyKeyedProxyHashTraits>::AdaptedTraits>::resize |
| `849eee070f21618be91415ea7852b2c8` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: WindowInsetsRulers.android.kt - androidx.compose.ui.layout.InsetsListener.onStart |
| `84d5bf592e2a760608cfd2967b2299c6` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: java.lang.Object.hashCode |
| `8511df11992198234de1d83ba8193195` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: Native method - J.N.OIIIIJJJOOOOOOOOOOZZZZZZZ |
| `8870f7670c44c13a88cd1aad6a4856d1` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.view.View.fitSystemWindows |
| `888cc6388c5ccc2c80d129468936ae8e` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libhwui.so] android::uirenderer::Properties::load |
| `891ef420484aa2dbed41a7437b395397` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libart.so] art::ConditionVariable::WaitHoldingLocks(art::Thread*) |
| `898aa3e874eab233a91d0b2573b5a2f9` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.view.inputmethod.InputMethodManager.unregisterImeConsumer |
| `8b560148583aa477af81380febff32a2` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: ImageResult.kt - coil.request.ErrorResult.getRequest |
| `8cc117308c48ab176d627e38689c6a89` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: Native method - android.os.MessageQueue.nativePollOnce |
| `8d31386ba2fe27763ecd823bb1d92583` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService.onStartJob |
| `8d34bc5e2a706efaf5e7c4705659340d` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libhwui.so] GrVkPrimaryCommandBuffer::copyBufferToImage |
| `8deb659a97feb035d239ae26d900d1dc` | anr | 1 | — | `unknown` | ANR; cause unproven from sampled stack: com.google.firebase.sessions.FirebaseSessionsComponent$MainModule$Companion.loadDataStoreSharedCounter |
| `90ebd655ad4268c67283a9640def6889` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: kotlin.coroutines.ContinuationInterceptor$DefaultImpls.get |
| `91d8c73e7d8fd5e52ce554fbac677798` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.view.View.isShowingLayoutBounds |
| `92cd8cdadabde67d2fe4e65d47760ca5` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: D8$$SyntheticClass - com.pairip.licensecheck.LicenseClient$$ExternalSyntheticLambda7.run |
| `92dc2d203105f2d5eb5c635e65d4f542` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: AndroidComposeView.android.kt - androidx.compose.ui.platform.CalculateMatrixToWindowApi29.<clinit> |
| `95dbc0ecea8dcdfc911a3f177dfd9458` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.compose.ui.platform.AndroidComposeView.recalculateWindowPosition |
| `96151c52408a9be377fa0745ebeb8a59` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.google.firebase.tracing.ComponentMonitor.lambda$processRegistrar$0 |
| `962a8d6f0d6193f3c08347136e1a3615` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.compose.ui.node.NodeCoordinator.x1 |
| `984212faed160cd53d4115f2e7178484` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.AppInfoViewModel.<init> |
| `9bbe07bc0c0d323d2a32fb808de65a1c` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.WidgetHostManager.initHosts |
| `9cd994cf218d55bad08655c2cf4b0a05` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: unavailable - sun.misc.Cleaner.add |
| `9dcb3c2622d96f220fe3edde82121a3c` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.view.Choreographer$FrameData.setInCallback |
| `9e7ab988e09563cadf6e99394303f49a` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: java.util.AbstractList.equals |
| `a095e51d4ee04728e6739009ad709837` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.mediatek.view.impl.ViewDebugManagerImpl.debugInputEventFinished |
| `a42cd4e129c434fa99a42a524f7522c9` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libIMGegl.so] KEGLGetDrawableParameters |
| `a7ae33b0c0214eb7076386e463f5107f` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.google.firebase.analytics.connector.AnalyticsConnectorImpl.getInstance |
| `a8ebacfbd4ff93fa3eca3bb2eb56be10` | crash | 1 | — | `a283ada` | 51-04; candidate, production unverified: io.ktor.client.call.SavedCallKt.save |
| `a8f0700470b43ac173645c5ab37fe391` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: kotlinx.coroutines.channels.BufferedChannel$BufferedChannelIterator.next |
| `a8f105555cfad6f77a6b9a9865140fcc` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.graphics.Matrix.reset |
| `aa158420366d1a08ccdda3cc224b0d3a` | crash | 1 | — | `a283ada` | Google SDK/system service; investigate full variants: :com.google.android.gms.dynamite_measurementdynamite@263332022@26.33.32 (150400-0) - m7.fy.onServiceConnected |
| `ab746021b82df9fbcf79d9a769a57a70` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: R8$$SyntheticClass - androidx.compose.runtime.Recomposer$$ExternalSyntheticLambda1.b |
| `abe664a1b957439e798352973094314e` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivityKt.TextClock--RWsq2U |
| `ad40a03be33de21835e6fc98545d529f` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.google.firebase.sessions.SessionEvents.getApplicationInfo |
| `af1396b2cc0cd9f45e25ad1e375a2eca` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.compose.runtime.IntStack.<init> |
| `b0327f1df9a4a9976ea52f8f30c2a13f` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.mediatek.view.impl.ViewDebugManagerImpl.debugOnMeasureEnd |
| `b20d68b4e3ac41ed995e50146e5561ad` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.graphics.Paint.setColor |
| `b29c9bb53a0e14455f29d441798770be` | crash | 1 | — | `a283ada` | 51-02; candidate, production unverified: android.widget.TextView.onDraw |
| `b6d71dfc1392dbf08d3b0bdfe83d0208` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.compose.ui.text.googlefonts.GoogleFontImpl.hashCode |
| `b8b1e80ccc355e35d204bc57892249ec` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.transsion.lice.LiceInfo.getImpl |
| `b8c9ad4afcd02add43135a870004b003` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: R8$$SyntheticClass - com.jeerovan.comfer.notifications.NotificationSettingsPagesKt$$ExternalSyntheticLambda1.m |
| `b94db80f3927f84d91d5e0e1a3c55804` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.app.unipnp.UnionManagerComponentFactory.getUniApi |
| `b9901325c38029fb57e0ab166944ac4d` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libGLES_mali.so] glLinkProgram |
| `ba3dbe44a2e5a1781d8596ae75be2e8c` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.AppInfoViewModel.reloadList |
| `bc92b2b5f50d44ffd5548706e9947420` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.work.impl.background.systemjob.SystemJobService.<clinit> |
| `bde6b191fc3c492c52e7bcc419a31688` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat.<init> |
| `be6e5c653b17e8cb85285ef60cd236ba` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: R8$$SyntheticClass - androidx.compose.ui.platform.AndroidComposeView$$ExternalSyntheticLambda10.b |
| `c0b7883c6c0f8e4af926531d4e3366f2` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.compose.ui.platform.ComposeViewContext.getSoundEffect |
| `c0ee1fc96b92de0541a787ea0ab5202b` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.compose.ui.platform.AndroidComposeView.onRequestMeasure |
| `c205185cd8647d8d7da4e246822b61c5` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libhwui.so] SkPath::isRect const |
| `c425c7d5d92fb0c71daa10013c3354aa` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.app.Activity.onResume |
| `c652863d50207676ce76bbe61fe8f576` | anr | 1 | — | `a283ada` | Google SDK/system service; investigate full variants: :com.google.android.gms.dynamite_measurementdynamite@263330054@26.33.30 (170300-0) - m7.kn.c |
| `c804b6a09a2b4e15bd40e458542bfa6b` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libart.so] art::ConditionVariable::WaitHoldingLocks |
| `c9f38975b58c28ad5cfa210a5f5996f2` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libc.so] fsync |
| `cab12bad3d041fb290ac7b0f8fd0ccb8` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.fragment.app.FragmentActivity.onPause |
| `cbd10cb3c3e8ab9b67e8f5971eb1402d` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libhwui.so] android::uirenderer::renderthread::RenderThread::threadLoop |
| `ceb2a51d6d2b2bc5b77d105e229094a3` | anr | 1 | — | `a283ada` | Google SDK/system service; investigate full variants: :com.google.android.gms.dynamite_measurementdynamite@263234060@26.32.34 (200300-0) - m7.es.m |
| `d0b3ef8e4fc1ddf47e8b71d4adf619ed` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.compose.ui.platform.Wrapper_androidKt.setContent |
| `d3d732b32920b2c770b156a11821698b` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: [libc.so] fsync |
| `d496b1bc0bf4780ca12197d6041399be` | anr | 1 | — | `a283ada` | Google SDK/system service; investigate full variants: :com.google.android.gms.dynamite_measurementdynamite@263332060@26.33.32 (200300-0) - m7.kd.b |
| `d54565f33a33ba2fbb1173143a65a2ce` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: java.lang.ThreadLocal.set |
| `d6046c282a0bd924eea195051534dc38` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: Native method - android.os.MessageQueue.nativePollOnce |
| `d74c104fbd361cf0f46d452ca9a6836f` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: java.util.concurrent.TimeUnit.toMillis |
| `d7e18954661061dee5e858ff7bb5969b` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.work.impl.model.WorkTagDao_Impl.get |
| `da6bbb19246a0112c49661c3c534796d` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: Native method - java.lang.Object.wait |
| `dac4afdd2f2b977c4c1f1b53216aacec` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivityKt$WidgetDate$1$1.invokeSuspend |
| `db27ae77cbe527adefd81c05d8fb5e93` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainViewModel$reloadImagePath$1.invokeSuspend |
| `e10226637f387e5cf607cfb284a70625` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.app.QueuedWork.processPendingWork |
| `e1c59bac89b10b5b00e6959431419294` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.SettingsViewModel$loadSettings$1$1.invokeSuspend |
| `e29851dca837d2f79a096932ad9cd368` | anr | 1 | 4 | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivityKt.LauncherScreen |
| `e3fc4c50510e5fc24b698861571e2f74` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: java.util.Collections$UnmodifiableCollection.isEmpty |
| `e8013ca1ea69647aebbd8a216b214ff6` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.google.firebase.encoders.json.JsonDataEncoderBuilder.registerEncoder |
| `eaccd3aff68e0d92dfecc8d46190cc0b` | crash | 1 | — | `a283ada` | Framework/native crash; matching reproduction needed: java.util.TreeMap.key |
| `eddc35b0b5b61487fa2f9a0012014db6` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivityKt.AppIcon-q88KkHs |
| `eed0c1dddbced47c71e861d2812db80d` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.jeerovan.comfer.MainActivity.onCreate |
| `efe4778b8f4ebe799c448266c05cc27c` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.google.firebase.tracing.ComponentMonitor.lambda$processRegistrar$0 |
| `f24b8d48edadcf9d0e978303c4cbd182` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.view.ViewRootImpl.updateDisplayMode |
| `f3922162d654988c01ba73e91d2e8ea7` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.collection.MutableScatterSet.f |
| `f5e60b6091862e483284212ca8e08f50` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: com.mediatek.boostfwk.policy.frame.FramePolicy.initLimitTime |
| `f8432beb29f1db9b74207a2fb444695c` | anr | 1 | 1 | `a283ada` | ANR; cause unproven from sampled stack: [libGLES_mali.so] cmpbe_v2_compile_multiple_shaders |
| `f8d1cd6264290dc51433bd5d45ede048` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.view.ViewRootImpl.doTraversal |
| `f9fc899d0da72b8c07dd647303517865` | anr | 1 | — | `a283ada` | Google SDK/system service; investigate full variants: :com.google.android.gms.dynamite_measurementdynamite@263332022@26.33.32 (150400-0) - m7.eq.b |
| `fab2ab29c3b4795295b0318df04e2f74` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.compose.ui.node.NodeChain.findRootChain |
| `faf0ae1eccc98e5cf1cd9209745f0121` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.os.ThreadLocalWorkSource.setUid |
| `fbe3bda2eda7b1de1f38b1f72895a5fd` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: androidx.compose.ui.platform.AndroidComposeView.onEndApplyChanges |
| `fc2b18db7801c844b7671c3b4806807d` | anr | 1 | — | `a283ada` | 51-07; candidate, production unverified: com.jeerovan.comfer.journals.JournalActivityKt$JournalScreen$lambda$26$$inlined$sortedBy$1.compare |
| `fc5fb4f8cf4dd253e84de54217555210` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: java.lang.Integer.valueOf |
| `febb976ef16c6bcb7645fbb1cd522467` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.view.ViewRootImpl$TraversalRunnable.run |
| `ffc260735491d06707b2a29ee5e08b67` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: android.os.MessageQueue.hasMessages |
| `ffff6f54a55405c572128260dcd8c209` | anr | 1 | — | `a283ada` | ANR; cause unproven from sampled stack: Native method - android.os.BinderProxy.transactNative |
