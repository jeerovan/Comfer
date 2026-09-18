# Journals implementation plan

Updated 15 September 2026. Requirements: [Journal-Features.md](Journal-Features.md). Shared layout: [thumb reach](docs/thumb-reach.md). Repository release record: [DEVELOPMENT-PROGRESS.md](DEVELOPMENT-PROGRESS.md).

Implementation and test evidence are tracked below. Core Journal code is present; phase exits remain open until their stated checks pass. The interim Search long-press route is superseded by the Workspace menu (phase 9); Tasks storage, reminders and notification actions remain intact.

## Confirmed integration and scope

- The final home entry is the [four-module menu](docs/home-module-menu.md): replace the middle Search icon with Workspaces; tap expands the Notes, Journal, Tasks and Search icons in their layout-specific positions exactly like a home app folder in both quick-list layouts. All four icons now launch their modules; Notes is a 17 September test build tracked in [Plan-Notes.md](Plan-Notes.md).
- This supersedes the interim Journal Search-long-press shortcut now that the shared Workspace menu is implemented. Tasks keeps its data, reminders, notification actions and backup/restore; its icon in the module menu resolves the alternative-entry decision.
- Journal is separate from Notes and Tasks, with its own activity/navigation, storage namespace and manual-backup section. No Journal content in Notes navigation/search or launcher global search.
- Initial scope: dated text/image feed, durable composer, one image per entry, live dictation, in-place edits, swipe deletion, five-second Undo, 7-day Archive, optional protection and portable manual backup. No audio files/playback, sync, accounts, remote AI, moods, streaks, rich text, reminders or resurfacing.
- Journal must open at today's latest entries without forcing the feed to its oldest item. Full-height initial layout means no reach gap; it does not override the required initial date/scroll position. At the actual scroll boundary, use the shared pull-down reach behavior, with the fixed composer/keyboard excluded from scrolling content.

## Progress tracking

Use Not started, In progress, Blocked, or Complete. A phase is complete only when its deliverables and exit checks pass. Record actual test commands, device/API, pass/fail/skip counts, reproduction limits and source/artifact identity in the evidence column or a linked validation record. Do not replace failed attempts with success-only summaries. No automatic production upload is authorized by this plan.

| Phase | Deliverable | Depends on | Status | Evidence / next gate |
|---|---|---|---|---|
| 0 | Requirements, entry audit and implementation contracts | — | Complete | Decisions recorded in [implementation contracts](docs/journals-implementation.md); English and interim entry approved |
| 1 | Journal persistence and crash recovery | 0 | In progress | Schema 1→2→3 migration, stale/duplicate writes, 10,000-row query and force-stop recovery passed; full exit matrix remains open |
| 2 | Dated feed, navigation and text composer | 1 | In progress | Real Activity keyboard, recreation, landscape and UI tests passed; date/lifecycle manual acceptance remains open |
| 3 | Bounded private image pipeline | 1–2 | In progress | Byte/pixel boundaries, bounded decode, malformed/media round trips passed; real provider and full-disk acceptance remain open |
| 4 | In-place edits, deletion, Undo and Archive | 2–3 | In progress | Edit/swipe/revision/retention and cold draft recovery tests passed; remaining gesture acceptance open |
| 5 | Dictation reducer and deterministic recovery tests | 1–4 | In progress | Reducer/fake backend, deduplication, stop/pause/interruption and failed-final retry tests implemented |
| 6 | Android speech integration and composer controls | 5 | In progress | Adapter and controls implemented; real provider/language/network/microphone acceptance pending |
| 7 | Optional Journal protection and privacy surfaces | 1–6 | In progress | Device credential gate, secure window, recovery and export authentication implemented; credential-device acceptance pending |
| 8 | Portable backup/restore, including protected media | 3–7 | In progress | Archive v4, encrypted fresh-install transfer and cold interrupted-restore recovery passed; real credential and full-disk acceptance remain open |
| 9 | Integrate four-module home menu | 2, 7–8; Notes later | Emulator verified | Workspace routes all four modules; Notes destination verified 17 September. Physical/TalkBack acceptance remains open |
| 10 | Accessibility, performance, migration and release acceptance | 1–9 | In progress | Emulator suites, real activity keyboard/rotation, 10,000-row queries and streamed media validation; user/device/release acceptance pending |

## Phase 0 — Contracts and integration audit

- [x] Review all core feature sections and acceptance scenarios; treat market-review appendix as background, not additional scope.
- [x] Resolve default navigation using the subsequent four-module menu decision; Tasks has its own menu icon.
- [x] Inspect the current callback path: `QuickListOverlay` → `HomeFolderLayout` → `CircularLayout` / `FiveColumnLayout` → center Search control in `MainActivity.kt`. The `onShowTasks` callback and `tasks_open` accessibility label currently describe Tasks.
- [x] Inspect the launcher action/navigation model and existing home folder animation for reuse. Map four stable internal module IDs to their own destinations; do not treat them as installed app package IDs.
- [x] Record proposed module ownership: `journals/` model/store, feed/composer, media, speech controller and backup adapter; reuse current theme/reach/backup coordination without coupling Journal to Tasks UI.
- [x] Specify persistent IDs, ordering tie-breaker, date/time semantics, versions, draft/session/edit ownership and lifecycle state transitions before implementing.
- [x] Specify image formats, compression strategy and staging/recovery protocol; capability-check supported Android/API behavior during implementation.
- [x] Choose the optional-lock and portable-encryption design, including authentication boundaries, key recovery and what loss of an export secret means. Verify existing app protection rather than assuming it meets Journal requirements.
- [x] User confirmed English-only labels/prompts for the first Journal test build, with localized dates and RTL.
- [x] User authorized the interim Search long-press → Journal route until Notes and the full module menu are ready.

**Exit:** no unresolved storage or capture-safety contract; remaining user-facing decisions explicitly recorded. Useful independent phases may proceed while a decision is pending; do not guess interim module-menu behavior or an encryption recovery promise.

## Phase 1 — Local model, storage and recovery

- [x] Add a versioned Room schema with non-destructive migration tests. Decide dedicated Journal DB versus isolated tables based on current repository conventions; document the choice.
- [x] Store stable entry ID, selected calendar date, creation time zone, created/updated instants, committed text, optional managed-image reference, revision and deletion/Archive metadata.
- [x] Keep composer and edit drafts separate. Persist target date, attachment staging reference, prompt identity, recognition language and session/segment recovery state. Ordinary configuration changes must not create duplicate drafts.
- [x] Persist finalized speech segments incrementally and distinguish durable data from the latest provisional hypothesis; recovered sessions always start with capture off.
- [x] Provide atomic submit/edit/delete operations, idempotent operation IDs and revision checks for stale writers. Define cancellation versus committed-operation outcomes.
- [x] Index date/order queries and provide paging plus direct date lookup; avoid reading all entries/media into memory.
- [x] Implement DB/media recovery manifests or journals: committed references must always resolve; abandoned staging files remain recoverable or are safely collected.

**Exit checks:** empty DB; duplicate submit; concurrent/stale update; process restart with draft; timezone change; migration; injected write failure; 10,000-entry paging fixture. Assert unchanged data on failure and bounded query/load behavior, not only successful method calls.

## Phase 2 — Dated feed and text composer

- [x] Add Journal activity/screen, current Comfer typography and wallpaper treatment; keep it accessible through an isolated test entry before replacing home navigation.
- [x] Ascending dates and oldest-to-newest entries within each date, using created instant plus stable ID as a tie-breaker. Show small centered date separators outside cards and a trailing time below content, without “Written”. Default creation time is now; the selected composer day supports backdating with the current local time. Precise date/time edits are available on saved entries; the new-entry clock control has been removed.
- [x] Use reverse layout and newest-first display items so the newest entry is naturally anchored at the bottom. Keep Archive/navigation scroll state separate; opening and Archive return start at the latest entry without startup/return scroll effects. Successful creation navigates to the saved entry’s date and scrolls to the latest entry.
- [x] The bottom date row above the composer shows Today or the selected date; tap opens a picker and horizontal swipe moves one calendar day. An end-aligned three-dot menu opens Settings or Archive. The centered date separator is display-only; the bottom date control opens the picker; support loaded, unloaded and empty dates. Selected empty dates remain writable; Today returns to current date. Date navigation must not retarget an existing draft silently.
- [x] Bottom expanding input with image action inside its trailing edge and mic/Submit outside. Bound input height, scroll internally, respect IME/system insets and RTL.
- [x] Local shuffled prompts, no immediate repeat; select a new prompt on each fresh opening, stable through editing/rotation/draft date navigation. Prompt is not draft text; maintain an independent accessible input label.
- [x] Whitespace-only text is empty; text/image drafts show Submit. Save exactly once, clear only on successful commit, rotate prompt after success; preserve content on failure.
- [x] Restore durable drafts and target dates. Handle midnight while idle separately from an active draft/session; preserve captured session date.

**Exit checks:** initial today with no entries; date jumps beyond loaded pages; canceled picker; historical draft plus Today; midnight/timezone change; rapid submit; failed save; keyboard/rotation; no prompt accidentally saved.

## Phase 3 — Managed images

- [x] Single-image system picker with scoped access and API-appropriate fallback; no broad photo-library permission. Cancel retains draft.
- [x] Stream at most 10,000,000 source bytes, including providers with unknown size; inspect dimensions before allocating and reject over 20,000,000 pixels.
- [x] Normalize orientation, strip unnecessary EXIF/location, retain aspect ratio, longest edge ≤2048 px, stored bytes ≤2,000,000. Explain resized copy; never modify the original.
- [x] Reject corrupt/unsupported/animated files explicitly. Bounded decode/compression and off-main thumbnails; reject safely when the size budget cannot be met.
- [x] Private managed copies survive removed originals/revoked URI grants. Stage replacements before committing DB references; preserve old files until commit/recovery obligations finish.
- [x] Image-only submit; image tap opens a secure full-screen pinch-zoom/drag-pan preview with bottom X/Change/delete-icon actions and no double-tap zoom, replacing the image bottom sheet. Image taps do not trigger text editing; deleting or replacing a saved image uses the existing reversible edit flow.

**Exit checks:** exact limit and limit+1; enormous/corrupt/rotated/animated input; unknown-length stream; provider disappears; duplicate picker result; low disk; interrupted copy; thumbnail budget; removing image-only content requires explicit entry deletion/cancel.

## Phase 4 — Editing, swipe deletion and recovery

- [x] Tap entry body for in-place editing, entry-local Save/Cancel and keyboard focus. One edit at a time; preserve and disable the separate composer.
- [x] Preserve journal date/created time during content-only edits; allow explicit timestamp edits with check/cross confirmation. Cancel restores text, staged image and chosen date/time.
- [x] Save/Discard/Keep editing only for changed buffers when leaving/switching. Failed saves retain text and media. Recover unsaved edits after interruption.
- [x] Empty text with image is valid; removing all content offers Delete or Keep editing. Preserve Unicode, literal punctuation, composing text, selection and undo/redo.
- [x] Swipe either direction deletes a saved entry into Archive; partial/cancelled/vertical/edge-Back gestures do not delete. Disable swipe for live sessions and unsaved edits; add accessible Delete.
- [x] Deleted / Undo lasts five seconds; restores original image/date/order/metadata subject to revision safety. Later deletes restart feedback without erasing earlier Archive recovery.
- [x] Journal settings exposes 7-day Archive, restore and confirmed permanent deletion/Empty trash. Retention cleanup respects drafts, active edits, Undo and all remaining media references.
- [x] Remove ordinary empty date groups while retaining an explicitly selected empty date and nearby scroll position.

**Exit checks:** text versus image tapping; unchanged-edit close; edit cancellation; write failure; swipe cancellation/RTL; repeated deletions/Undo; expiry boundaries; last entry of day; process loss during delete or restore; shared/staged media not prematurely collected.

## Phase 5 — Dictation state machine, before real microphone integration

- [x] Model Empty, Draft, Starting, Listening, Paused, Finalizing and In-place edit explicitly. One active session, one live entry, immutable prefix/image/date captured at session start.
- [x] Define fake recognizer interface and session/segment tokens. First meaningful result creates the live row, or attaches the pre-existing draft as its initial content; silence alone creates none.
- [x] Replace provisional hypotheses; append finalized segments exactly once. Reject duplicate finals, previous-segment callbacks and previous-session callbacks.
- [x] Pause stops capture, drains a segment for a bounded period, and gates Resume until settlement/provisional recovery. Resume appends to the same entry with a new segment identity.
- [x] Stop requests immediate capture stop, allows at most two seconds for final results, then invalidates callbacks. Missing final uses latest hypothesis with Needs review; failed persistence retains Retry/recovery.
- [x] Bound retry/continuation after silence/service limits; preserve provider limitations and error reasons. Never switch provider/network mode silently.
- [x] Background/lock/navigation/process loss returns to non-recording recovery. First Back during capture stops/finalizes and stays on Journal. Incompatible edit/date/delete actions request Stop recording.

**Exit checks with deterministic clock/failure injection:** revised partials, repeated/reordered finals, pause/resume race, stop timeout, late callbacks after new session, no partials, silence, permission cancellation, save failure, midnight and repeated interruptions. Assert mic-off commands, one entry and exact final text.

## Phase 6 — Android speech and live composer

- [x] Add only required microphone capability; request access at deliberate dictation start, after long-press/accessibility action. Cancel starting and permission denial restore original draft.
- [x] Capability/language checks and explicit language selection; prefer available on-device recognition. Clearly disclose network recognition and require explicit opt-in before using it.
- [x] Long-press mic starts one latched session; optional ordinary tap shows a brief hint. Long-press Submit starts dictation with existing text/image and suppresses submit-on-release. Equivalent labeled accessibility action is required.
- [x] Listening UI replaces input/image picker with real audio-level feedback where provided; otherwise label generic animation honestly. Show duration, Pause before Stop; paused Resume uses Play shape only to mean resume capture.
- [x] Provisional versus committed text remains visually distinct. Avoid per-word TalkBack announcements; make live text available on demand. Honor reduced motion.
- [x] Integrate lifecycle/interruption handling and bounded finalization; no raw audio retention, playback or automatic restart after background/lock.

**Exit checks:** fake-provider suite remains green; real on-device and explicitly opted-in network recognizers on supported physical configurations; denied/revoked mic; unsupported language; no recognizer; offline/network loss; finals-only engine; provider timeout. Verify actual microphone stops on Pause/Stop/background, not just the visual indicator.

## Phase 7 — Optional protection and privacy

- [x] Implement the reviewed biometric/device-credential protection boundaries for feed, drafts, Archive, images and thumbnails; lock state survives lifecycle changes correctly.
- [x] Re-locking ends capture and preserves recoverable state. Prevent private content from appearing before authentication or in recent-app previews.
- [x] Review caches, logs/crash reporting, exports, picker staging and backup previews for content disclosure. Journal stays outside home/global and Notes search.
- [ ] Test authentication cancellation/failure, credential/enrollment change, lock while saving/recording and process death. Do not promise recovery from an invalidated device key without a tested design.


**Exit:** typed/image/speech content and derivatives follow the same protection policy; no hidden live mic after locking; accessibility remains usable at the lock boundary.

## Phase 8 — Manual backup and portable restore

- [x] Extend the current archive with an independently versioned Journal section and managed-media manifest. Define inclusion of drafts, Needs review entries and unexpired Archive; preserve their states without serializing a live microphone session.
- [x] Exclude raw audio, runtime capture flags and Android URI/microphone grants. Export managed copies so restore does not depend on the original picker/provider.
- [ ] Authenticate protected export and use the portable encryption/recovery design from phase 0. Validate on another installation/device; device-bound keys alone are insufficient.
- [x] Review counts, protected status and replacement semantics. Missing Journal section preserves existing Journal data; an explicitly empty Journal replacement requires reviewed confirmation.
- [x] Stage and validate schema, media sizes/hashes, references, duplicate IDs and archive paths before mutation. Coordinate rollback/recovery with existing launcher, Tasks and notification sections.
- [x] Suspend active recording/edits safely before restore; after restart or interrupted restore, never reactivate the microphone. Collect orphan files only after successful recovery.

**Exit checks:** text/image/protected round trips across devices; older archives; reviewed empty replacement; corrupt/truncated/missing media; wrong export secret; low disk; interrupted staging/commit; rollback of all affected modules. Existing Tasks and notification backup tests remain green.

## Phase 9 — Four-module home menu integration

17 September update: Notes now opens its own capture/recovery screen. The earlier coming-soon route below is historical; see Plan-Notes.md for implementation and acceptance.

16 September decision: ship the Workspace center icon and four module icons now. Notes is explicitly coming soon; its full implementation remains separate. This supersedes the earlier three-dot/working-Notes integration gate.

- [x] Implement the [shared menu contract](docs/home-module-menu.md) in both quick-list layouts: Workspace center control, four icons without visible labels and Close reversal.
- [x] Reuse home-folder animation: home icons shrink into their own centers, modules expand from the center, and closing reverses both.
- [x] Route Search, Tasks and Journal independently; Notes reports coming soon without pretending to launch an implemented module.
- [x] Remove the old `onShowTasks`/Journal-long-press center callback; update accessible labels and guide visibility.
- [x] Guard rapid taps, animation reversal, mutual exclusion with folders, Back, pending launch cancellation and launch/return.
- [x] Complete emulator validation and update the test build: 13 focused UI tests and 167 JVM tests passed; see development progress.
- [x] Implement and validate the Notes destination (17 September); its P0 acceptance remains tracked separately in Plan-Notes.md.
- [ ] Physical-device and TalkBack acceptance of the shared menu.

**Exit checks:** both layouts; three live destinations and Notes status; ordinary folder regression; Close and Back; repeated taps; reversal; RTL; launch/return. No accidental Search launch from tapping Workspace.

## Phase 10 — Final acceptance and deployment

- [ ] Run the feature-spec acceptance matrix and affected launcher/Tasks/notification regression suites. Record actual outcomes, including unavailable recognition capabilities and skipped device cases.
- [ ] Test short/tall/landscape/multi-window layouts, large fonts, RTL, TalkBack, reduced motion, keyboard/picker insets, date navigation and composer reach.
- [ ] Profile paged 10,000-entry feed with representative thumbnails; no full-archive loads, main-thread decode, repeated unbounded sampling or callback-driven write storm.
- [ ] Test upgrade from the shipped baseline, migrations, durable composer/edit/speech recovery, fresh install + portable restore, and restored protected media.
- [x] Update emulator after app changes, preserving normal app data. Update Samsung only when explicitly requested; use isolated test packages for destructive recovery scenarios.
- [ ] Obtain acceptance, then prepare/verify signed release artifacts and record version/hash. Preserve recovery/mapping artifacts. Staged publication and production follow-up are separate explicit steps.

## Validation record template

For each phase/attempt append: date; requirement or defect ID; observed behavior; chosen change and alternatives rejected; source commit; test command and fixture; device/API; pass/fail/skip counts; remaining limitations; artifact/version; user acceptance or later production outcome. Store synthetic fixtures only; never copy private journal text, images, audio or credentials into repository evidence.

## Current validation record

See [docs/journals-implementation.md](docs/journals-implementation.md) for decisions, failed attempts, corrected fixtures and exact commands/results. Checkmarks above describe implemented deliverables; open phase statuses explicitly retain unverified exit/acceptance checks. No Samsung install or production publication is authorized by this work.

First test deployment (15 September): debug 50 / 50.0 installed with `adb install -r` on `emulator-5554` (API 24), preserving normal app data. Open Journal with Search long-press. Final JVM run: 166 passed; final targeted Journal run: 28 passed, followed by passing boundary, cold-start, stop-timeout, fresh-install transfer and interrupted-restore checks. Artifact identity and limitations are in the linked validation record.

UI follow-up: title has no back arrow; Android Back handles return. Entry editing uses check/cross icon buttons. Swipe deletes into Archive without a visible Delete text button; accessible deletion remains available. Deleted entries expire after seven days, retaining the existing unresolved-edit recovery safeguard.

Theme follow-up: explicitly pair the translucent surface with onSurface foreground; use Material date/time/security-setup dialogs inheriting the Journal palette. Validate rendered text/icon/button colors in light/dark palettes and ensure picker cancellation preserves entry data.
