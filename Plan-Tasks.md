# Tasks implementation plan

Updated 14 September 2026. Current requirements: [Tasks-Features.md](Tasks-Features.md). Technical design: [storage](docs/tasks-storage.md). Actual runs and limitations: [validation](docs/tasks-validation.md). Repository-wide release status: [DEVELOPMENT-PROGRESS.md](DEVELOPMENT-PROGRESS.md).

This plan tracks the current test-build scope, not superseded UI iterations. English labels are approved for this build; localized dates/times and RTL remain included. Completion of implementation does not imply release acceptance.

## Phase status

| Phase | Deliverable | Status | Evidence / remaining work |
|---|---|---|---|
| 0 | Entry and backup audit | Complete | Identified Search entry and notification configuration backup gap; remediation completed in phase 8. |
| 1 | Local model and persistence | Complete | Room schema 2; explicit 1→2 migration, concurrent writes, rollback, Undo and 2,500-row checks. |
| 2 | Entry and reachable shell | Complete | Search long-press in both home layouts; ordinary tap/folder Close preserved. Shared reach applied to browsing and settings. |
| 3 | Capture, lists, views and search | Complete | Draft Save/Cancel, shared add/rename sheet, list deletion/Undo, wrapped selectors, five-icon bar and sorting. |
| 4 | Gestures and completion history | Complete for current scope | Actual incomplete/Completed cards, matching drag preview, tap/double-tap, swipe deletion and five-second Undo. Subtask UI deferred below. |
| 5 | Dates and recurrence | Complete | Native pickers, calendar boundaries, recurring scope, frequency summaries and check-icon Done controls. |
| 6 | Reminder delivery/recovery | Complete for tested scope | Versioned notifications, exact/inexact scheduling, permission and reboot recovery; device limits remain. |
| 7 | Task backup/restore | Complete | Format 3, real ZIP round trips, empty/missing sections, rollback/journal and cross-device transfer. |
| 8 | Notification-settings backup | Complete | Portable configuration, paused/review restore, real ZIP and API-24→Samsung transfer. SharedPreferences retained. |
| 9 | Integration and release acceptance | In progress | Test build installed on emulator. Hands-on accessibility, final device/performance and release acceptance remain open. |

## Current refinement checklist

- [x] Reuse Inbox’s 300 ms bottom-up entry animation and stationary underlay for launcher Tasks entry points.

- [x] Match Notification Inbox’s 80%-opaque theme surface over wallpaper across Tasks.

- [x] Five-icon browsing bar, wrapped list/sort sheets and conditional search Clear.
- [x] Remove first-use browsing guide and Got it button; retain stored guidance preference only for compatibility.
- [x] Add/rename list share a sheet; Add hides Delete; no Manage lists in selection.
- [x] Both task cards share lifted-row/animated-neighbor/border-free drag feedback; persist only on drop.
- [x] Hide Completed card when current list, Starred or search results have zero completed tasks; regression reproduced before fix, all 16 Tasks UI cases passed afterward on API 24.
- [x] Stationary long-press does nothing. Move is beside Star in details and edits the draft through a sheet.
- [x] Details starts with Title; no three-dot, completion or Delete button and no inline list section.
- [x] Repeat displays frequency; Done actions use check icons; date/time remain draft until Save.
- [x] Notification settings appears only in Tasks settings; remove Manual order, panel controls and usage guide there.
- [x] Search/settings switches use 70% visuals with minimum 48 dp targets.
- [x] Home no longer renders the Tasks panel, regardless of legacy preference values.
- [x] Settings heading and reach padding scroll away; portrait uses the shared 360 dp bottom area, landscape omits padding.
- [x] English test build and share-draft integration delivered; retain legacy subtask data without exposing creation/nesting controls.

## Phase 9 remaining release gate

- [ ] Hands-on TalkBack speech, focus restoration, reachable controls and gesture comfort on final UI.
- [ ] Broader final settings/card checks across orientations, window sizes, navigation modes and enlarged fonts.
- [ ] Representative physical-device profiling of final drag/scroll UI; assess off-screen drag requirements.
- [ ] Long-duration OEM background/reminder behavior and manual document-provider backup/restore walkthrough.
- [ ] Review actual interrupted-write/process-kill coverage; staged journal recovery is already tested, not equivalent to power loss.
- [ ] Run the agreed final affected-feature and launcher regression gate; record skips and artifact identity.
- [ ] Obtain user acceptance, then refresh signed release artifacts and release checks. No production upload is part of this plan.

## Deferred phase: subtasks

- [ ] Agree one-level creation/nesting UI and entry points before reintroducing controls.
- [ ] Add expansion, progress, ordering and promotion controls.
- [ ] Review group move/completion/deletion confirmation, Undo, reminders and recurrence constraints.
- [ ] Verify persistence, legacy data, backup/restore, accessibility and gesture interaction.

Existing subtask data remains intact. Broader label translations are outside the first test build. Home panel and Manual order in settings are removed requirements, not pending work.

## Progress rules and latest evidence

Use Not started, In progress, Blocked or Complete; check work only after implementation and relevant verification. Record device/API, actual results and remaining limits in the validation document rather than appending duplicate execution logs here.

Latest settings cleanup: debug/test builds and 14 TaskRedesignTest cases passed on API 24. Subsequent thumb-reach correction: builds and 3 TaskLayoutTest cases passed on API 24. Earlier full JVM suite: 148 passed; earlier persistence/backup checks include API 24, Samsung API 30 and preview API 37. These are separate checkpoints, not one final all-device run. Regular debug installed on emulator-5554 with user data preserved; the latest Tasks debug build was also installed in place on Samsung Galaxy A30 on 13 September, and Tasks launched successfully. Physical UI acceptance remains pending.

Device deployment preference: always update the emulator after app changes with the latest successful build, preserving app data. Update Samsung only when the user explicitly asks.
