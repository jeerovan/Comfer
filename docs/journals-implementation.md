# Journal implementation contracts and validation

Updated 15 September 2026. This record supplements Plan-Journals.md; incomplete acceptance checks remain open there.

## Decisions

- Backup password minimum is four characters (16 September refinement). The export dialog shows no upfront length restriction and keeps Backup enabled; submitting fewer than four characters shows an inline error. Confirmation mismatch is also reported on submit. Both Journal export formats enforce the same minimum, and restore retains compatibility with existing longer passwords.

- User approved English-only labels/prompts for the first test build, localized dates and RTL, and interim Search long-press → Journal. Ordinary Search tap remains Search. Full Journals/Tasks/Search/Notes folder menu waits for a working Notes destination; no placeholder launches.
- Ownership is `journals/`: separate Room database, store, activity/view model, immutable media manager, pure speech protocol, Android recognition adapter, protection boundary and backup adapter. Existing Tasks tables/reminders are independent.
- `journals.db` is under `noBackupFilesDir`, Room schema 3 with exported schemas and non-destructive 1→2→3 migrations. Schema 2 adds a restore generation; schema 3 adds an active-entry/date/order composite index to avoid sorting the archive: writers queued before replacement cannot reinsert old drafts or dictation. The migrations preserve all earlier entries, drafts and segments. The feed retains at most 150 entries plus one lookahead row; Trash retains 100 plus one, with explicit older/newer paging.
- UUID entry/draft identity is the submit idempotency key. Order is calendar date, actual creation instant, ID. Date is epoch-day selected by the writer; zone is captured with the draft. Editing never changes date or creation time. Revision checks reject stale edits, deletion and Undo.
- Composer and entry edit buffers use separate durable rows. The view model serializes UI commands, preserving ordering between typing and submission. Speech has immutable session/segment tokens; microphone state is never restored. Recovered live rows retain Needs review.
- JPEG/PNG input only; APNG/GIF/WebP/other formats rejected explicitly for this first build. Source limit 10,000,000 bytes; decoded limit 20,000,000 pixels; longest stored edge 2048; stored JPEG limit 2,000,000 bytes. Normalize EXIF orientation and re-encode without metadata. Private immutable files are fully written/synced before database references publish. Failed commits may leave harmless unreferenced files. Garbage collection respects all entry/draft/Trash references and gives staging files a one-day grace period.
- Optional protection uses device credentials to gate Journal presentation, with private Android app storage and secure-window recent-app/screenshot protection. It is **not separate database encryption**, and the UI says so. No hardware-bound content key means credential enrollment changes do not destroy stored journals.
- Protected manual backup authenticates separately, then encrypts the Journal section (including images) with AES-256-GCM, random salt/nonce and password-derived PBKDF2-HMAC-SHA1 (1,300,000 iterations, API-24 compatible). Export password is portable, not the device credential; forgotten passwords cannot be recovered. Other Comfer archive sections keep their existing plaintext format. Journal archive schema 1 sits within Comfer archive version 4. Older missing sections preserve current Journals; present sections replace current Journals, including empty data. Restore confirmation shows only the source Comfer version and localized backup date/time, with password input for encrypted archives.
- The first inline adapter’s 12 MB image limit was rejected during implementation. Production export now streams separate image entries, with per-file size/hash validation and independent GCM nonces for protected files. JSON contains metadata, not image bytes. Bounds: 20 MB Journal metadata, 24 MiB combined archive JSON, 480 MiB Journal images and 512 MiB complete archive. An over-limit backup fails explicitly; no entries/images are silently omitted. Restore stages bounded images before replacing any rows; local rollback records refer to retained immutable files rather than copying image bytes into recovery JSON.
- Android recognizer runs only after deliberate long-press/accessibility activation, language selection and permission. On-device engine used where available; otherwise explicit per-session network consent is required. Provider final ends capture and offers explicit Resume. Pause/Stop drain at most two seconds; background immediately cancels recognition. No raw audio is retained.

## Validation attempts

16 September backup-password refinement: all four focused API-24 instrumentation tests passed (empty/three-character submit errors, confirmation mismatch, four-character acceptance, both export formats rejecting 0–3 characters, actual four-character encrypted text/image backup and restore, existing longer-password round trip and wrong-password rejection). All 167 JVM tests passed; debug build and whitespace checks passed. Emulator connection initially stalled during APK installation; adb reconnect resolved it without clearing data. Emulator updated; Samsung not updated. Report: `validation-artifacts/device-checkpoint/2026-09-16-backup-password/ui.txt`.

1. Initial debug build and existing unit suite: passed. Dedicated emulator Room suite: **4 passed** (duplicate/concurrent submit, stale update/delete/restore, injected transaction rollback, bounded 10,000-row fixture).
2. Speech reducer tests added: revised partials, duplicate finals, pause/resume race, stop timeout, prior-session callbacks and repeated interruptions. Debug build/unit run passed before backup integration.
3. Backup integration first compile failed for missing `OutlinedTextField` import in Settings; corrected.
4. Initial combined instrumentation attempt: Journal persistence passed; Journal backup class failed JUnit initialization because three expression-bodied tests returned Boolean from `File.delete()`. Changed them to Unit. Existing notification and Task suites were also included; final results recorded after rerun.

No Samsung update, production release or publication is claimed. Real speech-provider microphone behavior, credential changes, cross-device protected restore, TalkBack and final layout acceptance remain device acceptance work until recorded below.

5. Added streamed media after rejecting the initial 12 MB inline limit. Tests passed for >12 MB of images outside JSON, protected external-image round trip, missing section versus explicit empty replacement, and injected Journal write failure rolling back launcher/Tasks/notification settings.
6. A 48-case device run passed 46 and failed two fixtures: migration fixture assumed an `indices` array on every table (schema omits it for unindexed tables); the 10 px partial-swipe test was below touch slop and correctly opened editing. Fixture now handles optional indexes and uses a 60 px drag below the deletion threshold, with an explicit assertion that editing stays closed. Actual Journal activity keyboard and recreation checks passed.
7. Added schema-2 generation guards for pre-restore queued writes, successful-close draft draining, retained failed dictation Retry, scoped picker target/revision recovery, and speech segment/recovery data in portable backup. Restore now also recovers errors at the outer transaction boundary and only collects superseded wallpaper after all database commits succeed.

8. Corrected migration/swipe fixtures: **49/49 device tests passed** on API 24 (Journal storage/media/backup/UI/real activity/fake recognizer plus existing notification and Tasks backup/reminder suites). Source at this checkpoint precedes the bounded-window/index refinement below.
9. Performance follow-up: the host SQLite query plan used a temporary sort; added the schema-3 composite index and a device assertion that the 10,000-row feed query avoids a temporary sort. Capped the active feed at 150 rows plus lookahead and Trash at 100 plus lookahead instead of accumulating every page. Added cancellation-before-first-speech checks so starting/stopping dictation cannot implicitly submit an existing typed draft.

10. Final targeted API-24 run: **28/28 passed** (storage 9, backup 9, Compose UI 4 including RTL/150% font, real Activity 4 including IME/recreation/landscape/restored protection, fake speech backend 2). Protected restore retains incoming protection and never downgrades an already protected Journal.
11. Pixel-boundary fixture: 5000×4000 accepted with bounded decode; 5000×4001 rejected before decode. Power-of-two sampling bounds intermediate bitmap allocation. Boundary test plus cold-start seed: **2/2 passed**. After force-stop, cold-start verification plus stop-timeout/late-final rejection: **2/2 passed**. Composer, unsaved edit and provisional speech recovered with capture off.
12. Protected export: **1/1 passed**. Copied the synthetic archive to the host, verified ZIP CRC/payload digest, uninstalled only the disposable `.notificationtest` app, reinstalled and imported the same archive: **1/1 passed**. Text, managed image, composer/edit drafts, provisional segments and protection survived; microphone permission stayed denied. Export authentication was simulated in this test; real credential prompts and transfer to different hardware remain acceptance work.
13. Durable interrupted-restore checkpoint: seed **1/1 passed**, force-stop, cold recovery **1/1 passed**. Startup restored the exact Journal snapshot and protection, retained old referenced media through collection and removed the checkpoint. This exercises interruption recovery without corrupting normal user data.
14. Final debug assemble and JVM suite: **166 passed, 0 failures/errors/skips**. APK verification resolved **50 manifest component classes**. `git diff --check` passed. Installed debug **50 / 50.0** on `emulator-5554` with `install -r`; normal app data preserved. Samsung untouched. No signed release or publication performed.

## Reproduction and artifact

Source base: `2fe0876` plus the current uncommitted Journal implementation. Android API 24 Small_Phone emulator. Test builds use the isolated `com.jeerovan.comfer.notificationtest` package; destructive transfer/recovery checks use synthetic data only.

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug :app:testDebugUnitTest --offline
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleNotificationTest :app:assembleNotificationTestAndroidTest -PcomferTestBuildType=notificationTest --offline
adb -s emulator-5554 shell am instrument -w -e class <test-class-or-method> com.jeerovan.comfer.notificationtest.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

Journal test classes are under `app/src/androidTest/java/com/jeerovan/comfer/journals/`. `JournalColdStartTest` uses separate seed/verify invocations with force-stop between. `JournalTransferTest` uses export/import invocations across a fresh isolated install, retaining its synthetic archive. `JournalRestoreCheckpointTest` runs after transfer with separate seed/verify invocations and force-stop between. These tests deliberately depend on that documented sequence.

Debug APK SHA-256: `383e46df1ecb7f6f4f994843907f75349007a05bcf87e9c8d5325b6117dd5386`.

Remaining acceptance: real on-device/network recognizers, permission revocation and physical microphone shutdown, actual credential authentication/enrollment changes, TalkBack/reduced motion and complete layout/date-navigation matrix, full-disk exhaustion, different-device transfer and signed release acceptance. Fake recognizer and injected database failures cover deterministic protocol/recovery behavior, not every physical-device failure. The four-module menu remains blocked on Notes under the approved interim entry decision.

Reserved future internal menu IDs: `module:journals` → JournalActivity, `module:tasks` → TasksActivity, `module:search` → existing Search overlay, `module:notes` → future Notes destination. These are navigation identities, not installed application package IDs; the final menu is not shipped in this build.

## Journal UI follow-up — 15 September 2026

User feedback: move navigation beside the composer, simplify entry/header actions, rename Trash to Archive and reduce deleted-entry retention. Implemented a centered Today/selected-date picker button above the input, horizontal one-calendar-day swipes (reversed in RTL), and trailing three-dot Settings/Archive menu. Navigation is disabled during editing/capture/import. Removed the header back/settings/bin buttons and visible entry Delete action; Android Back and an accessible deletion action remain. Editing uses matching cross/check icon buttons. Successful submit selects the saved date, resets paging and scrolls to the latest entry after its row is observed. Archive expiry is seven days at the exact cutoff, preserving unresolved recovered-edit protection.

Validation: debug build and **166 JVM tests passed**. Initial API-24 UI/storage/Activity run: **18/19 passed**; the new menu test caught the old “Journal settings” label. Corrected it to “Settings” and updated previous/next accessibility labels to match calendar-day navigation. Reran all Journal UI cases: **5/5 passed**. Storage tests include exact seven-day expiry, cutoff+1 retention and preserved recovered edits. Activity checks cover IME, recreation, landscape and protection recheck. `git diff --check` passed. No physical-device installation in this follow-up.

Final debug 50 / 50.0 APK SHA-256: `fee0401b3f7862cf4e2f735dce8fd85b432131d88a594cfdd97a6e74993e9a59`. Commands: debug assemble/test as above; isolated instrumentation with `JournalUiTest,JournalPersistenceTest,JournalActivityTest`, then `JournalUiTest` after the label correction. New UI gestures still await user comfort testing.

Deployment confirmed: emulator `install -r` succeeded with normal app data preserved; disposable test packages removed. Samsung unchanged.

## Opening and composer refinement — 15 September 2026

User reported the first entry appearing at the top and “Journal entry” hiding the prompt. The feed was restoring persisted item index/offset, and Material’s unfocused text-field label occupied the placeholder position. Fresh openings now start at today and scroll to the newest loaded entry; short feeds use bottom alignment. Rotation still retains Compose state. Removed the visible composer label while preserving its semantic description, so the prompt displays without focus. Every new ViewModel chooses a different prompt without altering draft text, image or date; rotation retains the same ViewModel/prompt. Journal’s protection switch renders at 70% scale with its existing touch area.

Added a real-Activity regression with 30 entries and a stale saved top position. It checks newest-entry bottom placement, visible prompt, different prompt after reopening and unchanged prompt on recreation. Existing keyboard/landscape, draft, swipe/Undo and menu tests are included. Debug/JVM build passed (166 tests); final emulator outcomes recorded below. Final debug APK SHA-256: `fda031ebe1c88bc098395ae184e4a0f907696598a5fbfb50e2eef86024b7a6b9`.

Final result: **10/10 API-24 Activity/UI tests passed**, including the new reopening regression. `git diff --check` passed. Emulator debug update via `install -r` succeeded with app data preserved; disposable test packages removed. Samsung unchanged. Commands use the documented Gradle builds and instrumentation runner with `JournalActivityTest,JournalUiTest`.

## Date separators and editable creation time — 15 September 2026

User requested centered dates outside cards, trailing timestamps beneath content, no “Written” prefix, and explicit backdating. Date separators now sit above each date group independently of swipeable cards. Card footers show localized time when their calendar day matches; legacy differing-day entries show localized date/time without a prefix. The composer’s date/time control opens date then time pickers. Defaults use current local time on the selected composer day (today initially); an explicit selection is persisted. Tapping a saved footer edits its date/time through the existing check/cross draft flow. Text/image-only edits preserve timestamp; explicit timestamp edits update grouping/order and revision, with the existing stale-write guard.

Room schema **4** adds nullable `journal_drafts.createdAt` using a non-destructive 3→4 migration. Existing entries are unchanged. The optional field is serialized with durable drafts and manual backups; older drafts default to automatic time. Dictation captures the same chosen creation timestamp when creating its live entry. This user-approved change supersedes the original immutable creation-time contract above; updatedAt remains the actual update instant.

Validation: debug assemble and **166 JVM tests passed**. Added storage tests for default versus historical date/time, durable selected timestamp, edit cancellation, committed timestamp changes and stale-edit rejection, plus backup serialization of a timed draft. The existing migration fixture now exercises the full 1→2→3→4 chain. Final emulator results are recorded below. APK SHA-256: `0c030e710fbe9b873fc85941c71a3d7062d720c52c8f3a9b4956ce11b758de48`.

First full emulator run: **32/33 passed**. All 12 persistence and 11 backup tests passed, including the new timestamp cases and migration. Landscape IME check failed because the added creation-time row displaced Submit. Replaced that extra row with a clock button at the start of the existing bottom date row; it opens the same date→time picker without increasing vertical space. Final debug APK SHA-256 after this correction: `d7129904104b3baf83c989935064769ee685f4bd296608595460aa4af959c2bb`. UI rerun/deployment outcome follows.

Final UI/Activity rerun: **10/10 passed**, including landscape IME, latest-entry alignment, draft recreation, swipe/Undo and menu behavior. `git diff --check` passed. The automated tests cover timestamp storage/edit/serialization and layout; hands-on native picker interaction and timezone/DST comfort remain user acceptance.

Deployment: debug 50 / 50.0 installed successfully on `emulator-5554` with `install -r`; normal app data preserved. Disposable test packages removed. Samsung unchanged.

## Remove new-entry clock — 15 September 2026

User requested removing the clock implementation. Removed its bottom-row button, unused label and ViewModel setter. Fresh composer initialization clears any previously selected time override while preserving text, attachment and selected day, so removal cannot leave an invisible custom time active. Saved-entry timestamp editing remains available; its durable edit timestamp field and schema are retained. New entries default to the current local time on their selected calendar day.

Debug assemble and JVM suite passed (166 tests); `git diff --check` passed. Source search confirms the removed button tag, resource and setter have no remaining main-source references. This localized removal did not rerun device instrumentation. APK SHA-256: `94488317e136e3c4aa79105ae137c92981280b497511ac00c00b18634afa4c68`.

Emulator installation succeeded with `install -r`, preserving normal app data. Samsung unchanged.

## Full-screen image preview — 15 September 2026

User requested replacing the image bottom sheet with full-screen zoom/pan and image actions. Composer and saved-entry image taps now open a dedicated full-screen Compose dialog with secure-window policy, Close, Change image and Delete image. The viewer decodes off-main up to the stored 2048-pixel limit; feed thumbnails remain capped at 512 pixels. It supports 1–5× pinch zoom, double-tap zoom/reset, centroid-relative scaling and pan clamped to the fitted image bounds. Accessibility actions expose zoom in/out/reset. Failed image loading shows a message rather than an empty view indefinitely.

Change retains the system picker and target/revision checks. Delete on a composer clears its image; on a saved text entry it stages image removal in the existing cancellable edit; image-only saved entries retain explicit whole-entry deletion confirmation. Closing the preview does not alter content. The old image options bottom sheet is removed.

Debug build and JVM suite passed (166 tests). Added an emulator regression opening a saved image, exercising double-tap/pinch/pan, requesting image deletion, and cancelling the edit to verify the original image remains. Existing UI and Activity regression tests run alongside it. `git diff --check` passed. Final debug APK SHA-256: `32a4a0f173e9491f4623b87b634de3b95e582bf06e5efc98fa9e04068e795025`. Device results/deployment follow.

Final emulator result: **11/11 UI/Activity tests passed**, including the image-preview regression. Debug installation via `install -r` succeeded with normal app data preserved; disposable test packages removed. Samsung unchanged. Real provider image-replacement selection and gesture comfort remain hands-on acceptance.

## Preview bottom actions and pinch-only zoom — 15 September 2026

User requested bottom Cancel/Change image/Delete image actions and pinch/drag instead of double-tap zoom. Moved all three actions into one bottom row within system-safe insets. Cancel dismisses the preview without editing content. Removed the double-tap gesture detector; the remaining transform detector handles pinch in/out (1–5×) and one-finger drag with existing image-bound clamping. Accessibility zoom actions remain available.

Updated the emulator regression to assert bottom action placement, double taps leave zoom unchanged at both 1× and a zoomed scale, pinch outward increases zoom and inward decreases it, and dragging preserves zoom. Existing staged image deletion/cancellation coverage remains. Debug/test APK builds and `git diff --check` passed. Final APK SHA-256: `81095f33671724c75da8b2cd90c7e56c0b672be511dc631a9d27642234e69c93`. This focused UI change uses instrumentation validation; no new JVM run was required. Final device/deployment outcomes follow.

Final result: **11/11 emulator UI/Activity tests passed**. Debug installed successfully with `install -r`, preserving normal app data; disposable test packages removed. Samsung unchanged.

## Preview action icons — 15 September 2026

Replaced bottom Cancel text with an X icon button, shortened Change image to Change, and replaced Delete image text with a delete icon button. Icon accessibility labels remain Cancel and Delete image; bottom placement, touch targets and gestures are preserved. Updated the existing preview regression to use accessible icon descriptions. Debug/test builds and `git diff --check` passed; targeted device result follows. APK SHA-256: `d2dcc0bf134bd3e21742e2fdb185d440812ff6213302641d726af5a46eb9187a`.

The first isolated preview test timed out waiting for the decoded image (1 failure). Added failure-tree diagnostics; the same test then passed without a production-code change (1/1). The timeout was not reproduced or assigned a proven cause. Ran the full Journal UI suite next as a stability check; outcome follows.

Final full UI suite: **6/6 passed**, including preview icon actions and gestures. Emulator debug installation succeeded with data preserved; test packages removed. Samsung unchanged.

## Delete conflict after editing — 15 September 2026

Report: deleting a Journal entry showed “Journal changed; reopen it before saving”. Reproduced with a saved entry edited in place and then swiped: the remembered SwipeToDismiss state retained the initial entry/revision. A second regression also reproduced an error when the same deletion was requested twice. Both new tests failed before the fix (**0/2 passed**).

Fix: the remembered swipe callback now reads current entry/revision and deletion eligibility through rememberUpdatedState, preserving gesture state while avoiding obsolete captures. Store deletion accepts a repeat only when the stored row is exactly the requested snapshot plus one deletion revision/deletedAt; it returns the original deletion without extending retention. Edits, Undo/restore and restore-generation changes still invalidate stale requests. No force-delete or revision-check bypass was introduced.

Regressions cover edit→swipe→Undo preserving edited text, exact duplicate deletion, rejection of an old swipe after Undo, and rejection after a newer edit. Full UI/storage suites and JVM/build results follow. This evidence describes reproduced cases; no device-private Journal data was inspected.

After fix: **20/20 emulator UI/storage tests passed**, including both regressions that failed before the change. Debug assemble and 166 JVM tests passed; `git diff --check` passed. Final debug APK SHA-256: `d13a034d248c34ff2cbca08e9c2b767e18d897d37caa63d8e41b61dddb0b8ae5`. Commands use the documented Gradle tasks and instrumentation runner with JournalUiTest and JournalPersistenceTest; pre-fix run selected the two new regression methods.

Emulator installation succeeded with normal app data preserved; disposable test packages removed. Samsung unchanged.

## Reverse-layout feed and Archive return — 15 September 2026

User reported Archive→Journal returning to oldest entries and proposed reverse layout instead of repeated manual scrolling. A 40-entry regression navigated to the oldest entry, opened Archive, pressed Android Back and asserted latest-at-bottom: **failed before the fix**. The screens shared one LazyListState while the one-time initialization scroll had already completed.

Changed Journal to `reverseLayout = true` with a newest-first view of the existing bounded ascending database result. Index zero is now the newest/bottom entry; visual reading order remains oldest above newest. Date separator and card are grouped as one item so the separator remains above its day. Newer-page controls come before entries in reverse index order; older-page controls follow them. Archive uses ordinary layout. Navigation surfaces use keyed list state, and Journal page limits/offset reset across Archive mode changes, so an older batch cannot become the return target. Removed startup scroll-to-last initialization. Retained the deliberate post-submit jump to index zero required when creating while browsing older content; it is not a screen-return workaround.

Debug/JVM build passed (166 tests); final debug/test builds and `git diff --check` passed. Final APK SHA-256: `297d5330960a633bd018b975a6f5219a041a14134edafdcb5b2d949c5b3be537`. Regression and existing UI/Activity outcomes follow.

After fix: **13/13 emulator UI/Activity tests passed**, including the reproduced Archive-return failure, latest placement, chronological order, saved-entry deletion, preview gestures, keyboard and recreation. Emulator debug installation succeeded with app data preserved; disposable test packages removed. Samsung unchanged.

## Requested Samsung release update — 15 September 2026

Built signed/minified release 50 / 50.0 from source `ac20a72`; assembleRelease passed, APK v2 signature verified, and all 48 manifest component classes verified after shrinking. APK SHA-256: `c53ed434663be66465cb0d6661b7943fe48ccc167004d0dae946805ef1128436`. Preserved APK, mapping and metadata locally under `app/release/50/samsung-ac20a72/`.

Samsung Galaxy A30 update via `adb install -r` was rejected with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (signatures do not match). Package manager identifies the installed version 50.0 as installed by `com.android.vending`; its existing app and data were left intact. No uninstall performed. Installing this locally signed release requires a separately authorized fresh installation after the user preserves a backup, or delivery signed compatibly with the existing Play installation. No Play publication was performed.

User explicitly approved fresh replacement after confirming backup readiness. Samsung uninstall succeeded, followed by successful fresh installation of the verified release above. Package manager confirms version 50 / 50.0 with no DEBUGGABLE flag. Cold MainActivity launch returned Status: ok (2321 ms). Prior local app data was erased by the approved uninstall; backup restoration remains for the user. No Play publication performed.

## Journal theme consistency — 15 September 2026

User reported text, icons and buttons not honoring the theme. Audit found Journal’s root used an alpha-adjusted Material surface without an explicit contentColor. Because that color is not exactly the palette’s opaque surface role, automatic foreground lookup fell back to an inherited color; Inbox/Tasks already set onSurface explicitly. Journal now does the same, covering its title, date separators, feedback, bare icons and composer action. Cards, inputs, menus, settings sheets and image preview retain their existing Material semantic colors rather than per-control hardcoded tints.

The app’s Android XML theme is fixed light, and Journal used native DatePickerDialog/TimePickerDialog plus an AppCompat security-setup alert. Replaced those app-owned dialogs with Compose Material equivalents inheriting the active palette. Native system credential authentication remains system-owned. Date selection preserves the calendar date across Material’s UTC date representation and the entry’s stored timezone; time confirmation stages the existing reversible edit. Cancellation mutates no entry. Compact screens use date/time input layouts. The secure Journal window policy is retained.

Validation: **166 JVM tests passed**, debug/test APK builds passed, and `git diff --check` passed. Initial emulator UI/Activity run: **13/15 passed**; rendered-color test used a PixelCopy overload unavailable on API 24, and Archive fixture timed out. Adapted screenshots to UIAutomation. First focused run failed a light-palette screenshot taken immediately after dismissing a dialog and again timed out waiting for Archive fixture rows. Moved palette screenshots before opening/dismissing the dialog and added timeout diagnostics. Final focused run: **2/2 passed** (actual text/icon/button pixels in contrasting light/dark palettes, themed date dialog, and Archive return). Archive timeout was not reproduced in the final run; its cause is not proven. The initial successful cases included themed timestamp picker cancellation, security-setup dialog, keyboard/landscape and existing edit/delete/preview checks.

Final debug APK SHA-256: `c3b3d06330f456c133ebb4eb733f9974d9c561bfe12d4781d72dd854f3ac9ca3`. This is a Journal-scoped correction; it does not change global launcher themes or force colors on Android-owned permission/credential dialogs. Samsung hardware theme/contrast acceptance remains pending.

Deployment: emulator debug update succeeded with app data preserved; disposable test packages removed. Samsung remains on the previously requested release, unchanged by this follow-up.

## Recording controls alignment — 16 September 2026

Recording controls now share one full-width, vertically centered row: audio-level bar, pause/resume, then stop at the trailing microphone position. The bar retains its space when the speech provider has not supplied an audio level. Removed the visible “Listening” label; starting, paused, finalizing and recovery status remain available. This is a layout-only change; speech capture callbacks are unchanged.

Validation: debug build and whitespace checks passed; emulator updated in place. Live speech-provider visual acceptance was not performed for this layout-only update. Samsung was not updated.

## Dictation provider/start-stop investigation — 16 September 2026

- Reports: emulator does not start; connected Samsung reports no on-device dictation; another Samsung reports provider error 5 after Stop with no visible activity. The latter device/model and provider logs are unavailable, so its startup failure is not yet diagnosed.
- Device evidence: API-24 emulator has a null `voice_recognition_service` and no RecognitionService entry in package dumps. Connected Galaxy A30 runs API 30 with Google's `GoogleRecognitionService` selected. Android's dedicated on-device recognition API starts at API 31; on API 30 the existing consented system-provider path is required. Offline keyboard dictation is not proof of dedicated SpeechRecognizer availability.
- Attempt: reject unavailable-provider starts with an explicit setup message and disabled Start; clarify the consented provider option. Cancel a pending startup when Stop is tapped, invalidate its callbacks, avoid duplicate stop commands during finalization, and await natural endpoint results without calling stopListening again. Preserve final text and partial-result recovery. Natural endpoint results have a bounded 10-second drain; manual stop retains the existing 2-second recovery deadline. Startup timeout now identifies provider startup failure.
- Evidence: injected-backend regression first failed for natural-end duplicate Stop and repeated Stop (2 failures / 5 tests). After those fixes, added the user-clarified startup-stop regression, which failed independently (1 failure / 7 tests). These reproduce incorrect command sequences, not the other Samsung's native error or microphone failure.
- Reference: https://developer.android.com/reference/android/speech/SpeechRecognizer (automatic endpoint stopping, ERROR_CLIENT and dedicated on-device API availability).

Validation: all 7 speech-controller instrumentation cases passed on API 24 after fixes, plus all 166 JVM tests; debug/instrumentation builds passed. Logs: `validation-artifacts/device-checkpoint/2026-09-16-journal-speech/`. Actual speech transcription cannot be verified on this emulator without a provider. Samsung was inspected read-only, not updated; other-Samsung startup/error acceptance remains pending.

16 September 2026, user-requested affected-device install: built and signature-verified release 51 / 51.0 with the dictation changes, installed on Galaxy A13 SM-A135F (`RZ8W200C74V`, Android 14), and verified cold launch Status: ok. APK SHA-256 `758485880604371bc75d43553975ce81dea0d5b24259912af973d89fe1538bbd`. User will test actual dictation; installation does not establish provider/startup resolution.

## Galaxy A13 provider crash and explicit alternative — 16 September 2026

The user reported error 5 again on release `758485880604371bc75d43553975ce81dea0d5b24259912af973d89fe1538bbd`; the lifecycle fixes did not resolve this device failure. Retain the prior attempts above as command-sequencing fixes, not a confirmed solution to the Samsung report.

ADB logs now show Comfer starting `hi-IN` recognition through Google's `AiAiSpeechRecognitionService` (`com.google.android.as`). The provider opens the microphone and starts SODA detection, then its `-conformer_enco` thread crashes with native SIGSEGV at 06:25:33 and again at 06:26:03. Subsequent Stop reaches a restarted provider with no active session and Comfer logs “not connected to the recognition service”. Comfer's microphone permission is granted. Relevant evidence is saved in `validation-artifacts/device-checkpoint/2026-09-16-journal-speech/a13-provider-crash.txt`. The exact underlying native-model defect is not diagnosed.

Attempt: offer **Use device speech provider** for each session even when Android advertises a dedicated on-device recognizer. The switch is off by default and states that the default provider may send audio to its servers; choosing it routes to Android's default SpeechRecognizer, preserving the choice through Resume. No automatic network fallback or device-setting modification. On this phone, the default is Google's separate `GoogleTTSRecognitionService` in `com.google.android.tts`. Its actual transcription remains to be tested by the user.

Provider-routing regression failed before the change (explicit selection still chose the dedicated recognizer); unavailable-provider selection also lacked its intended rejection. Baseline: 2 failures / 9 adapter tests. This workaround bypasses the crashing provider; it does not repair Google's native process.

Validation: all 9 adapter tests passed after routing changes; all 166 JVM tests passed. Debug/release builds and release-signature verification passed. Updated emulator debug and connected Galaxy A13 release in place. Release 51.0 APK SHA-256 `570b20a8f6c68caa8981f49f78b2bd48fb21383e4f69768bdd569c3d2036ef24`; retained locally as `validation-artifacts/device-checkpoint/2026-09-16-journal-speech/a13-provider-choice-release-51.apk`. No live audio was sent through the alternate provider by the agent; user transcription acceptance remains pending.

## Journal motion and composer alignment — 16 September 2026

Removed Archive from Journal settings; the bottom date-row menu remains its entry point. Date text and the corresponding feed now slide/fade together for 320 ms in chronological swipe direction (mirrored in RTL), retaining each outgoing day's own query and scroll state. Composer actions are vertically centered beside multi-line input/prompts.

Swipe deletion now accepts the gesture, waits for the dismissed anchor to settle offscreen, then archives the entry. Remaining lazy-list items animate placement over 260 ms. A failed archive operation resets the swipe. Undo restores the same ID with a new revision, so swipe state is keyed to revision to prevent immediately dismissing the restored entry again.

Initial focused UI run: date navigation, RTL alignment and Archive return passed; three deletion/Undo cases failed because restored entries retained a dismissed saveable state. Added the revision key and reran. The new timing test verifies persistence is unchanged before the swipe exit settles and that Undo returns the card; existing partial-swipe and stale-edit checks are retained. Local reports: `validation-artifacts/device-checkpoint/2026-09-16-journal-motion/`.

Final validation: 6 focused Journal UI tests passed; debug build and whitespace checks passed. The 166-test JVM suite passed earlier in this change. Emulator updated; Samsung not updated for these UI refinements.

16 September 2026 requested Samsung UI update: signed release 51.0 built and signature verified, installed in place on the connected Galaxy A30 (`RZ8M80E8ZPZ`) from 50.0 with data preserved. Launch Status: ok. APK SHA-256 `bc12034ffb9d9bc5ee321056f20da20b2cd0bd18704ec871c5cfe139e888d46b`. Includes the Journal motion, composer alignment and dictation provider choice changes; hands-on acceptance remains pending.

## Muted Journal palette preview — 16 September 2026

User-requested visual experiment: Journal and Archive entry cards use black at 80% opacity with soft light-gray content for readability. Entry dates/times use gray (#9E9E9E); composer placeholders and focused/unfocused borders use the same gray. Editing text remains readable on the dark card. The date-row options menu uses a dark charcoal background (#202020), muted gray text (#B0B0B0), and no tonal elevation. These surfaces deliberately use the requested palette rather than theme-generated card/menu colors. Debug build and whitespace checks passed; visual acceptance is pending. No additional tests were added for this color-only change.

Palette correction: supersedes the black-card/charcoal-menu experiment above. Entry cards now use the current theme’s surfaceContainerHighest at **20% opacity**, with onSurface text (including the editor). The options menu uses surfaceContainerLow and onSurfaceVariant text with zero tonal elevation. Gray timestamps, placeholders and borders remain.

API-24/IDE warning investigation: core-library desugaring was already enabled in app/build.gradle.kts with desugar_jdk_libs 2.1.4, and the generated lint model includes its API support list. Gradle lint reported no Java time NewApi issues in JournalActivity.kt. It did find four configuration-unaware LocalContext resource lookups; changed those to LocalResources.current. After the change, JournalActivity.kt has zero lint issues. Debug assemble and emulator install passed; full-project lint still fails with unrelated errors elsewhere (220 errors). No API-warning suppression or minimum-SDK increase was introduced. IDE Java time warnings require checking Gradle sync/project-model state.

## Swipe icon showing through translucent cards — 16 September 2026

Cause: SwipeToDismissBox always drew its delete background icon, previously hidden by opaque cards. At 20% card opacity it became visible on every resting entry. Fix: render that background only while the swipe has a non-settled direction. No per-entry delete button was added. Extended the partial-swipe/Undo UI check to verify the indicator is absent both at rest and after a cancelled swipe.

Validation: focused resting/partial-swipe/delete/Undo instrumentation passed (1 test), debug/release builds and signature verification passed. Emulator and Galaxy A30 updated in place, preserving data. Release 51.0 APK SHA-256 `c3e4c42f80b53b89e87fe6e0b2c4f49f10cd45e0557442cf36d7eb7d1dbec63d`; retained with the test report under `validation-artifacts/device-checkpoint/2026-09-16-journal-delete-icon/`.

User refinement: removed the Journal swipe-delete background icon entirely, including during dragging. Swipe completion, animated list repositioning and Undo are unchanged. Supersedes the conditional-icon treatment above.

## Persistent sequential gesture guides — 16 September 2026

Added JournalGestureGuide.kt with input-transparent tap/swipe hand animations and local SharedPreferences progress (`journal_gesture_guides`, step). Activation occurs when an entry is first available, including an existing/restored entry for users receiving this feature. Sequence is EDIT → DELETE → DATE; only the current action advances it. Entry tapping must successfully open editing; swipe deletion must succeed; date completion requires a threshold-crossing date-row swipe. Date picker and accessibility date actions do not silently complete the swipe lesson. Partial swipes, elapsed time, navigation, Undo and reopening never dismiss a pending lesson.

No labels, timeout or dismiss action. Entry guides follow a visible entry; the date guide works with an empty feed after deletion. Editing/dialogs/Archive/dictation temporarily hide incompatible guides without completing them. Tests exercise native tap-through on the overlay, 10-second waits, screen disposal/reopen, partial swipe, successful deletion, Undo, empty-feed date hint and durable completion; a separate progress check prevents out-of-order completion. Two new guide tests plus two existing date/swipe tests passed on API 24 (4 total). Initial test build failed due to positional snapshot-fixture arguments; corrected to named arguments before execution. Logs: `validation-artifacts/device-checkpoint/2026-09-16-journal-guides/ui.txt`.

Final placement: the tap hint targets the top text area so image preview is not mistaken for editing. Both guide tests passed again after this placement adjustment. Debug build and all 166 JVM tests passed; emulator updated, Samsung unchanged.
