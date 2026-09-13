# Tasks validation — 13 September 2026

This is the first test build, using **English labels as explicitly selected by the user**. Dates/times use locale formatting; RTL layout is supported. See [Plan-Tasks.md](../Plan-Tasks.md) for phase progress and [tasks-storage.md](tasks-storage.md) for persistence/reminder behavior.

## Latest checkpoints and interpretation

- Settings cleanup: debug/test builds and **14 TaskRedesignTest cases passed** on API 24.
- Subsequent settings thumb-reach fix: debug/test builds and **3 TaskLayoutTest cases passed** on API 24.
- Regular debug APK installed in place on emulator-5554, preserving user tasks. Disposable test packages removed. Latest UI revisions were not installed over the regular physical app.
- The full **148 JVM test** result and API-24/API-30/API-37 persistence/reminder checks below predate the latest UI-only edits. Counts overlap between checkpoints; do not sum them as unique final-build coverage.
- No final release certification: TalkBack/focus quality, broader final UI orientations, physical gesture performance, long-duration OEM behavior, document-picker walkthrough and actual process kill during a multi-store write remain open.

## Historical implementation evidence

The following records preserve actual runs and failures from successive 13 September iterations. Earlier descriptions of three controls, long-press Move, list ordering, subtask controls and home-panel quick add are **historical**, not current requirements. Current behavior is defined only in [Tasks-Features.md](../Tasks-Features.md). Temporary artifacts may not survive or be available in a fresh checkout.

## Automated results

- `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:assembleDebug --offline`: **148 passed**, zero failures/errors/skips.
- Isolated instrumentation builds: `:app:assembleNotificationTest :app:assembleNotificationTestAndroidTest -PcomferTestBuildType=notificationTest --offline`.
- Small emulator, Android 7 / API 24: combined **52-test suite passed**: `TaskPersistenceTest` (13), `TaskGestureTest` (3), `TasksUiTest` (4), `TaskLayoutTest` (2), `TaskListFlowTest` (1), notification backup (7), folder expansion (5), home input (10), Settings reach (1), Inbox layout (5), Inbox/home routing (1).
- After the final notification-identity adjustment, `TaskPersistenceTest`: **13 passed again on both API 24 and Samsung Galaxy A30 / API 30**. The final Rename/Back/list-ordering refinement passed `TaskListFlowTest` again on API 24.
- Samsung: `TaskGestureTest`, `TaskLayoutTest`, `TasksUiTest`: **9 passed**. These cover tap/double-tap/hold, swipe cancellation/RTL completion, large text, short viewport scrolling, capture and share-draft recreation. Earlier notification-settings backup regression suite: **7 passed**.
- Pixel emulator, API 37: earlier task persistence/reminder/backup suite **8 passed**; posting/exact-alarm denial test **1 passed** after revocation; future AlarmManager delivery under forced deep idle **1 passed** in 5.2 seconds. Emulator idle/battery overrides were reset afterwards. API 37 is a preview emulator, not a production-device compatibility claim.
- Reduced motion: task gesture suite **3 passed** with emulator animator duration scale set to zero; original setting restored.

## Persistence, failures and portability

Coverage includes empty/populated Room data, reopen, 25 concurrent additions, repeated completion, an aborted Room transaction, conditional Undo, Undo of a newly selected list, schema 1→2 migration, and persistence of 2,500 ordered tasks/notes without affecting launcher data.

Backup tests exercise real ZIP export/import, task/list counts, recurrence, future reminder intent, repeated restore, missing versus explicitly empty task sections, invalid data before mutation, injected task insert failure with rollback of every participating store, and staged interrupted-journal recovery.

`TaskTransferTest#exportFixture` exported a synthetic parent/star/note-link/completed-child fixture from API 24. The archive was pulled, only the disposable test app's data was cleared, and the archive was pushed back. `#restoreIntoFreshData` passed on both the emulator and Samsung. The emulator was rebooted and `#verifyAfterRestart` passed. Fixture: `/private/tmp/comfer-tasks-transfer.zip`; it contains synthetic data only. The regular physical Comfer installation was not replaced or cleared.

The combined run initially exposed an obsolete-summary cancellation race. The regression now deliberately posts a missed-summary reminder, replaces it with a fresh snoozed reminder, and verifies its visibility, snooze, stale-action rejection and completion. Fixes cancel obsolete summaries before replacements, keep standalone replacements ungrouped, and use version-specific task notification tags. Summary-only delivery is posted before acknowledgement is committed. A Samsung suite timeout led to isolating this path and verifying the fix on both devices; the final 13-test device runs passed.

The transfer test initially had a non-void JUnit export method; this test-harness signature was corrected before successful export/restore runs.

## Layout and remaining review

Portrait and landscape were visually inspected on the small emulator. Landscape has no initial reach padding. Automated checks exercise 320×320 and 320×480 dp windows, 2× text, Arabic list text with RTL layout, the three bottom controls, final-item scrolling, keyboard capture and saved-state recreation. Existing home/folder/inbox gesture tests continue to pass.

This is bounded validation, not exhaustive device certification. Hands-on TalkBack/focus quality, subjective thumb reach and gesture comfort, long-duration OEM battery restrictions, and a forced process kill during an actual multi-store write still need release review. Journal interruption was tested by staging the real recovery journal; it is not represented as a power-loss test. The document-provider picker was not walked through manually during Tasks testing; tests use the real archive pipeline with file URIs. Opening/creating/reminding works without task network services; no automatic task sync was added.

Physical rendering was sampled with `dumpsys gfxinfo` in the isolated app. Debug timings included cold-start and runtime overhead and are not treated as a release performance threshold. The optimized isolated build compiled and ran on Samsung. A warm two-swipe sample recorded 112 frames, median 14 ms, p90 25 ms, p95 32 ms, four frame-deadline misses and 46 legacy janky frames. Rendering headroom remains; this small sample is not performance certification. The temporary phone app/runner were removed; the regular phone installation was preserved. The final regular debug APK was installed successfully on the small emulator.

Final integration check: `TasksUiTest` **5 passed** on API 24 after the panel quick-add change (date/star/selected-list preservation and explicit Save). Final unit suite: **148 passed**, zero failures/errors/skips. Disposable emulator app/runner removed and the installed regular Tasks screen opened for review.

## Five-control redesign — 13 September 2026

Implemented the five-icon contextual bar, list/sort sheets, cross-list search, focused capture/schedule/repeat pages, completed section, completion squeeze, swipe deletion/Undo and long-press move. UI tests were updated for icon semantics and Android system Back. The initial list-flow test had an ambiguous text match between heading and sheet option; selecting the clickable option fixed the harness.

Revised UI suite: 15 passed. Extended redesign/list flow/home input/folder suite: 22 passed, with overlapping coverage. Checks include due date versus snooze ordering, preserved manual positions, parent-group move, cancelled sheet, partial/cancelled swipe, RTL deletion, swipe Undo, parent-completion consent, short viewport, 2× text/RTL, and explicit draft Save. Final regular unit/build: 148 tests passed. The regular emulator installation preserves user tasks. Hands-on TalkBack and redesigned-screen performance certification remain open.

- Final-code smoke suite: **11 passed** (capture/share/recreation plus six redesign flows). Final installed portrait and landscape layouts visually inspected; rotation restored to portrait. Disposable emulator test app/runner removed; regular Tasks left open.

## Feedback fixes — 13 September 2026

22 UI tests passed on API 24: TaskGestureTest (4), TaskRedesignTest (10), TasksUiTest (5), TaskLayoutTest (2), TaskListFlowTest (1). New checks verify deletion-only Deleted/Undo with a five-second timeout, unchanged versus edited detail closing, completed rows inside the card, time selection before a date, and hold-and-drag without opening the destination sheet. The first run exposed a hold/release conflict between gesture recognizers; unified handling passed the rerun. Native time-picker OK committed the selected minute to the saved task. Physical-device drag feel, screen-reader speech and long-history nested scrolling are not claimed as newly tested here.

- Gesture/sheet simplification: 14 checks passed in the initial 15-test run; the remaining test had an ambiguous sheet selector and passed after correction (focused rerun: 1 passed). Verified stationary hold does nothing, hold-and-drag reorders, details-based group moves and sheet Back cancellation, no list-search input, and conditional task-search clear icon. Debug build passed and installed on emulator-5554 with user data preserved; disposable apps removed and regular Tasks opened.

- Single-card/list refinement final verification: strengthened drag persistence/cancellation test and both layout tests **3 passed**. Drag uses measured unequal-height rows and ignores completed history when ordering incomplete siblings. Earlier UI run passed the other 20 tests; final coverage comprises 23 UI cases. All **148 unit tests passed**. Test scope does not claim off-screen drag auto-scrolling or physical-device gesture validation for this revision.
- Updated regular APK installed successfully on emulator-5554; existing tasks preserved, disposable test apps removed and Tasks opened for review.

- Inline list editor/live drag final evidence: both focused tests passed after harness corrections; the full selection covers 19 UI cases. Screenshot inspection then revealed the held row was clipped by its size-animation container. Removing that clip preserved the lifted row; the strengthened drag visibility/neighbor-position/drop/cancellation test passed again. Verified screenshot: `/private/tmp/tasks-drag-preview.png`. Preview does not modify stored order before release. Empty and populated list deletion restore correctly through Undo, including list name and task ownership. All 148 unit tests passed; final debug build passed. Off-screen drag auto-scroll and physical-device animation profiling remain outside this check.

- Add-list refinement: reused the edit-list bottom sheet with an empty name and Save check, hiding Delete and the final-list deletion hint. Save creates/selects the new list and closes the sheet. Updated list-flow regression passed (1 test covering creation, blank-name disabled Save, hidden Delete, rename, populated deletion cancellation and Undo). Debug build passed; feature/UI documents updated.

### Completed-card drag feedback — 13 September 2026

TaskRedesignTest: **13 passed** on API 24 emulator. New completed-card regression checks visible drag preview and held row, animated neighbor displacement, no persisted change before drop, persisted order after drop, cancellation cleanup, unchanged incomplete position and completion timestamps. Existing incomplete drag regression also passed. Debug build succeeded and installed with `-r` on emulator-5554; disposable test packages removed. This revision did not test off-screen drag auto-scroll or physical-device animation performance.

### Simplified task details — 13 September 2026

All **13 TaskRedesignTest cases passed** on API 24. Updated list-move test uses the bottom icon, dismisses the sheet with Back, verifies no persistence before Save, then verifies saved destination and preserved legacy relationships. Debug and isolated instrumentation builds succeeded. Subtask creation/nesting/promotion UI was removed; existing storage and backup format remain compatible. No destructive hierarchy migration was performed.

### Task-details correction — 13 September 2026

Removed completion/reopen and the unrequested Delete button from task details, including their unused editor callbacks. Details now starts with Title. Subtasks are explicitly deferred with unchecked follow-up work in Plan-Tasks.md. Debug build passed; this localized control removal was checked in source, without rerunning instrumentation.

### Repeat summary and notification settings — 13 September 2026

Debug/instrumentation builds passed. Initial API 24 run: 13 existing cases passed, including Daily summary and accessible Done check action. New navigation case reached Settings$AppNotificationSettingsActivity but failed using Espresso Back outside the app. Corrected to system Back; focused rerun **1 passed**. Notification settings was verified by the resumed system activity after tapping the actual Reminder Date control. Modern Android destination retained; not re-tested on a physical device for this revision.

### Tasks settings cleanup — 13 September 2026

Debug and instrumentation builds passed. **14 TaskRedesignTest cases passed** on API 24. Updated navigation case checks no Notification settings action on Reminder Date, no Manual order/home-panel toggle in Tasks settings, and successful notification-settings navigation from Tasks settings. Source verification confirms the home composition no longer calls TaskHomePanel and settings toggles scale to 70% within minimum 48 dp targets. No physical-device visual profiling for this revision.

### Tasks settings thumb reach — 13 September 2026

Debug/test builds passed. **3 TaskLayoutTest cases passed** on API 24: new settings heading bottom-reach and scroll-away regression, existing large-text RTL selector and short task-list viewport tests. The new settings test verifies the heading begins lower in the viewport within 360 dp of bottom controls, moves upward when scrolling, and leaves Add visible. Landscape settings geometry was source-checked, not separately instrumented in this revision.

## Notification-settings backup evidence retained from the plan

The initial audit found notification configuration in `notification_configuration` SharedPreferences (`config` JSON), absent from manual backup. Phase 8 added a version-1 portable DTO in archive format 2; Tasks subsequently advanced the current archive to format 3. No notification-settings Room migration occurred.

- Initial phase-8 JVM/build gate: **132 tests passed**, including archive compatibility, future-version rejection, malformed input and portable configuration round trips.
- `NotificationBackupRestoreTest`: **7 passed on API 24 and 7 on Samsung Galaxy A30/API 30**. Covers real ZIP, persistent/observable state, repeated restore, legacy omission, pre-mutation rejection, injected commit rollback across stores, staged recovery, serialized edits and recovery-mode repair.
- `NotificationBackupColdStartTest#exportFixture` and `#restoreIntoFreshAppData` passed in separate emulator processes. Synthetic export was restored after clearing only the isolated app. The same archive restored into the isolated Samsung app: **1 passed**, preserving portable identities/rules, refreshed history cutoff and paused automation, without importing a DND runtime ID.
- Fixture stages require explicit `notificationBackupStage=export` or `restore`; ordinary suites skip them. Tests never cleared the regular physical app. Temporary phone test packages were removed.
- Manual system document-picker interaction, OEM DND cleanup with granted policy access and forced termination during an actual write were not verified. Staged journal recovery is not a power-loss test.

Run debug unit tests separately from the `-PcomferTestBuildType=notificationTest` instrumentation override. Use isolated packages for destructive fixtures and in-place installation for the user's regular build.
