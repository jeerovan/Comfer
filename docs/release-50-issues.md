# Version 50 issue-fix ledger

Started 14 September 2026. Target release: **50**. Baseline source: `7692267`; fixes below are working-tree changes until committed. At the user's request, version 50.0 has now been assigned and a signed release APK installed fresh on Samsung for acceptance testing. Play publication remains pending.

## Evidence and comparison rules

Version 49 shipped without notification-settings backup/restore (confirmed by the release owner). Local backup support was added by `cb12787`; version 50 must include it. Historical claims that version 49 had not shipped are superseded.

Firebase MCP import 2 in `play_reporting.db`: version `49.0 (49)`, 17 June–14 September 2026 06:06:41 UTC, **48 groups / 187 events (14 crash groups / 84 events; 34 ANR groups / 103 events)**. Import 1 has version 48 over 13 August–11 September 2026 23:59:59 UTC: 206 groups / 1,182 events. These are different windows and exposures, not comparable crash rates. Issue-user counts must not be summed into unique users. Some Firebase sample traces omit frames; one sample has no stack. Nine default samples belonged to version 48 and were replaced with version-filtered version-49 samples before importing.

For every future attempt, append an attempt ID, full provider issue ID, affected release/device, observed evidence, hypothesis, exact files/commit, test/reproduction outcome, shipped artifact/build, and 24/48-hour production follow-up. Never overwrite an earlier attempt. Use **proposed → implemented → locally tested → shipped → production verified**, or **ineffective/reverted** with the reason. A passing test or absent report alone does not prove production success. Keep provider state and local investigation state distinct; do not close Firebase issues just because code changed.

## Trackable phases

- [x] Import and triage every version-49 group; compare prior safeguards and record baseline.
- [x] Implement five evidence-backed first-party crash mitigations below.
- [x] Complete bounded local regression/retained-safeguard checks and record exact results; broader compatibility harness remains a release gate.
- [ ] Investigate remaining widget hierarchy, accessibility framework and first-party ANR families on affected configurations; avoid speculative dependency changes or broad exception swallowing.
- [x] Notification backup transfer into fresh disposable app data.
- [ ] Device acceptance, including task UI/reminders and upgrade from the shipped version-49 artifact.
- [x] Assign version 50 and build/verify signed APK for requested Samsung acceptance testing.
- [ ] Build/verify the final AAB and inspect Play-generated artifacts after acceptance.
- [ ] Staged release; compare 24/48-hour telemetry with device/version exposure and record outcomes for each attempt.

## Preserve and evaluate earlier attempts

- `e001473`, `7dc831c` (source at version 48): bounded owned icon snapshots, Honor power-save compatibility, WorkManager platform preflight/manual initialization, widget ownership and unsafe OEM provider guards. Keep these safeguards. The version-49 snapshot has no exact Honor power-save/WorkManager startup group; that is encouraging but affected-device exposure is unknown, so neither is production verified.
- Widget hierarchy group `b9243dd5994a058da551ec3dcf57b5c3` persists in version 49 (one event), as do Huawei weather and Honor gallery provider crashes. Prior fixes are not sufficient evidence of resolution. Verify exact provider component and shipped artifact before widening guards; asynchronous vendor failures cannot be safely repaired by catching unrelated UI exceptions.
- `511dd6c`: wallpaper decoding was changed to bounded sampled decode with smaller retries on allocation failure. The version-49 sample still shows createScaledBitmap, which is absent from the current decoder. Preserve the current fix and rerun allocation-failure tests; confirm the release artifact before attributing improvement.
- Existing component/profile lazy keys preserved separate activities/profiles but did not remove duplicate entries. Attempt 50-04 addresses that remaining condition.
- Native/system-death, Binder, renderer and incomplete ANR stacks remain investigations, not automatically first-party fixes. Preserve accessibility; do not disable it to hide the API-34 missing-method report.

## Attempts

### 50-01 — `64a0bfd0b15b3d38c91b303172f08da1`

- Baseline: 35 version-49 events; LazyDsl.kt - com.jeerovan.comfer.notifications.NotificationInboxActivityKt$NotificationHomeEntry_eDu8a20$lambda$10$0$$inlined$items$default$4.invoke.
- Change: Use the existing owned/bounded drawable painter in NotificationHomeEntry; remove raw toBitmap conversion.
- Files under `app/src/main/java/com/jeerovan/comfer/`: DrawableExt.kt; notifications/NotificationInboxActivity.kt.
- Verification: Zero intrinsic size renders a bounded red bitmap; original drawable bounds survive.
- Status: implemented; final local test result recorded below. Production result: pending version-50 rollout. Commit/artifact: pending.

### 50-02 — `d1a1d5b193c2d61bbd35024cb27fd42a`

- Baseline: 19 version-49 events; android.view.View.sanitizeFloatPropertyValue.
- Change: Guard unmeasured, non-finite and overflowing drawer layer scale; preserve valid interpolation.
- Files under `app/src/main/java/com/jeerovan/comfer/`: ui/LayerScale.kt; MainActivity.kt.
- Verification: Regression failed before guard; zero/negative/NaN/infinity/overflow and normal ratios tested.
- Status: implemented; final local test result recorded below. Production result: pending version-50 rollout. Commit/artifact: pending.

### 50-03 — `a6befacbdc58480db83738fc99edea2c`

- Baseline: 11 version-49 events; com.jeerovan.comfer.ui.theme.ThemeKt.ComferTheme.
- Change: Fall back to matching static light/dark palette only when dynamic resources are missing.
- Files under `app/src/main/java/com/jeerovan/comfer/`: ui/theme/Theme.kt.
- Verification: Inject missing resource; verify fallback and successful dynamic palette.
- Status: implemented; final local test result recorded below. Production result: pending version-50 rollout. Commit/artifact: pending.

### 50-04 — `159e25351be7042db517f3af684684db`

- Baseline: 5 version-49 events; androidx.compose.ui.internal.InlineClassHelperKt.throwIllegalArgumentException.
- Change: Deduplicate initial and filtered search results using the same component/profile identity used by both lazy layouts.
- Files under `app/src/main/java/com/jeerovan/comfer/`: SearchAppIdentity.kt; MainActivity.kt.
- Verification: Repeated entries collapse; separate activities and work profiles survive; empty input supported.
- Status: implemented; final local test result recorded below. Production result: pending version-50 rollout. Commit/artifact: pending.

### 50-05 — `8b119648c25bd19c6b17322bbb749438`

- Baseline: 1 version-49 events; com.jeerovan.comfer.utils.CommonUtil.openUrl.
- Change: Contain AndroidRuntimeException from external URL launch and show the existing failure message.
- Files under `app/src/main/java/com/jeerovan/comfer/`: utils/CommonUtil.kt.
- Verification: Inject startActivity failure on the main thread; Settings call returns without crashing.
- Status: implemented; final local test result recorded below. Production result: pending version-50 rollout. Commit/artifact: pending.

## Notification backup compatibility

Version 50 must export/restore notification configuration already implemented locally, including rollback/recovery, with restore paused for review. Existing archives from version 49 have no notification section: restoration must preserve the device's current notification settings, not reset them. Upgrading cannot retroactively add missing settings to an old archive. Saved notification history remains excluded. NotificationBackupRestoreTest covers repeated round trips, old archives, invalid input, commit rollback, interrupted journal recovery and serialized access.

## Full version-49 triage inventory

Counts refer only to import 2. Exact-ID version-48 counts are context, not exposure-adjusted comparisons.

| Firebase issue ID | Type | V49 events | V48 events, same ID | Disposition / title |
|---|---|---:|---:|---|
| `64a0bfd0b15b3d38c91b303172f08da1` | crash | 35 | Not in snapshot | Attempt 50-01: LazyDsl.kt - com.jeerovan.comfer.notifications.NotificationInboxActivityKt$NotificationHomeEntry_eDu8a20$lambda$10$0$$inlined$items$default$4.invoke |
| `4d05f9e74e77520b418eac3a355108f1` | anr | 32 | 299 | Investigate ANR; no proven fix yet: Native method - android.os.MessageQueue.nativePollOnce |
| `d1a1d5b193c2d61bbd35024cb27fd42a` | crash | 19 | Not in snapshot | Attempt 50-02: android.view.View.sanitizeFloatPropertyValue |
| `15c1049c4d0bc536c073e5cbbeef1b7c` | anr | 14 | 120 | Investigate ANR; no proven fix yet: Native method - android.os.MessageQueue.nativePollOnce |
| `a6befacbdc58480db83738fc99edea2c` | crash | 11 | Not in snapshot | Attempt 50-03: com.jeerovan.comfer.ui.theme.ThemeKt.ComferTheme |
| `0a427c5692949811fcbb46d8e5e16a3f` | crash | 5 | Not in snapshot | Investigate platform/provider or retained safeguard: com.huawei.android.totemweather.WeatherDataManager$HwServiceConnection.connect |
| `159e25351be7042db517f3af684684db` | crash | 5 | Not in snapshot | Attempt 50-04: androidx.compose.ui.internal.InlineClassHelperKt.throwIllegalArgumentException |
| `ec94f9d136e7da26d520d47942d46deb` | anr | 5 | 28 | Investigate ANR; no proven fix yet: [libart.so] art::ConditionVariable::WaitHoldingLocks(art::Thread*) |
| `416f1c679b091716c78157591849051c` | anr | 4 | 19 | Investigate ANR; no proven fix yet: [libhwui.so] android::uirenderer::ThreadBase::waitForWork |
| `e29851dca837d2f79a096932ad9cd368` | anr | 4 | Not in snapshot | Investigate ANR; no proven fix yet: com.jeerovan.comfer.MainActivityKt.LauncherScreen |
| `302d6c90af10653f908aac894932b278` | anr | 3 | 19 | Investigate ANR; no proven fix yet: [libc.so] __ioctl |
| `7487bf9aa6a13353cfd84b898fa70a9d` | anr | 3 | 17 | Investigate ANR; no proven fix yet: Native method - android.os.MessageQueue.nativePollOnce |
| `2cf94c3abf5079d50a2a13d1cfdbb3fb` | anr | 2 | Not in snapshot | Investigate ANR; no proven fix yet: android.os.ThreadLocalWorkSource.restore |
| `49e77967e24836ed20e1450247201422` | anr | 2 | 21 | Investigate ANR; no proven fix yet: [libhwui.so] android::uirenderer::ThreadBase::waitForWork |
| `6928bd70aff53e74c689ce73a5ef5d0e` | anr | 2 | Not in snapshot | Investigate ANR; no proven fix yet: com.jeerovan.comfer.MainActivityKt.AppListOverlay |
| `83d4ab4722c15cc3aa1166087936712f` | anr | 2 | Not in snapshot | Investigate ANR; no proven fix yet: kotlinx.coroutines.flow.internal.AbstractSharedFlow.freeSlot |
| `890e086d60a0dac62be2975dc400ec17` | anr | 2 | 2 | Investigate ANR; no proven fix yet: [libart.so] art::ConditionVariable::WaitHoldingLocks |
| `8b286badc3fcea82f03b1f651ac939bb` | anr | 2 | Not in snapshot | Investigate ANR; no proven fix yet: com.jeerovan.comfer.ComposableSingletons$MainActivityKt.lambda_867179202$lambda$0 |
| `8cea93e60ac6516390e783687ba4e15e` | anr | 2 | Not in snapshot | Investigate ANR; no proven fix yet: com.pairip.licensecheck.LicenseClient.bindToLicensingService |
| `958ed166f1584b7c22378e82ad6968b1` | anr | 2 | 20 | Investigate ANR; no proven fix yet: [libart.so] art::ConditionVariable::WaitHoldingLocks |
| `a224edc3bf737cfceeecfae8184d7881` | anr | 2 | 1 | Investigate ANR; no proven fix yet: [libGLES_mali.so] eglQuerySurface |
| `ca482bfbfda25e326bb1f30b50bfe6c7` | anr | 2 | Not in snapshot | Investigate ANR; no proven fix yet: R8$$SyntheticClass - io.ktor.client.plugins.contentnegotiation.ContentNegotiationKt$$ExternalSyntheticLambda0.m |
| `ee55825a1619a156570c521037844675` | anr | 2 | Not in snapshot | Investigate ANR; no proven fix yet: com.jeerovan.comfer.MainActivityKt$LauncherTwoPaneLayout$1$1.measure_3p2s80s$lambda$1 |
| `01b2470c281193e6f14643cc765f4a1e` | anr | 1 | 1 | Investigate ANR; no proven fix yet: [libart.so] art::DumpNativeStack |
| `0565d3f7e0abd5c37d2a5f08555505da` | anr | 1 | Not in snapshot | Investigate ANR; no proven fix yet: kotlinx.coroutines.flow.internal.AbstractSharedFlow.freeSlot |
| `081d507f42887a933d6a06d8034bf4fc` | crash | 1 | 2 | Investigate platform/provider or retained safeguard: android.app.LoadedApk.forgetReceiverDispatcher |
| `0d14fbe7fb5154a32e61ce10b717742b` | anr | 1 | Not in snapshot | Investigate ANR; no proven fix yet: com.google.android.datatransport.runtime.util.PriorityMapping.valueOf |
| `1f62473dfae416accc2676ee4647022b` | anr | 1 | Not in snapshot | Investigate ANR; no proven fix yet: com.jeerovan.comfer.utils.CommonUtil.handleStartActivity |
| `23ef0b2efb89a5e8e63dd43d82aee5f6` | crash | 1 | Not in snapshot | Investigate platform/provider or retained safeguard: android.app.ActivityThread.handleStopService |
| `4c07795404b768bd410a7584a828ca70` | anr | 1 | Not in snapshot | Investigate ANR; no proven fix yet: android.view.InsetsController.lambda$new$2 |
| `6cb047a17b6eac2ddad61dd6e41d45b5` | crash | 1 | 1 | Investigate platform/provider or retained safeguard: android.app.ActivityClient.reportSizeConfigurations |
| `7143923d65b86aafd879de983c99d964` | anr | 1 | 1 | Investigate ANR; no proven fix yet: com.jeerovan.comfer.utils.CommonUtil.handleStartActivity |
| `7c88610d1ae7d56449279325e13011c6` | anr | 1 | Not in snapshot | Investigate ANR; no proven fix yet: com.jeerovan.comfer.MainActivity.onStop |
| `7db5df868029a8138a743355aaccc372` | anr | 1 | Not in snapshot | Investigate ANR; no proven fix yet: androidx.appcompat.app.AppCompatDelegate.<clinit> |
| `8b119648c25bd19c6b17322bbb749438` | crash | 1 | Not in snapshot | Attempt 50-05: com.jeerovan.comfer.utils.CommonUtil.openUrl |
| `8d148077ccbe90466167a0aa6fc2be1d` | anr | 1 | 5 | Investigate ANR; no proven fix yet: [libGLES_mali.so] glProgramBinary |
| `8fb7226a47bffdacf7a8227f961882e1` | anr | 1 | Not in snapshot | Investigate ANR; no proven fix yet: com.jeerovan.comfer.DrawableExtKt.toOwnedBitmap |
| `901023f312e4c8a37f604ccfb3ec41c6` | anr | 1 | 12 | Investigate ANR; no proven fix yet: com.jeerovan.comfer.MainActivityKt$LauncherTwoPaneLayout$1$1.measure-3p2s80s |
| `93f14c07098a1c9b606b269e406974f7` | crash | 1 | Not in snapshot | Retain sampled wallpaper fix; verify artifact: com.jeerovan.comfer.utils.CommonUtil.decodeWallpaperBitmap |
| `a5c1f855585a55e6a9cf91d97c82b5ea` | anr | 1 | 10 | Investigate ANR; no proven fix yet: [libart.so] art::ConditionVariable::WaitHoldingLocks |
| `b9243dd5994a058da551ec3dcf57b5c3` | crash | 1 | 17 | Investigate platform/provider or retained safeguard: android.widget.FrameLayout.onMeasure |
| `bebe81192d44401a0ca0c36f110bb755` | anr | 1 | Not in snapshot | Investigate ANR; no proven fix yet: com.pairip.licensecheck.LicenseClient.bindToLicensingService |
| `cd62ce00006e78a5e7d661d2f24ced87` | crash | 1 | Not in snapshot | Investigate platform/provider or retained safeguard: com.hihonor.gallery.app.widgetWonderful.WidgetWonderfulPhotoView.lambda$onFinishInflate$0 |
| `d407e59611040a5624a808c1c021b4e8` | anr | 1 | 2 | Investigate ANR; no proven fix yet: [libGLES_mali.so] cmpbe_v2_compile_multiple_shaders |
| `d824165a07c803ce6fd2fd97ab4af17f` | crash | 1 | Not in snapshot | Investigate platform/provider or retained safeguard: android.os.Parcel.createExceptionOrNull |
| `ecdb3c221473dea4b125a2b3eba81cfa` | crash | 1 | Not in snapshot | Investigate platform/provider or retained safeguard: androidx.core.view.accessibility.AccessibilityEventCompat$Api34Impl.setAccessibilityDataSensitive |
| `f8432beb29f1db9b74207a2fb444695c` | anr | 1 | 2 | Investigate ANR; no proven fix yet: [libGLES_mali.so] cmpbe_v2_compile_multiple_shaders |
| `fe2000c2e5ac4841cdeb155fc710d5af` | anr | 1 | Not in snapshot | Investigate ANR; no proven fix yet: androidx.compose.ui.platform.ComposeViewContext$callback$1.onConfigurationChanged |

## Validation and release outcomes

14 September 2026: **157 JVM tests passed**, zero failures/errors/skips. The new scale test first failed for the intended zero-size division, then passed after the guard. Other fixes were source-supported with injected-failure tests rather than reproductions on the original OEM devices.

API-24 isolated emulator run reported OK (20 tests): **19 passed, 1 assumption-skipped**. Passed: Release50RegressionTest 4, NotificationBackupRestoreTest 7, WallpaperDecodeTest 3, WidgetInflationGuardTest 3, SubscriptionActivityCompatibilityTest 2. WorkManagerCompatibilityTest requires its fresh-package API-34 healthy/missing-namespace harness and was skipped; this run does not certify it. Debug/test builds and whitespace checks passed.

Notification cold-start transfer additionally passed **2 explicit stages** on API 24: export, pull archive, clear only the disposable app data, push archive and restore into a fresh process. Verified persisted configuration and paused restore. This is a fresh-data transfer test, not a signed version-49 upgrade test.

Attempts 50-01 through 50-05 are **locally tested**, not shipped/production verified. Their database notes now link these attempt IDs; local status changed from pending to investigating, with resolution fields preserved and a pre-update database backup. Original affected-device reproduction, broad UI acceptance, signed artifact validation, version-code assignment and rollout follow-up remain open. Append dated production outcomes here, retaining this checkpoint.

14 September 2026, subsequent requested release-device install: assigned `versionCode=50`, `versionName=50.0`; release/debug builds passed. Signed/minified release APK signature verified and all 47 manifest component classes found in DEX. APK SHA-256: `7e0359efb71a4d9ce71a07b28236f4a63eeb23b3153e23bf3b372c999dcff625`. Uninstalled Samsung Galaxy A30's debug app without retaining data, installed this release fresh, and cold launch returned Status: ok. User will restore their backup manually. Emulator updated in place with matching debug version. This is local acceptance testing, not Play distribution or production verification.
