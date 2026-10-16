# Version 56 recurrence review and next-release fixes — 15 October 2026

Five changes are prepared against source baseline `104a288`: a missing
accessibility API guard, contacts-provider failure recovery, package-key
deduplication in management lists, asynchronous wallpaper preference loading,
and ordered off-main clock receiver cleanup.
Their stable attempt IDs are **56-R01–56-R05**. The target is the **next release
after 56**; its version has not been assigned or bumped. These are local changes,
not production-verified resolutions. No Play release or remote Firebase issue
state change is included.

## Evidence and recurrence

This review uses [Firebase import 10](release-56-issues.md): **965 groups / 4,793
events**, version **56.0 (56)**, 15 September 13:44:30–15 October 13:44:30 UTC.
All selected raw events, including all supplied threads, remain in
`play_reporting.db`. There is one selected event per group, not every event or
variant. The sample inventory was screened for fatal signatures, blamed/main
thread paths and exact-ID history; detailed source review focused on actionable
app paths and earlier attempted fixes. This is not an exhaustive root-cause
analysis of all incidents.

| Local exact-ID history | Fatal groups / events | ANR groups / events | Total groups / events |
| --- | ---: | ---: | ---: |
| Seen in earlier versions | 15 / 143 | 168 / 3,595 | 183 / 3,738 |
| Newly observed IDs | 15 / 50 | 767 / 1,005 | 782 / 1,055 |
| Total | 30 / 193 | 935 / 4,600 | 965 / 4,793 |

These are counts for different windows and unknown exposure, not comparable
release rates. New IDs can represent old failure families. Per-group user counts
must not be added. A repeated ID or old title is not proof of the same cause:

- `8d782ca9e751c6476994a4d4f4ad0075` still has a CLOSE_SYSTEM_DIALOGS permission
  title, but its selected v56 event is Google certificate rejection inside the
  measurement SDK on GoogleApiHandler. The group has 13 events; the one sample
  does not establish the signature of all 13.
- `ca8f7f21e3ec633d0d1dca453409435b` retains an Honor launcher activity title,
  but now samples a missing **MlKitInitProvider** in delivered split APKs.
- 959 selected events carry build-stamp revision `11db594`, one carries
  `a283ada`, and five have none. A working-tree build stamp is not exact source
  identity. The archived original v56 artifact/mapping identity is recorded in
  [release readiness](release-56-readiness.md); newly built mappings must not be
  used to guess old obfuscated frames.

## Earlier attempts and what version 56 shows

| Earlier attempt | Behavior retained | Current evidence and decision |
| --- | --- | --- |
| 50-04 / 51-06 | Search deduplicates component/profile identity; drawer count/key/content share one snapshot; restored folder packages are deduplicated. | `159e25351be7042db517f3af684684db` recurs: 42 events, selected duplicate key is a plain package. Two management lists still trusted package uniqueness. 56-R03 reproduces and guards those lists; the originating production input remains unknown. |
| 53-02 and v56 accessibility-node follow-up | Exact missing overlay/event/node API calls use kept bridges and an exact-signature bytecode transform. | The v55 node issue `c41e409d257c7384774b3aac74a8da94` is absent. New `3cc69c910bb989b24a8dc2ab6336694f` calls a different missing API, addressed by 56-R01. |
| v56 stale-folder action and persistent deletion fixes | Cleared selections safely stop stale actions; deletion explicitly removes the Room row. | `46899b52662ccfe38b2ed305819cf25c` is absent. Retain both fixes; absence alone is not production proof. |
| OEM widget guards / 51-03 / v56 Huawei weather follow-up | Exact unsafe provider namespaces are blocked during selection and saved-widget rendering. | The targeted Huawei weather issue is absent. Honor Gallery attachment still samples privileged WRITE_SECURE_SETTINGS access (2 events). Its actual provider identity/entry path is absent; retain existing Gallery guards rather than invent another package from a class name. |
| 51-01 | Exact Honor activity compatibility class remains packaged. | Current same-ID sample is ML Kit provider loading, not evidence that the Honor activity fix failed. Local packaging guards cannot certify the user's Play-generated splits. |
| 51-02 / 53-08 | Widget starts and pending RemoteViews stay on Main; stops run on IO, with serialized/conflated transitions and per-host recovery. | Eight sampled start-related ANR groups / 58 events still expose the documented Binder-versus-view-mutation constraint. Moving the whole start to IO would reopen the earlier hierarchy race. No new start fix claimed. |
| 51-04 / 51-05 / 50-01 | Wallpaper downloads and widget decoding are bounded; drawable snapshots have a size cap and are cached. | Four ANR samples / 27 associated events involve drawable conversion/runtime waits. The generic Compose allocation failure needs heap evidence. Retain bounds; no allocation or ANR resolution inferred. |
| 51-07 / 53-01 / 53-03 | Cached background dictation labels, Journal migrations, generated Room packaging checks. | The original target signatures are not established in current samples. Keep these changes; unrelated text shaping and ML Kit failures do not justify reverting them. |
| 53-05 / 53-07 / 54-01 / 53-09–53-11 | Durable bounded reminder broadcasts; routine WorkManager setup on IO with synchronized cold fallback; vibration permission; deferred launcher lookup and queued sound work. | Current system-server deaths, runtime/SDK work and external activity Binder waits do not demonstrate recurrence of these exact defects. Keep the safeguards; production auto-init and cold system-service entry remain separate limits. |
| 54-03 / 54-04 | Gated off-main sensor setup and wallpaper environment registration/read/cleanup. | Their four original IDs are absent. New display-service reads inside tilt callbacks and preference loading are different paths. 56-R04 addresses the preference read; display-query timing remains under investigation. |
| Earlier clock registration migration | Clock receiver registration moved to IO; disposal canceled that job and independently unregistered on Main. | Four selected clock-disposal ANR groups / 4 events. Current source still has Main cleanup and an early-unregister race. 56-R05 gives registration and cleanup one IO lifetime. |

Earlier attempts and limitations remain in the [v50](release-50-issues.md),
[v51](release-51-issues.md), [v53](release-53-recurrence.md),
[v54](release-54-recurrence.md), and [v55](release-55-review-2026-10-02.md) ledgers.

## Prepared fixes

### 56-R01 — missing accessibility-tool request API

- Issue `3cc69c910bb989b24a8dc2ab6336694f`: **19 crashes**, Pixel 8 Pro / Android 14
  selected sample. AndroidX calls a missing
  `AccessibilityManager.isRequestFromAccessibilityTool()` method.
- Extend the existing exact-signature ASM transform and kept
  `FrameworkCompatibility` bridge. Catch only `NoSuchMethodError`; return `true`
  when the API is missing, matching [AndroidX's older-platform behavior](https://raw.githubusercontent.com/androidx/androidx/androidx-main/core/core/src/main/java/androidx/core/view/accessibility/AccessibilityManagerCompat.java).
  Working API values are read for each request; unrelated failures propagate.
- Samsung's actual pre-34 framework reproduces the same missing method when
  invoking the AndroidX API-34 implementation directly. Complete/incomplete JVM
  framework fixtures cover both boolean values, repeated calls and unrelated
  failures. The reported incomplete API-34 Pixel framework is unavailable.

### 56-R02 — recover from contacts-provider database failure

- Issues `6380853658a02626929b323100e0fc52` (**8**) and
  `052e60d8c388eba8c736faf552c85bf8` (**1**): provider SQLITE_BUSY and disk I/O
  exceptions escape `ContactsViewModel`'s refresh coroutine.
- Catch `SQLiteException` at that optional feature boundary. Preserve the last
  complete successful list and leave the repository dirty for another refresh.
  Do not publish partial results or retry in an unbounded loop. Existing permission
  revocation clears contacts; cancellation still propagates. Queries retain IO,
  cursor `use`, cancellation signaling and trace cleanup.
- A small internal source boundary allows deterministic provider-failure injection
  without touching device contacts. Tests cover first-load failure, cached-data
  preservation, later success/cache reuse, revoked permission and cancellation.
  Actual failing Tecno/Samsung provider storage conditions were not reproduced.

### 56-R03 — package-keyed management-list guard

- Related recurring issue `159e25351be7042db517f3af684684db`: **42 crashes**.
  Production sample is a duplicate plain package key. Its last screen breadcrumb
  is `ManageAppListActivity`, supporting the management-list association; deferred
  Compose measuring does not identify the exact list or originating input.
- `AppListColumn` and `FolderAppListRow` now capture a list distinct by package
  before lazy items, reorder keys and reveal behavior use it. Keep first occurrence
  and order. Management actions already use package identity; search continues
  to preserve distinct activities/profiles with its existing separate identity.
- Duplicate and alias inputs reproduce the exact Compose key exception before
  the guard on Samsung. Tests check one visible/selectable card per package,
  geometry/order in LTR/RTL and compact/wide constraints, updated/empty lists,
  plus existing search identity, drawer replacement and reorder regressions.
  This is a proven local guard and **partial production mitigation**, not a
  claim to have explained or prevented every event in that group.

### 56-R04 — wallpaper preference loading outside composition

- New ANR `c5ab19e6332ea3da3fd27c2be6a4e867`: **2 events**; selected Main thread
  waits in `SharedPreferencesImpl.awaitLoadedLocked` from
  `rememberLocalWallpaperChoice`. The synchronous read still exists in current
  source and was not covered by 54-04's environment observer fix.
- Acquire preferences, register, read and unregister in one IO flow lifetime.
  Main callbacks enqueue conflated refreshes. Keep spatial motion off until the
  preference is available; state publication remains on Main. Disposal rejects
  late values and releases the exact listener after blocked work returns.
  Read failure stays conservative and a later preference change can recover.
- Before the fix Samsung reports acquisition/read/registration/cleanup on Main.
  Focused tests inject a delayed read and disposal, check responsive Main and
  listener release, and exercise failure/recovery. This removes the confirmed
  synchronous path; production ANR prevention still needs rollout evidence.

### 56-R05 — ordered off-main clock receiver cleanup

- ANRs `2dcdef947466b48130ee44eeb076a72c`,
  `367f000b6656ea29824213ac384cfd3d`, `45896a5e09af38a4cf781450d3a593cd`,
  and `f5660eaa0c492859d32a1e4721952239`: **1 event each**. Their selected Main
  threads unregister the text/analog clock receiver during disposal. The first
  ID also appears in v51; the other three are newly observed IDs.
- Current source registered on IO but canceled and unregistered independently on
  Main. A delayed registration could finish after that unregister attempt and
  leak its receiver. New `ClockTime.kt` owns registration and matching cleanup
  in one IO lifetime for both clocks. Main callbacks enqueue timestamps; Compose
  state and formatting stay on Main. All three system time actions are retained.
- Corrected Samsung baseline checks reproduce both Main cleanup and cleanup
  overtaking registration. Six regressions cover both clock styles, disposal
  during a blocked registration with responsive Main, actual callback delivery
  for tick/time/timezone changes, and failure/recovery on another subscription.
  Two existing real clock checks pass under their LTR/RTL parameters.
- The initial test harness awaited a latch before advancing Compose disposal;
  its timeouts are retained separately. Adding `waitForIdle` produced the intended
  baseline failures before installing the fix. The earlier per-issue decision
  to defer this path remains in SQLite, followed by the reproduced repair and
  validation; history is appended, not replaced.

## Remaining issues

The five attempts relate to **9 groups / 76 events**: 4 fatal groups / 70 crashes
and 5 ANR groups / 6 events. These are associated group counts, not incidents proven
prevented. The remaining **956 groups / 4,717 events** have no new fix claimed.

Largest recurring IDs remain `4d05f9e74e77520b418eac3a355108f1` (1,428) and
`15c1049c4d0bc536c073e5cbbeef1b7c` (538), both selected idle native-poll samples.
Full supplied threads were screened, but these snapshots do not establish which
earlier work exceeded its deadline. ANR reason/timing, system-server traces and
representative variants are still needed. One group
`51ab3f8f0c820211f368dbd7527432fc` (7) supplies no frames.

Other fatal samples include system-server death; OEM looper/activity faults;
Google measurement certificate rejection; vendor widget privileged operations;
RemoteViews list adapter faults; DataStore rename failure; missing ML Kit provider;
Compose OOM and tab-indicator constraints; and Android-7 Toast token failure.
Their per-ID decisions, historical attempts and next evidence requests are in
the database and `validation-artifacts/firebase-v56-review-20261015/triage.json`.
The DataStore message suggests multiple instances but current source has a single
delegate; the message alone does not prove duplicate stores. The tab-indicator
sample identifies Material3 but not a reproducing size/selection sequence.
No broad exception suppression, guessed provider blacklist or unverified
dependency change is applied to these reports.

## Validation and database ledger

- **259 app unit tests and 17 build-logic tests pass**; isolated app and
  instrumentation APK builds succeed. The pre-fix transform regression fails for
  the intended unguarded call. An initial standalone buildSrc command lacked
  Gradle APIs; rerunning through the root build supplies the proper build context.
- **Samsung SM-A305F / Android 11 / API 30**: the first fixed crash suite passes
  **30/30** for the three crash fixes, including existing accessibility,
  search/contact selection, drawer and app reorder tests. The four-fix wallpaper
  suite passes **29**, with one optional live-cloud test skipped because no
  `cloudSceneUrl` was supplied. The final five-fix APK passes **9/9** clock and
  repeated preference checks, plus **2/2** existing real clock checks. Across
  these phases, **54 distinct tests pass**, with no remaining failures. The first
  unparameterized existing-clock method filter selected no such tests; rerunning
  the exact `[Ltr]`/`[Rtl]` names executed both successfully. This is combined
  evidence across the documented APKs, not 54 tests on one APK.
- The management-list visibility/key change warrants LTR/RTL and size checks.
  Those tests explicitly set both directions and compact/wide constraints on
  Samsung; they do not claim a physical device rotation. The service/error
  changes do not alter localized labels or geometry. The focused review did not
  include the complete device matrix. The subsequent pre-commit run is recorded
  below. Actual affected OEM devices, final signed release artifacts, Play
  delivery and production Firebase auto-init remain release checks. No release
  candidate is built in this review.
- Only the isolated `notificationTest` package is updated. Production data and
  device system locale/rotation settings are preserved.
- Database update appends a review to all 965 v56 rows, including selected
  signature, prior versions/notes, disposition, stable attempt IDs, target,
  implementation, test evidence and remaining limits. Status stays
  **investigating**, including prepared fixes, until production evidence supports
  resolution. Earlier rows and notes, all provider fields, snapshots and import
  runs are preserved. No remote issue state is modified.
- Final checks confirm all 965 current reviews, all telemetry fields, 938 older
  issue rows, Play history, snapshots and import runs are preserved as intended.
  SQLite integrity and foreign-key checks pass. Recovery backup:
  `play_reporting.db.backup-v56-review-20261015T143103Z`. The earlier refresh
  backup was byte-identical and removed only after hash verification. Full source,
  APK and test evidence is in `test-verification-final.json`; database and cleanup
  proofs are beside it in `validation-artifacts/firebase-v56-review-20261015/`.

After rollout, associate 56-R01–56-R05 with the actual new version, source commit
and signed APK/AAB/mapping hashes. Compare the same signatures/variants at 24 and
48 hours and seven days with usage/device/OS denominators. Keep failed attempts
and newly appearing variants in the record rather than overwriting this history.

## Full pre-commit validation — 15 October 2026

**Completed with one accepted stale assertion failure.** All **574 current
functional instrumentation cases** have execution evidence: **573 pass** across
Samsung and the staged emulator runs. The remaining case is
`NotesInteractionTest.draggingOverAnotherCardKeepsBackgroundOpaque` (line 210).
The user explicitly chose to preserve the current **50% card transparency** and
report this older opaque-card assertion. Both the app behavior and that assertion
remain unchanged; the suite is therefore not entirely green.

| Check | Result |
| --- | --- |
| Fresh app unit suite | 259 passed, zero failures/errors/skips |
| Fresh build-logic suite | 17 passed, zero failures/errors/skips |
| Python/script suite | 38 passed |
| Translation resources | 34 translated locales, 1,035 required resources, zero errors |
| Functional instrumentation | 573/574 passed; one accepted stale assertion; no missing cases |
| Samsung locale/orientation matrix | 128/128 final case/configuration results passed after targeted test repairs |
| Isolated minified macrobenchmarks | All six passed on the API-37 emulator |
| Isolated lint | Zero errors, 390 warnings |
| Production release lint | Zero errors, 393 warnings |
| SQLite and whitespace checks | Read-only integrity/foreign-key checks and `git diff --check` passed |

The physical device was **Galaxy A30 / SM-A305F, Android 11 / API 30**. The
matrix covered 17 classes and 32 cases per configuration, including explicit
LTR/RTL parameters, visibility and absolute placement, gestures, widget/clock
rendering, Notes, settings wrapping, wallpaper options and live cloud scenes:

| Real system language | Orientation | Final result |
| --- | --- | --- |
| English (en-US) | Portrait | 32/32 passed |
| English (en-US) | Landscape | 32/32 passed |
| Arabic (ar-AE) | Portrait | 32/32 passed |
| Arabic (ar-AE) | Landscape | 32/32 passed |

The broader locale regressions compare following the system with explicit app
locales through the production `LanguageUpdateActivity` handler, switching both
ways and returning to the system default. Additional folder regressions opened
**Settings → app language**, scrolled the visible picker and tapped English and
Arabic in **all four configurations**, then checked locale direction, geometry
and stale-action safety. The picker offers generic `en`/`ar` tags, while the
system configurations were `en-US`/`ar-AE`; exact regional-tag equality is not
claimed. The current picker has **no system-default row**, so reset was verified
through the shared handler. Other languages received resource checks rather
than a full physical UI matrix. Representative English/Arabic screenshots in
both orientations were visually inspected; automated assertions cover the
broader geometry and interaction matrix.

The first Samsung ordinary pass executed 128 classes / 553 cases: 495 passed,
57 failed and one required a listener grant. Initial failures included obsolete
test assumptions, emulator-only scenarios and a protected-fixture cleanup
cascade. Relevant cases were repaired and rerun, with the listener grant restored
afterwards. Final discovery contains 574 unique cases after replacing the old
pre-shared-session privacy assertion with two valid/locked entry checks.
Discovery and duplicate suite entries are not counted as test executions.

The first locale matrix had five failures in two classes. Clock pixel tests
cropped a full-screen screenshot with window coordinates; Samsung's landscape
window offset truncated the captured glyphs. Converting the crop to screen
coordinates preserves every rendering assertion. All eight clock cases then
passed on Samsung landscape and on API-24/API-37 emulators. The folder fixture
now waits for both seeded folders to load before replaying stale callbacks;
those safety/deletion assertions pass in every configuration. The matrix ledger
retains the initial failures and **nine targeted class/configuration reruns**,
for 144 actual executions and 128 final unique configuration results.

Other test maintenance matches the isolated application ID, current Workspace
and collapsed-search flow, localized labels and theme-aware text colors. It
waits for Room/editor transitions, uses row-relative swipes and the Compose
timer, and gives the Tasks test host the real activity's `adjustResize` behavior.
Samsung Notes tests account for Back dismissing selection controls before
leaving the editor; formatting, image persistence and real archive round trips
pass in Samsung's dark theme and the API-24 emulator's light theme. Widget reset
checks await actual saved positions rather than an empty collection's vacuous
success.

OEM credential tests identify the resumed native credential activity, tolerate
ActivityScenario's transient STARTED state, and restore raw fixture snapshots in
`finally`. Protected backup fixtures hold a foreground session during slow
password derivation. Cold checkpoint recovery is asserted while locked before
separately authorizing media collection. A failed earlier cleanup left only
known synthetic protected fixtures in the isolated app; guarded recovery saved
their raw snapshots and recovered them without resetting the phone or production
app. Native successful credential entry on Samsung remains unautomated.

All **20 staged methods** were exercised in their required process-death,
fresh-data transfer, permission-denied or actual-reboot phases. The API-24
emulator also passed three WorkManager SIGKILL recovery rounds. API 37 passed
both healthy and missing-reflection compatibility variants, all 11 granted
quiet-hours scenarios, both focus/schedule reboot scenarios, protected Journal
transfer and the 8K-wallpaper memory fixture. Existing fix regressions passed
28 cases on API 24 and 30 on API 37. The six macrobenchmarks used a temporary
minified isolated package with controlled widget fixtures; their measurements
describe emulator performance. Production Firebase initialization and Play split
delivery were not part of that fixture.

No production application code changed during this validation: **383 production
source files** match the start-of-run hashes, preserving prepared fixes
56-R01–56-R05. Only test maintenance and validation records were added. The
regular Samsung package was not updated or reset. Final verification restored
the exact original language order (`en-US,hi-IN,ar-AE`), rotation, animation,
listener/DND and other recorded device settings; production app data and device
credentials were preserved. Newly added notification fixture packages were
removed. The existing API-24 emulator was restored. The disposable API-37 process
was stopped and its temporary data and benchmark checkout removed after preserving
results, APK/mapping identity and fixture sources; the original AVD was preserved.
A delayed test-APK installation
completed, and the installed APK hash was verified against the intended build.

Evidence is retained locally under
`validation-artifacts/precommit-v56-20261015/`: `final-validation.json`,
`functional-coverage.json`, per-class raw logs, all matrix attempts, screenshots,
fresh unit/lint XML, benchmark traces, APK/source hashes and restoration history.
Results combine the full initial runs and documented targeted retries. No
commit, push, version bump, release candidate or publication was performed.
