# Version 55 release preparation

Prepared 29 September 2026: versionCode **55**, versionName **55.0**,
application ID `com.jeerovan.comfer`. Source baseline `3cd860a7c3bbeff13f313ec6a5a8221348b74c27`
plus the retained working-tree changes. No Play upload, rollout, commit or push
has been performed. Signed artifacts are prepared for release review; validation
results and remaining acceptance limits follow below.

## Included changes

- Cloud spatial wallpaper assets and motion gating, including reduced-motion,
  power-saving and lifecycle behavior.
- Consistent circular hold progress and haptic readiness feedback, combined
  hold-and-drag guidance, and Notes onboarding gestures.
- Notification filter guidance dismisses on a swipe; home inbox guidance lasts
  five seconds and timed guides dismiss when their action is performed.
- Glass effects default off; protection timeout has a shield icon; widget
  height controls and clock/date presentation improvements are included.
- Two current-source ANR mitigations from the [v54 recurrence review](release-54-recurrence.md):
  background and cancel tilt-service work (54-03), and background wallpaper
  environment registration/read/cleanup (54-04). Production resolution remains
  unverified; other open v54 issues retain their investigation status.
- Release lint exposed a stale locale read in the clock settings preview. It
  now observes Compose configuration changes. Added the two missing widget
  height accessibility labels in 33 translations; Arabic already contained them.

Store text: [English release notes](release-55-notes-en-US.txt).

## Build and package validation

- Signed APK and AAB build successfully with shrinking enabled; all four
  generated Room implementations pass the build guard.
- **246 app unit tests, 9 build-logic tests and 33 Python tests pass.**
- Translation validation: **34 translated locales, 1,035 required resources,
  zero errors**. Native-speaker proofreading remains pending.
- Release lint: **zero errors, 392 warnings**. The existing TypographyEllipsis
  quick-fix generation exception is printed, but analysis completes successfully.
- APK signature verifies and matches the prior build's certificate:
  `52ef555d772b43fbd473a4e4dc03d7211fd146d3b0e06bca14db7a46206431a0`.
- AAB signature verifies; bundletool 1.18.3 validation passes. Jarsigner reports
  the self-signed certificate, missing timestamp and archive manifest-order
  warnings. These do not fail signature or bundle validation; Play ingestion
  has not been tested.
- All **54 manifest classes and one dynamic Honor class** are present.
  ZIP alignment and LOAD segments in **17 native libraries** meet 16 KB alignment.
- Final manifest confirms 55 / 55.0 and the production application ID.

The initial candidate failed lint on the locale read and missing translations;
that failure is retained with the successful final build. Its Gradle invocation
also executed `uploadCrashlyticsMappingFileRelease`. Automatic approval review
rejected the subsequent build because that task exports a mapping to Firebase.
The final build explicitly excludes it with
`-x :app:uploadCrashlyticsMappingFileRelease`. Its exact mapping is retained
locally and has not been uploaded by the final build. Mapping upload must be
handled in the authorized publishing workflow for useful production retracing.
The archived R8 `pg_map_id` is
`d8e7d768627172bfd1cedf6ced8aca09c56b17bfe620fe11b966fbfca3ed2b81`;
the generated Crashlytics mapping resource ID is
`aad8dd9be07a4f189ced5d88f805e849`. The AAB embeds the exact archived R8 mapping.

## Archived artifacts

Local archive: `app/release/55/` (ignored by Git). Keep these files together;
rebuilding can change artifact and mapping identity. Checksums are also in
`SHA256SUMS` beside the artifacts.

| Artifact | SHA-256 |
| --- | --- |
| `Comfer-55.apk` | `e695c6f0a1b540e9cc51b4c3311ad5dad57952fc075fb97d09d855c56c928bf7` |
| `Comfer-55.aab` | `c3981648c66f2cf928295656bc3675a46d0f2591c3714670ef5f946505b53a45` |
| `mapping.txt` | `670fa6c50dab79788aa8bc82ecf056cbd7cbd3837f837dd5cb9d12a5884bc039` |
| `native-debug-symbols.zip` | `8964dde2a00a9ed3ed838f74476bbd22881a06763afa4652ee188575f2acf6b4` |

Logs, reports, source identity and device evidence are retained in the ignored
`validation-artifacts/release-55/` directory.

## Device acceptance and remaining limits

Only Samsung SM-A305F, Android 11/API 30, is used. Instrumentation runs against
the isolated `com.jeerovan.comfer.notificationtest` application; production
application data is preserved. No emulator tests are run for this preparation.

The initial broad run completed **45 tests successfully**, covering database
packaging, compatibility, migrations, Journal UI, encrypted backup and persistence.
It was stopped during `JournalPersistenceTest.tenThousandEntriesAreBoundedAndOrdered`
after prolonged execution. The database worker remained active and Samsung logged
`ProcessCpusetController: Slowdown ... abnormal=true`; this does not prove the
stress test would pass or establish the sole cause of its duration. The terminal
`Process crashed` message follows the deliberate force-stop, not an observed app
crash. This large-data stress check remains unverified. These 45 tests used the
initial v55 test APK, before the locale-read and translation lint corrections.

Final-build English-system validation completed **133 passes and two assumption
skips** in the 135-case run. It covers actual reminder delivery, reminder actions,
startup/recurrence safeguards, spatial cancellation, gesture timing and feedback,
Notes/Tasks guides, widget height/clock bounds, and explicit LTR/RTL layouts.
The two skipped cases require a live cloud URL and existing notification access.
The URL was then provided: **all 13 cloud and bounded Journal persistence checks
passed**, including the real scene download. The API returned HTTP 200 with two
layers. The notification-access-dependent home/inbox routing case remains skipped;
no notification access was granted to the isolated application.

English system-language checks compare the drawer, hold/drag, Notes, notification
filter and spatial layouts against selecting the same language through the app's
language-update flow. They also switch to Arabic and back to system default.
With **Arabic (ar-AE) as the real system language, all six tests pass**, including
the same source-equivalence checks and another actual cloud-scene download.
The final-build runs therefore contain **152 passing executions**, including
overlaps. The earlier 45 passes are separate and are not presented as final-build
coverage. No dedicated glass-effect locale test was run.

Samsung's original language list `en-US,hi-IN,ar-AE` is restored exactly;
animation scale is back at `1.0`. Both isolated test packages are uninstalled
after retaining screenshots and test logs. During preparation, the production
package and its data were not replaced or cleared.

The signed, minified production APK was package-validated during preparation;
the subsequent requested clean installation is recorded below.
Other OEMs/API levels, Play delivery and upgrade of production data, long-term
battery behavior, and native-speaker review remain outside this session.
Post-rollout telemetry must distinguish v55 from v54 and retain unresolved
crash/ANR investigations; successful local tests do not prove their resolution.

## Requested Samsung clean installation — 29 September 2026

After preparation, the user explicitly requested uninstalling previous and test
builds and installing the release. Samsung had only `com.jeerovan.comfer` installed;
no Comfer test packages remained. The previous application was uninstalled,
clearing its local data, and the exact archived `Comfer-55.apk` above installed
successfully. Package Manager confirms **55 / 55.0**, without `DEBUGGABLE`.
Cold launch of `MainActivity` returned `Status: ok` in 1,982 ms; the process stayed
alive and its AndroidRuntime log contained no fatal startup exception. Only the
release package remains installed. This is a clean-install/startup smoke check,
not production-data upgrade or full signed-build interaction coverage.
