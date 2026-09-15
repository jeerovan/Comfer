# Journal: Features and UX

Updated 15 September 2026 for Comfer. Proposed behavior specification; not a claim of implementation or completed device testing. Phased delivery: [Plan-Journals.md](Plan-Journals.md).

Implementation is underway in the English first-test build; progress and unverified acceptance gates are in [Plan-Journals.md](Plan-Journals.md). User approved temporarily opening Journal from home Search long-press until the four-module menu and Notes are ready.

## 1. Scope

Provide a separate Journal space with a dated feed and a bottom composer for text, an attached image, and live speech-to-text. Notes remains for reusable information and checklists; Tasks retains scheduled actions. Shared technical utilities are acceptable, but Journal is not a Notes view and must not appear in Notes navigation or search.

Carry forward the local-first baseline: no account, sync, automatic cloud backup, or remote AI writing. Typed entries and saved images work offline. Speech availability depends on the device, language, and recognition engine; disclose network recognition and require explicit opt-in before using it. No saved audio recordings or audio playback feature is required: the microphone transcribes speech into journal text.

The planned home entry is the [four-module menu](docs/home-module-menu.md): a three-dot icon replaces the middle Search icon in QuickListOverlay; tapping expands Journals, Tasks, Search and Notes icons using the existing home-folder animation. Each icon launches its own module. This final shared navigation replaces the earlier proposed Journal-only Search-long-press shortcut when implemented. Preserve Tasks data, reminders and other navigation. Optional mood tracking, streaks, rich text, reminders, and memory resurfacing are not part of this initial journal experience.

## 2. Feed and navigation by date

- Group entries under small centered date separators outside content cards, with entries oldest-to-newest within each date and newer dates below older dates. Display the time beneath the content at the card’s trailing edge; no title or “Written” prefix is required.
- Open at today’s latest entries aligned at the bottom on each fresh opening. Successful creation scrolls to the latest entry.
- Tapping the bottom date control opens a date picker initialized to the selected date. Choosing a date navigates to that day's group. Cancel keeps the prior position.
- A selected date without entries shows an empty date group with a brief empty state and the composer available. Provide a Today action to return easily.
- Composer entries belong to the currently selected journal date, visibly identified above the composer when it is not today. Navigating to a historical date must not silently backdate an existing draft: retain the draft's target date and explicitly show it, with an option to change it.
- Store the entry's selected calendar date separately from created/updated timestamps and creation time zone. Editing content or traveling must not regroup saved entries unexpectedly. Default creation date/time is now; users may explicitly choose a historical date/time. Selecting a historical composer day defaults its time to the current local time.
- A recording session keeps the date captured when it starts, including across midnight. Starting a new session after midnight uses the current selected date; refresh Today when no draft/session is active.
- Page older content without loading the entire archive. Date lookup must work even for a date not yet loaded.

## 3. Bottom composer and prompts

Place an expanding text input at the bottom. The image-picker icon sits inside the input at its trailing end. The mic/submit control sits outside the input at the screen's trailing edge, after the input. In English this means image icon at the right end of the box and mic farther right; use directional layout for RTL.

- Show a different writing prompt as placeholder for each new empty composer, successful submission, or fresh Journal opening. Cycle through a shuffled local prompt list without immediately repeating the previous prompt; keep the prompt stable while editing, recording, rotating, or navigating dates with a draft.
- Examples: “What stood out today?”, “What is on your mind?”, “What would you like to remember?”, “What went well today?” Localize prompts; no network generation is needed.
- Placeholder prompts are hints, not saved text. Keep an accessible input label independent of the placeholder. Empty submission must not create an entry containing the prompt.
- Typing content changes the outside mic icon into Submit. Show Submit for an image-only draft too so it can be added. Whitespace without an image is empty and retains the mic.
- Submit stores one entry atomically; clear the composer only after success and then select the next prompt. Disable duplicate commits while saving. On error, preserve text/image and show retry.
- Allow text plus one attached image per entry in the initial release. Image-only entries are valid. Preview selected media above/inside the expanding draft area with replace/remove actions.
- Input expands to a bounded height, then scrolls internally. Keep input and action controls above the keyboard/system inset without obscuring the final entry. Preserve the draft through configuration changes and navigation; durable draft recovery is required after interruption.
- To retain an image while adding speech, permit long-press on the outside Submit control to start dictation when a draft exists; expose the same labeled Start dictation accessibility action. Its ordinary tap remains Submit. Suppress Submit on release after a recognized long-press. Existing text becomes a fixed prefix in the live entry and the attached image stays attached.

## 4. Composer state model

| State | Input area | Extra control immediately before outside button | Outside button |
|---|---|---|---|
| Empty | Prompt placeholder and image picker | None | Mic; long-press starts dictation |
| Draft text/image | Editable draft and image picker | None | Submit; tap adds entry |
| Starting | Permission/readiness feedback | None | Cancel starting request |
| Listening | Animated sound-level/wave feedback replaces input | Pause | Stop |
| Paused | Static paused feedback replaces input | Play-shaped Resume | Stop |
| Finalizing | Finishing indicator; no microphone capture | None | Disabled Stop briefly |
| In-place edit | Existing entry editable; bottom composer temporarily disabled | None | Entry-local Save/Cancel |

Play means resume listening, not playback of previously captured audio. Pause stops microphone capture, not just the animation. A recording long-press starts a latched session: the user can release their finger and continue speaking until Pause or Stop.

Ordinary mic tap may display a short long-press hint. Provide a directly invokable Start dictation accessibility action so long-press is never the only way to record. No passive or automatic microphone activation.

## 5. Live speech-to-text

- On long-press, request microphone access only if needed, check recognizer/language readiness, and start audio capture only after permission and readiness succeed. If unavailable or denied, restore the original draft and keep typing usable.
- Replace the input with animated visual feedback driven by actual input level when available. If the provider exposes no level, show a clearly labeled listening animation rather than implying measured audio. Respect reduced motion with a static indicator/level meter. Keep an accessible Listening/Paused status and visible duration.
- Change the mic to Stop and add Pause immediately before it. During recording, the input and image picker are unavailable; a previously attached image remains visible on the live entry.
- Create one in-progress feed entry when the first nonempty recognition result arrives (or use the existing text/image draft as its initial content). Update that same entry in real time for the whole session; do not create a new entry for every callback.
- Separate finalized segments from the current provisional hypothesis. Replace provisional text when the recognizer revises it; do not append every partial result. Commit each finalized segment exactly once using session/segment identity, including repeated final callbacks and recognizer restarts.
- Visually distinguish provisional text from finalized text. Incrementally persist recovery state and finalized segments. A shown live hypothesis must not be described as durably saved until its write succeeds.
- Pause ends capture for the current segment and retains the session/entry. Accept its final result during a bounded drain window; prevent Resume until that segment settles or is preserved as provisional. Resume starts a new segment appended to the same entry. Repeated pause/resume must not duplicate text or lose an earlier segment.
- Stop ends microphone capture immediately and exits speech recognition after allowing up to two seconds for the last final callback. If final text does not arrive, preserve the latest hypothesis with a Needs review state. Invalidate later callbacks so they cannot alter this or a subsequent entry.
- After Stop, save the nonempty entry, restore the normal empty composer and rotate its prompt. If saving fails, retain the recoverable entry and Retry; never clear its text or image. Silence with no text/image creates no entry.
- Mark an interrupted/error-terminated entry so the user can review it. Preserve recognized text and explain what stopped; never display a partial session as fully successful.
- Android speech services may stop on silence, impose session limits, or omit partial results. Support segmented continuation where feasible, with visible reconnecting feedback and bounded retries; otherwise stop honestly and retain the entry. Do not promise unlimited uninterrupted transcription or live words from a provider that offers finals only.
- Maintain selected recognition language explicitly. Show whether on-device speech is available. A lost network connection must preserve text and expose retry/typing; do not switch recognition providers or upload content silently.
- Navigating away, device locking, app backgrounding, an incoming interruption, or process loss ends active capture. Persist recovery state and return to a non-recording state; never resume the mic automatically. First Back while recording stops/finalizes it and stays on Journal.
- Block date navigation, editing, and swiping the live entry while recording; allow reading/scrolling older content. Present a Stop recording action when an incompatible action is requested.
- Do not retain raw audio after transcription. Any future audio retention/playback requires a separately specified user choice and backup/privacy handling.

## 6. In-place text editing

- Tapping the text/body of a saved entry makes that entry editable in place with keyboard focus, preserving its date, timestamp, and image. Do not navigate to a separate editor screen.
- Display entry-local Save and Cancel controls. Save commits changes and an edited timestamp; Cancel restores the previous saved content. Creation date/time stays unchanged during text/image-only edits. Tapping the timestamp allows explicit date/time changes; check saves and cross cancels them.
- Only one entry can be edited at a time. Preserve the bottom composer draft separately and temporarily disable it to avoid editing the wrong text.
- Switching entry/date or leaving with changes requests Save/Discard/Keep editing. Failed saves preserve the edit buffer; backgrounding persists a recoverable draft without silently claiming a committed edit.
- Empty text is valid if an image remains. If saving would leave neither text nor image, offer explicit Delete or Keep editing rather than silently removing the entry.
- Preserve normal selection, autocorrect, composing text, undo/redo, multiline input, and literal Unicode/punctuation. Speech callbacks must never overwrite manual edits.

## 7. Images: add, replace, remove, and limits

- The input's image button opens Android's system image picker for one image. Cancellation leaves the draft unchanged. Use scoped access rather than requesting all-photo-library access.
- Tapping an image in a saved entry opens actions to Change image or Delete image (and optionally View full image). Image tapping must not also trigger text editing. Provide the same actions through accessibility.
- Stage replacement and validate it before updating the entry. Keep the old image until the new file and database reference are committed; failure/cancellation leaves the original intact.
- Removing an image preserves text. For an image-only entry, ask whether to delete the whole entry or cancel. During in-place editing, image changes belong to that edit transaction and Cancel restores the original.
- Initial limits: one image; maximum source file **10 MB (10,000,000 bytes)**; maximum decoded **20 megapixels**; normalize the stored image to a longest edge of **2048 pixels** and maximum **2 MB (2,000,000 bytes)**. These are chosen product defaults, not Android restrictions.
- Reject files exceeding source/decoded limits before full-resolution allocation where possible. Bound streamed reads when a provider does not report size. Accept only formats supported by the chosen safe decoder; clearly reject unsupported/corrupt/animated files rather than dropping frames silently.
- Correct orientation, resize proportionally, and compress to the stored budget. If the budget cannot be met safely, reject with a useful message and preserve the draft. Explain that a resized copy will be saved; leave the original photo unchanged.
- Strip unneeded location/EXIF metadata from the managed copy. Copy into private managed storage; deleting the original or losing picker access must not break the saved entry.
- Decode thumbnails off the UI thread and limit memory use. Treat low storage, interrupted copies, malformed content, and duplicate callbacks as recoverable errors.

## 8. Swipe deletion and undo

- A deliberate horizontal swipe in either direction deletes a saved entry from the feed. Partial/cancelled swipes do not mutate data and must not conflict with vertical scroll, text selection, or Android edge Back.
- Show Deleted / Undo for five seconds. Undo restores text, image, original date/order, and metadata without overwriting later conflicting changes. A later deletion restarts the feedback timer and must not permanently destroy an earlier deletion.
- Move deleted entries to a recoverable 7-day Archive, accessible through Journal settings; permanent deletion/Empty trash require confirmation. If undo feedback expires, the entry remains recoverable there.
- Remove an empty date group from the ordinary feed after its last entry is deleted; keep a selected empty date available for writing. Preserve the user's nearby scroll position.
- Disable swipe on the live recording entry and on an entry with active unsaved edits. Require stopping or resolving edits first. Provide an accessible Delete action for users who cannot swipe.
- Keep deleted/replaced image files while referenced by an active entry, recoverable edit, Archive, or undo state. Clean up unreferenced files only after recovery obligations expire.

## 9. Persistence, privacy, backup, and shared UI

- Store stable entry IDs, journal date, creation time zone, timestamps, committed text, provisional recovery state, optional image reference, version, and deletion metadata. Preserve draft target date and selected recognition language.
- Coordinate database writes and media staging so a crash cannot publish a broken image reference. Use versions/session identities to reject stale recognition and edit callbacks.
- Extend Comfer manual backup with a separate versioned Journal section and managed images. Exclude raw audio, active microphone state, and Android grants. Restoring never restarts recording.
- Missing Journal data in an older archive preserves current entries; replacing with an empty dataset requires explicit reviewed restore. Validate before mutation and roll back on failure. Test cross-device restoration including protected media.
- Keep content out of analytics/logs and hide it from launcher home/global search by default. Carry forward optional biometric/device-credential protection; protect private content, derivatives, and recent-app previews consistently. Re-locking ends capture and retains a recoverable entry.
- Core text/images use private local storage. Explain uninstall/data-clear loss and support portable manual export; protected exports require authentication and a portable encryption/recovery design rather than only a device-bound key.
- Follow current Comfer typography, wallpaper treatment, shared thumb-reach behavior, and platform Back. Do not reuse obsolete layout rules when the current repository specification differs.
- Give all controls at least 48 dp touch targets, semantic labels and visible state. Keep the composer reachable above keyboard/insets; allow large text and long localized prompts. Use reduced-motion feedback and RTL-aware placement. Do not announce every provisional word to TalkBack; announce state changes and make live text readable on demand.

## 10. Acceptance checks

These are future implementation checks, not tests already run.

| Scenario | Expected result |
|---|---|
| Tap any date; choose loaded/unloaded/empty date; cancel | Correct group reached; empty date writable; cancellation retains position |
| Draft while changing date; session crossing midnight | Target date remains explicit; no silent reassignment |
| Open, submit, recompose, rotate | New prompt at intended boundaries; no immediate repeat; no prompt saved as text |
| Type, erase, whitespace, image-only draft, repeated submit | Correct mic/submit state; exactly one valid entry; no blank entry |
| Long-press mic/Submit and release | Dictation starts once; no accidental text submission on release |
| Permission denied, unsupported language, missing partials | Honest status and typed fallback; no loss or false listening state |
| Revised partials, repeated finals, segment restarts | One entry with correct text; no duplicated hypotheses/segments |
| Pause/resume repeatedly; final callback racing Resume | Mic stops while paused; text stays ordered; no duplication |
| Stop, then delayed callbacks or new recording | Finalization bounded; mic off; late callbacks ignored; next entry unchanged |
| Network error, incoming interruption, Back, background, crash | Durable text recoverable; no automatic background recording/resumption |
| Text/image tap, edit save/cancel/failure | In-place edit and image actions distinct; original preserved on cancellation |
| Exactly-at/above image limits; huge dimensions; malformed file | Deterministic validation; no excessive allocation; draft preserved |
| Change image then cancel or lose storage | Original image intact; no orphaned committed reference |
| Swipe/partial swipe/undo, final entry of a day | Only intended deletion; restoration includes image/date; stable navigation |
| Backup/restore with images, older archives, interrupted restore | Correct counts and content; rollback on failure; no active mic restored |
| Tall/short phone, RTL, large text, TalkBack, keyboard | Composer and all actions remain visible and operable |

## Appendix — Earlier review evidence

The following is carried forward from the 14 September 2026 market review, not a fresh survey. It covers nine publicly displayed reviews across three established journal apps, selected by the Play Store listing rather than random sampling. Reports can be historical or device-specific and were not independently reproduced. They inform robustness checks; they do not override the explicit interaction requirements above or justify adding mood tracking or reminders to this release.
### Day One

Stephen Worden (9 Feb 2025) wanted search within entries. Crystal Lew (14 Aug 2026) reported formatting and paragraph-selection regressions; the developer's 31 Aug reply says version 2026.17 fixed selection/formatting, so this is historical regression evidence, not a verified current bug. Tiffany Cherubini (10 Jul 2026) questioned price/customization and mentioned a Windows lock limitation; that platform-specific claim is not an Android finding. [Displayed Play reviews and reply](https://play.google.com/store/apps/details?id=com.dayoneapp.dayone).

Design inference: test editing mechanics and privacy on every surface; do not paywall basic recovery in a proposed monetization plan. Pricing mentioned by reviewers was not independently checked.

### Journey

An anonymous reviewer (18 Sep 2025) valued quick memories and returning after gaps. Min_D_77 (21 Aug 2026) praised quick entry and media. Leeann McClure (9 Jan 2026) reported repeatedly restarting historical-entry downloads on a new device. [Displayed Play reviews](https://play.google.com/store/apps/details?id=com.journey.app).

Design inference: retain low-friction journaling and make large-history restore a tested workflow. The reported cloud-download problem motivates recovery testing; it is not evidence that Comfer requires sync.

### Daylio

Aselyn Morris (28 Oct 2025) liked customization and requested hourly mood views. Alex Felix (30 Jan 2025) requested a configurable day boundary. Ryan Strong (9 May 2025) reported crashes, later attributing them to angle-bracket input; that proposed cause is the reviewer's explanation, not a verified technical diagnosis. [Displayed Play reviews](https://play.google.com/store/apps/details?hl=en&id=net.daylio).

Design inference: support optional time-stamped observations, a journal day boundary, and robust literal-text handling. Do not infer medical benefits from product marketing or these anecdotes.

## Approved first-test UI update

The bottom row above the composer contains a centered Today/selected-date button: tap opens the date picker; horizontal swipe changes one calendar day. Its trailing three-dot menu offers Settings and Archive. Remove top date arrows, back arrow and separate settings/archive icons. Successful creation scrolls to the latest entry on its saved date. Entry deletion uses swipe (with an accessibility action), without a visible Delete text button. In-place edit cancellation uses a cross icon beside the save check. Archive is the UI name for deleted entries and retains deleted entries for seven days; unresolved recovered edits retain their recovery safeguard. These decisions supersede conflicting earlier UI descriptions.

Opening refinement: fresh Journal openings show today’s latest entry at the bottom, including short feeds. The empty composer displays a prompt directly (no visible “Journal entry” label), with a different prompt on each fresh opening and stable wording across rotation. Existing draft content remains intact. The protection switch in Journal settings renders at 70% size.

The new-entry clock control is removed. New entries use the current local time on the selected composer day. Previously chosen composer time overrides are cleared on opening, preserving draft text, image and day. A saved card’s trailing timestamp opens date/time pickers and uses the entry’s existing check/cross edit confirmation.

Image interaction: tapping a composer or saved-entry image opens a secure full-screen preview, replacing the image bottom sheet. Support pinch zoom in/out (1–5×) and bounded drag panning; double taps do not zoom or reset. Bottom actions are an X icon, Change text button and delete icon, with accessible labels for the icons. Change image opens the system picker; Delete image follows existing draft/edit confirmation and image-only entry deletion safeguards. Close or Android Back returns to Journal.

Feed anchoring: use a reverse-layout list backed by newest-first items, so the latest entry is the natural bottom origin while visual reading order stays oldest-to-newest. Journal and Archive must not share a scroll position. Returning from Archive starts Journal at the latest entry without an on-return scroll workaround. Date separators stay above their groups; older/newer paging remains bounded. Explicit new-entry submission still reveals the newly saved content.
