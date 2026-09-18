# Notes: Features and UX

Prepared 14 September 2026 for the Comfer Android launcher.

Status: implementation requirements, tracked in [Plan-Notes.md](Plan-Notes.md). An English-only P0 test build is in development; this document is not a release-acceptance claim. Priorities below are product recommendations informed by the bounded review study in the appendix.

## 1. Product direction and scope

Build a dedicated lightweight Notes space for reusable information, ideas, and checklists, separate from Tasks. A user should be able to capture a thought from the launcher immediately and retrieve it months later without remembering which folder contains it.

Default to everyday notes and checklists. Notes has no dated reflection feed, mood tracking, prompts, or streaks.

Planning assumption: follow Comfer Tasks' local-first approach. Core use requires no account or connectivity; cloud sync, collaboration, companion clients, automatic cloud backup, and remote AI processing are outside the initial scope. This is a recommended scope choice, not an explicit new user prohibition on future sync. Manual portable backup is a release requirement because local storage alone does not protect against loss of the phone.

Confirmed future placement (15 September): Notes gets its own icon in the [four-module home menu](docs/home-module-menu.md), alongside Journals, Tasks and Search. The current Workspaces center control expands like a home app folder, with the layout-specific positions in that contract. Notes replaces its coming-soon destination; do not add a home content panel.

## 2. Priorities

| Priority | Deliverable | Release condition |
|---|---|---|
| P0: initial release | Quick text/checklist capture, durable drafts, organization, full-text and in-note search, privacy, trash, manual backup, accessibility | All data-integrity and privacy acceptance checks pass |
| P1: next iteration | Photos, modest rich text, templates, Tasks links | Core capture/search remain responsive; no migration regressions |
| P2: later | Voice recording/transcription, on-device OCR, sketches, readable PDF export | Device/language capability and storage costs are explicit |
| Outside initial scope | Cloud sync, public publishing, team features, AI rewriting, medical interpretation, infinite-canvas knowledge graphs | Separate product decision required |

Priority is not permission to omit integrity, privacy, or accessibility for a feature that ships. Each attachment type must participate in backup and deletion before release.

## 3. P0 — Capture and durable editing

- Launching Notes opens the main collection titled Notes, with all non-deleted notes. New note opens the editor with text focus; pending drafts remain available through recovery. Use one borderless writing canvas: the first logical line automatically becomes the title and uses a larger font; subsequent lines are body text. No separate title/body input boxes. An empty first line is allowed; derive a preview from the first nonempty line. Do not block capture with a notebook or note-type selection dialog.
- Checkboxes are inline content within any note, alongside ordinary paragraphs. Provide Insert checkbox in the editor options; no separate checklist note type, conversion flow, or checklist launch icon.
- Accept shared text and URLs as a recoverable draft. Never silently append to an arbitrary existing note or publish received content.
- Use autosave for Notes, explicitly distinct from Tasks' Save/Cancel contract. Show Saving, Saved, or Couldn't save; display Saved only after durable local commit. Avoid a snackbar for every save. No save/check button in the note editor; Back and the collection action flush pending edits.
- Persist a recoverable working draft during editing. Flush on navigation/backgrounding where possible; on restart restore the last durable state and disclose any pending recovery. Do not promise preservation of keystrokes that never reached storage.
- Preserve cursor, selection, scroll, and keyboard context when practical. Background processing must not move the cursor or overwrite newer text.
- Retain unsaved text on storage failure and offer retry/copy. Opening another note must not discard a failed draft.
- Support normal keyboard suggestions, dictation through the keyboard, selection across paragraphs, paste, undo/redo, and literal punctuation including angle brackets. Plain text must never be interpreted as executable markup.
- Empty abandoned drafts need not become notes. A draft with content must not disappear merely because its title is blank.
- Support at least 100,000 characters per text note as an initial acceptance target. Never silently truncate larger input; explain any tested limit before refusing an operation and retain the source draft.

## 4. P0 — Organization and retrieval

- Provide only Notes and Bin. Pinned notes always appear first in Notes; no separate Pinned, notebook, Inbox or Archive views. Previously archived and notebook-organized notes remain visible in Notes.
- Present labels in a wrapping bottom sheet with transparent chip backgrounds and muted gray borders, including the + chip: a ring/circle checkbox selects or deselects each label independently; tapping its name opens rename/delete. A + icon chip alongside the labels creates a label. Support label creation, rename, deletion and per-note assignment. Deleting a label removes its relationships without deleting notes. Notebook fields are retained only for decoding older data/backups.
- Fix the search row to the bottom with Add note at its end. Place the action bar immediately above it, with the three-dot button at the end. The action bar opens a bottom sheet with Sort, Grid view, Protect Notes and Bin with its seven-day retention subtitle.
- Bin replaces the top title with Bin and shows an Empty action with permanent-deletion confirmation. Android Back or the sheet's Notes row returns to Notes.
- Hold a card and release without dragging to select it and reveal Pin, Color, Labels and Delete in the action bar. Hold and drag instead reorders with Tasks-style animated neighbor movement and spring settling on drop; use a floating card over a full-size reserved destination slot, with neighbors animating aside; keep cards opaque at rest and during dragging, preserving their background color and size with no drag tint, shadow or fade; cancelling does not save order or select the card. Support both list and grid, with accessible move actions.
- Note cards contain title/content and labels as chips at the bottom; pinned cards show a small pin icon at the trailing edge of the title row instead of a leading “Pinned” label. No date/time, star or delete buttons. Deletion remains available through selection actions.
- Provide compact list and grid views, with list as the recommended default. Preserve the user's view, sort and browsing position; a fresh launch starts with all notes.
- Sort by updated time, created time, title, and manual order across Notes. Keep manual order stored when another sort is selected. Pinned items stay above unpinned items for every sort. Dragging adopts manual order and cannot cross the pinned/unpinned boundary.
- Provide pin/unpin, background color, label, and move-to-Bin actions. Only moving notes to Bin shows “Deleted” with Undo for five seconds; pin, color, label and restore actions show no update/Undo banner. Moving content should retain the user's current browsing context.
- Search title, body, labels, and checklist text. The same search input searches only the current view: Notes or Bin. No notebook/type/pinned-only filters.
- Search results show a relevant excerpt with highlighted matches. Opening a result jumps to the match; Find in note offers next/previous and a match count. Updating a note updates search results without a restart.
- Use Unicode-aware matching and script-appropriate behavior; do not assume every language separates words with spaces. Preserve original text and distinguish literal matching from optional accent-insensitive search.
- Locked content must not appear in global launcher search, previews, excerpts, or recommendations until authenticated under its privacy policy.

## 5. P0 — Inline checkboxes

- A note can mix paragraphs and checkbox lines in any order. The first logical line remains the title.
- Insert checkbox adds a checkbox at the current body line (or a new body line when the title is selected). Tap the checkbox to toggle it without changing surrounding prose.
- Preserve checked state through autosave, undo/redo, reopen, search and backup. Provide accessible toggle actions.
- Preserve old standalone checklist notes by opening them as regular mixed notes, including both checked and unchecked items; no separate creation/conversion controls remain.
- Do not move completed lines into a separate card or hide them. There is no Completed section, list-type filter or checklist creation icon.

## 6. P0 — Privacy, Bin, and recovery

- Provide optional Notes access protection using system biometric or device-credential authentication. Use one module-level Protect Notes switch, matching Journal: request device credentials when entering protected Notes and after backgrounding; cancellation returns to the previous screen. No individual-note protection control. Migrate older item locks to module protection without exposing content.
- Authentication for protected content must cover editor access, deep links, search, thumbnails, shared/exported data, and background previews. Re-lock on device lock; make the background timeout explicit.
- Protect stored private content with an established encryption design backed by Android Keystore, including attachments and derived indexes where applicable. A cosmetic lock overlay is not encryption. Do not claim protection from a rooted/compromised OS.
- Keep protected-content indexes unavailable while locked; either encrypt their index or search them in memory after unlock. Avoid plaintext derivative caches and content in logs/crash reports.
- Hide protected text from notifications and recent-app thumbnails. Limit screenshot restrictions to sensitive surfaces; do not blanket-disable screenshots throughout the launcher.
- Use a 7-day Bin retention policy with restore and immediate undo. Permanent deletion and Empty Bin require confirmation. Restore preserves labels, background color, inline checkbox states, and attachments.
- Explain key-loss, device-reset, and uninstall consequences. Do not offer a password reset that silently destroys data. Define and test key recovery before shipping protected backups.

## 7. P0 — App-wide backup and restore

- Extend Comfer's manual backup flow with versioned Notes data: content, labels, background colors, ordering, preferences, dates, Bin state and legacy metadata, and attachments for shipped media features.
- No export/import controls within Notes. Use Comfer Settings backup/restore exclusively, like Journal; include Notes content, drafts and protection preferences.
- App-wide backup requests a password when Notes or Journal is protected, and authenticates protected content before reading it. For portable encrypted backups, use an established password-based authenticated encryption format; do not rely solely on a device-bound key that cannot decrypt on another phone.
- Use the existing app-wide backup status and compact restore summary (Comfer version and date-time). A backup is not successful until the archive is fully written and validated.
- Validate archive version, paths, sizes, checksums, and content before changing live data. Reject unsafe paths, malformed archives, and oversized expansion with a clear error.
- Preserve the app-wide restore summary; handle duplicate/conflict behavior safely. Keep both versions on unresolved conflicts rather than silently replacing newer edits.
- A missing Notes section in an older Comfer backup preserves current notes. An explicitly empty section may clear data only through the reviewed replacement flow.
- Restore transactionally or through staging with rollback across database and attachment stores. Interruption must leave the old dataset usable or allow a clear resumable recovery.
- Do not hard-code a small per-run import cap. Display progress and support thousands of entries without repeated manual restarts.
- Automatic cloud backup stays excluded under the initial local-first scope. User-selected export destinations may themselves be cloud-backed; make that distinction clear.

## 8. P1 — Richer notes and task links

- Add bold, italic, headings, bullets, numbering, links, and checklists through a compact, keyboard-accessible formatting bar. Support cross-paragraph selection and undo of formatting. Defer tables and nested blocks until justified.
- Add photos through the system picker/camera with contextual permission requests. Copy selected media into managed storage; later deletion of the source image must not break a saved note. Show progress and recoverable attachment errors.
- Provide a few optional templates: Meeting notes, Project ideas, and Reference information. Users can edit, duplicate, and delete their own templates; applying one must not overwrite existing writing.
- For a note-specific action, create a Task linked by stable note ID after explicit user action. Notes stay intact when a Task completes. Notes deletion leaves a clearly unavailable link rather than cascading task deletion. Protect link previews for locked notes.
- Tasks owns all optional reminders for linked actions. Do not add a separate note-reminder scheduler or duplicate alerts. This is a product integration requirement, not a verified code architecture claim.

## 9. P2 — Capability-dependent enhancements

- Voice notes: tap to record, explicit stop/cancel, playback, interruption handling, storage-size visibility, and optional transcription. Preserve audio if transcription fails. Clearly distinguish on-device recognition from a network-dependent engine; no silent upload of private audio.
- On-device OCR: explicit extract-text action, supported-script indication, editable results, and reference to the source image. Treat recognized text as suggestions, never an authoritative replacement.
- Sketches: basic pen/eraser and image export; defer advanced handwriting recognition and infinite canvas.
- PDF export: readable pagination with dates and media; validate long text and RTL rendering before release.

## 10. Launcher UX and implementation constraints

- Follow Comfer's existing colors, typography, navigation, wallpaper treatment, and reduced-motion behavior. Keep Notes-specific editing choices explicit rather than copying Tasks' Save semantics.
- Use the current shared thumb-reach specification in docs/thumb-reach.md during implementation. Do not assume a fixed distance represents every user's physical reach. Do not reserve blank reach padding above a focused editor when the keyboard needs the space.
- Place frequent editor actions within reach and above measured keyboard/system insets. Support 48 dp touch targets, TalkBack labels, multiline controls, high contrast, and large text. Preserve Android Back and accessible alternatives to gestures.
- Support RTL and mixed-direction text, Unicode/emoji, localized dates, and font fallback. Never infer the language of the note from the language of the app UI.
- Store content transactionally with stable identifiers and explicit schema migrations; coordinate external attachments with crash recovery. Conflict/version checks prevent stale editor instances and background jobs from overwriting newer data.
- Core writing/search must work in airplane mode. Loading Notes should not delay home interaction or initialize media/OCR models until needed.
- Initial performance targets, to be measured on a declared midrange reference Android device: warm quick-capture editor ready within 500 ms at p95; search results within 300 ms at p95 over 10,000 representative notes. These are proposed budgets, not measured results. Record cold-start and long-note editing behavior separately.

## 11. Acceptance and release gates

| Scenario | Required observable outcome |
|---|---|
| Process death after Saved, reboot, app upgrade | Durable content and organization survive; migrations retain counts and relationships |
| Death during autosave or full storage | Last durable version remains valid; no false Saved indicator; recoverable draft/error when possible |
| Long note, large paste, multi-paragraph formatting, undo | No silent truncation, selection loss, or content corruption |
| Keyboard composition in Indic/CJK scripts, RTL, emoji, angle brackets | Input remains literal and correct; composing text is not duplicated or discarded |
| Search from results and within an entry | Correct highlighted match; next/previous works; stale results update |
| Checklist completion/hide/show/reopen and cancelled reorder | Completed items remain recoverable; cancellation makes no data change |
| Lock, background, restart, deep link, global search, export | Protected content is unavailable without required authentication and absent from previews/logs |
| Trash, restore, permanent delete, notebook removal | No unrelated data lost; restoration and attachment cleanup follow policy |
| Backup onto a second test device; corrupt, old, empty, interrupted archive | Successful round-trip or safe rollback; password/key recovery works as documented |
| 10,000 entries with representative media | Search/capture budgets measured; restore finishes without manual batch restarts |
| Linked task completed/deleted or source note removed | No cascading content loss; unavailable links are clear; no duplicate reminder |
| Low-end phone, keyboard, 200% text, TalkBack, short/tall windows | Main actions remain visible and operable; no clipped essential content |

No application implementation, hands-on competitor test, security audit, or device performance test was performed for this document. The checks above define future acceptance. Do not promote a phase until its data-loss and privacy tests pass.

## Appendix A — Market sample and method

Research date: 14 September 2026. This Notes-focused subset covers three established Google Play apps for quick notes and organized collections. Download scale and substantial review volume support their inclusion as leading benchmarks; this is not a verified current Play Store chart ranking or an exhaustive top-app list.

The study read the three user reviews exposed on each app's public English listing: nine displayed reviews in this subset. No login, full review export, star-stratified random sampling, or competitor installation was performed. Store selection, locale, device filters, and search-engine cache can bias the sample. Older reviews may describe resolved issues. Ratings below use listing-header figures, which can differ from the phone-only review section; they are retrieved snapshots, not synchronized real-time measurements. ColorNote's page was cached approximately three months earlier; the other retrieved listings were marked more recently crawled.

| App and source | Rating / header review count / installs as displayed | Benchmark role |
|---|---|---|
| [Google Keep](https://play.google.com/store/apps/details?id=com.google.android.keep) | 4.7 / 2.57M / 1B+ | Fast capture and retrieval |
| [ColorNote](https://play.google.com/store/apps/details?gl=US&hl=en&id=com.socialnmobile.dictapps.notepad.color.note) | 4.9 / 3.95M / 100M+ | Simple notes, lists, home access |
| [Microsoft OneNote](https://play.google.com/store/apps/details?id=com.microsoft.office.onenote) | 4.6 / 1.5M / 1B+ | Larger organized collections |

Install counts can include preinstallation and do not measure satisfaction or active use. Developer descriptions establish advertised positioning; user reports below are anecdotal evidence, not verified defects or population-wide sentiment.

## Appendix B — Review evidence and resulting decisions

### Google Keep

Sam Damti (18 Dec 2024) praised simplicity/search while criticizing capture steps and note-length limits. Rebecca (12 Jun 2026) disliked narrower cards and rearranged organization. Cole Cieslewicz (27 Apr 2026) reported keyboard autocorrect trouble. [Displayed Play reviews](https://play.google.com/store/apps/details?id=com.google.android.keep).

Design inference: preserve user layout, open directly into writing, test long input and real keyboards, and prioritize retrieval. These reports do not establish current failure rates.

### ColorNote

Medina Green (4 May 2026) valued home widgets, individual locks, and long-term use. Raeann Adams (27 Feb 2026) favored simplicity over extra features. E St (9 Apr 2026) liked basic organization but found checked items disappearing confusing. [Displayed Play reviews](https://play.google.com/store/apps/details?gl=US&hl=en&id=com.socialnmobile.dictapps.notepad.color.note).

Design inference: offer fast launcher access and a visible Completed section; keep advanced tools optional.

### Microsoft OneNote

Joy Hoskins (2 Jul 2025) wanted control over the opening view and less disruptive page moves. Tim Holt (29 May 2025) reported stale content and restart-dependent behavior. Michael Wise (21 Feb 2025) reported missing notes and organization frustrations. These loss/sync accounts were not independently reproduced. [Displayed Play reviews](https://play.google.com/store/apps/details?id=com.microsoft.office.onenote).

Design inference: local durable writes, context-preserving batch operations, and independently restorable backups deserve release priority. Local-only storage still requires recovery planning.

## Appendix C — Evidence synthesis

In this bounded notes-app sample, quick capture and simple organization are positive signals. Reported friction concerns layout changes, keyboard behavior, checklist visibility, and reliable access to saved information. These anecdotes guide testing and design; they do not establish failure rates.

The proposed response is a dependable Notes core with explicit recovery/privacy requirements. Rich media follows after the core works. Feature priorities, acceptance targets, data design, and Comfer integration choices above are recommendations rather than claims about competitor behavior.

Notes visual refinement (18 September): use a pill-shaped collection search border, muted gray both focused and unfocused to match the circular icon-button outlines. Keep 8 dp bottom spacing below the entire search/Add-or-clear row, above system or keyboard insets. With an empty query, the adjacent button is + (new note); from the first character onward it becomes X (clear search), outside the input. Clearing restores + immediately. This applies to Notes and Bin search.

The Notes collection search placeholder is “Search notes”.

## Individual-note editor — 18 September refinement

The editor has no three-dot menu or collection-settings sheet. Back stays fixed at the bottom; all other controls scroll horizontally and retain muted gray ring outlines. Undo/Redo share a group; direct controls manage labels, insert circular checklists, open Aa formatting, insert grouped numbered/bulleted lists, and pick an image. The Aa sheet provides Title, Heading, Subheading, Body, Bold, Italic, Underline, Strikethrough, text colors and URL editing. Formatting applies to the selected text (paragraph choices cover its lines), or subsequent typing at a collapsed caret. Links accept HTTP(S) URLs and open only through an explicit Open link action. Checklist markers display an empty ring or filled circle; storage remains compatible with existing mixed text/checklist notes. Enter continues a list; Enter on an empty list item ends it.

Selected images are copied, orientation-corrected and compressed into encrypted note content; they appear below the text, with tap-to-preview/removal. The first image build uses bounded previews up to 1440 pixels per side and 250 KB JPEG per image, within the existing note-size budget. Source removal does not affect the saved image. Images and formatting participate in autosave, undo/redo, Bin retention and app-wide backup/restore. Camera capture and templates are outside this requested increment.
