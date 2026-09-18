# Notes design and validation

17 September 2026. Implementation in progress; use [Plan-Notes.md](../Plan-Notes.md) for phase gates. English-only first test build approved; dates/RTL/Unicode remain included.

## Storage and protection contract

Notes owns `notes/` and a dedicated Room database in `noBackupFilesDir`; no destructive migration fallback. No Android automatic backup of device-bound ciphertext. Note and draft payloads, label names and preferences are encrypted before Room receives them, using Android Keystore AES-256-GCM, random provider-generated nonces and row identity/revision as authenticated additional data. Protected notes use a separate authentication-required key; unprotected notes use a device-bound key. Both keys remain outside the database. Public structural metadata is limited to opaque IDs, revisions, notebook membership, deletion timestamps and lock flags. This is not protection from a compromised/rooted OS.

Protected content has no persisted plaintext search index. Search will operate on authenticated, in-memory content, clearing it on re-lock. Do not log content or put it in saved-instance-state bundles. Module protection gates every repository operation; individual protection gates decryption. Re-lock immediately on background/device lock; foreground sessions expire before the Keystore's five-minute authentication window. Sensitive screens suppress screenshots/recents, not the whole launcher. Credential changes/key invalidation produce a recovery error, never key deletion or plaintext fallback. Portable password-encrypted backup is the recovery path; no password reset can recover an unknown export password or lost device key. Key recovery requires phase-8 tests before release.

Platform basis: [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [cryptography recommendations](https://developer.android.com/privacy-and-security/cryptography), [KeyGenParameterSpec](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder). These justify standard primitives, not a claim of independent security audit.

Autosave persists a versioned draft first, then atomically updates the note and the draft's base revision. Saved is shown only after that commit. Failed/stale commits retain the durable draft. Blank capture creates no note; clearing an existing note remains an explicit edit, not deletion. Stable UUIDs and optimistic revision checks prevent old sessions from replacing newer content. Dataset generation invalidates editors after restore. Copy recovery uses a new ID. Updates use an instant; manual order remains independent. Trash retention is 30 days from deletion, distinct from Archive. Batch undo checks post-operation revisions.

Collection defaults: list, updated descending, Inbox capture; other choices persist. Module tap opens capture or recovery, with a collection action in the editor. Pinned notes are separated. Created/updated date filters are explicit. Shared text becomes its own recoverable draft. Portable restore retains both conflicting versions unless the user explicitly reviews replacement; missing Notes sections preserve current data. Existing compact global backup/restore UI remains intact.

## Source audit

Tier-2 graph project `Volumes-JS-Repos-Comfer`, root `/Volumes/JS/Repos/Comfer`, recorded generation 2026-09-15T14:38:54Z. Backup restore trace identifies Settings and existing notification/Tasks/Journal tests, staged archive validation and restore-journal rollback. Coverage flagged changed Journal/MainActivity and untracked Workspace source; current source was read instead. No exhaustive architecture claim. JournalProtection is an access gate, explicitly not an at-rest cipher; Notes does not reuse it. Existing global restore journal is plaintext and must only contain locally encrypted Notes rows, never decoded Notes content.

## Attempts

- 17 September: created encrypted Room storage/draft foundation. Validation pending. Workspace now opens Notes in both layouts; four-module geometry and launch-after-close are preserved. No Samsung install.

## Current implementation and boundaries

Room schema 2 adds the encryption-key generation without rewriting existing ciphertext. Each ciphertext carries its key generation; confirmed replacement imports use fresh keys and retain old keys for rollback. Existing schema-1 rows remain readable. A regression deletes a synthetic generation's device key, restores a portable snapshot using replacement, and verifies readable content under a different key generation. This does not substitute for physical-device credential/enrollment-change testing.

Comfer archive format 5 adds `notes/content.bin` plus a versioned descriptor. Older archives without Notes preserve current Notes. Global restore merges Notes and keeps conflicting versions; the Notes import screen previews counts and offers explicit replacement, including empty replacement. Notes' interrupted-restore snapshot contains encrypted rows. Human-readable text export is explicitly unencrypted and supports recovering the current in-memory draft after a save error. Notes exports are bounded by 128 MiB of serialized data, not a record-count cap; 10,000 synthetic records round-trip in one archive. First-build persisted note/draft records are bounded to 1 MB including structure to stay below SQLite cursor-window limits; larger input remains in the editor for text export and is never truncated. The 100,000-character acceptance target remains within this budget.

Autosave, collection/list-grid selection, notebook/tag management, pin/archive/trash, batch operations with revision-safe Undo, checklist conversion/reordering, normalized Unicode search, in-note match navigation and credential gates are implemented. Recovery drafts can be selected from the collection. Shared text is persisted separately before changing editors; an existing failed in-memory draft is retained. Protected content is filtered out of stale result caches on re-lock. Search indexes are in memory only. The collection currently retains decoded visible records in memory; 10,000-note device memory/search profiling is still a release gate, not a claim of fully paged rendering/data loading.

Trash cleanup runs on module initialization, skips active recovery drafts and defers encrypted-item cleanup until authentication. Delayed execution may retain entries longer than 30 days; it never deletes them earlier. Manual reorder uses drag preview with animated neighbors and accessible move actions; cancellation leaves stored order intact. Off-screen drag autoscroll is not implemented; accessible moves remain available.

## Validation attempts (17 September)

1. Foundation: Kotlin build passed; eight initial storage tests passed on API 24. Corrected the Gradle invocation to include `-PcomferTestBuildType=notificationTest` for isolated instrumentation; JVM tests use the default debug invocation separately.
2. Expanded suite: storage/search/editor checks passed; backup test setup returned a non-void value, so that class did not run. Corrected test lifecycle signatures.
3. Twenty-case suite: nineteen passed. The editor test expected an empty text editor after the preceding test left a durable checklist; adjusted cleanup and explicitly opened a fresh capture. Product recovery was correct. Hardened Saved status for recovered uncommitted drafts and metadata edits.
4. Thirty-case integration suite: twenty-eight passed, including Notes portable archives, 10,000-record serialization, folder motion and existing Journal/notification backup regressions. Both Workspace return checks failed because the Notes keyboard remained open; the collection action now hides it.
5. Recovery suite: twenty of twenty-one passed, including both Workspace routes, real Keystore key-loss recovery and actual Comfer archive merge. The schema-fixture reader incorrectly assumed all exported tables have an indices array; corrected the fixture. No production data was used.
6. Final expanded verification is recorded below after completion. Physical credential/enrollment cancellation, second physical-device protected restore, TalkBack/large-text/keyboard comfort and declared midrange performance remain unverified. P1/P2 remain deferred behind the P0 acceptance gate.

Commands use `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`, offline Gradle builds, and isolated package `com.jeerovan.comfer.notificationtest` on `emulator-5554` (API 24). No Samsung install has been performed for Notes.

Final editor/recovery suite: **23/23 passed** on API 24, including real 100,000-character editor autosave, oversize-input preservation, non-destructive migration, synthetic device-key loss/replacement, Unicode matching, actual Comfer archive restore and both Workspace layouts. A further **4/4 focused checks passed** for fresh-entry authorization isolation, standalone encrypted export/unsafe import and both Workspace routes. The focused suite includes repeated cases, not four additional unique tests. The JVM suite passed **167/167**. The editor was visually checked at default and 200% system text size with the keyboard visible; essential editor/navigation controls remained available. Original font-scale setting restored. This is not full TalkBack or multi-window certification.

Test logs are under `validation-artifacts/device-checkpoint/2026-09-17-notes/`; only synthetic fixtures are used. Debug build remains version 51 / 51.0. Fresh entry clears any prior backup authorization; protected Notes require their own session. The final grid-scroll persistence refinement compiled; broader grid interaction acceptance remains open.

Final storage follow-up: all **14 storage tests passed**, including the regression that a protected recovery copy is protected on its first write. The explicit two-process fixture also passed: write a committed note plus a newer durable draft; `am force-stop` only the isolated test package; start a new instrumentation process, verify both versions, commit recovery and remove the fixture. The fixture is opt-in (`-e notesProcessFixture write/read`) so ordinary suites cannot accidentally run half of it. Logs: `final-storage.txt` and `process-recovery.txt`.

Emulator artifact: debug 51 / 51.0, SHA-256 `c0d787372de5994124082ab3f7b41b0480f6cfd9629b2ba0d0d3dbc8e487449b`. Installed with `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`; normal app data preserved. Physical acceptance was subsequently deferred by the user; see the emulator authentication follow-up below.


## 17 September — real emulator credential validation

User deferred physical-device testing and supplied the test emulator credential. Tested the unchanged API-24 debug-equivalent `notificationTest` build in the isolated `com.jeerovan.comfer.notificationtest` package; ordinary Comfer data and Samsung were not changed. Used `adb install -r`, `am start`, UI Automator hierarchy inspection and label-based taps through Android Settings credential confirmation, rather than bypassing authentication with `NotesSession.authorize()`.

Manual outcomes:
- Cancelled Protect note: existing synthetic note stayed unprotected.
- Correct device PIN: note saved and menu changed to Remove protection.
- Switching to Android Settings and returning: protected editor replaced by Notes is locked; synthetic content absent from the visible hierarchy.
- Cancelled unlock: remained locked. One deliberately incorrect PIN: stayed in Android credential confirmation. Correct PIN afterward: unchanged content and Saved state restored.
- Protect all Notes: required a new credential confirmation; after `am force-stop` and relaunch, module started locked. Correct PIN recovered the encrypted draft unchanged.
- Sleep/wake and device PIN unlock: Notes still required its own Unlock action.
- Read-only scan of the isolated notes.db, notes.db-wal and notes.db-shm: synthetic note marker absent. This is a bounded plaintext check, not an exhaustive security audit.

An initial Home-key background attempt did not move away from the launcher activity; it was not counted. Switching to Android Settings provided the actual background check. No credential was recorded in source, fixtures or validation logs. The disposable package was removed after testing. No application code changed, so prior automated results remain applicable; no automated suite was rerun for this documentation-only follow-up.

Still open: five-minute session expiry, credential/enrollment change and no-credential scenarios, protected export across installations, broader lock-during-save/search/export coverage, accessibility and measured performance. Physical/OEM and biometric checks are deferred, not passed. P1/P2 remain deferred behind P0 acceptance.


## 17 September — first-line title canvas

Replaced separate title/body outlined fields with one borderless BasicTextField. Its first logical line (even when visually wrapped) is rendered with headline typography and persisted as the title; remaining lines retain their original whitespace as body content. Existing notes combine their stored title and body without a database migration or background rewrite; body-only older notes retain a blank first line. A blank first line is permitted. Title/body edits share a single undo step and autosave transaction. IME composition and newly entered newlines remain in editor state while saves run; find navigation maps stored title/body offsets into the unified canvas. Checklist heading/item fields also lose their outlines while retaining checklist controls.

Four editor instrumentation tests passed initially: autosave/search/reopen, first-line title/body persistence plus undo/reopen, checklist conversion, and a 100,000-character body. Visual inspection found excess spacing caused by a paragraph-style boundary; removed it and use natural font line heights. Final rerun passed 4/4; final screenshot verified the corrected title/body spacing. Debug 51 / 51.0 installed successfully on the emulator. Physical tests remain deferred. This follow-up does not change protection or backup schemas.


## 17 September — collection, labels, Bin and module protection revision

This revision supersedes earlier capture-first, per-note protection, 30-day Trash, Archive and standalone-export UI descriptions above. Workspace launches the main collection titled Notes; no empty draft is created on entry. Pending drafts remain accessible through recovery. New note/checklist and Android shared-text entry still open the editor. Existing archived records remain intact and are included in normal queries; their legacy serialized flag remains compatible with old backups.

Cards show title/body and bottom label chips, without timestamps, star/delete controls. Tags are called labels in UI; serialized IDs/fields remain unchanged. Selection actions handle move-to-Bin, restore and confirmed permanent deletion. The editor keeps autosave status and collection/Back navigation, with no check/save button. Bin expiry is seven days from deletion; active drafts still prevent destructive cleanup until resolved.

Notes has a single Protect Notes switch. Entry and return from background automatically invoke Android device credentials; cancellation exits Notes like Journal. Protected storage remains encrypted. Older individually protected notes/drafts migrate transactionally to module protection, preserving content and clearing item-level flags so disabling module protection works consistently. Authentication is foreground-only: the expiry/resume path cannot launch a PIN prompt while Notes is behind another activity. The collection observer now propagates coroutine cancellation instead of showing it as a user-facing storage error.

All Notes export/import UI was removed. Existing Comfer Settings backup asks for a password if either Notes or Journal is protected; both encrypted sections use that password. Backup schemas and merge/rollback are unchanged. Old device-key loss remains a separate recovery limitation: app-wide merge cannot decrypt already-unreadable local data, so backed-up content can be recovered onto a fresh installation. No destructive reset or silent replacement is introduced. Error text no longer points to the removed Notes replacement screen.

Validation: `:app:assembleDebug` and isolated test builds passed. **29/29** instrumented checks passed on API 24: five editor/collection cases, sixteen storage cases, six backup cases and two Workspace layouts. Added exact seven-day boundary, legacy protection normalization/unprotect, module-protected empty backup requiring a password, and minimal-card/label/removed-controls checks. Test log: `validation-artifacts/device-checkpoint/2026-09-17-notes/collection-simplification.txt`. Existing standalone archive codec checks remain internal compatibility tests, not a Notes UI feature. The final foreground authentication guard was checked manually after the suite: enabling protection required PIN; Android Settings stayed foreground after leaving Notes; returning automatically prompted; cancellation returned to Settings. Synthetic card screenshot verified label placement and old archived content visibility. No full JVM rerun or physical-device acceptance is claimed for this follow-up. Final build installed on emulator with normal data retained; disposable test packages removed. Samsung unchanged.


Authentication follow-up attempt: the scripted Enter key initially returned Android RESULT_CANCELED, appearing to reject a valid PIN. Inspection showed Android's Cancel button, not the PIN field, had focus. Explicitly focusing the PIN field yielded RESULT_OK and opened the protected collection normally. The trace also exposed a real cancellation race: a pending resume coroutine could launch another prompt after finish(). Added finishing/destroyed and already-authenticating guards, retaining the foreground lifecycle guard. Temporary result-code diagnostics were removed. No PIN or note contents were written to diagnostic logs. This manual issue was outside the 29-case automated suite; cancellation was rechecked on the final isolated build.


## 17 September — Notes/Bin workspace and mixed checkboxes

This revision supersedes notebook/Inbox/Pinned views and standalone checklist UI. The collection has only Notes and Bin. The first line remains a large title; checkbox lines coexist with prose using portable `[ ]` / `[x]` markers, rendered as tappable checkbox symbols. Editor options offer Insert checkbox. Checked lines remain where written, with no Completed section. Touch and accessibility toggle actions preserve surrounding text, autosave and undo. Old checklist records open as mixed text with both existing prose and item states preserved. Legacy notebook/checklist fields remain decodable in stored payloads/old backups; there are no notebook/folder/type-conversion controls, creation routes or notebook filters. Global ordering no longer requires all notes to share a legacy notebook ID.

The fixed bottom row contains current-view search and Add; the action bar is immediately above, with the three-dot control at its end. The sheet opens fully to show wrapped Sort choices, 70% Grid/Protect Notes switches and Bin with a seven-day subtitle. Bin changes the header and offers confirmed Empty. Search results are scoped to the current view. Adding from Bin starts a normal note in Notes. The collection does not auto-focus its search field or open the keyboard on entry.

One card gesture recognizer distinguishes tap, hold/release and hold/drag. Holding without a drag selects; dragging clears selection and animates neighbor placement. The 18 September motion refinement preserves card size/color/opacity, removes drag shadows and fades, and adds spring settling after release. Successful drop adopts manual sorting; pinned/unpinned boundaries remain fixed and pinned notes lead every sort. Canceled drag does not persist or select. Both list and adaptive-grid layouts use the same code, including accessible move actions and viewport-edge scrolling. Selection offers pin/unpin, background palette, per-note labels and Bin deletion; Bin selection offers restore/permanent deletion. Color is an additive serialized field with a default for old payloads and participates in app-wide backup.

Validation attempts:
1. Initial nine-case UI run: two passed, seven failed because test tags were inside the new merged gesture node; the visible cards were present. Moved tags to the full interactive surface.
2. Second run: eight of nine passed. The first list test tried to locate its second card before it was available. Tightened visible-target waits and explicitly dismissed the entry keyboard; collection focus handling now also prevents unwanted initial keyboard display.
3. Expanded run: **11/11 passed**, covering list/grid reorder, no selection during drag, canceled drag, pin/color/label persistence (including color archive round-trip), current-view search and row placement, legacy checklist conversion, actual checkbox tap/insertion, undo/reopen and a 100,000-character body.
4. Storage/backup/home suite: **24/24 passed**. JVM suite: **167/167 passed**. Existing Comfer archive round-trip/conflict checks, protected password enforcement, seven-day retention, legacy protection, both Workspace layouts and 10,000-record archives remain covered.
5. Visual review caught Bin falling below the default half-expanded sheet. Set the options sheet to skip partial expansion; removed empty body/label spacing from title-only cards and rendered checkbox symbols in card previews. Added an explicit visible-Bin/subtitle assertion for the final interaction rerun.

Physical, broad TalkBack/RTL/IME and off-screen drag stress/performance acceptance remain deferred/open. Edge scrolling is implemented but the automated drag checks cover visible targets and cancellation, not exhaustive auto-scroll cases. Ordinary app data and Samsung remain untouched by isolated fixtures; final emulator artifact/cleanup is recorded in the phase plan.

Final interaction rerun: **11/11 passed**, including immediate visibility of Bin and its retention subtitle after the full-sheet refinement. Logs are under `validation-artifacts/device-checkpoint/2026-09-17-notes/two-view-*`. Debug 51 / 51.0 installed on emulator with normal data preserved; isolated test app/runner removed. Samsung unchanged.

## 18 September — compact labels and deletion-only Undo

Labels now wrap as chips. Each has an independent 48 dp ring/circle checkbox for assignment and a text target for rename/delete; the + icon chip opens creation. Existing label editor and non-destructive label removal remain in use. Batch feedback is now restricted to live-to-Bin transitions, retaining revision-safe Undo for five seconds and displaying only “Deleted”. Pin, color, label and restore changes create no Undo banner.

Validation: debug and isolated test APK builds succeeded; all 167 JVM tests and 13 API-24 emulator interaction/editor tests passed on the first run. Added checks cover checkbox on/off, tapping a label to rename without assignment, + creation, silent batch feedback, restoring a deleted note with Undo, and expiry. Existing drag, search and editor checks also passed. The regressions were first executed with the implementation; a failing pre-change run was not performed. Evidence: `validation-artifacts/device-checkpoint/2026-09-18-notes/labels-interaction.txt`. Physical testing remains deferred.

## 18 September — pin/search visual refinement

Pinned cards use an accessible trailing pin icon in the title row. Notes icon buttons share a 40 dp muted gray circular outline inside the standard 48 dp touch target. Collection search has semicircular ends; the adjacent + becomes X for any non-empty query and clears it without creating a note. Both Notes and Bin use this row. Debug build succeeded and was installed on the API-24 emulator. Visual inspection confirmed pin placement, rings and pill border; manual first-character search and clear restored the collection and + button. No new automated tests were added or run for this localized visual refinement. Physical-device checks remain deferred.

Search spacing follow-up (18 September): focused and unfocused collection-search borders now use the same gray at 55% opacity as the icon rings. The full search/button row has 8 dp bottom padding inside the existing safe-area/keyboard insets. Debug compilation and diff whitespace checks passed; emulator APK updated. No automated tests added for these styling-only changes.

Label-outline follow-up (18 September): label assignment chips and the + chip now use transparent backgrounds, theme-aware text, and 1 dp gray borders at 55% opacity. Collection placeholder changed to “Search notes”; Bin retains its scoped placeholder. Debug build and whitespace checks passed; no new automated tests for this styling-only change.

## 18 September — movement-only note reorder

Compared the Tasks neighbor-preview/translation behavior with Notes. Notes now disables placement animation on the actively held card so its translation follows the finger directly, animates neighbors with a spring, and springs the released card into its slot. Removed the 1.025 scale, 8 dp drag shadow and default item fades; no drag-dependent color or alpha is applied. Existing pinned-group, persistence, cancellation and selection rules remain. Debug and instrumentation builds passed; all nine NotesInteractionTest cases passed on the API-24 emulator, including list/grid drag, canceled drag and hold selection. These checks do not establish frame-by-frame visual fidelity or exhaustive edge-scroll behavior. Log: `validation-artifacts/device-checkpoint/2026-09-18-notes/drag-interaction.txt`.

## 18 September — live drag preview correction

The earlier movement-only change was insufficient. The prior nine-case suite checked final order, not the held preview. A new unequal-height regression failed before this fix: the note center jumped from the expected 680 px to 536 px after moving past a shorter neighbor. Default/colored card fills also retained 50%/90% alpha, allowing underlying cards to bleed through even without a drag-specific fade.

Replaced per-item translated gesture handling with a stationary parent recognizer, a floating card and a full-height destination placeholder in the lazy grid. Neighbors animate into their new positions while the finger remains down; the floating card settles into the reserved slot on release. Held movement is consumed in the initial pointer pass before lazy/nested thumb-reach scrolling can intercept it. Pin-group ordering, hold-only selection and cancellation are preserved. Cards now use consistently opaque theme/palette backgrounds at rest and while dragging; no drag-only tint, opacity or scale is introduced.

Attempts: equal-height preview passed before the fix; unequal-height preview failed as above. First replacement run passed 12/15, exposing competing lazy scrolling and lost held movement. Initial-pass consumption fixed the movement failures (14/15); a pixel comparison differed during a full run but passed in the focused run; baseline capture now waits for native window transitions to settle. The three focused list/grid/opacity checks subsequently passed, and screenshots were inspected. Final suite results and emulator installation are tracked in the plan. Edge-scroll stress, broad accessibility and physical-device acceptance remain open.

The expanded 16-case run then caught an intermittent rapid-drag reorder failure (15/16): pointer events could reorder against stale layout indices before the previous preview move was measured. Added an explicit layout/preview-index guard before choosing another destination. Final revalidation is recorded below.

With the layout guard, all twelve interaction checks passed. The broader run exposed an unrelated editor-test timing issue: it asserted immediately after asynchronous reopen; the test now waits for the editor to appear, as the other editor tests do. No editor behavior was changed.

Final verification: **16/16** emulator interaction/editor tests passed, plus **167/167** JVM tests. Debug build and whitespace checks passed. Inspected held list/grid and overlap screenshots. This specifically verifies full-height reservation, neighbor displacement while held, unequal-height finger alignment and identical background pixels while overlapping a blue card. Final log: `validation-artifacts/device-checkpoint/2026-09-18-notes/drag-slot-final.txt`.

Follow-up verification: the emulator-installed APK SHA-256 exactly matched the previously delivered build (`a38152d10b128c8f5c923181db28ff1572bfe5198564b41297aa4282a52e7eb8`). The user confirmed the apparent missing gap was an attempted pinned/unpinned move and that dragging works correctly. Preserved the existing pin-group boundary. An experimental nearest-slot targeting change made during investigation was discarded; no new behavior was shipped.

## 18 September — editor toolbar, formatting and images

The individual-note editor now has fixed Back and a horizontally scrolling toolbar: grouped Undo/Redo, labels, circular checklists, Aa formatting, grouped numbered/bulleted lists and image picker. Its three-dot collection/options sheet is removed; the main collection keeps its own options. Aa supports paragraph presets, inline bold/italic/underline/strike, named text colors and HTTP(S) links. Empty-caret formatting applies to subsequent typing; selected formatting spans survive edits, autosave, undo/redo, reopen and backup. List-prefix edits rebase each line separately to preserve existing styled words; Enter continues lists and exits empty items. Literal text remains literal; no HTML/WebView execution.

`NoteContent` has additive defaulted `marks` and `images` fields; old text payloads still decode without a database schema change. Spans use Compose UTF-16 selection offsets and are range/kind validated. Selected images are copied into the encrypted note payload, not a plaintext file or disk image cache. The importer bounds input to 20 MB/100 megapixels, samples to at most 1440 px, corrects EXIF orientation, flattens transparency to white and recompresses to JPEG at most 250 KB. The 900 KB content guard leaves space for row/draft metadata within the existing 1 MB record cap. There is no source-URI dependency after import. Images render lazily below text and can be previewed/removed; undo restores them. Existing encrypted drafts, protection, Bin lifecycle and app-wide archive copying carry image bytes and styles together. Interrupted/failed imports leave existing saved content intact; imports not yet committed remain subject to the existing failed-save/process-death limits. Camera, templates and full-resolution originals remain deferred.

Coverage: graph project/root verified with recorded generation 2026-09-18T03:30:13Z; coverage reported no recorded gaps for the seven existing Notes evidence files. Current source reads resolved implementation details and all post-edit claims; new files were not claimed graph-indexed.

Validation attempts:
- Initial build and eight pure-formatting tests passed. First 21-case emulator run passed 20: a toolbar test incorrectly treated vertical IME accommodation as movement of the fixed Back button. Corrected it to assert horizontal anchoring.
- Expanded 45-case run passed 44, including all 22 storage/backup checks and new image/formatting checks. Stabilized the old coordinate-based checklist tap test by closing the keyboard before measuring its glyph.
- Visual inspection prompted explicit composition observation of formatting marks so toolbar-only changes redraw immediately; added assertions against rendered bold/color spans. Added a ninth unit test preserving styles through multiline list insertion.
- Final **23/23 UI/interaction tests**, **22/22 storage/backup tests**, and **176/176 JVM tests** passed. Checks include formatting undo/redo/reopen, safe URL validation, circular checklist taps, list commands, fixed Back during horizontal toolbar scrolling, corrupt-image recovery, image undo/redo, no plaintext image data in the encrypted row, and an actual Comfer backup/restore containing styled text and an image after deleting its original source.
- Reviewed toolbar/formatting/styled-text/image screenshots. Native Android GetContent picker round trip passed with a synthetic image (selection completed via the picker's keyboard activation path). System-picker lifecycle returned to a saved note. Temporary fixture and isolated packages were removed after validation.

Evidence is retained under `validation-artifacts/device-checkpoint/2026-09-18-notes/editor-*`. Protected-picker reauthentication, broad TalkBack/RTL/IME, low-storage interruption and physical-device/media performance remain acceptance gates; no new physical-device acceptance is claimed.

### Image cursor space — 18 September 2026

Cause: images were appended after a single BasicTextField, with no editor beneath them. Extra padding alone would not allow typing there. The canvas now renders text segments around persisted UTF-16 image offsets, with a minimum 120 dp final writing area. Selection and marks still use the shared text offsets, so toolbar actions and search operate on the same stored content. Image offsets rebase during edits; removing an image rejoins its surrounding text. Image paragraphs keep separating newlines when preceding text is erased. Existing image payloads without offsets gain trailing paragraphs when opened; new fields have backward-compatible defaults. No Room schema change or separate plaintext media storage is introduced.

Validation includes typing/formatting below an image, reopening, undoing image removal, actual app backup/restore, legacy images, text between two images, and erasing text above images. Physical-device checks remain deferred.

The initial expanded regression run caught an IME callback using pre-replacement text bounds. The callback now resolves current image boundaries and ignores stale selection-only events; focus changes no longer write old selection state back into the canvas. The existing title/body replacement-and-undo test covers this recovery path. A legacy multi-image test was corrected to scroll the lazy list to index 0 before accessing an uncomposed first editor.

Final validation: all 12 affected emulator tests and 178 JVM tests passed; debug APK installed on the emulator. The rendered image-and-text layout was visually inspected. Isolated test apps were removed.
