# Notification implementation plan

Prepared: 8 September 2026. Status: implementation in progress; phases 0–4 have an initial executable slice. No phase release gate is declared complete. Source of truth: [Notification-Features.md](Notification-Features.md), especially its authoritative priority register and section 11. No PDF generation is part of this work.

## 1. Delivery rules

Implement complete vertical slices in the order below. P0/P1/P2 are product priorities, while phase numbers express dependencies. A phase is complete only after its requirement IDs, acceptance criteria and validation evidence are recorded. Checked tasks have implementation evidence in the execution ledger; unchecked tasks include unfinished work or validation. A passing build does not complete a phase gate.

P0 release requires phases 0–4, including only the device-quiet capabilities verified on supported configurations. P1 completes through phase 8. Phase 9 is optional feasibility work and must not block the usable inbox. Do not silently substitute launcher hiding for system muting. Preserve the “other apps” terminology and do not generate PDFs.

## 2. Verified starting points and proposed boundaries

Targeted Tier 2 graph/source inspection on 8 September 2026 confirmed project `/Volumes/JS/Repos/Comfer`, graph generation `2026-09-07T16:44:53Z`. Coverage checks for the six paths below reported matching metadata and no recorded gaps. The search returned all 30 matching entries without pagination; the bounded inbound trace of `restoreBackup` found `SettingsScreen`. This is planning evidence, not a full architecture or dynamic-reference audit. Recheck source and relevant callers before implementation.

| Existing integration point | Planning implication |
|---|---|
| [MyNotificationListenerService.kt](app/src/main/java/com/jeerovan/comfer/MyNotificationListenerService.kt) | Current snapshots deduplicate by package. Preserve full records before deriving the row; handle connection state and event capture separately from UI debouncing. |
| [MainActivity.kt](app/src/main/java/com/jeerovan/comfer/MainActivity.kt), `NotificationIconRow` | The current guard hides an empty row. Replace it with the inbox fallback and make the entire row one tap target opening the full All apps inbox. |
| [SettingsViewModel.kt](app/src/main/java/com/jeerovan/comfer/SettingsViewModel.kt), `setShowNotificationRow` | Visibility also changes widget IDs. Update widget inclusion and migration, not only the composable guard. |
| [BackupRestoreManager.kt](app/src/main/java/com/jeerovan/comfer/BackupRestoreManager.kt) | Existing version-1 ZIP payloads include settings/Room data, validation and restore journaling. Extend this system with notification categories; do not assume its current whole-snapshot replacement supports selective merge or notification-category recovery. |
| [Settings.kt](app/src/main/java/com/jeerovan/comfer/Settings.kt), `SettingsScreen` | Existing restore caller; reuse its integration while adding inbox-accessible configuration backup and restore. |
| [app/build.gradle.kts](app/build.gradle.kts) | Minimum API 24, target API 37 and AndroidJUnitRunner define the initial compatibility/test boundary. Verify actual installed SDK/toolchain and build tasks in phase 0. |

Proposed new implementation responsibilities, with final filenames chosen in phase 0:

- `notifications/model`: immutable records, profile-aware IDs, revisions, capability/state model and view projections.
- `notifications/data`: memory-only live repository, separate consent-controlled encrypted history, durable configuration, migrations and export DTOs.
- `notifications/actions`: one dispatcher for source actions, eligibility, selection snapshots and outcomes.
- `notifications/ui`: inbox, reachable portrait scaffold, landscape layout, bottom controls, configuration and gestures. Keep substantial new UI outside MainActivity.
- `notifications/rules` and `notifications/scheduling`: deterministic evaluation, preset overrides, clock abstraction, owned DND integration and idempotent jobs.
- `notifications/backup`: notification-category adapters to the existing backup manager, unencrypted, freely shareable export and restore coordination.

These are proposed boundaries, not claims that these packages already exist. Avoid unrelated refactoring. Capture minimal source capabilities in memory; never serialize PendingIntents or raw platform notification objects into history or backups.

## 3. Phase 0 - Contracts, fixtures and feasibility

Dependencies: none. Coverage: foundation for all IDs; especially NF-03, NF-09, AC-01, AC-04, AC-06, AC-08.

- [x] Verify active repository instructions, current symbols/callers, storage conventions, startup recovery, available tests and Gradle variants. Record a baseline build and focused existing notification/backup test results.
- [ ] Finalize state/action tables and configuration ownership. Define a content revision, a configuration generation, action outcomes and clock behavior without persisting message content by default.
- [x] Prepare synthetic notification fixtures: summaries/children, rapid updates, messaging, custom/empty content, protected items, profile identities and expired actions.
- [ ] Prototype portrait reach with keyboard/large text and a full-screen landscape layout. Confirm the compact-input fallback before building all screens.
- [ ] Prototype DND ownership, access loss, overlaps, reboot and idle expiry on available physical devices. Record supported capabilities and system-settings fallbacks.
- [x] Select a maintained encrypted-storage design for retained notification history; validate API 24 compatibility and key loss behavior. Define a separate unencrypted, versioned configuration export format and app-private restore-journal handling. Backup import/export requires no credentials or ownership verification.

Gate: documented contracts, executable fixtures, known baseline failures distinguished from new failures, and explicit feasibility results. Unavailable physical devices are an outstanding gate, not a passed test. DND limitations may narrow that feature without blocking phases 1–3.

## 4. Phase 1 - Complete live state and service health

Depends on phase 0. Coverage: NF-08; NF-09 live-data safeguards; foundations for NF-01/NF-02/NF-16; AC-01, AC-03, AC-07, AC-08.

- [x] Capture accessible posted/updated/removed records before rendering debounce; reconcile with full active snapshots. Keep profile identity and distinguish summaries from children.
- [x] Produce separate live-item, row-icon and group-count projections. Preserve text/action updates even when key/post time do not change.
- [x] Implement Connected/Access needed/Reconnecting/Restricted states, successful-sync timestamps and privacy-safe diagnostics. Stale records cannot dispatch source actions.
- [x] Add a versioned notification configuration store and automation pause flag. Keep the live store memory-only when history is disabled.
- [x] Bound work queues and coalesce repeated updates; reconcile after overload without claiming complete capture of transient events. Do not put database work on callbacks/main thread.

Validation: synthetic post/update/remove tests, duplicate summary counts, profile separation, disconnect/reconnect, permission loss, 100-update burst and 1,000-record fixture. Verify no notification payload in logs.

Gate: full live state is consistent and current; row derivation loses no underlying items; unavailable states never look like an empty connected inbox.

## 5. Phase 2 - Persistent entry and reachable inbox

Depends on phase 1. Coverage: NF-01 display/navigation; DC-01–DC-05; AC-01 content scope, AC-04, AC-08.

- [x] Replace empty-row disappearance with one inbox icon. Migrate row visibility and widget inclusion together; preserve styling and recovery entry after access loss.
- [x] Add grouped/chronological inbox, app selection, All apps, empty/error views and bottom configuration/navigation. Normalize text/messaging/progress with safe fallbacks.
- [ ] Implement a full-height portrait viewport with fixed-formula scrollable starting padding, full safe screen width, reachable fixed controls and keyboard handling. Landscape fills the safe window and may use two panes.
- [ ] Add mutually exclusive tap/double-tap/long-press recognition, scrolling and action reveal. Provide labeled button and accessibility equivalents; unreleased actions are not presented as functional.
- [ ] Preserve navigation, selection, drafts and scroll across rotation. Apply reach rules to every nested Comfer screen.

Validation: empty/nonempty transitions; horizontal/vertical row overflow; orientation/resizing; left/right hand; large text, RTL and TalkBack; screenshot/interactive-bounds checks; keyboard variants; gesture conflict tests.

Gate: users can always reach the inbox after setup and navigate/configure it with reachable initial content and fixed controls; deliberate scrolling can move content into the upper viewport. Complete DC-05 usability tasks on physical devices.

## 6. Phase 3 - Manual actions, hiding and lifecycle controls

Depends on phase 2. Coverage: NF-01 actions, NF-02, NF-16; AC-01–AC-03, AC-07–AC-08; DC-03–DC-04.

- [ ] Centralize Open, Hide/Resume, eligible Dismiss and app/channel settings with per-item capability checks. Implement permanent and expiring visual hiding and priority placement.
- [ ] Snapshot selected IDs/revisions for bulk operations; confirm scope, skip protected/non-clearable items, prevent duplicate dispatch and report partial outcomes. Do not expose generic ambiguous Clear all.
- [ ] Implement Pause automation and Reset notification settings; add Delete saved data as later storage becomes available. Pause/reset preserves the mandatory entry and handles failed system cleanup visibly.
- [x] Implement changed/removed-item notices while menus and selection are open. Revalidate at execution time and leave unknown outcomes explicit.

Validation: expiry after restart/clock changes; changed item between selection and dispatch; missing app/intent; hidden-only fallback; protected item in a mixed bulk selection; pause/reset idempotency and settings write failure.

Gate: only the requested current item/scope is acted on; manual hiding has no sound side effect; no unsupported action appears enabled. P0 dismissal has no implied Undo until phase 5 adds it.

## 7. Phase 4 - Quiet hours and P0 release validation

Depends on phases 0 and 3. Coverage: NF-03, NF-08, NF-09, NF-16; AC-08 and all P0 regressions.

- [x] Add manual focus timer and weekday/overnight schedules with owned DND contributions, system exception guidance and clear launcher-only/device-quiet labels.
- [ ] Handle overlapping user/system rules, restart, time changes, permission revocation and unavailable scheduling. Never remove another owner's DND contribution.
- [ ] Use the verified scheduler design; document timing tolerance and fallback on each supported configuration. Pause/resume recalculates future boundaries without backlog actions.
- [ ] Complete permission disclosure, privacy text, migration and regression checks for launcher home, widgets, wallpaper/weather and existing settings/backup flows.

Validation: API 24–25, 26, 33, 35, 36 and target 37 as available; physical Pixel, Samsung and another relevant OEM; idle/reboot schedules; alarms/exceptions; existing DND remains after Comfer focus ends. Compare frame timing, memory and 24-hour idle battery with baseline.

Gate: all P0 IDs pass, no unresolved unintended cancellation/data disclosure issue, and supported DND behavior is documented. Ship a system-settings route on unsupported devices. Record user-study results against the requirements' task-completion targets.

## 8. Phase 5 - History, snooze, saved items and undo

Depends on phase 3 and persistence design from phase 0; P1 release follows P0 gate. Coverage: NF-04, NF-05, NF-12, NF-13, NF-16 saved-data controls; AC-02–AC-05; NF-01 Copy/Share extension.

- [ ] Implement encrypted opt-in history with exclusions before persistence, age/count/byte limits, bookmarks, search and deletion of associated indexes.
- [ ] Add native snooze with API fallback and distinct saved-copy reminders. Implement bounded repeating reminders, Done/Stop/Snooze and Needs attention.
- [ ] Add five-second Undo through deferred cancellation and version-aware delayed cleanup. Source disappearance/update cancels obsolete work.
- [ ] Handle full storage, inaccessible keys and database failure with capture paused and live inbox still usable. No plaintext fallback.
- [ ] Add explicit Copy/Share preview and disclosure. Keep data out of logs and every existing backup serialization path before enabling history.

Validation: disabled-history capture test; protected/excluded content absence; retention/eviction; truncated Unicode records; key loss; full disk; reboot/Doze reminder behavior; no sound burst; expired source actions; Undo and interrupted delayed cancellation.

Gate: saved copies never masquerade as live source actions; privacy/storage budgets pass; every notification-content persistence route is consent-controlled and excluded from configuration export.

## 9. Phase 6 - Rules, explanations and presets

Depends on phases 3 and 5. Coverage: NF-06, NF-11; NF-08/NF-16 extensions; AC-03, AC-07.

- [ ] Build one pure evaluator shared by live processing and preview: normalized literal matching, explicit fields/ANY/ALL, missing-content handling, protected exceptions and stable precedence.
- [ ] Add rule creation from current items, observation mode, bounded content-free activity outcomes and editable templates. Introduce auto-dismiss only behind its separate consent and lifecycle controls.
- [ ] Add folders and presets with timed overrides, idempotent activation and visible conflict order. Provide row/app shortcuts and an optional Quick Settings tile.
- [ ] Re-evaluate visual rules after edits; require preview before applying destructive rules to existing notifications. Pause suppresses automatic actions without erasing configuration.

Validation: evaluator truth tables, missing/truncated/redacted fields, ties/overlapping presets, expiry after intervening edits, restore generations and paused backlog. UI tests verify why matched, why not matched and action failure explanations.

Gate: preview and live decisions agree; protected records survive automation; preset expiry never overwrites a subsequent user edit.

## 10. Phase 7 - Scheduled review and digest

Depends on phases 4–6. Coverage: NF-07A, NF-12/NF-16 schedule interactions; AC-03, AC-05.

- [ ] Add fixed review slots and interval schedules, next-review time, held-in-Comfer view and Review now. Release changed records that no longer match.
- [ ] Build one optional Comfer digest alert per window, gated by posting permission and DND; inbox-only review remains available when alerts are unavailable.
- [ ] Use current active records for release and label source-cleared records as saved copies only with consent. Avoid source reposting and replayed sounds.

Validation: timezone/DST/overnight slots, duplicate job delivery, restart, source removal/update, pause and permission denial. Verify counts represent records, not inferred unread messages.

Gate: each window produces at most one alert, current content is reconciled and all labels accurately describe Comfer-only behavior.

## 11. Phase 8 - Configuration backup and restore

Depends on durable schemas from phases 1–7; design integration in phase 0. Coverage: NF-15, AC-06; DC-02–DC-03; NF-09/NF-16 interactions.

- [ ] Add a versioned Notifications category to existing backup manifest/payload/preview and standalone export. Keep legacy version-1 compatibility through an explicit migration path; notification absence in old backups is not an implicit deletion instruction during merge.
- [ ] Export all durable settings, including layout/reach/presets/rules, using explicit DTO allowlists. Exclude runtime handles, content stores, grants and jobs. Audit whole-app backup and restore journals for leaks.
- [ ] Add manual unencrypted export, optional redacted sharing and opt-in automatic versions. Anyone holding a compatible file can inspect and import it without credentials. Implement configuration/destination access recovery, five-version retention and last-success status.
- [ ] Implement preview, selected-category Merge/Replace, duplicate resolution, inactive imports and profile/channel compatibility reporting.
- [ ] Extend existing journal/rollback to configuration generations and notification categories. Pause execution, restore atomically and recreate only future owned jobs after review.

Validation: legacy/new round trips, repeated imports, partial-category replacement, privacy scan of archives and journals, cross-user/device sharing, corruption, malicious size/schema, interrupted write/restore/rollback, folder-access loss and new-device reach recalculation.

Gate: both standalone and full-app backups satisfy NF-15; no captured notification content appears in exported settings or restore journals, and shared backups import without credentials or owner checks; failures preserve existing configuration or leave a clear paused recovery state. Full P1 release requires regression of all earlier gates.

## 12. Phase 9 - Optional research and later features

Depends on P1 stability; separate decisions per candidate. Coverage: NF-07B, NF-10, NF-14, AC-01 optional source actions.

- [ ] NF-14/NF-07B: measure conversation identity stability, sound/vibration leakage and unrelated-app effects on physical devices. Keep best-effort mute experiments separate from production display rules. Record a go/no-go per device/API, with no guaranteed silence claim.
- [ ] NF-10: implement local aggregate insights only if user testing supports them; distinguish posts/updates and visual hiding from prevented interruptions.
- [ ] Optional source actions: prototype manually invoked current Reply/Archive/Mark as read, auth requirements and outcome reporting. No action through historical handles and no automatic replies.
- [ ] Other P2 ideas require new scoped requirements before implementation. No implicit commitment to voice, flashlight, external automation or AI classification.

Gate: each accepted feature has testable requirements, user value evidence and its own release validation. A rejected prototype closes with a documented reason; it does not leave an unverified production feature enabled.

## 13. Traceability and completion ledger

| Requirement | Implement/validate in phases |
|---|---|
| NF-01 | 1–3; Copy/Share in 5 |
| NF-02 | 3 |
| NF-03 | 0, 4 |
| NF-04, NF-05 | 5 |
| NF-06 | 6 |
| NF-07A / NF-07B | 7 / 9 |
| NF-08 | 1, then every lifecycle integration |
| NF-09 | 0–1, 4–8 and every data/action boundary |
| NF-10 | 9 |
| NF-11 | 6 |
| NF-12, NF-13 | 5; schedule interactions in 7 |
| NF-14 | 9 |
| NF-15 | schema groundwork 0–1; full delivery 8 |
| NF-16 | 3–4, extended in 5–8 |
| DC-01–DC-05 | 2–3, repeated for every added screen |
| AC-01 | 0–3, 5; optional actions 9 |
| AC-02, AC-03 | 1, 3, 5–8 |
| AC-04 | 0, 2 and every input screen |
| AC-05 | 5–7 |
| AC-06 | 0, 8 |
| AC-07 | 3, 6 |
| AC-08 | 0–4 and every release/migration |

For every phase, record: completed requirement IDs; changed files and schema version; tests/commands and outcomes; device/API evidence; unsupported cases; unresolved risks; migration/rollback behavior; and release decision. Start by discovering valid Gradle tasks rather than assuming variants. Run focused unit/instrumentation tests per change, then build/lint and relevant regression checks at release gates. Do not report completion from emulator-only evidence where physical-device validation is required.

No calendar estimates are committed. Size the work after phase 0 establishes the actual storage, scheduling and device-validation costs. Work is implemented only when the implementation tasks are separately pursued; this document is the executable sequence for that work.


## 14. Execution ledger — 8 September 2026

Implementation is authorized phase-wise, with automated emulator tests. The first slice changes `MyNotificationListenerService`, `NotificationIconRow`, widget inclusion and the row settings explanation, and adds the `notifications` package, localized-resource keys, unit/instrumentation tests and two debug fixture APKs. Notification configuration schema is version 1 with a monotonically increasing local generation. Live records and source handles remain memory-only. Configuration load failure preserves the original stored value, pauses configuration execution and requires an explicit reset; failed writes do not publish new configuration.

### Current evidence

- Baseline `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` passed before implementation.
- Full posted/updated/removed records are captured using a bounded event queue and reconciled against Android active notifications. A separate profile-aware projection supplies home icons. Content and action changes invalidate revisions, including updates with unchanged post time. UI reconciliation is debounced, with a one-second dirty-state ceiling during sustained bursts and periodic idle reconciliation. Summary children do not double-count within an app/profile/group.
- The home entry remains after setup, including an empty or hidden-only inbox and access loss. Existing horizontal/vertical layout, tint, border and overflow preferences are supported, with minimum 48dp touch targets.
- Portrait inbox uses density-independent `min(safeHeight * 0.40, 360dp)` with no user-facing height controls; legacy saved adjustments are ignored. Landscape fills the safe window. Navigation, manual actions, configuration and confirmation stay within the panel.
- Manual Open, local visual hiding (15 minutes, 1 hour, 1 day, permanent), resume, priority, app protection, eligible dismiss and API-gated native snooze are implemented. Bulk dispatch snapshots key/revision pairs, confirms and reports aggregate requested/skipped outcomes. Source actions revalidate the current platform record immediately before execution. Historical handles are not retained.
- Pause/reset settings and Android notification/sound-settings routes are available. A separate Comfer-owned automatic DND rule now implements focus timers and one weekday/overnight schedule. Schedules can instead hide selected apps only in Comfer without DND access. Until-tomorrow and custom resume deadlines are available for manual hiding. Visual hiding never invokes it. The UI distinguishes quiet requests, system-disabled rules, missing access and cleanup failures. Physical validation remains open.
- Initial API 24 run passed 3 real cross-package listener tests, then 5 listener/UI tests. Screenshot inspection found a cramped navigation label; it was shortened and the home row styling was restored. The final expanded suite passed; see the validation report below.
- Build, JVM tests and `:app:lintDebug` passed after configuration-aware resource fixes. New notification strings currently use English fallback through a file-scoped missing-translation exemption; translation review remains required before release. Existing lint warnings remain visible.

### Contracts and remaining gates

`requested` means Android accepted the action call, not confirmed source-app completion. `app_open_requested` identifies the explicit app-launch fallback when a notification intent is missing or expired. `changed`, `removed`, `protected`, `expired`, `unavailable` and `failed` remain distinct dispatcher outcomes. Reconnect/access loss clears actionable live state. Cross-profile fallback launch is refused when no valid source PendingIntent is available. Source actions include a connection identifier as well as key/revision, so selections cannot become valid again after reconnect or process recreation. Platform action handles are compared inside the service and exposed only as opaque local identity tokens. Configuration generation advances only after a successful write. The live store is never serialized into notification preferences.

Android can reject rapid source posts before listener delivery; the burst fixture validates convergence to the latest accepted active record and does not promise lossless observation. API 24 has no native snooze; the button is omitted. The system quiet-settings route remains available. Phase 4 now uses Android's owned automatic-rule APIs, with API-specific behavior described in the [NotificationManager documentation](https://developer.android.com/reference/android/app/NotificationManager) and [ConditionProviderService documentation](https://developer.android.com/reference/android/service/notification/ConditionProviderService); no global-DND policy setter is used. The owned-rule implementation is not approved for release until physical-device gates pass.

Outstanding phase 0–3 work includes detailed per-item bulk outcomes, complete gesture/accessibility coverage, RTL/keyboard reach, restore edge cases and physical reach usability. Channel-specific navigation, progress display, health details, enlarged-text and production-activity rotation checks have been added. Physical-device DND ownership/idle/reboot/OEM checks, physical storage/key-loss behavior, whole-launcher regression/performance and translations remain open. History, reminders/Undo, rules/presets, digest and notification-category backup integration are not yet implemented. Backups remain specified as unencrypted and freely shareable, without credentials or owner checks.

Release decision: **development slice only; P0/P1 gates remain open**. No PDF was generated.


### Phase 4 implementation notes

`NotificationQuietHours`, `QuietSchedule`, the protected condition-provider service and a boot/time-change/alarm receiver now own the quiet-hour lifecycle. Configuration stores weekday/start/end choices; a separate private runtime file stores the owned rule ID and manual timer metadata. No global interruption-filter or policy setter is called. User-disabled/deleted rules are not silently recreated; an explicit Apply/start action is required. Reset first removes the owned contribution and reports access/cleanup failure before discarding configuration.

Manual focus uses elapsed time within the same boot so wall-clock edits cannot extend it. Reboot ends manual focus; recurring schedules are recalculated in the current timezone. Quiet boundaries use `setAndAllowWhileIdle`, which can be delayed by Android; the UI discloses this and offers system settings. Equal start/end represents a full local day, and overnight windows belong to their starting weekday. Physical timing tolerance, DST edge-case policy and OEM behavior still require validation.

The first quiet-rule test passed on API 24 and API 37. Expanded validation separates actual expiry from preservation of an overlapping automatic rule. The overlap fixture is another rule in the test application's ownership domain; it is not evidence for every external app/OEM. The final current suite passed 15 test methods on each emulator; see the validation report for the exercised and gated paths.


### Storage feasibility decision

The platform Keystore AES-GCM design is documented in [notifications-storage.md](docs/notifications-storage.md), with a bounded authenticated-record prototype and an isolated-key instrumentation test. This prepares phase 5 without enabling history capture. Configuration remains unencrypted and shareable; phase 8 integration is still pending. Real storage-pressure, database maintenance and hardware-backed key behavior remain release gates.


### Review checkpoint

The checked boxes above identify implemented tasks, not completed phase/release gates. The initial slice is executable and has passed 81 JVM tests and 15 instrumentation tests on each of the API 24 and API 37 emulators. The final lifecycle-review changes passed the same checks. Evidence and scope limitations are recorded in [notifications-validation.md](docs/notifications-validation.md).

Remaining software acceptance work includes complete bulk outcome/lifecycle reporting, richer group/profile presentation, RTL/TalkBack coverage and translation review. No text-input screen is currently shipped in the inbox; keyboard reach must be implemented and validated before phase-5 search and later rule editors. A Samsung Galaxy A30 running Android 11 / API 30 is now available. Physical tests use a separate `notificationTest` build and application ID to preserve the installed launcher; all 15 instrumentation methods passed on the full rerun. Results and the initial interrupted-run investigation are recorded in the validation report. Broader reach usability, sound/vibration, Doze and reboot gates remain open. History capture, reminders/Undo, rules/presets, digest, and notification-category backup/restore remain later work; the Keystore prototype does not enable content retention.

System backup and device-transfer rules explicitly exclude notification configuration and runtime rule IDs. Reviewed, freely shareable notification-category import/export remains phase-8 work.

### Samsung reach confirmation

On 8 September 2026, the user confirmed one-thumb access to notification actions, Settings and Back without changing grip in the Galaxy A30 portrait inbox. Record this as a passed device/user check; the broader DC-05 usability and keyboard gates remain open.

### Gesture and settings follow-up

Horizontal swipes now reveal a labeled Dismiss action to the left and native Snooze to the right (API 26+), with Cancel and explicit-tap commitment. Swiping does not dispatch a source or bulk action; protected items and unavailable snooze show an unavailable result. Source dispatch still validates the item revision and listener session. Existing labeled action buttons remain alternatives. Keep awaits phase-5 retention. Android settings navigation now tries channel, app notifications, app details, then general settings; cross-profile destinations remain explicitly unavailable. Full accessibility/RTL and lifecycle gates remain open.

Validation of the gesture follow-up: 81 JVM tests and notification-test lint passed; the complete API 24 suite passed all 16 methods. Samsung passed the 15 existing methods plus the corrected focused swipe-reveal test. Test-harness binding/fixture corrections and retained failure evidence are documented in the validation report. These results do not close remaining bulk lifecycle, accessibility, history or quiet-scheduling reliability gates.

### 9 September 2026 — full-height design revision

DC-02 now uses the full safe height with scrollable top padding: initial content begins at the computed thumb-reach boundary, then moves into the upper viewport as the user scrolls. Fixed navigation and action controls stay at the bottom. Opening a different scope/settings/menu resets the list to its reachable start; rotation and ordinary updates preserve scroll. Earlier bottom-panel screenshots and the Samsung reach confirmation apply to the previous design and do not validate this revision. Keyboard and wider device/accessibility gates remain open.

The full-height revision passed all 17 API 24 instrumentation methods, including initial reach, upward content movement, fixed bottom navigation and restoring the reachable start on scope changes. Build/lint passed. Samsung was disconnected, so hands-on validation of this revision remains pending.

Main Settings now shares the full-height, reachable-start layout and fixed reach formula, with a fixed bottom Back control. The inbox background matches Settings through ComferTheme and its translucent wallpaper-backed surface. The emulator retains only the main Comfer installation after removal of the preview and test fixtures; see validation notes for the passing layout/launch checks.

### Native Back revision — 9 September 2026

Remove app-rendered Back buttons from main Settings and the notification inbox. Settings relies on Android’s native Back to finish; inbox native Back dismisses active reveal/confirmation/menu/selection, returns from configuration/app scope, then finishes. Retain scope/filter/configuration controls at the bottom of the inbox. Earlier references to fixed Back controls are superseded.

### Listener recovery follow-up

Fixed the granted-but-unbound inbox scenario exposed after emulator APK updates. Add bounded entry/disconnect recovery, an API 24–25 enabled-registration fallback without permission toggles, explicit recovery-needed controls and a distinction between disconnected and healthy empty states. A real-notification regression passed without changing access; see validation notes. Broader OEM/process/idle recovery gates remain open.


### Group presentation and direct gestures

Grouped view now renders expandable app headers with live child counts. Tap opens the source notification; long press starts selection with ring/solid-circle indicators and a bottom quick menu. Multi-selection exposes shared hide/priority actions and eligible snooze/dismiss operations; More actions remains single-selection. Horizontal swipes directly dismiss eligible individual items. Returning from Android notification settings restores the selected card into view. These interaction rules supersede the earlier expand-on-tap, double-tap-selection and swipe-reveal behavior.


### Selection presentation refinement

Use 14 dp indicators (70% of the former 20 dp size) in an always-reserved 48 dp slot; hide both drawing and checkbox semantics outside selection mode. Keep preview wrapping stable. Selection mode follows the selected records and ends at zero. Cancel is multi-selection only. Group headers show app names and up/down carets, without counts; no selected-count label or count in batch action labels.


### Option wrapping and indicator alignment

Center selection indicators vertically against their notification cards. Use wrapping layouts for bottom quick actions, recovery/confirmation controls, quiet-hours choices and shared settings option groups. Choices must remain visible within the available width without horizontal scrolling; verify narrow layouts and selection geometry on device.


### Swipe feedback and settings ring controls

Add finger-following card translation and animated return for cancelled/incomplete swipes. Share ring/dot drawing between inbox selection and notification settings; center the indicator beside multiline labels and make the whole settings row toggleable. Verify visible movement before release, cancellation without dismissal, and settings toggle geometry/state.


### Complete dismissal transition correction

Replace immediate swipe-release dismissal with an off-screen exit animation followed by the service action. Keep row space until completion, account for viewport insets in either direction, and retain cancellation/rejection recovery. Follow AOSP SwipeHelper's distance/velocity decision and animate-before-dismiss order. Add controlled-clock regression checks proving that the entire card clears the viewport before the dismissal callback.


### Group selection and non-dismissing Open

Separate group title selection from the expand/collapse button. Cover partial-to-all selection, collapsed children, other-group preservation and zero-selection exit. Assert that opening an auto-cancel fixture leaves its Android notification active. Comfer only issues cancellation from explicit dismissal paths; source-owned cancellation remains independent. The user confirmed that removed notifications should not be retained as separate inbox copies; preserve the current live-only behavior.
