# Version 54 release readiness

26 September 2026. Candidate: versionCode 54 / versionName 54.0.
**Decision: ready for a staged version 54 rollout using the final artifacts below.**
No Play upload, rollout, push or Crashlytics mapping upload has been performed.

## Included changes

- Arabic/RTL drawer placement and equivalence between system and in-app languages.
- The five locally tested v53 follow-up mitigations documented in
  [the recurrence analysis](release-53-recurrence.md): background WorkManager setup,
  ordered background widget stopping, VIBRATE permission, deferred launcher-service
  acquisition, and background drawer click feedback. Production resolution remains
  unverified; broader sampled ANR groups remain investigations.
- Integrated local/cloud spatial effects and caching, with the discarded standalone
  picker and bundled renderer removed. Active 3D Effect strings cover English and
  all 34 translated locales. See [spatial documentation](local-spatial-wallpapers.md)
  and [localization/cleanup](spatial-localization.md).
- Journal and Notes UI test hosts use LocalActivity instead of casting LocalContext.

## Release checks

Final results and artifact hashes are recorded below. Device instrumentation
uses the isolated `.notificationtest` app. The actual signed release APK is checked
separately on the emulator; Samsung production data is preserved.

The first combined release build was cancelled after Gradle reported heap exhaustion.
The retry uses a 6 GiB heap and two workers without changing project JVM defaults.
Standalone buildSrc invocation lacked Gradle API dependencies; its tests are rerun
through the root build. Python reporting tests require the repository virtualenv.
The cloud asset-generation test module additionally needs numpy/Pillow/OpenCV, which
are absent from the available Python environments; it is not part of app packaging.

## Suggested store release notes

Improved Arabic and right-to-left layouts, including language switching. Added 3D
wallpaper effects for supported wallpapers and translated their settings. Improved
startup responsiveness and addressed several reported stability issues.

## Remaining acceptance limits

Native-speaker review of every translation, live CDN spatial delivery, long-term
battery/thermal behavior, and the affected Vivo device's vibration path are not
certified by the automated suite. Existing production ANRs must be monitored by
version after rollout; this release does not claim every v53 report is resolved.

## 54-01: cold scheduled-job startup race

The initial signed v54 candidate reproduced a new fatal crash on API 24: after
SIGKILL of the launcher process, forcing its existing JobScheduler wallpaper job
started `SystemJobService` before the asynchronous initialization coroutine finished.
The service threw `WorkManager needs to be initialized` and killed the process.
The earlier successful Activity startup check did not cover this entry point.

`ComferApp` now implements `Configuration.Provider`; both normal background setup
and OS-started services obtain the synchronized singleton through
`WorkManager.getInstance(context)`. The previous check-then-manual-initialize race
is removed. The missing-JobScheduler-API guard applies to both configuration and
normal setup. This follows Android's [on-demand initialization contract](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration).

Normal Activity startup still schedules initialization on IO. If a cold system
service arrives first, initialization can occur on that service's main thread;
this is a deliberate correctness fallback, not a claim that every WorkManager
entry point is now off Main. The prior 53-07 ANR mitigation remains subject to
production monitoring, including this cold-service path.

The new `scripts/test_workmanager_cold_start.py` uses a rooted API-24 emulator,
retains job/data state through process death, forces the persisted job, verifies a
new surviving process and rejects fatal logs. It failed against the initial
signed candidate with the exact reproduced crash. The first harness attempt timed
out on a prior crash dialog; setup now force-stops that stale process before the
measured SIGKILL rounds. No app data is cleared.

## 54-02: time-sensitive reminder broadcasts

Both real reminder-delivery tests timed out on Samsung before and after an approved
reboot. AlarmManager enqueued the explicit `comfer.tasks.ALARM` broadcasts, but
Android's background broadcast queue never dispatched them. The queue had more
than 24,000 entries before reboot and stalled again afterward; the same tests
passed on API 24. This establishes the local delivery boundary, not the underlying
cause of Samsung's queue behavior.

Alarm and notification-action intents now use `FLAG_RECEIVER_FOREGROUND`. Their
receiver already finishes through a six-second watchdog and preserves durable
replay. Android's [foreground broadcast timeout](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs)
is shorter, so that bounded completion remains essential. Alarm tokens are
replaced and then reused for cancel/schedule within each reconciliation; action
tokens are also replaced, because updating extras alone cannot upgrade old Intent
flags. Existing actual-delivery checks cover the alarm, persisted completion,
notification removal and duplicate action safety. The alarm test additionally
seeds a legacy background token and verifies its cancellation before waiting for
real scheduled delivery.

## UI test correction

The broad Samsung run also exposed an archive-return assertion that compared the
entry text to a raw 150-pixel bottom threshold. Text sits above the timestamp footer,
and pixel spacing varies by screen density. The test now checks the actual latest
footer within 48 dp of the feed bottom, while retaining chronological order,
visibility and exclusion of the oldest entry. The focused post-reboot run passed
that assertion without changing Journal production UI.

The foreground-priority fix passed **28 Samsung tests**: all 16 Tasks persistence
checks (including both real alarm-delivery cases and the notification Complete
button), five receiver failure/replay checks and seven startup/recurrence checks.
No timeout allowance was increased and delivery was not manually triggered by the
tests. The separate legacy-token upgrade assertion is recorded with final checks.

The initial broad Samsung run reported 115 tests with three failures (the two
undispatched reminders and the density-dependent Journal assertion). Its optional
real-model test had no seeded model and is not counted as verified inference.
After the first fix/reboot, 17 focused tests left only the two reminder failures;
the corrected Journal positioning and spatial/locale checks passed. Preserve these
failed runs as history rather than presenting a single clean broad run.

## Final validation and artifacts

- App unit tests: **228 passed**; build-logic tests: **9 passed**.
- Python reporting/importer/translation/APK-verifier tests: **33 passed** in the
  repository virtualenv. Cloud-generation tests remain dependency-blocked as noted.
- Translation checker: **34 locales, 1,032 required resources, zero errors**.
- Signed release APK/AAB and isolated test APKs build successfully. Final release
  lint: **0 errors / 386 warnings**; test variant: **0 errors / 380 warnings**.
  Lint prints a TypographyEllipsis quick-fix generation exception for an existing
  Tajik resource; analysis and report generation complete successfully.
- APK and AAB signatures verify; APK certificate matches the prior release.
  Bundletool validation passes. All 54 manifest classes and the dynamic Honor class
  are present; the build verifies all four generated Room implementations.
- ZIP alignment and all LOAD segments in **17 native libraries** meet 16 KB alignment.
  Discarded picker images/depth grids are absent from the final application APK.
- Samsung final reminder/startup suite: **28 passed**. The added legacy alarm-token
  upgrade assertion separately passed with actual scheduled delivery (**1 test**).
- API 24: **7** earlier locale/database/framework checks, **10** focused startup,
  reminder and locale checks, and **1** real Arabic-system locale-equivalence check
  passed across runs (overlapping cases are not counted as distinct coverage).
  Both system-language sources were exercised; original English-only system
  configuration was restored. The first property-only switch attempt failed the
  expected-locale precondition; changing language through Settings passed.
- Cold JobScheduler startup: three independent process-death rounds passed after
  54-01, plus one repeat on the exact final signed artifact after 54-02.
  This checks service startup survival, not wallpaper network-download success;
  forced early periodic requests can be rejected before their normal due time.
- Signed v53 → initial v54 upgrade preserved installed data and launcher state.
  The final signed v54 update also installs in place, survives the cold-job check,
  and opens. The launcher and app-search layout were inspected on the emulator.
  Samsung production package/data were preserved; its tests used `.notificationtest`.

Final output hashes (SHA-256):

| Artifact | Hash |
| --- | --- |
| `app/build/outputs/bundle/release/app-release.aab` | `f14099004a9c48800243d0bbf02b218fc5e7553513bff6cd7f9d302b0e808392` |
| `app/build/outputs/apk/release/app-release.apk` | `0802034af585108d336f0cb8a868a86195b24735c66b70c50b44ceab59a026a2` |
| `app/build/outputs/mapping/release/mapping.txt` | `54646ec013b19a77b13b290ce1696e4886141654cff1e2734d5b6826fd30ae2a` |
| `app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip` | `8964dde2a00a9ed3ed838f74476bbd22881a06763afa4652ee188575f2acf6b4` |

Evidence is under `validation-artifacts/release-54/`. Keep mapping and native-symbol
files with this exact build for future crash analysis. Neither production crash
resolution nor Play's upload/pre-launch checks are implied by local validation.
