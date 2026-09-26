# Version 53 remediation

## 26 September Firebase recurrence follow-up

Import 6 contains 195 groups / 605 events for version 53. The
[recurrence analysis and next-release fix ledger](release-53-recurrence.md)
records 72 previously seen IDs, 123 newly observed IDs, the outcome of earlier
attempts and five new local fixes (53-07 through 53-11). Its linked inventory
retains all 195 dispositions. These are not production-resolution claims.

## 26 September follow-up — RTL app drawer resolved in tested build

The reported Arabic drawer issue is **resolved in the fixed local build**:
the physical-coordinate canvas now uses `AbsoluteAlignment.TopLeft`, preventing
RTL parent alignment from shifting the icons off-screen. See the
[investigation and validation record](app-drawer-rtl-investigation.md).

Validation: 228 unit tests and 16 emulator tests passed; Samsung Galaxy A30 /
Android 11 passed 17 tests under system English and 5 under system Arabic.
System-language and in-app-language switching produced identical drawer geometry
for both Arabic and English, with correct inherited layout direction and tap
targets. Actual launcher screenshots verified both columns and the centered arc.
The phone's original English/Hindi system-language list was restored.

The fixed isolated build and rebuilt signed release are installed on Samsung.
The first in-place release update was rejected for a signing-certificate mismatch.
Following the user's explicit request, the old production app was uninstalled
and the fresh **53 / 53.0** release installed successfully; the old installation's
local data was removed. Launch and drawer rendering were verified on the release.
This closes the demonstrated RTL defect and local installation blocker; no Play
rollout or resolution of unrelated production issues below is claimed.

## Earlier 22 September release audit

**22 September candidate: 53-05 receiver recovery; [release audit](release-53-readiness.md).** The first 53-02 build was rejected after the
user reported missing app icons on Samsung. Its hashes remain here only as
history; do not distribute that build.

Prepared 22 September 2026 on `main`, based on
`7f7cdb042507893fb2a9bac3eafd5b69dd1e2c56`. Version code/name: **53 / 53.0**.
Changes are local; production resolution requires version-53 telemetry after rollout.
At that audit, the spatial-wallpaper experiment remained on its separate branch.

## 53-05 — bounded reminder broadcasts and durable action recovery

The receiver previously held `goAsync()` while waiting indefinitely for startup.
An injected stalled-startup regression reproduced this on API 37: the ordered
broadcast remained held after nine seconds. The change addresses that demonstrated
code risk; it does not establish the original cause of Play ANRs
`ceb8910ffa30f8b98fad228e54a08897` (boot) or
`a1a2dafcd4d391213ad4715781eb4989` (package replacement).

- A separate six-second deadline finishes the broadcast once and cancels waiting
  work without joining blocked file/Binder calls. This leaves headroom under
  Android's [broadcast execution guidance](https://developer.android.com/develop/background-work/background-tasks/broadcasts).
- Before waiting for startup, atomically persist a receipt in `noBackupFilesDir`
  and schedule an inexact retry through the existing receiver. Receipts contain
  task IDs, versions, actions and original timestamps, never task text. All fields
  are encoded explicitly so constructor defaults cannot change on replay.
- The retry receiver requests startup recovery. Normal startup and reminder
  reconciliation also drain receipts. A failed startup attempt returns promptly;
  activities keep their existing wait-for-recovery behavior.
- Acknowledge receipts only after task changes and notification reconciliation
  succeed. Task version checks make replay after a commit safe: Complete applies
  once, stale actions are ignored, and Snooze retains the original requested time.
- Inbox writes and acknowledgement share a lock; processing is serialized. An
  interrupted atomic write retains the prior accepted actions. A one-MiB limit
  bounds inbox decoding; storage/scheduling failures are logged and previously
  persisted receipts are retained for recovery.
- No database schema, permission, manifest component, feature module or library
  dependency is added. The fix does not depend on WorkManager on runtimes where
  the existing compatibility preflight disables it.

Inexact retries remain subject to Android scheduling/Doze delays; the one-minute
requested delay is not a delivery guarantee. Durable receipts survive process
restart/reboot, with replay when startup or a retry receiver can run. Clearing app
data removes them. A completely blocked OS broadcast queue is outside the
receiver's completion logic. Current validation and artifact hashes are recorded
in the release audit.

## 53-04 — fixes-only release audit

The [readiness audit](release-53-readiness.md) records the replacement signed
artifacts, full release lint, host/device regression results and complete current
Firebase/Play inventories. It supersedes the earlier acceptance limitations below
only where a completed check is explicitly recorded. No feature or product module
was added. The additional source changes make existing wallpaper API guards
explicit, observe Tasks resources/weekday locale correctly, remove an unused
constraints container and brace the existing Journal composer branch.

Firebase import 5 has 50 groups / 145 events; Play import 9 has 30 groups /
77 events. These are separate providers, not additive incident counts. All remote
issues remain open and no production resolution is claimed.

Emulator follow-up: **31 focused tests passed on API 24 and 36 on API 37**
(16 KB pages), with no failures/skips. Both real Tasks alarm-delivery checks passed
twice on each emulator. The signed release also starts and renders launcher icons
on both. Samsung's undispatched-broadcast limitation and the separate receiver
startup-wait risk remain open; see the audit for the current publication decision.

## 53-01 — Journal schema migration and encrypted-content conversion

Firebase issue **`2c36a2cdc4a142141e1b60fca86436ee`** accounts for
34 version-52 crashes / 21 affected users in import 4. The sampled error is
Room's missing migration 4→5. Source inspection found JournalDatabase at schema
5 with no registered migrations. Its DAO also rejects the plaintext content
stored by schemas 1–4, so adding columns alone would not restore journals.

- Restore/register migrations 1→2→3→4→5 without destructive fallback.
- Mark only upgraded legacy databases for a transactional conversion into the
  existing encrypted format. Preserve entries (including trash), drafts,
  dictation segments, revisions, timestamps, generation and shared image references.
- Encrypt images into new immutable files. Retain original files until successful
  conversion; the existing cleanup workflow checkpoints/vacuums the database and
  removes obsolete media after recovery. Failed encryption rolls back database
  changes and can retry on reopening.
- Continue rejecting unsupported schema-5 content formats. Missing, invalid or
  oversized legacy attachments stop conversion while retaining original data;
  automatic recovery of an already missing attachment is outside this fix.

Changed files: `JournalDatabase.kt`, new `JournalMigrations.kt`,
`JournalDao.kt`, new `JournalMigrationTest.kt`, and `app/build.gradle.kts`.

Validation: the new 4→5 regression failed with the exact missing-migration error
before the fix on Samsung SM-A305F / Android 11. After the fix, all seven migration
tests passed: schemas 1–4, reopen, transactional encryption failure/retry, encrypted
image conversion with shared references, and missing-image recovery. The 196-test
unit suite passed on retry; its first run had an unrelated local HTTP timeout in
`BoundedDownloadTest.rejectsDeclaredOversizeWithoutWaitingForBody`.
These fixture tests do not reproduce the original Android-16 device or prove
production resolution.

The nine existing Journal encryption tests also pass on Samsung, including real
Keystore round trips, tampering, protection changes and failed-rekey rollback.
A broader Journal package run was not clean: the emulator rejected installation
because its test app already existed; Samsung hit the existing
`reopeningShowsNewPromptAndLatestEntryAtBottom` UI test failure and later stalled
in `encryptedPortableRoundTripAndWrongPassword`. That isolated run was stopped.
The full UI/backup suite therefore remains an outstanding release check.
The persistence suite's `tenThousandEntriesAreBoundedAndOrdered` stress test was
also stopped after several minutes of device Keystore work; it is not verified
for this candidate.
The other **11 persistence tests pass** in a separate Samsung run excluding that
one stress test. In total, **27 focused device tests pass** (7 migration,
9 encryption, 11 persistence).

Initial **53-01-only** signed release APK and AAB builds, including release vital
lint, passed. These hashes are retained as history; 53-02 replaces the artifacts.
Initial candidate SHA-256 values (not evidence of a Play upload):

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/release/app-release.apk` | 8597497 | `0d0a84110f7f8784f4286595b44c187af52450a0ce3417696dca70cf14df29ed` |
| `app/build/outputs/bundle/release/app-release.aab` | 12484002 | `6b728f452e9caacb60146f2a0f9168f5238492cf44c18d8081dc79141fd2766d` |

## 53-02 — missing API-34 framework methods

Follow-up to migration commit `d0ab736`, 22 September 2026. Targets eight
version-52 crashes across five groups:

- `87073ca8fd3c019e5e5bb6172c9d96e1` (4),
  `0aeaa43763a36f5ba69451942c902f67` (1),
  `a0a49e222e9a6533a72abea121ca7b19` (1),
  `b7313afe656f494ee046862d3a0d2b30` (1): missing
  `WindowInsets.Type.systemOverlays()`.
- `ecdb3c221473dea4b125a2b3eba81cfa` (1): missing
  `AccessibilityEvent.setAccessibilityDataSensitive(boolean)`.

The exception signatures establish absent runtime methods, although the reason
the sampled Pixel 8 Pro / Android-14 runtime lacks them remains unknown.
`FrameworkCompatibility` guards only these exact method calls against
`NoSuchMethodError`. Complete frameworks retain their actual overlay mask and
both sensitivity states. Missing overlay support contributes zero to the inset
mask; the absent accessibility setter uses AndroidX's pre-34 no-op behavior,
preserving the event and password metadata. Other errors are not swallowed.
An incomplete framework cannot provide the API-34 sensitivity restriction.

The calls originate in AndroidX/Compose, so a small AGP bytecode visitor redirects
the exact owner/name/descriptors in dependencies and app code to the bridge.
It excludes the bridge itself to prevent recursion. `@Keep` preserves that
boundary through R8. No SDK level spoofing, blanket exception handler, dependency
downgrade, or accessibility disabling is used.

Before the fix, Samsung tests reproduced both exact linkage failures. A JVM
fixture compiled against complete APIs and then run with the two methods absent
also failed with `NoSuchMethodError`; complete-API and unrelated-error fixtures
passed. After the guard, all **seven host tests and five Samsung device tests
pass**, including forced execution of both AndroidX API-34 implementations on
Android 11, event/password metadata preservation, and mixed inset type masks.
The reported Pixel runtime is not available for direct testing. Production
resolution remains pending.

Final 53-02 candidate validation: all **196 app unit tests** pass, and signed
APK/AAB builds including release vital lint pass. APK signature matches the
existing upload certificate. Release DEX inspection confirms both
`NoSuchMethodError` catch boundaries survive R8 and all direct calls to the two
affected APIs are confined to that guarded bridge.

Replacement **53.0 (53)** artifacts (migration fix plus 53-02; not uploaded):

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/release/app-release.apk` | 8548345 | `9a4bdf540766754ee212f31097a7c9de05aa5213c3f8c60cdca412f23192a98c` |
| `app/build/outputs/bundle/release/app-release.aab` | 12381536 | `8e4949213fdbe93540e4a4113dbf896270771b65985b57c3222f3843fa44d621` |

Earlier 53-01 UI/backup and stress-test limitations remain outstanding; these
checks do not establish full release acceptance or a production resolution.

References: [AndroidX accessibility implementation](https://raw.githubusercontent.com/androidx/androidx/androidx-main/core/core/src/main/java/androidx/core/view/accessibility/AccessibilityEventCompat.java)
and the installed AGP 9.4 `AsmClassVisitorFactory`/`Instrumentation` API sources.

## Other version-52 investigations

The following preserves the initial 53-01 investigation; 53-02 above subsequently
adds the narrow missing-method guard after regression reproduction:

- `87073ca8fd3c019e5e5bb6172c9d96e1`, `0aeaa43763a36f5ba69451942c902f67`,
  `a0a49e222e9a6533a72abea121ca7b19`, `b7313afe656f494ee046862d3a0d2b30`,
  `ecdb3c221473dea4b125a2b3eba81cfa`: eight crashes from missing framework
  methods in AndroidX/Compose. All selected samples report Pixel 8 Pro / Android
  14. Reproduce that runtime before dependency bytecode changes or accessibility
  workarounds; API level alone does not explain absent API-34 methods.
- `1ea5988c748d88947b0a46bb05ff104d`: three widget startListening Binder ANRs.
  Moving the entire operation off Main risks reintroducing the view hierarchy
  corruption addressed in attempt 51-02. Await a safe targeted reproduction.
  Follow-up source verification: [AOSP AppWidgetHost.startListening](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/appwidget/AppWidgetHost.java)
  performs the synchronous service call and then dispatches pending view,
  provider and collection updates inline. The public API does not expose a
  separate fetch/apply boundary. No off-main change was made.
- `8d782ca9e751c6476994a4d4f4ad0075`: selected stack concerns Google certificate
  validation/measurement, despite the CLOSE_SYSTEM_DIALOGS group title. No
  broadcast-permission workaround is supported by that stack.
- `973a7a1429c335323bfe72f30dad1df7`: DeadSystemException has no established
  app-side fix. Remaining native/system ANRs retain their investigation status.

Firebase issue states, historical import counts and previous attempt notes are
preserved. See [the v52 evidence](release-52-issues.md) for the full inventory.

Samsung SM-A305F was updated in place with the signed APK; Package Manager
confirms 53.0 (53), and MainActivity launches. App data was not cleared. This
startup check does not replace the outstanding full UI/backup acceptance checks.

The replacement 53-02 APK was installed in place on Samsung SM-A305F on
22 September 2026. App data was retained; version remains 53.0 (53).

## 53-03 — restore generated Room implementations in the release

User-reported Samsung regression, 22 September 2026: saved launcher icons/app
lists were absent after installing the initial 53-02 candidate. Device logs
showed `Cannot find implementation for com.jeerovan.comfer.data.ComferDatabase`.
Generated Kotlin sources existed, but all four database implementations were
absent from compiled release classes and the final APK. The app-list load failed
before publishing icons; this was not an icon drawable/cache defect.

The buildSrc AGP API dependency introduced by 53-02 polluted the parent plugin
classpath and disrupted KSP's AGP generated-source wiring. The fix removes all
AGP/Kotlin plugin dependencies from buildSrc. Only the pure ASM transform remains
there; the small AGP factory adapter now lives in the app build script's plugin
classloader. Compiler input verification confirms KSP-generated Room sources are
included again, without manual generated-directory wiring or keep-rule workarounds.
Both API compatibility guards and the Journal migration remain in place.

`verify<Variant>RoomImplementations` checks the final project class directories
and JARs for Comfer, Notes, Tasks and Journal implementations before APK/bundle
packaging. Tests reject generated-source-only and partially complete outputs,
and accept compiled implementations split across directories/JARs. All nine
build-logic tests pass. Signed release/vital-lint builds and the four-class gate
pass. Final APK DEX inspection confirms all four named implementations and both
`NoSuchMethodError` handlers survived shrinking.

Corrected **53.0 (53)** artifacts:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/release/app-release.apk` | 8581113 | `6c7770c649bb7f0552a272b1c4cfa0f9eaaf963aed564beff2ac37293c00953c` |
| `app/build/outputs/bundle/release/app-release.aab` | 12482019 | `6e6e972490d1260ade51ea13367162ef6a56d743374310c9f3e080e931166f1f` |

Samsung was updated in place without clearing data. Earlier 53-01 broad UI/backup
and stress-test limitations remain; production resolution is not established.

The isolated Samsung `DatabasePackagingTest` passed, opening all four generated
databases and checking their schema versions. After a clean release startup, the
previous missing-implementation error no longer appears. Visual verification in
launcher search confirmed icons including WhatsApp, Instagram and Clock; entering
`c` produced matching Camera, ChatGPT and Clock icons. App lists are loading again.
Evidence: `/tmp/comfer-v53-icons-search.png`,
`/tmp/comfer-v53-icons-search-results.png`, and the `comfer-v53-icons-*.log` files.

The five Samsung framework-compatibility tests also pass again with the isolated
AGP adapter, including the actual AndroidX API-34 paths.
