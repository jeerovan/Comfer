# Comfer 57 release readiness

**The archived APK/AAB predates the wallpaper preload changes below. Rebuild the
release candidate and complete the required release checks before shipping the
current source.** The full-suite results later in this report describe the
earlier candidate.

The wallpaper follow-up prepares one cloud photo and its published spatial assets
on first launch, while Auto wallpapers remains disabled. The cache survives
restarts and is reused at opt-in when its day/night phase is appropriate. A phase
change fetches an appropriate replacement. Enabling Auto wallpapers now starts a
foreground refresh immediately and queues recovery work. Cached spatial assets
are imported into the renderer's cache without enabling 3D for the user.
Re-enabling Auto wallpapers also reapplies the cached photo within its current
rotation interval.

Focused validation passed **56 host tests and 23 Samsung instrumentation tests**
(10 preload cases, four solar scheduling cases and nine spatial lifecycle/service
cases). The original toggle failed the new immediate-work regression before the
fix. A live cloud download prepared both photo and spatial assets before opt-in;
an offline fixture applied the cached photo and loaded its 3D scene. Failure,
cancellation, retry, restart, phase mismatch and Battery Saver/local-source
boundaries were checked. One new test assertion was corrected to account for
Battery Saver's existing effective black background; its targeted rerun passed.
Android lint reported zero errors and 390 warnings. No UI layout rules changed,
so this follow-up did not repeat the locale/orientation matrix. Evidence is in
`validation-artifacts/release-57-20261016/wallpaper-preload-followup/`.

For manual testing, the current source was built as a signed regular APK and
fresh-installed on the Samsung Galaxy A30 on 16 October 2026 at 16:53:12 local
time. The installed version is **57.0 (57)**, with matching first-install and
update times. The APK and matching R8 mapping are retained in
`validation-artifacts/samsung-fresh-v57-20261016T112029Z/`; the APK SHA-256 is
`4436855f3a98793af215ca34ea9d00717ab17fb9e8a153cba08d05e90ab383ca`.
The handoff helper verified the installed APK and healthy foreground Home.
Workspace opened and closed, and Home → Recents → Home returned correctly.
The feedback prompt was dismissed with “Not now.” Device languages and rotation
settings were unchanged, and the isolated test apps were stopped with their data
retained. Auto wallpapers was left disabled for the user's manual opt-in check.
This manual APK does not replace the archived release candidate or its AAB.

The following records the earlier candidate and its subsequent Notes assertion
cleanup.

Prepared on 16 October 2026. Version **57.0 (57)** is fresh-installed on the
Samsung Galaxy A30 (SM-A305F, Android 11/API 30, serial `RZ8M80E8ZPZ`). Signed
APK and AAB files are in `app/release/57/`. Nothing was uploaded to Google Play.

The source base is `51edc36b4cb3d46b900ce1a8da6de6e8668802d3`. This preparation
changes the version and corrects stale Settings and Notes instrumentation tests;
it adds no other application behavior changes. The release includes the previously
committed drawer scrolling, Workspace sound, wallpaper, Settings and reliability
changes listed in [the release notes](release-57-notes-en-US.txt).

| Validation | Final result |
| --- | --- |
| App unit tests | 259 passed |
| Build-logic tests | 17 passed |
| Python script tests | 41 passed |
| Translation validation | 34 translated locales, 1,036 required resources, zero errors |
| Samsung functional regressions | 240 passed across 43 selected classes, including the corrected Notes assertion follow-up |
| Samsung layout/localization matrix | 132/132 passed: 33 in each English/Arabic × portrait/landscape cell |
| Release lint | Zero errors, 393 warnings |
| Signed APK/AAB and packaging checks | Passed |

The initial functional run passed 239/240 tests because
`NotesInteractionTest.draggingOverAnotherCardKeepsBackgroundOpaque` expected an
opaque dragged card. It has been renamed to
`draggingOverAnotherCardPreservesHalfTransparentBackground` and now verifies
the intended 50% blend over the blue card beneath it, using measured reference
pixels and a two-level RGB rounding tolerance. The test also checks the drag
position and cancels the gesture after the assertion. All **20 Notes interaction
tests passed** in the focused Samsung follow-up. Application card styling is
unchanged. This test-only correction occurred after release packaging; the
signed APK and AAB remain byte-identical.

The layout/localization matrix completed **before** the release APK/AAB build.
It exercised English/LTR and Arabic/RTL, portrait and landscape, real system
language changes and the actual app picker, switching in both directions.
Selected screenshots were also reviewed in all four cells. The picker supplies
`en`/`ar`, whereas the system supplies `en-US`/`ar-AE`; the checks compare language,
direction, geometry and interactions, not identical regional tags. Returning to
system language uses the shared locale handler because the current picker has
no system-default row.

Initial ADB transport interruptions were recovered with targeted reruns. A
notification-listener test passed after its required grant was supplied and
restored. `SettingsReachLayoutTest` was corrected to recognize the intentionally
disabled landscape pull-into-reach behavior, read the Activity's effective
locale and wait for native startup; its final test passed in all four cells.
No application layout change was needed.

The functional selection covers drawer drag/fling, click sounds, contacts,
wallpaper scheduling and decoding, widgets, notifications, framework
compatibility, Notes/Journal/Tasks persistence and backup, the 10,000-entry
Journal case and ANR stress. Tests used the isolated `notificationTest` app.

The fresh regular installation reports matching first-install and update times
of **2026-10-16 15:37:48**. Its installed APK content matches the archived APK.
Comfer is the default Home, and Home → Recents → Home returned to a healthy,
opaque launcher surface. Workspace, Notes, Tasks and Journal opened with
interactive controls. Settings visibly reports 57.0; fresh defaults for Auto
wallpapers and Monochrome background are both disabled. No sample Notes, Tasks
or Journal content was created. The feedback prompt was dismissed with
“Not now.” The phone was left on Comfer Home.

The original device languages (`en-US,hi-IN,ar-AE`) and rotation settings were
restored. Temporary test fixtures installed during this run were removed. The
isolated test app and runner remain installed, with their data retained and
their processes stopped.

APK/AAB signatures match the release certificate. ZIP alignment, all 17 native
libraries' 16 KB alignment, manifest/dynamic components, four generated Room
implementations and the minified compatibility guard passed validation. The
AAB's embedded mapping matches the archived mapping. The existing build task
successfully uploaded the matching Crashlytics mapping file. Mapping IDs,
artifact sizes, SHA-256 hashes and validation results are recorded in
`app/release/57/release-metadata.json` and `SHA256SUMS`.

APK SHA-256:
`12759f18662b5bff967edb522f695c0bfa0a0ba7940819570ea8b186881ac660`.

This is a selected release regression run, not every instrumentation class in
the repository. Physical-device coverage is limited to the Samsung/API 30
above. Google Play delivery, an in-place production 56 → 57 data migration and
long-term production behavior were not tested by this fresh-install run.

Detailed logs, XML results, source manifests and screenshots are retained in
`validation-artifacts/release-57-20261016/`. The canonical pre-build results are
`pre-candidate-validation.json`; final installation and smoke results are in
`fresh-install-result.json` and `signed-smoke-result.json`.
The resolved Notes results are in `notes-opacity-followup/results.json` and
`notes-opacity-followup/instrumentation.log`; the original pre-build gate is
retained as historical evidence.
