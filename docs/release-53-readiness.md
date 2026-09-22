# Version 53 release readiness audit

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

- Firebase import **5**: 1 September through **22 September 10:58:33 UTC**,
  **50 groups / 145 events**: 8 fatal groups / 44 events and 42 ANR groups / 101 events.
  This replaces import 4's current counts (46 / 127), retaining its history.
- Play import **9**: 1 September through **22 September 11:00 UTC**,
  **30 groups / 77 events**: 4 crash groups / 21 events and 26 ANR groups / 56 events.
  26 sample reports were returned; 22 contain stack text. Provider group IDs and
  counts are separate and must not be added together as distinct incidents/users.
- 23 Firebase v52 IDs also occur in v51. None of the 14 exact target IDs listed in
  attempts 51-01 through 51-07 recur in this v52 snapshot. This is not proof of
  resolution; the short observation window and differing exposure limit inference.
- The migration and framework guards target groups containing **42 of 44 Firebase
  fatal events**. Play independently reports 18 missing-migration crashes. Counts
  describe the observed groups, not a predicted success rate.
- No ANR group is marked fixed. Native polling/GC/rendering, Google measurement,
  Play licensing, widget-service Binder latency and queued preference IO remain.
  Four additional Firebase groups have been reviewed: Honor frame scheduling,
  Play licensing bind, preference-file removal and preference fsync. Their samples
  do not establish a safe app-level patch.
- Two Play ANRs name `TaskReminderReceiver` during boot/package replacement. The
  main-thread samples are idle and have no receiver work stack. Source has an
  unbounded `StartupCoordinator.awaitReady()` inside `goAsync`; failed startup can
  leave the pending broadcast unfinished. 53-05 now addresses this source-level
  risk with a six-second deadline and durable action recovery, verified below.
  It remains an unproven diagnosis of the original two production samples.
- Other Play-only findings include a cold Coil initialization sample, activity-launch
  Binder latency, a platform ConfigurationController NPE and a native HWUI crash.
  Four reports have no sample object; eight have no stack text. Do not infer root
  causes or production fixes for these from titles alone.

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

| Issue | Type | Events | Review disposition |
| --- | --- | ---: | --- |
| `2c36a2cdc4a142141e1b60fca86436ee` | crash | 34 | 53-01 implemented; production outcome pending |
| `4d05f9e74e77520b418eac3a355108f1` | anr | 31 | Open: sampled framework/SDK/native ANR; cause not established |
| `293b2117cdf3ed5f2c38dd6de73a722a` | anr | 10 | Open: sampled framework/SDK/native ANR; cause not established |
| `15c1049c4d0bc536c073e5cbbeef1b7c` | anr | 8 | Open: sampled framework/SDK/native ANR; cause not established |
| `87073ca8fd3c019e5e5bb6172c9d96e1` | crash | 4 | 53-02 implemented; production outcome pending |
| `958ed166f1584b7c22378e82ad6968b1` | anr | 4 | Open: sampled framework/SDK/native ANR; cause not established |
| `ec94f9d136e7da26d520d47942d46deb` | anr | 4 | Open: sampled framework/SDK/native ANR; cause not established |
| `1ea5988c748d88947b0a46bb05ff104d` | anr | 3 | Open: synchronous widget-service Binder wait; preserve UI-thread ownership |
| `302d6c90af10653f908aac894932b278` | anr | 2 | Open: sampled framework/SDK/native ANR; cause not established |
| `416f1c679b091716c78157591849051c` | anr | 2 | Open: sampled framework/SDK/native ANR; cause not established |
| `4593bd5c8c65cb959be5aea7506dc75f` | anr | 2 | Open: sampled framework/SDK/native ANR; cause not established |
| `71851376d2af5f56955bf728d321d90b` | anr | 2 | Open: sampled framework/SDK/native ANR; cause not established |
| `a5c1f855585a55e6a9cf91d97c82b5ea` | anr | 2 | Open: sampled framework/SDK/native ANR; cause not established |
| `0759651f42c5b565e22114f551d539fa` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `0acc70619791c84c70038f113c92dab7` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `0aeaa43763a36f5ba69451942c902f67` | crash | 1 | 53-02 implemented; production outcome pending |
| `1937e46fd663b7d99751837e0df19f0d` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `1b1c16c2b655108b91d97f1c40168fa2` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `1f62473dfae416accc2676ee4647022b` | anr | 1 | Open: system activity-launch Binder wait; no safe thread change established |
| `348157e030bac748d3d1262e0edc1d81` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `4d0c5c7386f61f4c8d155ecada9d1835` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `7374229d15c50d4a8bbd58af61b5fc55` | anr | 1 | Open: Play-injected licensing Binder call; no app patch established |
| `7487bf9aa6a13353cfd84b898fa70a9d` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `74b003f0db14a1943ad8176be8e18c6c` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `77182372e143cca4a3c3e6f31cc13711` | anr | 1 | Open: cold Coil setup sample; profile before changing initialization |
| `773e5ed21e11ba8b8d1bdaeea7442d91` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `870f8c2c2dc68acb0432a0e22e6a6366` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `8cea93e60ac6516390e783687ba4e15e` | anr | 1 | Open: Play-injected licensing Binder call; no app patch established |
| `8d148077ccbe90466167a0aa6fc2be1d` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `8d782ca9e751c6476994a4d4f4ad0075` | crash | 1 | Open: framework/SDK/native failure; no safe app patch established |
| `901023f312e4c8a37f604ccfb3ec41c6` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `973a7a1429c335323bfe72f30dad1df7` | crash | 1 | Open: system death; no app-level recovery established |
| `a0a49e222e9a6533a72abea121ca7b19` | crash | 1 | 53-02 implemented; production outcome pending |
| `b154eac56826e4d1da6ef2e88376ced3` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `b5395f3cd6c5f753025111ba803fe29e` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `b7313afe656f494ee046862d3a0d2b30` | crash | 1 | 53-02 implemented; production outcome pending |
| `bd8d0004ffcf5b7910db383016a1907b` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `bf1f0f211233040ad8ca2bdcd2db58e4` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `c849df8b408ef236deaa1d03d5641bb7` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `cdbd3d02e0be2e73f8749122ad839663` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `d22aff7a52e2a7191f3ae59075f894eb` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `d370f53b3ba600efab5d8f3a63c9326f` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `d3d732b32920b2c770b156a11821698b` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `df03497b378bd61024e66b27f2f5cad3` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `e246a54e106dd3bb5a3779892b86903e` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `ecdb3c221473dea4b125a2b3eba81cfa` | crash | 1 | 53-02 implemented; production outcome pending |
| `ed0aa54e9217f2cd395c628b46509826` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `ef715e1213c320b2739fbc5b0ead0a5f` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `f3922162d654988c01ba73e91d2e8ea7` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `f7408226fc46cdd8762a94984ad4c127` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |

## Complete current Play inventory

| Issue | Type | Events | Review disposition |
| --- | --- | ---: | --- |
| `bd4c3a3dd2869469101e189a2f7a71ce` | crash | 18 | 53-01 implemented; production outcome pending |
| `df0e1dd19a31e299cb4668528c7c200b` | anr | 16 | Open: sampled framework/SDK/native ANR; cause not established |
| `47238de4892c7d0f0c65cb996de2fd75` | anr | 14 | Open: sampled framework/SDK/native ANR; cause not established |
| `dbf0da5b2ab9f4d005f4a0cfa2f03ad4` | anr | 2 | Open: synchronous widget-service Binder wait; preserve UI-thread ownership |
| `f78ad3460e2ba9f564000d4fd1930183` | anr | 2 | Open: sampled framework/SDK/native ANR; cause not established |
| `01ff220bda404a086b2960f51207adcb` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `23ca043579e0b362574e80fac7ed866c` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `2508e2ae81cbc84f2269339a2da9988e` | anr | 1 | Open: no stack text available; retain report metadata |
| `2f561e673c86bcdb92bc949d04d287b0` | anr | 1 | Open: no stack text available; retain report metadata |
| `3c809b00d613fd5c5d85b777ae85f132` | anr | 1 | Open: no stack text available; retain report metadata |
| `4bbb4a4224adaf38f1185993657d945f` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `5cbe5628e9f678bbb0f0a2f6396c4411` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `751a3976fbdae698444b866871589a26` | anr | 1 | Open: cold Coil setup sample; profile before changing initialization |
| `8500281c8e8a1a57d83e3dc2f41dc4bd` | anr | 1 | Open: no stack text available; retain report metadata |
| `88798fa0c16b552af055e5b1135bf530` | anr | 1 | Open: no stack text available; retain report metadata |
| `88e097484f73edd0b1d5d1845024be60` | crash | 1 | Open: framework/SDK/native failure; no safe app patch established |
| `938b6f90d2e0c681d9634fd5c20f737f` | crash | 1 | Open: platform startup NPE; no app frame in sample |
| `a140243b8c57152fcdc089765fd40752` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `a1a2dafcd4d391213ad4715781eb4989` | anr | 1 | Open: broadcast/startup-wait investigation; sampled main thread idle |
| `a6de15e9a91794d45b55e1a9282d0460` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `a940f6e9058db2a1186bd8c73366fee7` | anr | 1 | Open: no stack text available; retain report metadata |
| `b337469a85e9792c8af76a92cd57b2ea` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `ceb8910ffa30f8b98fad228e54a08897` | anr | 1 | Open: broadcast/startup-wait investigation; sampled main thread idle |
| `d6de0c7365744c3ab167c07df2e60195` | crash | 1 | Open: system death; no app-level recovery established |
| `ddf9d28cc040b1be0db2fe4c37c80019` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `eaae9e5f099a2e1db10122203516c0a7` | anr | 1 | Open: Play-injected licensing Binder call; no app patch established |
| `f00eb88079f507176cfdd9dad694a346` | anr | 1 | Open: system activity-launch Binder wait; no safe thread change established |
| `f28b61442969cb409c036e0d2089fc0c` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |
| `fbbab1ac69d7c65a91d24784b7f252f1` | anr | 1 | Open: no stack text available; retain report metadata |
| `ff3adc673a8035162860b8de42704210` | anr | 1 | Open: sampled framework/SDK/native ANR; cause not established |

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
