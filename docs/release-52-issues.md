# Version 52 issues and fix-history baseline

Latest refresh: the [version-53 readiness audit](release-53-readiness.md) records
Firebase import **5** (50 groups / 145 events, through 22 September 10:58:33 UTC)
and Play import **9** (30 groups / 77 events, through 11:00 UTC). The import-4
inventory below is retained as historical evidence. Current records are in
`play_reporting.db`; local triage notes and prior snapshots were preserved.

22 September follow-up: [attempt 53-01](release-53-issues.md) fixes the missing
Journal migration and legacy plaintext conversion in the local version-53
candidate. This does not change the historical refresh below or establish
production resolution. Other platform/ANR groups remain investigations.

Additional follow-up: [attempt 53-02](release-53-issues.md) guards the two absent
framework methods behind five v52 crash groups (eight events). Missing-method
regressions were reproduced before the fix; seven host and five Samsung tests
pass afterward. Widget Binder and other system/SDK issues remain investigations.

## Firebase refresh — 22 September 2026

Firebase MCP import **4**, filtered to **52.0 (52)**, covers
**1 September 2026 00:00:00–22 September 2026 08:21:01 UTC**.

| Type | Issue groups | Events |
| --- | ---: | ---: |
| Crash | 8 | 44 |
| ANR | 38 | 83 |
| Total | 46 | 127 |

The 46 top-issue groups reconcile exactly with the independent top-versions
total of 127 events. The report requested 1,000 rows, returned 46, and exposed no
continuation token. A separate NON_FATAL query returned no groups. The existing
database schema stores crash/ANR evidence only; Play data was not refreshed.
Per-issue affected-user counts are not an app-wide unique-user count or a crash
rate. This window and its unknown release exposure are not directly comparable
to earlier imports.

Eight default issue samples belonged to version 51. They were replaced through
version-52-filtered event queries before validation/import. Every stored sample
now matches the package, issue, error type and version and lies in the requested
window. All 46 carry revision **`7f7cdb042507893fb2a9bac3eafd5b69dd1e2c56`**
(20 September, `version 52.`). Sample event times range from 21 September
09:30:36 to 22 September 08:17:47 UTC. A revision stamp is not proof of a clean
working tree or an exact signed artifact; rollout channel, APK/AAB hashes and
exposure remain unverified. Do not attribute these reports to the later spatial
wallpaper experiment merely because local builds also use version 52.

There are **21 exact issue IDs shared with the stored version-51 snapshot** and
25 IDs absent from that snapshot. An absent prior ID is not necessarily a new
defect. All Firebase issue states remain OPEN; none were changed remotely.

### Investigation priorities

1. Issue `2c36a2cdc4a142141e1b60fca86436ee`
   - Title: `androidx.room.BaseRoomConnectionManager.onMigrate`.
   - Subtitle: `IllegalStateException`: migration from **4 to 5** required but not found.
   - **Description:** 34 crash events / 21 affected users. The sample is Samsung
     SM-S938U, Android 16, with a Room/SQLite upgrade stack. Identify the specific
     database and missing migration registration; the sample omits downstream
     frames and does not identify the database file. No destructive migration or
     application-code fix was made during this reporting refresh.
2. Android API compatibility failures: 8 crash events across five groups.
   - `87073ca8fd3c019e5e5bb6172c9d96e1` (4),
     `0aeaa43763a36f5ba69451942c902f67` (1),
     `a0a49e222e9a6533a72abea121ca7b19` (1), and
     `b7313afe656f494ee046862d3a0d2b30` (1) report missing
     `WindowInsets.Type.systemOverlays()`.
   - `ecdb3c221473dea4b125a2b3eba81cfa` (1) reports missing
     `AccessibilityEvent.setAccessibilityDataSensitive(boolean)`.
   - **Description:** all five selected samples report Google Pixel 8 Pro /
     Android 14. This is sample metadata, not proof that every event came from
     that device or that its framework matches standard Android 14. Verify the
     actual framework/API availability and AndroidX compatibility before choosing
     a workaround. Titles and full subtitles are retained in the inventory.
3. Issue `1ea5988c748d88947b0a46bb05ff104d`
   - Title: `MainActivity.kt — WidgetHostManager$startListening$1.invokeSuspend`.
   - Subtitle: ANR triggered by thread waiting for a Binder transaction.
   - **Description:** 3 ANR events / 2 affected users. Infinix X6525D / Android 14
     sample shows main-thread `IAppWidgetService.startListening` →
     `AppWidgetHost.startListening` → `WidgetHostManager` (`MainActivity.kt:6001`).
     This matches the Binder-latency risk already documented for attempt 51-02;
     it is a new group, not proof that the prior off-main hierarchy failure recurred.
     Keep the earlier attempt and investigate host/service latency separately.
4. Issue `8d782ca9e751c6476994a4d4f4ad0075`
   - Title: `android.os.Parcel.createExceptionOrNull`.
   - Retained subtitle: CLOSE_SYSTEM_DIALOGS permission denial.
   - **Description:** 1 crash / 1 affected user. The actual version-52 sample is
     `SecurityException: GoogleCertificatesRslt: not allowed` on GoogleApiHandler,
     through Google measurement listener registration (realme RMX5261 / Android
     16), matching the warning in the version-51 ledger. The retained group subtitle
     must not be used to justify adding a broadcast permission.

The largest ANR group, `4d05f9e74e77520b418eac3a355108f1`, has 21 events / 19
affected users and an unknown-root-cause `nativePollOnce` sample. The measurement
Binder group `293b2117cdf3ed5f2c38dd6de73a722a` has 10 events / 3 users. Preserve
both for investigation; sampled idle/native frames alone do not establish a fix.

### Import validation

- Existing importer dry-run passed; import 4 wrote 46 issues and 46 snapshots.
- Backup: `play_reporting.db.backup-20260922T082404824178Z`.
- Six importer tests passed, including triage preservation and rollback.
- SQLite integrity/foreign-key checks and comparison of prior records against
  the backup passed after import. Version-48, -49 and -51 records and Play tables
  are preserved; no resolution states were inferred from absence or local fixes.
- Raw sample evidence is retained in the ignored database. No credentials,
  installation identifiers or complete raw events are added to Markdown.

## Acceptance and release identity

On 20 September 2026, the user confirmed manual device testing is complete.
This records user acceptance; it does not claim independent verification of
every OEM scenario or production resolution of the reported issues.

Source baseline: `97f1cbef6c5ca0c0eeb1e5630c2743a2376bd3d2` (`UI changes.`).
The source version was bumped to 52 / 52.0 on 20 September 2026. The refresh above
now establishes version-52 telemetry with the `7f7cdb0` revision stamp. Signed
APK/AAB hashes, exact shipped artifact identity and rollout date remain to be
recorded independently; a sample stamp alone does not establish those details.

## Fix attempts carried into version 52

Keep the existing attempt IDs: `51-*` identifies the version whose reports
triggered the investigation, not the release in which the fix shipped.
The [version-51 ledger](release-51-issues.md) contains exact issue IDs, prior
implementations, changed files, regression evidence and limitations for each:

| Attempt | Implementation |
| --- | --- |
| 51-01 | Exact-name Honor power-save compatibility class for factory-bypass startup crashes. |
| 51-02 | Main-thread, ordered widget host lifecycle updates with per-host failure isolation. |
| 51-03 | Block the unsafe Honor weather provider package before binding/preview. |
| 51-04 | Stream wallpaper downloads with declared/actual size limits, cancellation and partial-file cleanup. |
| 51-05 | Load widget icons/previews on demand with serialized loading and bounded bitmap sizes. |
| 51-06 | Capture a stable drawer list snapshot and deduplicate restored folder packages; duplicate-key cause remains under investigation. |
| 51-07 | Load dictation language names off the composition thread, calculate once and cache sorted labels. |

Earlier attempts remain in [the version-50 ledger](release-50-issues.md).
The local `play_reporting.db` holds version-51 issue notes and sampled evidence;
the version-51 ledger preserves the full 236-group inventory in version control.
Do not overwrite old notes or mark issues resolved solely because a mitigation
was implemented or manual testing passed.

Other release changes include [module localization](module-localization.md),
centered Notes search/add and selection actions, and matching Tasks search,
title and description input styling. Localization provenance and test results
are recorded separately; native-speaker proofreading remains pending.

## Recurrence-review procedure

1. Import version-filtered Firebase/Play evidence while preserving prior records.
2. Match issue IDs and compare current exceptions, stacks, devices and exact
   build identity; a retained group title alone does not establish recurrence.
3. Check whether the sampled build actually contains the attempted fix above.
4. Append the observed outcome to the original attempt and record any new
   implementation as a new attempt, including source commit, regression test,
   validation result and intended shipping version. Preserve ineffective attempts.
5. Compare crash/ANR rates using comparable device and release exposure after
   rollout, including 24/48-hour checkpoints. Keep unproven system/native ANRs
   open for investigation rather than claiming all issues are fixed.

## Complete version-52 inventory — import 4

Counts refer to the report interval above. Devices and Android versions describe
one verified version-52 sample per group, not all affected devices. All samples
carry the revision documented above. Titles/subtitles are Firebase group labels;
consult the stored event stack where they differ from the current exception.

| Issue ID | Type | Events | Users | Title | Subtitle | Sample device / Android | Seen in stored v51? |
| --- | --- | ---: | ---: | --- | --- | --- | --- |
| `2c36a2cdc4a142141e1b60fca86436ee` | FATAL | 34 | 21 | androidx.room.BaseRoomConnectionManager.onMigrate | java.lang.IllegalStateException - A migration from 4 to 5 was required but not found. Please provide the necessary Migration path via RoomDatabase.Builder.addMigration(...) or allow for destructive migrations via one of the RoomDatabase.Builder.fallbackToDestructiveMigration* functions. | samsung SM-S938U / 16 | No |
| `4d05f9e74e77520b418eac3a355108f1` | ANR | 21 | 19 | Native method - android.os.MessageQueue.nativePollOnce | Root cause for this ANR is unknown | ITEL itel A662L / 12 | Yes |
| `293b2117cdf3ed5f2c38dd6de73a722a` | ANR | 10 | 3 | :com.google.android.gms.dynamite_measurementdynamite@263232029@26.32.32 (190400-0) - m7.es.m | ANR triggered by thread waiting for a binder transaction | INFINIX Infinix X6525D / 14 | Yes |
| `15c1049c4d0bc536c073e5cbbeef1b7c` | ANR | 7 | 6 | Native method - android.os.MessageQueue.nativePollOnce | Root cause for this ANR is unknown | Google Pixel 5 / 14 | Yes |
| `87073ca8fd3c019e5e5bb6172c9d96e1` | FATAL | 4 | 2 | androidx.core.view.WindowInsetsCompat$TypeImpl34.toPlatformType | java.lang.NoSuchMethodError - No static method systemOverlays()I in class Landroid/view/WindowInsets$Type; or its super classes (declaration of 'android.view.WindowInsets$Type' appears in /system/framework/framework.jar!classes3.dex) | Google Pixel 8 Pro / 14 | No |
| `ec94f9d136e7da26d520d47942d46deb` | ANR | 4 | 4 | [libart.so] art::ConditionVariable::WaitHoldingLocks(art::Thread*) | ANR triggered by main thread waiting for too long | ITEL itel S667LN / 13 | Yes |
| `1ea5988c748d88947b0a46bb05ff104d` | ANR | 3 | 2 | MainActivity.kt - com.jeerovan.comfer.WidgetHostManager$startListening$1.invokeSuspend | ANR triggered by thread waiting for a binder transaction | INFINIX Infinix X6525D / 14 | No |
| `302d6c90af10653f908aac894932b278` | ANR | 2 | 2 | [libc.so] __ioctl | ANR triggered by thread waiting for a binder transaction | INFINIX Infinix X6525D / 14 | Yes |
| `4593bd5c8c65cb959be5aea7506dc75f` | ANR | 2 | 2 | Native method - android.os.MessageQueue.nativePollOnce | Root cause for this ANR is unknown | Xiaomi 2506BPN68G / 17 | Yes |
| `71851376d2af5f56955bf728d321d90b` | ANR | 2 | 2 | [libc.so] __ioctl | ANR triggered by thread waiting for a binder transaction | Google Pixel 6 Pro / 12 | Yes |
| `958ed166f1584b7c22378e82ad6968b1` | ANR | 2 | 2 | [libart.so] art::ConditionVariable::WaitHoldingLocks | ANR triggered by main thread waiting for too long | TECNO TECNO KL5 / 14 | Yes |
| `a5c1f855585a55e6a9cf91d97c82b5ea` | ANR | 2 | 1 | [libart.so] art::ConditionVariable::WaitHoldingLocks | ANR triggered by main thread waiting for too long | INFINIX Infinix X6525D / 14 | Yes |
| `0759651f42c5b565e22114f551d539fa` | ANR | 1 | 1 | Native method - android.os.BinderProxy.transactNative | ANR triggered by thread waiting for a binder transaction | TECNO TECNO BG6m / 14 | Yes |
| `0acc70619791c84c70038f113c92dab7` | ANR | 1 | 1 | com.jeerovan.comfer.ComferApp.<init> | ANR triggered by slow operations in main thread | OPPO CPH2385 / 14 | No |
| `0aeaa43763a36f5ba69451942c902f67` | FATAL | 1 | 1 | com.jeerovan.comfer.GestureShortcutActivityKt.GestureShortcutScreen | java.lang.NoSuchMethodError - No static method systemOverlays()I in class Landroid/view/WindowInsets$Type; or its super classes (declaration of 'android.view.WindowInsets$Type' appears in /system/framework/framework.jar!classes3.dex) | Google Pixel 8 Pro / 14 | No |
| `1937e46fd663b7d99751837e0df19f0d` | ANR | 1 | 1 | sh.calvin.reorderable.ReorderableLazyCollectionState$onDragStop$1.<init> | ANR triggered by slow operations in main thread | INFINIX Infinix X6525D / 14 | No |
| `1b1c16c2b655108b91d97f1c40168fa2` | ANR | 1 | 1 | androidx.compose.ui.Modifier$Node.getAggregateChildKindSet$ui | ANR triggered by slow operations in main thread | LAVA LAVA LZG411 / 15 | No |
| `1f62473dfae416accc2676ee4647022b` | ANR | 1 | 1 | com.jeerovan.comfer.utils.CommonUtil.handleStartActivity | ANR triggered by thread waiting for a binder transaction | TECNO TECNO BF6 / 12 | Yes |
| `348157e030bac748d3d1262e0edc1d81` | ANR | 1 | 1 | [libandroid_runtime.so] android::android_os_MessageQueue_nativePollOnce | Root cause for this ANR is unknown | vivo vivo 1938 / 12 | Yes |
| `416f1c679b091716c78157591849051c` | ANR | 1 | 1 | [libhwui.so] android::uirenderer::ThreadBase::waitForWork | ANR triggered by blamed thread waiting for too long | TECNO TECNO KL5 / 14 | Yes |
| `4d0c5c7386f61f4c8d155ecada9d1835` | ANR | 1 | 1 | [linker64] __dl__ZNK6soinfo10gnu_lookupER10SymbolNamePK12version_info | ANR triggered by slow IO operations | vivo vivo 2007 / 11 | No |
| `7374229d15c50d4a8bbd58af61b5fc55` | ANR | 1 | 1 | com.pairip.licensecheck.LicenseClient.unbindFromLicensingService | ANR triggered by thread waiting for a binder transaction | TECNO TECNO BG6m / 14 | No |
| `7487bf9aa6a13353cfd84b898fa70a9d` | ANR | 1 | 1 | Native method - android.os.MessageQueue.nativePollOnce | Root cause for this ANR is unknown | INFINIX Infinix X6532 / 14 | Yes |
| `74b003f0db14a1943ad8176be8e18c6c` | ANR | 1 | 1 | com.jeerovan.comfer.MainActivityKt.EffectTextBlock-K4T8iFM | ANR triggered by slow operations in main thread | TECNO TECNO BG6m / 14 | No |
| `77182372e143cca4a3c3e6f31cc13711` | ANR | 1 | 1 | com.jeerovan.comfer.ComferApp.newImageLoader | ANR triggered by slow operations in main thread | TECNO TECNO BG6m / 14 | Yes |
| `870f8c2c2dc68acb0432a0e22e6a6366` | ANR | 1 | 1 | androidx.emoji2.text.flatbuffer.Table.__offset | ANR triggered by slow operations in main thread | vivo V2026 / 11 | No |
| `8d148077ccbe90466167a0aa6fc2be1d` | ANR | 1 | 1 | [libGLES_mali.so] glProgramBinary | ANR triggered by slow operations in blamed thread | realme RMX3501 / 13 | Yes |
| `8d782ca9e751c6476994a4d4f4ad0075` | FATAL | 1 | 1 | android.os.Parcel.createExceptionOrNull | java.lang.SecurityException - Permission Denial: android.intent.action.CLOSE_SYSTEM_DIALOGS broadcast from com.jeerovan.comfer (pid=2522, uid=10161) requires android.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS. | realme RMX5261 / 16 | Yes |
| `901023f312e4c8a37f604ccfb3ec41c6` | ANR | 1 | 1 | com.jeerovan.comfer.MainActivityKt$LauncherTwoPaneLayout$1$1.measure-3p2s80s | ANR triggered by slow operations in main thread | TECNO TECNO BG6m / 14 | Yes |
| `973a7a1429c335323bfe72f30dad1df7` | FATAL | 1 | 1 | android.app.ActivityClient.activityResumed | android.os.DeadSystemException | samsung SM-S918N / 16 | Yes |
| `a0a49e222e9a6533a72abea121ca7b19` | FATAL | 1 | 1 | com.jeerovan.comfer.ManageAppListActivityKt.ManageLayersScreen | java.lang.NoSuchMethodError - No static method systemOverlays()I in class Landroid/view/WindowInsets$Type; or its super classes (declaration of 'android.view.WindowInsets$Type' appears in /system/framework/framework.jar!classes3.dex) | Google Pixel 8 Pro / 14 | No |
| `b154eac56826e4d1da6ef2e88376ced3` | ANR | 1 | 1 | [libGLES_mali.so] egl_color_buffer_wrap_external_planar | ANR triggered by slow operations in blamed thread | TECNO TECNO KL5 / 14 | No |
| `b5395f3cd6c5f753025111ba803fe29e` | ANR | 1 | 1 | com.google.firebase.tracing.ComponentMonitor.lambda$processRegistrar$0 | ANR triggered by main thread waiting for too long | ZTE Z2472 / 15 | No |
| `b7313afe656f494ee046862d3a0d2b30` | FATAL | 1 | 1 | com.jeerovan.comfer.AppSelectionActivityKt.AppSelectionScreen | java.lang.NoSuchMethodError - No static method systemOverlays()I in class Landroid/view/WindowInsets$Type; or its super classes (declaration of 'android.view.WindowInsets$Type' appears in /system/framework/framework.jar!classes3.dex) | Google Pixel 8 Pro / 14 | No |
| `bd8d0004ffcf5b7910db383016a1907b` | ANR | 1 | 1 | kotlin.collections.ArrayDeque.getSize | ANR triggered by slow operations in main thread | vivo I2219 / 15 | No |
| `c849df8b408ef236deaa1d03d5641bb7` | ANR | 1 | 1 | android.view.WindowManagerGlobal.addView | ANR triggered by slow operations in main thread | itel itel A661W / 11 | No |
| `cdbd3d02e0be2e73f8749122ad839663` | ANR | 1 | 1 | com.google.android.gms:play-services-basement@@18.9.0 - com.google.android.gms.common.api.internal.BackgroundDetector.zza | ANR triggered by slow operations in main thread | INFINIX Infinix X6525D / 14 | No |
| `d22aff7a52e2a7191f3ae59075f894eb` | ANR | 1 | 1 | [libart.so] art::ConditionVariable::WaitHoldingLocks | ANR triggered by main thread waiting for too long | ITEL itel A667L / 14 | No |
| `d370f53b3ba600efab5d8f3a63c9326f` | ANR | 1 | 1 | com.google.android.datatransport.runtime.dagger.internal.DoubleCheck.get | ANR triggered by slow operations in main thread | TECNO TECNO KL5 / 14 | No |
| `df03497b378bd61024e66b27f2f5cad3` | ANR | 1 | 1 | androidx.compose.ui.node.NodeChain.getTail$ui | ANR triggered by slow operations in main thread | AIPLUS AI+ Nova 1 5G / 15 | No |
| `e246a54e106dd3bb5a3779892b86903e` | ANR | 1 | 1 | [libart.so] art::ConditionVariable::WaitHoldingLocks | ANR triggered by slow IO operations | TECNO TECNO BG6m / 14 | No |
| `ecdb3c221473dea4b125a2b3eba81cfa` | FATAL | 1 | 1 | androidx.core.view.accessibility.AccessibilityEventCompat$Api34Impl.setAccessibilityDataSensitive | java.lang.NoSuchMethodError - No virtual method setAccessibilityDataSensitive(Z)V in class Landroid/view/accessibility/AccessibilityEvent; or its super classes (declaration of 'android.view.accessibility.AccessibilityEvent' appears in /system/framework/framework.jar!classes3.dex) | Google Pixel 8 Pro / 14 | No |
| `ed0aa54e9217f2cd395c628b46509826` | ANR | 1 | 1 | java.util.TreeMap.getEntryUsingComparator | ANR triggered by slow operations in main thread | realme RMX3834 / 15 | No |
| `ef715e1213c320b2739fbc5b0ead0a5f` | ANR | 1 | 1 | [libart.so] art::ConditionVariable::WaitHoldingLocks | ANR triggered by thread waiting for a binder transaction | INFINIX Infinix X6525D / 14 | Yes |
| `f3922162d654988c01ba73e91d2e8ea7` | ANR | 1 | 1 | androidx.collection.MutableScatterSet.f | ANR triggered by slow operations in main thread | Xiaomi 22011119UY / 13 | Yes |
| `f7408226fc46cdd8762a94984ad4c127` | ANR | 1 | 1 | [libhwui.so] GrGeometryProcessor::ProgramImpl::emitCode | ANR triggered by slow operations in blamed thread | motorola moto e14 / 14 | No |
