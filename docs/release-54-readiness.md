# Version 54 release readiness

Issue/fix history is maintained in `play_reporting.db`; this file retains the
release artifact and acceptance summary. The [history guide](../scripts/README-play-reporting.md#issue-and-fix-history)
explains queries and recovery. Archived source key: `docs/release-54-readiness.md`.

Historical build-validation record, 26 September 2026. Candidate: versionCode 54 / versionName 54.0.
**Decision at that time: ready for a staged version 54 rollout using the final artifacts below.**
No Play upload, rollout, push or Crashlytics mapping upload was performed during that validation session.

Version 54 is now the current release. See the [29 September Firebase issue ledger](release-54-issues.md)
for observed reports and follow-up work. The decision below records candidate
validation; it does not establish that current crash/ANR reports are resolved.

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

Historical detail is stored in `play_reporting.db` under this report's
archived source, section **54-01: cold scheduled-job startup race**. Use the [history guide](../scripts/README-play-reporting.md#issue-and-fix-history)
to query it or export the complete original report.

## 54-02: time-sensitive reminder broadcasts

Historical detail is stored in `play_reporting.db` under this report's
archived source, section **54-02: time-sensitive reminder broadcasts**. Use the [history guide](../scripts/README-play-reporting.md#issue-and-fix-history)
to query it or export the complete original report.

## UI test correction

Historical detail is stored in `play_reporting.db` under this report's
archived source, section **UI test correction**. Use the [history guide](../scripts/README-play-reporting.md#issue-and-fix-history)
to query it or export the complete original report.

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
