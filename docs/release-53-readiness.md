# Version 53 release readiness audit

Issue/fix history is maintained in `play_reporting.db`; this file retains the
release artifact and acceptance summary. The [history guide](../scripts/README-play-reporting.md#issue-and-fix-history)
explains queries and recovery. Archived source key: `docs/release-53-readiness.md`.

Audit date: 22 September 2026. Baseline: shipped v52 source
`7f7cdb042507893fb2a9bac3eafd5b69dd1e2c56`. Reviewed v53 commit `adbaffe`
plus the audit changes in the working tree. No release upload or push was performed.

**Decision: READY for a staged v53 rollout using the 53-05 candidate below.
The receiver startup-wait risk is addressed with bounded completion and durable
action replay. Final focused suites pass on API 24, API 37 and Samsung, including
both real reminder-delivery checks. The earlier Samsung acceptance failure no
longer reproduces. Production crash/ANR resolution still requires rollout data;
no upload, rollout, commit or push was performed.**

## Scope and fixes-only check

The baseline diff adds the Journal migration/conversion, exact missing-framework-API
fallbacks, Room packaging checks, tests and reporting documentation. No new product
feature, module, manifest component, permission, dependency version or schema version
is introduced. Notes, Tasks and Journal already shipped in v52. Spatial wallpapers
remain on their separate branch. BuildSrc is build logic, not an app module.

This audit additionally makes existing wallpaper API guards explicit, corrects Tasks
resource/weekday locale observation, replaces an unused constraints container, and
braces the existing Journal composer branch. These clear nine full-lint errors;
no screen or navigation flow is added.

53-05 additionally bounds reminder broadcast lifetime and preserves pending
actions in a small atomic file. It reuses the existing receiver and alarm APIs;
no schema, permission, manifest component, feature module or dependency is added.

## Evidence and remaining issues

Historical detail is stored in `play_reporting.db` under this report's
archived source, section **Evidence and remaining issues**. Use the [history guide](../scripts/README-play-reporting.md#issue-and-fix-history)
to query it or export the complete original report.

## Earlier 53-04 validation (historical)

The initial failures and holds below are retained as investigation history.
The 53-05 results supersede the reminder acceptance limitations.

- App JVM suite: **196 passed**. Build logic: **9 passed**. Python reporting and
  APK verifier tests: **26 passed** in the existing virtual environment.
- Full release lint: **0 errors / 380 warnings**. Existing warnings remain. Lint also logged a
  typography quick-fix generation warning for a Tajik string; report generation
  and analysis completed. No lint baseline/suppression was added to hide failures.
- Signed release APK and AAB builds passed; bundletool 1.18.3 validation passed. APK signature matches the existing
  upload certificate. The AAB signature verifies and includes the R8 mapping. Manifest: `com.jeerovan.comfer`, version **53 / 53.0**,
  minSdk 24, targetSdk 37, not debuggable.
- Final APK and AAB DEX contain all four Room implementations and the kept bridge.
  Exactly two direct calls to the affected framework methods remain, both inside
  the guarded bridge. Both NoSuchMethodError handlers survive R8.
- APK verifier found all **49 manifest classes plus the dynamic Honor class**.
  APK zip alignment passed with 16 KB pages; all eight native libraries have
  16 KB PT_LOAD alignment. The API-37 follow-up below also validates database
  loading and signed-release startup on a 16 KB emulator.
- The isolated Samsung encrypted backup test passed in about **122 seconds**.
  A temporary stack trace showed PBKDF2 derivation, not a Room deadlock. Temporary
  diagnostic logging was removed; cryptographic parameters were unchanged.
- The previous Journal bottom-position test used a hardcoded text-to-feed pixel
  gap. Its corrected assertion measures the trailing timestamp with density-aware
  padding and verifies that older content appears above it. No feed layout changed.
- The HTTP regression test now holds the server open until client completion and
  allows cold HTTP-client startup time. It cannot pass merely because the server
  closes after a delay. The full host suite passed with this stronger fixture.
- Initial Samsung suite: **73 passed / 4 test failures / 1 inapplicable harness
  assumption**, reported as five failures by AGP. All 12 Journal backup tests,
  12 persistence tests (including 10,000 real-encrypted entries in 260 seconds),
  9 encryption tests, 7 migrations and 6 activity tests passed. Notes migration,
  database packaging, icons and framework fallbacks passed.
- The Tasks search assertion matched the input sharing the button's accessible
  label; its selector now excludes the input. Snooze verification now waits for
  asynchronous NotificationManager cancellation. The API-34 WorkManager-specific
  harness was removed from this Samsung suite; it requires its separate runner.
- Both actual alarm-delivery tests failed. An isolated repeat also timed out.
  Samsung's broadcast dump showed more than 5,000 pending background broadcasts,
  including other apps' records since 10:54 local time; all three Comfer ALARM
  records were enqueued but never dispatched. This establishes a system queue
  blockage for these local timeouts, not a production receiver diagnosis. A device
  restart did not restore delivery: the fresh broadcast dump again showed other
  apps queued and Comfer's explicit receiver enabled but still undispatched.
- Post-reboot focused Tasks run: **19 passed / 2 failed**. Both corrected Tasks
  tests now pass. Remaining failures are
  `futureAlarmActuallyDeliversWithoutForegroundActivity` (45-second timeout) and
  `scheduledDateTimeNotificationCompleteButtonPersistsCompletion` (90-second
  timeout). This is not evidence that v53 introduced an alarm regression; it is
  an unresolved device acceptance gate. Across the final relevant selection,
  **75 distinct Samsung tests passed and these 2 remain failing**.
- Do not weaken alarm assertions or replace actual delivery with a manual
  reconciliation to get a green gate. Re-run these two tests on a Samsung device
  with functioning background dispatch (or diagnose this device's queue first).
  Keep the separately identified unbounded receiver/startup-wait risk open.
- Evidence is retained under `validation-artifacts/release53-audit/` (ignored),
  including the initial full-suite XML and post-reboot runner log.
- Final signed APK installed in place on Samsung with app data retained. Version
  53.0 (53) cold-started in 1,294 ms; its process logged completed initialization
  and no missing Room implementation/startup failure. Launcher search visibly
  rendered installed app icons, including WhatsApp, Instagram and Clock; a `c`
  query showed Clock, Camera and ChatGPT. This
  passes the icon/startup smoke check but does not clear the reminder gate.

### API 24 / API 37 emulator follow-up

Both user-provided emulators were tested with the isolated `notificationTest`
application. API 37 notification permission and exact-alarm access were enabled
for that test package. No Samsung settings or data were changed in this follow-up.

| Device | Focused regression results | Additional evidence |
| --- | --- | --- |
| `emulator-5554`, API 24 | **31 passed**, no failures/skips | Minimum-supported Android; signed release startup and visible launcher icons |
| `emulator-5556`, API 37, **16,384-byte pages** | **36 passed**, no failures/skips | Five framework compatibility tests; signed release startup and visible launcher icons |

The shared 31 tests cover all four Room implementations, seven Journal migration
and recovery cases, Notes migration, repeated installed-icon loads, all 16 Tasks
persistence/reminder tests and five Tasks layout tests. The API-34-specific
framework test class was selected only on API 37; its direct platform inset APIs
are unavailable on API 24.

Both real alarm checks also passed in an initial isolated two-test run on each
emulator. Thus each passed twice per emulator; these repeats are not counted as
additional distinct tests above. Delivery occurred without manual reconciliation;
the scheduled notification's actual Complete action persisted completion, removed
the notification and remained safe on repeated invocation. These results support
the Samsung queue-blockage finding without establishing the cause of the two
production boot/package-replacement ANRs or verifying failed-startup recovery.

The unchanged signed candidate APK installed successfully on both emulators.
First launch took 5,318 ms on API 24 and 3,987 ms on API 37; both logged completed
application-data initialization and displayed launcher icons, with no startup
crash or missing Room implementation in the captured logs. These first-install
launches are smoke checks, not a performance benchmark or a Play-split test.
Logs and screenshots are retained alongside Samsung evidence under
`validation-artifacts/release53-audit/comfer-v53-emulator-*`.

## 53-05 final validation and rollout preparation

See [53-05 implementation details](release-53-issues.md#53-05--bounded-reminder-broadcasts-and-durable-action-recovery).
The original stalled-startup receiver regression failed at nine seconds before
the fix. It now finishes within the test deadline on all three devices. Separate
host tests prove completion does not join blocked IO and happens only once.

| Validation | Final result |
| --- | --- |
| App JVM tests | **200 passed**, no failures/skips |
| Build logic tests | **9 passed**, no failures/skips |
| Python reporting / APK verifier tests | **26 passed** |
| API 24 focused device suite | **36 passed**, no failures/skips |
| API 37, 16 KB pages, focused device suite | **41 passed**, no failures/skips |
| Samsung SM-A305F / API 30 focused device suite | **41 passed**, no failures/skips |
| Two-stage real process-restart harness | Prepare and verify passed on **Samsung and API 37** |
| Release build and full lint | APK/AAB built; **0 lint errors / 380 warnings** |

The device selection covers database packaging, seven Journal migration/recovery
tests, Notes migration, repeated icon loads, all 16 Tasks persistence/reminder
tests, five Tasks UI tests and five receiver recovery tests. API 30/37 also run
five framework compatibility checks. Broader Journal/UI/encryption/backup tests
passed in the preceding audit and were not repeated after this Tasks-only fix.

Recovery tests cover stalled and failed startup, recovery through the retry entry
point, concurrent durable receipts, interrupted atomic writes, stale actions and
replay after the Room commit but before acknowledgement. The process-restart
harness deliberately leaves both Complete and Snooze pending, force-stops only
the isolated test package, and verifies automatic replay in a new process. Snooze
keeps its original timestamp and increments the task version once. The explicit
restart harness is separate from the normal regression suite.

An initial new-code test caught an omitted dynamic timestamp default during
serialization. Explicit encoding of all receipt fields fixed it; the final suites
above include that regression. No assertion was weakened. Samsung's two ordinary
alarm checks passed both the initial 21-test Tasks run and the final 41-test run.
This clears the local acceptance gate, but does not prove why the previous global
broadcast queue stopped dispatching or that either original Play ANR is resolved.

The final APK signature matches the existing certificate. All 49 manifest classes
and the dynamic Honor class are defined; all four Room implementations and the
framework bridge are also defined in release DEX. The bridge retains both exact
framework calls and `NoSuchMethodError` handlers. Bundletool validation and AAB
signature verification pass; APK 16 KB zip alignment and all packaged ELF LOAD
alignments pass. Samsung was updated in place with data retained; application
initialization completed, the launcher opened (627 ms, reported WARM), and search
rendered app icons including WhatsApp, Instagram and Clock.
The same signed APK also initialized and opened on API 24 (3,137 ms) and API 37
(2,846 ms, reported COLD), with no startup crash or missing Room implementation
in the captured logs. These are smoke checks, not performance benchmarks.

No new feature needs release notes. Suggested user-facing text:

> Fixes crashes when upgrading journal data, restores reliable app-icon loading,
> and improves task-reminder recovery and Android compatibility.

Use the candidate AAB below for the release workflow, starting with limited
exposure. Watch v53 startup/migration crashes, missing-framework-method crashes,
reminder delivery and boot/package-replacement ANRs before expanding. Compare
version-filtered crash/ANR rates using active-user/session denominators; raw issue
counts alone are insufficient. Keep production issues open until verified.
Affected OEM framework variants and Play-generated splits remain untested here;
retry delivery is subject to Android scheduling and working system dispatch.

Final logs, restart stages, screenshots and build/verifier output are retained in
`validation-artifacts/release53-audit/`. Current source edits remain uncommitted.

## Candidate artifacts

These 53-05 artifacts replace earlier 53-01/02/03/04 candidates. Do not distribute the rejected
53-02 artifact which omitted generated Room classes.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/release/app-release.apk` | 8597497 | `6585e32595aff76653e1e103681f56338a71198bf592bd6b405d71ee81321b16` |
| `app/build/outputs/bundle/release/app-release.aab` | 12494522 | `975008f176bd1edf6776884e9ea37cd7e8f714aad67e287f433c516955a8d1d8` |

Superseded 53-04 hashes: APK
`714df91eb06b5041e3a5b42ea8d4f1e41d60765827625ec26ddf154ee831ca1a`;
AAB `8215c09e4c34e89bbfcb4750f44314ec284fd655fd6b985c248d1687f4f2a465`.

The affected Pixel/Honor/Infinix runtimes and Play-generated splits have not been
reproduced on Samsung. Crash-free/ANR rates cannot be computed from issue counts
without active-user/session denominators. Firebase and Play states remain open;
production verification must follow rollout.

## Complete current Firebase inventory

Historical detail is stored in `play_reporting.db` under this report's
archived source, section **Complete current Firebase inventory**. Use the [history guide](../scripts/README-play-reporting.md#issue-and-fix-history)
to query it or export the complete original report.

## Complete current Play inventory

Historical detail is stored in `play_reporting.db` under this report's
archived source, section **Complete current Play inventory**. Use the [history guide](../scripts/README-play-reporting.md#issue-and-fix-history)
to query it or export the complete original report.

## Re-running the device gate

```sh
ANDROID_SERIAL=RZ8M80E8ZPZ JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew :app:connectedNotificationTestAndroidTest \
  -PcomferTestBuildType=notificationTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jeerovan.comfer.Release53RegressionSuite
```

The suite targets the isolated `.notificationtest` package. It includes real
Keystore work and password derivation, so allow several minutes. The separate
WorkManager API-34 harness is intentionally excluded from this Samsung suite.
