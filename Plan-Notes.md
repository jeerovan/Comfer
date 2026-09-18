# Notes implementation plan

Prepared 17 September 2026. Requirements: [Notes-Features.md](Notes-Features.md). Shared contracts: [Workspace menu](docs/home-module-menu.md), [thumb reach](docs/thumb-reach.md). Release status: [DEVELOPMENT-PROGRESS.md](DEVELOPMENT-PROGRESS.md).

Status: implementation in progress; see [design and validation](docs/notes-implementation.md) for actual evidence. No release acceptance is claimed. This plan covers all P0 requirements, with P1/P2 tracked separately. The market-study appendix informs priorities rather than adding requirements. Existing module implementations are reuse candidates, not proof that Notes' stronger integrity/privacy requirements are already met.

## Scope and resolved precedence

- Build local-first notes with inline checkboxes, separate from Tasks and Journal. P0 includes organization, search, module protection, 7-day Bin and app-wide backup/restore. No account, automatic cloud sync, home content panel or Notes reminder engine.
- The current Workspace menu supersedes the feature document's older three-dot placement. Replace only Notes' coming-soon destination after the module is ready. Preserve circular positions (Notes left, Journal right, Tasks above, Search below), five-column order **Notes | Tasks | Close | Search | Journal**, icon-only presentation and accessible names.
- Tapping Notes in Workspace opens the main Notes collection with all non-deleted notes. New note opens a focused canvas without a notebook/type prompt. Keep pending drafts accessible through recovery; never overwrite them. Protection may require authentication before focus/content is shown.
- Notes uses durable autosave, not Tasks' Save/Cancel contract. Saved means a successful durable commit; failed/pending changes remain recoverable where possible.
- Follow current theme, wallpaper, insets, motion and shared reach behavior. Browsing starts at full height; downward unconsumed drag at the top reveals reach space capped to the 360 dp bottom area. Keep frequent actions fixed and do not add blank reach padding above a keyboard-focused editor.
- Notes uses a **7-day Bin** and no Archive, per the 17 September UI revision. Older archived notes remain visible in the main collection.
- Current app minimum is API 24 with core-library desugaring configured. Verify new APIs and dependencies against that baseline.
- English-only labels for the first Notes test build were explicitly approved on 17 September 2026; localized dates, Unicode input and RTL are mandatory regardless of label scope.
- Preserve the existing compact Comfer restore summary (version and date/time). There are no Notes-specific import/export screens; do not restore removed global-summary detail. Existing backup password UX remains unchanged unless a reviewed format change requires a product decision.

## Tracking rules

Statuses: **Not started → In progress → Implemented / validation pending → Verified**; use **Blocked** with a concrete dependency and next action. P1/P2 begin as **Deferred**. A checkbox means its deliverable and associated checks are complete; phase verification additionally requires its exit gate. Never mark a phase verified solely because it builds.

Update the table and checkboxes after each work session. Record actual commands, outcomes, device/API, artifact and limitations in the attempt log below. Separate emulator success, physical-device acceptance and release approval. Do not promote a phase with unresolved data-loss or privacy failures.

| Phase | Priority / deliverable | Depends on | Status | Evidence / next gate |
|---|---|---|---|---|
| 0 | P0 contracts, reuse audit and threat model | — | In progress | Storage/privacy contracts recorded; English approved; physical performance device remains to be selected |
| 1 | P0 transactional model and durable drafts | 0 | Implemented / validation pending | Encrypted Room schema 2, revisioned drafts, failed-write recovery and key generations; final expanded checks below |
| 2 | P0 protection and key lifecycle | 0–1 | Implemented / validation pending | Real emulator PIN flows and protected restart passed; credential changes, expiry and recovery edge cases remain open |
| 3 | P0 quick capture and autosave editor | 1–2 | Implemented / validation pending | Long input, IME and durable-state UX |
| 4 | P0 collection and organization | 1–3 | Implemented / validation pending | Notes/Bin, label/color actions and global manual ordering |
| 5 | P0 checklists and conversions | 1–4 | Implemented / validation pending | State-preserving conversion and accessible reorder |
| 6 | P0 search and in-note navigation | 2–5 | Implemented / validation pending | Unicode correctness, freshness and privacy |
| 7 | P0 Trash and deletion recovery | 2–5 | Implemented / validation pending | Retention, restore and confirmation semantics |
| 8 | P0 backup, export and transactional restore | 1–7 | Implemented / validation pending | Portable protected round trip and safe rollback |
| 9 | P0 Workspace/share integration | 3–8 | Implemented / validation pending | Both Workspace layouts passed after keyboard-return fix; share/recovery and physical acceptance remain open |
| 10 | P0 accessibility, performance and release gate | 0–9 | In progress | Emulator PIN authentication, cancellation/rejection, background/device re-lock and protected process recovery passed. Physical acceptance deferred by user; remaining accessibility/performance gates stay open. |
| 11 | P1 photos | 10 | In progress | System-picker compressed images promoted by 18 September editor request; encryption/backup checks in progress; camera and broad media stress remain deferred |
| 12 | P1 rich text and templates | 10 | In progress | Formatting toolbar promoted by 18 September editor request; templates remain deferred |
| 13 | P1 explicit Tasks links | 10 | Deferred | Stable links and no cascading deletion/reminders |
| 14 | P2 voice, OCR, sketches and PDF | 10; media where needed | Deferred | Separate capability and acceptance gates |

## Phase 0 — Contracts and integration audit

- [x] Read the complete Notes requirements, current Workspace/reach contracts and existing phase-plan conventions.
- [x] Verify the current Notes route is a coming-soon message; record API-24 compatibility and navigation precedence above.
- [x] Audit actual storage, draft, protection, backup/restore, share-intent and lifecycle callers before selecting reusable components. Read current source and relevant tests; do not copy Journal protection assumptions into Notes.
- [x] Document ownership boundaries for proposed `notes/` storage/repository, editor, collection, search, protection and backup adapters. Decide dedicated Room database versus isolated tables using migrations and restore boundaries.
- [x] Specify IDs, revisions, transactional operations, tombstones, ordering, draft ownership, empty-note behavior, timestamps, label invariants and legacy metadata compatibility. Define stable sort tie-breakers.
- [ ] Specify editor-to-collection navigation, quick capture/recovery behavior, checklist conversion preview, batch-selection UX, undo semantics and locked-item metadata visibility. Resolve ambiguous interactions before implementing dependent UI.
- [x] Threat-model device/module/item locks, drafts, indexes, WAL/journals, caches, logs, exports, screenshots and task-switcher previews. Choose maintained encryption/authentication primitives and a portable key-recovery design; no custom cryptography.
- [x] Define password/key-loss, enrollment/device-credential changes, uninstall/reset, background re-lock timeout and no-credential behavior. Identify any product decisions needing user input without blocking unrelated work.
- [x] Confirm English-only labels for the first Notes test build (17 September).
- [ ] Designate a physical midrange reference device before performance acceptance measurements.

**Exit:** written storage/privacy/recovery contracts and a requirement-to-phase map; unresolved choices have owners and explicit dependent work. Planning/source review is not a security audit.

## Phase 1 — Local model, transactions and durable drafts

- [x] Implement versioned storage with stable note/label IDs, relationships, revisions, created/updated times, pin/Bin/color state and global manual order; decode legacy notebook and checklist fields.
- [x] Retain the legacy Inbox storage key only for old-data compatibility; it is not a view. New notes share one collection with optional multiple labels. Model protected content according to phase 0 from the outset, avoiding plaintext private drafts/indexes.
- [x] Persist recoverable working drafts separately from committed content, including target note/revision and practical selection/scroll context. Never persist a live authentication session.
- [x] Serialize/version autosave operations; late writes cannot replace newer text. Atomic mutations and idempotent retries cover duplicate actions, stale editors and batch failures.
- [ ] Preserve last durable content on storage failure; keep a failed in-memory draft while the process survives, with retry/copy paths. Define durable draft promotion and cleanup without deleting meaningful untitled content.
- [ ] Provide paged observable queries, background I/O, schema exports and non-destructive migrations. Seed synthetic long-note and 10,000-note fixtures.

**Exit checks:** empty database; whitespace/untitled content; duplicate writes; out-of-order saves; two editors; full storage; process death before/after commit; restart/upgrade; transactional rollback; relationship integrity. Saved content must survive; unpersisted keystrokes are not guaranteed.

## Phase 2 — Protection and key lifecycle

- [ ] Add optional module authentication matching Journal (no individual-note controls) with system biometric/device credentials, using the approved encrypted storage/key design.
- [ ] Gate editor, draft recovery, deep links, search, previews, thumbnails, share/export and future attachments consistently. Locked indexes are unavailable or searched in memory only after authentication.
- [ ] Re-lock on device lock and the explicitly defined background timeout; clear sensitive UI/session caches. Handle locking during a save without plaintext fallback or false success.
- [ ] Limit screenshot/recents restrictions to sensitive surfaces; exclude note content from logs, notifications, crash metadata and global launcher search while locked.
- [ ] Implement safe protect/unprotect transitions, key invalidation/error states and documented recovery. Do not silently reset keys or destroy unreadable data.

**Exit checks:** canceled/failed authentication; no enrolled credential; restart; lock mid-edit/save/search/export; credential/enrollment changes; stale intents; key loss. Inspect database/WAL/temp/index/cache artifacts using synthetic secrets for plaintext leakage. Portable recovery is additionally gated by phase 8.

## Phase 3 — Quick capture and autosave editor

- [ ] Add Notes screen/editor through an isolated test route first; one borderless canvas with a larger first-line title (blank allowed), preview derived from first nonempty line, default Inbox and immediate text focus after any authentication.
- [ ] Render Saving / Saved / Couldn't save from repository acknowledgements, without per-save snackbars. Flush on navigation/background where possible; preserve failed drafts when switching notes and offer retry/copy.
- [ ] Restore durable working state with an explicit pending-recovery indication; preserve cursor, selection, scroll and keyboard context where practical. Blank abandoned captures do not create junk notes.
- [ ] Support native suggestions/keyboard dictation, composition, multiline selection, paste, undo/redo and literal punctuation. Keep draft updates from resetting cursor or replacing newer composing text.
- [x] Support at least 100,000 characters, with bounded rendering/save work. Reject only explicit tested limits, retaining source content and never truncating silently.
- [ ] Apply theme/contrast, fixed reachable editor actions, measured keyboard/system insets, 48 dp targets, accessible labels and reduced motion.

**Exit checks:** rapid typing/navigation, background/rotation/process loss, failed save and retry, 100,000-character paste plus above-target input, Indic/CJK composition, mixed RTL, emoji and angle brackets; durable state and caret behavior stay correct.

## Phase 4 — Collection, organization and batch operations

- [x] Provide only Notes and Bin, bottom search/Add row and action bar above it. Options sheet contains Sort, Grid view, Protect Notes and Bin; Bin shows Empty. No folder, notebook, Inbox or checklist creation controls.
- [x] Implement per-note labels and background colors. Label deletion removes relationships only. Preserve legacy notebook data for backup compatibility without notebook UI.
- [ ] Add created/updated/title/manual sorting; preserve manual order when other sorts are active and keep pinned items above unpinned items for every sort. Persist view, sort, selection and browsing position across restart/upgrades.
- [ ] Long press without drag selects notes for pin/unpin, background color, labels and move-to-Bin. Bin selection offers restore/permanent delete. Preserve Undo and transactional failure behavior.
- [ ] Apply shared reach to collection/internal browsing/settings and preserve scroll/reach through selection toolbar changes. Hidden gestures must have accessible alternatives.

**Exit checks:** old notebook metadata, duplicate label names per chosen policy, empty groups, pinned sorting, cancelled reorder, repeated batch/undo, protected mixed selections, restart/upgrade preference restoration and keyboard/short-window layouts.

## Phase 5 — Inline checkboxes and compatibility

- [x] Support checkbox lines alongside paragraphs in the same note; first line remains the title.
- [x] Insert checkbox via editor options and toggle by touch or accessibility action; preserve surrounding prose and autosave/undo.
- [x] Remove standalone checklist creation, conversion, type filters and Completed-section UI. Open old checklist notes as mixed notes preserving states and existing prose.
- [x] Use one hold recognizer for card selection versus list/grid drag; persist only a successful reorder, animate neighbors, keep pinned groups separate and support accessible moves.
- [ ] Complete broad long-content, RTL/IME and accessibility acceptance on the mixed editor.

**Exit checks:** mixed text/checks, insert/toggle while autosaving, undo/reopen, legacy checklist import, 100,000-character body, selection versus drag, canceled drag, pinned ordering, off-screen dragging, process recovery and portable backup.

## Phase 6 — Collection search and Find in note

- [ ] Search title/body/labels/inline checkbox text, scoped to the current Notes or Bin view.
- [x] Define/test Unicode-aware matching, scripts without whitespace, normalization and optional accent-insensitive behavior. Preserve original text and highlight offsets correctly; do not reuse ASCII-only matching as fulfillment.
- [ ] Add a standard typing debounce, cancellation/versioning for obsolete queries and paged results. Committed changes update results without restart.
- [ ] Show relevant highlighted excerpts; opening a result navigates to its actual match. Find in note provides next/previous and counts without modifying text.
- [ ] Apply authentication to both queries and results; clear protected excerpts on re-lock and avoid unencrypted derivative indexes. Treat global launcher integration as separate from required Notes search.

**Exit checks:** punctuation/literal wildcards, emoji/combining marks, Indic/CJK/RTL, tag-only/checklist matches, no matches, rapidly changing queries/filters, edit/delete during search, lock mid-query and results beyond the first page. Measure the 10,000-note search target in phase 10.

## Phase 7 — Bin, undo and recovery

- [ ] Implement 7-day Bin with immediate undo, explicit restore, confirmed permanent deletion and confirmed Empty Bin. Remove Archive while keeping previously archived content accessible.
- [ ] Restore labels, inline checks, color, order and protection metadata; preserve legacy relationships for old archives.
- [ ] Make cleanup revision-safe and coordinate with active drafts, pending undo, import/export and protected storage. Deletion must not touch unrelated notes or references.
- [ ] Define retention based on deletion time, boundary behavior and clock changes; run cleanup safely after interruptions without depending on exact background execution.

**Exit checks:** retention boundary, repeated delete/undo/restore, removed notebook/tag, last item in a view, locked trash, process death during cleanup and failed permanent deletion. Confirm restore retains content and checklist states.

## Phase 8 — App-wide backup and safe restore

- [ ] Extend manual Comfer backup with an independently versioned Notes section covering content, notebooks/tags, order, preferences, dates, trash/protection state and recoverable drafts per the phase-0 contract.
- [x] Remove all Notes-specific export/import UI. Use the app-wide backup/restore route, with a password when Notes or Journal is protected.
- [ ] Authenticate protected export and require explicit inclusion; use established password-based authenticated encryption portable across installations. Test recovery independently of the original Keystore.
- [ ] Validate version, path safety, declared/actual sizes, checksums, IDs and relationships before mutation. Bound decompression/memory without imposing an arbitrary small per-run record cap.
- [ ] Preserve the compact app-wide restore summary and validate duplicate/conflict behavior. Keep both versions for unresolved conflicts; define repeat-import semantics. Missing Notes sections preserve live data; explicit empty replacement requires reviewed confirmation.
- [ ] Stage database/attachment changes and coordinate rollback with existing Comfer sections. Suspend conflicting edits; interruption preserves the old dataset or offers a clear resumable recovery.
- [ ] Use app-wide backup progress and failure reporting; no backup/export settings within Notes. Mark success only after the destination write completes and the archive is validated; handle revoked/unavailable document providers.

**Exit checks:** fresh second installation/device protected and unprotected round trips; wrong password/key loss; older/missing/empty sections; conflicting newer edits; malformed/truncated archives, traversal and oversized expansion; full storage; interrupted staging/commit/rollback; thousands of notes in one run. Existing Tasks, Journal and notification-settings backup regressions must pass.

## Phase 9 — Workspace and Android share integration

- [x] Replace only the Notes placeholder with its real destination and update its accessible name. Preserve both icon orders, existing folder animation, launch-after-close, Back cancellation and single-launch guard.
- [x] Wire Notes capture/collection navigation and return-home lifecycle; returning home leaves Workspace closed. Preserve Tasks/Journal/Search routes.
- [ ] Accept shared text/URLs into a distinct recoverable draft; validate incoming payloads, deduplicate repeated intent delivery and never silently append to an existing note. Handle locked Notes and existing failed drafts without losing either input.
- [ ] Update Workspace/Notes feature docs and development progress with actual behavior and evidence; remove coming-soon wording only when routing is implemented.

**Exit checks:** both layouts/RTL, rapid and repeated taps, Back during animation, launch/return, ordinary folders, cold/warm share intents, denied/cancelled authentication and process loss while accepting shared content. URL text is not executed or automatically published.

## Phase 10 — P0 acceptance and release preparation

- [ ] Execute every P0 scenario in Notes-Features sections 3–7 and 10–11; retain P1/P2 scenarios as deferred rather than silently treating them as passed.
- [ ] Verify airplane-mode capture/search, API 24 and representative modern Android, light/dark themes, short/tall/landscape/multi-window, system insets, 200% text, TalkBack, RTL and reduced motion.
- [ ] On a declared midrange device, measure p95 warm focused capture ≤500 ms and p95 search ≤300 ms over 10,000 representative text/checklist notes, stating debounce and authentication treatment. Record repetitions/dataset/build, cold starts and long-note editing separately; targets are not current results. Media benchmarks follow phase 11.
- [ ] Test upgrade/migrations, process interruption, low storage, fresh install/portable restore and all locked-content boundaries with synthetic data. Record gaps rather than claiming a completed security audit.
- [ ] Run meaningful unit/storage/UI/integration regressions for affected code and existing launcher/Tasks/Journal/notification backup paths. Fix failures before broadening test scope.
- [ ] Update emulator after implementation builds; update Samsung only on explicit request. Use isolated test packages/datasets for destructive checks and preserve normal user data.
- [ ] Obtain user/device acceptance and record release version/hash, mapping/recovery artifacts and known limitations. Production publication requires a separate explicit action.

**Exit:** all P0 data-integrity/privacy gates verified, measured performance and accessibility evidence recorded, no hidden deferred P0 requirements, and release acceptance distinguished from test-build delivery.

## Additional phases — P1 and P2

### Phase 11 — Managed photos (P1)

- [ ] System picker/camera, contextual permissions, private managed copies, bounded decode/copy, progress/retry and source-independent persistence.
- [ ] Stage replacement/deletion with crash recovery; apply protection to images, thumbnails and caches. Include media in backup/restore and Trash cleanup before shipping.
- [ ] Verify corrupt/large/revoked sources, low storage, process interruption, encryption, orphan cleanup and 10,000-entry representative-media performance.

### Phase 12 — Rich text and templates (P1)

- [ ] Add modest bold/italic/headings/bullets/numbering/links/checklists and keyboard-accessible formatting, cross-paragraph selection and formatting undo. Defer tables/nested blocks.
- [ ] Preserve plain-text notes through versioned migrations, search and lossless backup; test malicious/literal markup, mixed scripts and text-export fidelity.
- [ ] Offer Meeting notes, Project ideas and Reference information templates; support user edit/duplicate/delete. Applying a template must not overwrite existing writing.

### Phase 13 — Explicit Tasks links (P1)

- [ ] Create a Task linked by stable note ID only after explicit action; Tasks owns scheduling and reminders.
- [ ] Task completion/deletion leaves Notes intact; deleted Notes yield an unavailable link rather than deleting Tasks. Authenticate protected previews/deep links.
- [ ] Test restore/conflicting IDs across both modules, missing targets and repeated actions; no duplicate Tasks or reminder engine.

### Phase 14 — Capability-dependent enhancements (P2)

- [ ] Voice: explicit recording/stop/cancel/playback, interruptions/storage costs and optional transcription; preserve audio on transcription failure and never silently switch to network processing.
- [ ] OCR: explicit on-device extraction, supported scripts, editable output and original-image reference; no automatic replacement of writing.
- [ ] Sketches: basic pen/eraser and image export; no advanced handwriting/infinite canvas.
- [ ] PDF: readable pagination, dates/media, long text/RTL and protected export authentication.
- [ ] Give each subfeature its own capability, storage/backup/deletion, privacy, accessibility and physical-device gate before enabling it. Load models only on demand.

## Requirement coverage

| Notes-Features section | Implementation / acceptance phases |
|---|---|
| 1–2 Product scope and priorities | 0, 10; deferred 11–14 |
| 3 Capture and durable editing | 1, 3, 9 |
| 4 Organization and retrieval | 4, 6; privacy 2 |
| 5 Checklists | 5 |
| 6 Privacy, Trash and recovery | 0–2, 7–8 |
| 7 Backup and export | 8 |
| 8 Rich notes / Tasks links | 11–13 |
| 9 Capability enhancements | 14 |
| 10 Launcher and implementation constraints | 0–3, 6, 9–10 |
| 11 Acceptance scenarios | Each phase exit gate; final 10 and deferred-feature gates |

## Attempt and validation log

For every implementation/fix attempt record: date; phase/requirement; observed problem; chosen change and rationale; source commit or working-tree identity; exact commands/fixtures; device/API; pass/fail/skip counts; artifact/build; remaining risks; user/production outcome. Retain failed attempts so later work can assess what was tried and what worked. Keep private notes, credentials and real attachments out of logs/fixtures.

| Date | Phase / attempt | Outcome and evidence | Next action |
|---|---|---|---|
| 2026-09-17 | Planning / initial review | Read Notes-Features.md, current Workspace/reach contracts and existing plans. Confirmed Notes placeholder in MainActivity/WorkspaceModuleIcon and API-24 configuration. No app code changed, no tests run, no build installed; no full architecture/security audit claimed. | Complete phase-0 contracts and bounded source audit when implementation is authorized. |
| 2026-09-17 | Foundation | Debug Kotlin compiled; eight initial emulator storage/encryption tests passed. The initial test-build command omitted the repository build-type property; corrected before tests. Later UI/backup additions still undergoing validation. | Finish UI, expanded privacy/restore tests and full P0 gates. |
| 2026-09-17 | Emulator acceptance | 23/23 editor/storage/search/recovery/navigation checks; 4/4 focused follow-ups (includes repeats); 167/167 JVM tests. Default and 200% editor visual check passed; font setting restored. | Await intended Samsung connection/install instruction for physical acceptance; P1/P2 remain deferred. |

Implementation checkpoint: the P0 surfaces and integrations are available in code; **Implemented / validation pending** is not release approval. Unchecked items retain their outstanding edge-case or manual validation. Read `docs/notes-implementation.md` for the 1 MB record / 128 MiB archive budgets, in-memory collection loading, retention timing, off-screen drag limitation and test attempts. P1 formatting and compressed picker images were subsequently promoted by the 18 September editor request; templates, camera and remaining P1/P2 scope stay deferred.

Process-recovery follow-up: 14/14 storage checks passed, followed by a successful isolated app force-stop/new-process recovery of both the previous committed note and a pending durable draft. See the implementation log for commands, artifact hash and limitations.

17 September authentication follow-up: physical-device testing is deferred at the user’s request, not a current input blocker. The PIN-protected API-24 emulator passed individual-note protection, cancelled protection, cancelled unlock, incorrect-PIN rejection, successful reauthentication, background re-lock, device-lock re-lock, module protection and cold-process encrypted draft recovery. Synthetic plaintext was absent from database/WAL/SHM. These are manual UI checks, separate from the automated counts above. See the implementation log for scope and remaining checks.

17 September canvas refinement: use one borderless text editor whose first logical line becomes the title, styled larger than body text. Preserve existing stored title/body content; edit the two stored fields atomically for autosave and undo. Checklist headings and rows use matching borderless typography. Final emulator editor suite: 4/4 passed, including 100,000-character body, title/body persistence, undo/reopen and checklist conversion. Final visual spacing check passed; debug 51 / 51.0 installed on emulator, Samsung unchanged.

17 September collection simplification: collection-first launch, Notes title, minimal cards with label chips, autosave without editor check button, Archive removal, seven-day Bin, Journal-style module protection and app-wide-only backup/restore implemented. 29/29 emulator regressions passed (editor/collection, storage, backup and Workspace). Manual card/label inspection and automatic PIN/cancel/background checks passed. Debug emulator updated; physical acceptance remains deferred.

17 September two-view/mixed-content revision: removed notebook/Inbox/Pinned views and standalone checklist controls; added fixed bottom search/Add and action bar, per-note pin/color/labels, Notes/Bin options, mixed checkbox lines, and list/grid card dragging. Final eleven editor/interaction checks, 24 storage/backup/home checks and 167 JVM tests passed. Visual review corrected the options sheet so Bin and its retention subtitle are immediately visible. Debug emulator updated. Physical testing remains deferred.

## 18 September — label controls and deletion feedback

- [x] Wrap labels into chips with independent ring/circle selection controls; label names open edit/delete.
- [x] Add labels through a + icon chip in the same group.
- [x] Show “Deleted” / Undo only for moves to Bin, expiring after five seconds; other batch changes stay silent.
- [x] Validate independent selection/edit/create, deletion undo/expiry, and existing editor/interaction behavior; update emulator. All 13 emulator interaction/editor checks and 167 JVM tests passed; normal debug APK installed on emulator. Physical testing remains deferred.

## 18 September — card and search styling

- [x] Replace leading Pinned text with a trailing pin icon.
- [x] Switch the external + button to clear-search X on the first character; restore + when empty.
- [x] Use muted gray circular icon-button rings and a pill-shaped collection search field.
- [x] Build and install debug on emulator; visually verify pin/rings/search and manually check first-character filtering and clear/+ restoration.

- [x] Search spacing follow-up: muted gray focused/unfocused border and 8 dp bottom padding for the complete search/button row; debug build passed and emulator updated.

- [x] Label-outline follow-up: transparent label/+ chips with muted gray borders and “Search notes” placeholder. Debug build passed; emulator updated.

## 18 September — movement-only drag transitions

- [x] Match Tasks-style neighbor movement and settle Notes on release without drag color/opacity/size changes.
- [x] Run all nine Notes interaction emulator checks, including list/grid reorder, cancellation and hold selection; all passed.
- [x] Build/install debug on emulator and remove isolated test apps. Physical testing remains deferred.

## 18 September — live drag correction after device feedback

- [x] Reproduce unequal-height drag jump before the fix (144 px error).
- [x] Float the card above a full-size reserved slot; claim drag before list scrolling and reject stale layout targets.
- [x] Use opaque card fills consistently, with no color/alpha change while held.
- [x] Verify held destination gaps in list/grid, unequal heights, overlapping-card pixel consistency and cancellation; final 16 emulator interaction/editor checks and 167 JVM tests passed.
- [x] Install corrected debug build on emulator; remove isolated test apps. Samsung unchanged.

## 18 September — individual-note editor enhancement

User promoted the formatting toolbar and image picker from deferred P1 scope. Camera, templates, OCR, recordings and Tasks links remain deferred.

- [x] Fixed Back plus horizontally scrolling controls; remove editor three-dot/options sheet, preserve collection options.
- [x] Group Undo/Redo and numbered/circular bullets; expose labels and circular checklists directly.
- [x] Aa sheet: Title, Heading, Subheading, Body, Bold, Italic, Underline, Strikethrough, text colors and HTTP(S) links.
- [x] Store formatting ranges and copied/compressed photos inside existing encrypted note content; preserve old notes and app-wide backup integration.
- [x] Validate formatting edits/undo/reopen, native picker, circular checklists, image failures and backup/restore; visual review complete. 23 UI/interaction, 22 storage/backup and 176 JVM checks passed.
- [x] Build and install the normal debug APK on emulator; isolated test packages/fixture removed. Physical testing remains deferred.

## 18 September — cursor space below images

- [x] Replace the image-only tail with editable paragraphs; reserve 120 dp below the last image.
- [x] Persist image positions with encrypted content; retain existing text, formatting, undo and backup behavior.
- [x] Make older image notes editable underneath their images.
- [x] Verify boundary edits, multiple images, formatting, reopen and backup: 12 emulator tests and 178 JVM tests passed; debug emulator build updated.
