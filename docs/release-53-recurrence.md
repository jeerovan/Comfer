# Version 53 recurrence review — 26 September 2026

Status: five fixes implemented and tested locally. This is a local
next-release candidate, not a production-resolution record. Source baseline:
`12d94c2`; observed release samples overwhelmingly identify `adbaffe`.

## Evidence and recurrence

Firebase import 6, app `1:141154670700:android:3463921d29d4a97bee2049`,
version `53.0 (53)`, September 1–26 2026 09:39:15 UTC: 195 groups / 605
events (9 crash groups / 20 events; 186 ANR groups / 585 events). Fifteen
default samples were replaced with version-filtered samples before import.
72 exact IDs / 471 events also occur in earlier stored Firebase versions;
123 IDs / 134 events are newly observed in this local history. New IDs do not
establish newly introduced defects. Windows and release exposure differ.

194 selected samples carry revision `adbaffef671af0bb3bc5d79527776611c77a1c77`;
one has no build stamp. These samples predate the RTL fix and later wallpaper
changes. Each group has one selected sample, not a complete set of variants.
The unstamped group is `cfa1dc25ac11edc73ae56b57d8d0d3c9`. Full per-issue
history, sample frame/revision and disposition are in the
[195-row inventory](release-53-recurrence-inventory.md).

## What previous fixes did, and what recurred

| Previous attempt | Implemented behavior | Version-53 observation |
| --- | --- | --- |
| 50-01 through 50-05 | Bounded notification drawables, finite drawer scale, dynamic-palette fallback, component/profile search deduplication, external URL failure containment. | None of their five exact target IDs appears in import 6. This is not proof of resolution. |
| 51-01 / 51-03 | Honor factory-bypass activity and exact unsafe weather-provider package guard. | Target crashes absent from this snapshot; keep both mitigations. |
| 51-02 | Main-thread ordered widget lifecycle to protect pending RemoteViews/hierarchy changes. | Original hierarchy crash IDs absent. The documented Binder risk persists: two start groups / 6 ANRs, plus four fresh stop groups / 8 ANRs. 53-08 changes only stop threading and lifecycle coordination; starts remain an open risk. |
| 51-04 / 51-05 | Stream and bound wallpaper downloads; load widget images lazily with bounded, serialized decoding. | Target allocation-failure IDs absent. GPU/resource/GC waits are not evidence that those exact failures returned. |
| 51-06 | Stable drawer-list snapshot and restored-folder deduplication after the earlier search-key fix. | Target duplicate-key IDs absent. Keep the prior limitation: the original failure was not fully explained. |
| 51-07 | Defer/cache dictation language labels and compute them off composition. | Target ICU language-name ANR ID absent. |
| 53-01 | Register Journal schema migrations and transactional legacy encryption. | Missing-migration crash absent; sample revision includes `d0ab736`. |
| 53-02 / 53-03 | Exact missing-framework-method bridges; repair generated Room compilation and gate packaging. | Target missing-method crashes absent; sample revision is `adbaffe`, which includes these changes. |
| 53-05 | Bound reminder broadcasts and durably replay actions. | Implemented in later `53f2cc7`; the 194 stamped `adbaffe` samples do not test it. |
| RTL drawer follow-up | Anchor physical icon coordinates with AbsoluteAlignment.TopLeft. | Implemented in `12d94c2`, after the sampled build. Keep its separate device evidence; do not judge its result from these samples. |

The recurring startup group was still an **open investigation** in the prior
ledger, not a previously confirmed fixed issue. Earlier asynchronous preference
startup did not cover WorkManager. Group `8d782ca9e751c6476994a4d4f4ad0075`
also demonstrates why retained titles and IDs are not sufficient root-cause
matches: its sampled exception changed between releases.

## Planned attempts (recorded before implementation)

| Attempt | Evidence and prior work | Planned implementation | Required validation |
| --- | --- | --- | --- |
| 53-07 | Recurrent `05babf260a655d1e7a2738eef61f0a2b` (2) and fresh `a1f4abcadd5448f75431cabc3d5a0a31` (1): WorkManager initialization on Main. Earlier compatibility preflight avoided missing API crashes but left Room/network-tracker setup in Application.onCreate. | Schedule the complete compatibility check, initialization and unique periodic enqueue off Main. Keep the existing runtime guard and UPDATE policy. Isolate optional scheduling failures from launcher/data startup. | Block injected setup and prove Main stays responsive; failure/retry; real unique periodic work; compatibility regressions. |
| 53-08 | Fresh stopListening groups `de9f6297165cb12f529166bea25085e4` (3), `172e5e87ec9f8a292d4f3de43cb987d2` (2), `3991552a7f31cef4bd941b81fbfd214e` (2), `1bb48fa985f3160d6aef914f924a5760` (1). Prior 51-02 put starts and stops on Main to fix RemoteViews hierarchy races. | Move only stopListening Binder calls off Main; serialize lifecycle transitions, coalesce obsolete requests and retain startListening on Main because it applies pending RemoteViews inline. Avoid repeated registration of successfully started hosts; retain per-host recovery. | Stalled stop leaves Main responsive; restart cannot overtake stop; starts/RemoteViews stay on Main; rapid transitions, duplicates and failed-host recovery. startListening Binder latency remains open. |
| 53-09 | Fresh `bd2ab4706a80a4e93f89effbf4ac6300` (4): vibrator service explicitly rejects missing VIBRATE permission on Vivo/Android 16. Obfuscated click handler ownership is unknown. Manifest lacks the normal permission. | Declare the normal VIBRATE permission; no runtime dialog or unrelated broad exception suppression. | Installed manifest permission check and a real vibration request. Affected Vivo/click-path acceptance still needed. |
| 53-10 | Fresh `961b98ab1c8e66832d52638f4c0972f7` (1): LauncherApps service lookup in the ViewModel constructor blocks Main. Callback registration and inventory reads already use background dispatchers. | Defer LauncherApps/UserManager acquisition until those existing background paths first use them. | Constructor must not call either service; real inventory still loads and refreshes in LTR/RTL. |
| 53-11 | Fresh `924617660b314ca9b61c0d0b223787c0` (1): drawer selection calls View.playSoundEffect during composition side effects, blocked on audio-service Binder lookup. | Enqueue optional drawer click sounds to a bounded background consumer using AudioManager; preserve the View's enabled/attached checks, system sound setting and click effect. Drop stale feedback and close the queue when the drawer leaves composition. | Block sound service while Main stays responsive, coalesce bursts, recover from runtime failure, and exercise drawer gestures in LTR/RTL. |

## Open investigations

Recurrent startListening groups `1ea5988c748d88947b0a46bb05ff104d` (5) and
`ca287ab996c84b875fddc790c9409329` (1) still contain synchronous service waits.
Moving the entire start operation to IO would reintroduce the RemoteViews defect
from 51-02. A safe replacement requires separating service retrieval from view
application using supported APIs, or a demonstrated alternative; no such fix is
claimed here.

System-server death (12 events), framework parcel/insets failures, driver waits,
Google measurement/licensing waits, GC/resource-lock stacks and idle
nativePollOnce samples do not establish an app root cause. Preserve them as
investigating, with no blanket exception handlers, dependency changes or claims
that code edits resolve all 605 events. In particular, the current sample for
`8d782ca9e751c6476994a4d4f4ad0075` really is the system PhoneFallbackEventHandler
CLOSE_SYSTEM_DIALOGS path; versions 51/52 sampled a different Google certificate
exception under that same ID. Exact-ID recurrence alone is insufficient.

## Follow-up and release identity

Keep original attempts in [version 50](release-50-issues.md),
[version 51](release-51-issues.md), [version 52](release-52-issues.md), and
[the prior version-53 remediation](release-53-issues.md). Append outcomes and
validation below; retain unsuccessful attempts. Before rollout record the new
version/build revision and artifact hashes. After rollout compare matching
signatures/variants, devices, OS and exposure at 24/48 hours and seven days.
Absence in a small sample is not resolution. No Firebase issue state is changed.

## Implemented result and remaining risks

- **53-07:** `ComferApp.scheduleImageWorker` runs complete setup on the existing
  process IO scope, independent of data readiness. Runtime setup failures are
  logged; cancellation propagates; the next process start can retry. Compatibility
  preflight and unique UPDATE work remain. Test calls also verify repeated setup
  leaves one unfinished periodic request. This removes the demonstrated Main
  blocking path; it does not make a stalled system service finish sooner.
- **53-08:** one Main consumer owns host lifecycle state and a conflated request
  channel. Only stop Binder calls run on IO. A restart waits behind a stop;
  successful hosts do not register twice. Failed starts remain eligible for retry
  and still receive cleanup; failed stops retain their retry obligation. A blocked
  stop can delay widget resubscription while the launcher stays responsive.
- **53-09:** the installed package declares/grants normal VIBRATE permission and
  can perform direct feedback. No runtime prompt is added. The sample's obfuscated
  `c0.b.M` handler was not attributed to a specific app/widget library; testing on
  the original Vivo Android-16 firmware remains required.
- **53-10:** lazy service acquisition now first occurs in existing background
  callback/inventory work. The regression verifies both lookups actually occur,
  neither on Main. Existing resource/icon serialization is retained.
- **53-11:** only the high-frequency drawer-selection sound path changes.
  `DrawerClickSound` acquires AudioManager and plays default-volume click feedback
  on IO, with one pending request and a 100-ms stale cutoff. View enabled/attached
  checks stay on Main; the queue closes with composition. Other ordinary click
  sites are outside this sampled fix.

These five attempts target **9 groups / 17 events** (1 recurring group / 2
events; 8 newly observed groups / 15 events). The other **186 groups / 588
events** remain investigations. Counts describe the imported reports, not events
proven prevented by the candidate. No production issue is marked resolved.

Platform references: [AppWidgetHost implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/appwidget/AppWidgetHost.java)
shows pending-view processing during start and a service-only stop;
[AudioManager implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r1/media/java/android/media/AudioManager.java)
documents system-setting-aware default-volume feedback. The [Vibrator contract](https://developer.android.com/reference/android/os/Vibrator)
requires the normal permission. OEM widget/click behavior still needs affected-device acceptance.

## Repeatable future analysis

After importing the next release, run:

```sh
venv/bin/python scripts/analyze_crashlytics_recurrence.py --version 54 \
  --output validation-artifacts/firebase-v54-recurrence.json
```

Use the actual next version if it differs from 54. The read-only analyzer records
the import window, exact-ID history, prior statuses/notes and both sample
signatures/revisions. It does not aggregate old counts into current totals or
equate an unchanged ID with an unchanged exception. Retain this report and append
each attempt's rollout revision, exposure and result; do not rewrite its initial
plan. The current five attempts are **implemented, locally tested, not released**.

## Validation — 26 September

- Pre-fix API-24 emulator: both new baseline cases failed for the intended
  reasons (missing VIBRATE grant and a stalled stop blocking Main). The WorkManager,
  service-lookup and sound paths were established from version-filtered stacks and
  source; their original production latency was not reproduced on these devices.
- All **228 app unit tests** passed (zero failures/errors/skips), using
  `:app:testNotificationTestUnitTest` with `-PcomferTestBuildType=notificationTest`.
- **Samsung SM-A305F / Android 11:** 36 instrumentation tests passed, including
  all seven new recurrence regressions and five retained framework checks.
- **API-24 emulator:** 34 applicable instrumentation tests passed. The first
  broad run additionally attempted two API-30-only framework tests and failed
  on the absent WindowInsets.Type class. Those tests now declare minSdkVersion 30;
  the focused rerun passed all three applicable framework cases and excluded the
  other two. This was a test applicability error, not a new app crash.
- Both devices exercised U-shaped placement in explicit LTR/RTL, ten gesture
  regressions, stable grid keys, module locales (German/LTR and Arabic/RTL), and
  system-default versus in-app locale switching/return. System language itself
  was not changed in this run; the earlier physical Arabic-system comparison
  remains documented separately. Tests used `com.jeerovan.comfer.notificationtest`.
- All **10 reporting tests** passed: six importer cases and four new recurrence
  analysis cases, including zero-issue imports and provider/app/version boundaries.
- `lintNotificationTest` remains blocked by **seven pre-existing errors** in
  untouched files: two LocalContext-to-Activity casts in JournalUiTest/NotesThemeTest
  and five missing spatial-wallpaper translations. It also reports 385 warnings.
  These are release-readiness debt; no lint-baseline suppression was added.
- Isolated APK builds and generated-Room packaging checks passed. The full
  signed release/AAB, broad backup/stress suite, incomplete-API-34 WorkManager
  harness and affected Vivo/widget-provider firmware were not retested here.

Evidence: `validation-artifacts/v53-recurrence-20260926/`, including pre/post
logs, complete triage, source hashes and `verification.json`. Tested app APK
SHA-256: `65d417828565109bcae971af0b8fdba56b6d635c090eb2f821dc9a9349827139`.
Source base: `12d94c2432dc8d5191856b14998f8abec7bd82bc` plus the recorded working
tree; version remains 53 / 53.0 for the local test candidate. No production
installation or rollout was performed during this recurrence task.

`play_reporting.db` now has appended analysis notes for all 195 version-53
issues; pending entries moved to investigating, including locally fixed paths
awaiting rollout evidence. Existing other-version triage, event evidence,
snapshots and import runs were compared with the pre-analysis backup and
preserved. Integrity and foreign-key checks passed. Backup:
`play_reporting.db.backup-v53-recurrence-20260926T100614Z`.
