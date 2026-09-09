# Comfer notification management requirements

Research date: 7 September 2026. Updated: 8 September 2026. Status: proposed product requirements; implementation has not started.

## 1. Product recommendation

Extend the existing notification icon row into a private notification inbox: expand an app to inspect its notifications, hide distracting apps from the launcher temporarily, reach Android's sound controls quickly, and review what was hidden. Add scheduled quiet hours through Android Do Not Disturb (DND), with separate access and explicit exceptions.

The central promise should be **“Choose what appears on your home screen and when you review it.”** Sound suppression must be described according to what Android actually permits. Notification access alone does not make Comfer a privileged notification controller.

Start with organization, clear controls, and reliability. Advanced filtering and digests should follow once users can understand and reverse every rule. Priorities below are product judgments based on qualitative evidence and feasibility, not estimates of market prevalence.

## 2. Research method and evidence

Reviewed publicly visible English-language Play Store reviews for three notification utilities, plus Reddit discussions, Google support discussions, and official Android documentation. The sample includes eight individually dated Play reviews summarized below. Store pages expose a selected subset, not a complete review export. Older reviews establish historical needs; they do not establish current competitor defects. No Comfer user interviews or quantitative review coding were conducted.

Reddit items were available through indexed search extracts; direct page retrieval failed for some. Treat them as directional anecdotes. Store listings describe advertised capabilities, not independently tested behavior. All links were accessed or surfaced on the research date; review dates are stated separately. A Daywise listing could not be retrieved reliably and is excluded from the evidence base.

### Pain points and feature implications

| ID | Observed need | Evidence and date | Proposed response |
|---|---|---|---|
| E1 | Useful alerts compete with low-value interruptions. | Play reviews of other apps, 21 August and 25 June 2026, value selective filtering and visibility of important messages. | Per-app controls, priority display, optional content filters. |
| E2 | Flexible rules are difficult to discover and configure. | A review of other apps, 20 August 2026, describes trial and error finding rule options. | Plain-language templates, preview, explanation of matches. |
| E3 | App-level blocking also removes useful messages; channels may not separate the desired content. | FilterBox review, 11 February 2023, describes needing regex and requests allow-list filtering. [Play source](https://play.google.com/store/apps/details?id=com.catchingnow.np&hl=en_US) | Allow exceptions before hide rules; start with simple text matching. |
| E4 | Users cannot tell whether filtering failed or their setup is wrong. | FilterBox review, 15 November 2024, reports uncertain filtering and support problems; 1 December 2024 review reports a purchase before discovering a mismatch. Developer replies discuss fixes/refund support. These are historical reports. [Play source](https://play.google.com/store/apps/details?id=com.catchingnow.np&hl=en_US) | Connection status, test mode, action outcome, compatibility demonstration before purchase. |
| E5 | Notifications disappear before users finish with them. | Recent Notification reviews, 11 November 2019 and 12 November 2018, discuss lost alerts after restart or opening, and request favorites; the latter also objects to noisy history after exclusions became paid. [Play source](https://play.google.com/store/apps/details?id=com.libin.notification&hl=en_US) | Optional history, bookmarks, exclusions and explicit retention. |
| E6 | Grouping can conceal messages and make accidental bulk removal easy. | Reddit bug report, 5 December 2024, describes opening a group clearing its notifications. [Reddit report](https://www.reddit.com/r/bugs/comments/1h6xu4b/) | Expand without opening/dismissing; individual controls; separate bulk action. |
| E7 | Users want control over grouping, not one forced presentation. | Reports describe both an overlong ungrouped shade and unwanted grouping hiding conversations. [Ungrouped clutter](https://www.reddit.com/r/GooglePixel/comments/qqxy6k/), [grouping concerns](https://www.reddit.com/r/GooglePixel/comments/x6cl0i/), [Google community request](https://support.google.com/pixelphone/thread/181943534/disable-notification-grouping?hl=en) | Grouped and chronological views inside Comfer; persistent per-app preferences. |
| E8 | Quiet notifications may still be important; silencing every category is cumbersome. | Users distinguish silent-but-visible group chats from low-priority items and ask how to silence a whole app. [Silent grouping discussion](https://www.reddit.com/r/android_beta/comments/cndstm), [per-app silence question](https://www.reddit.com/r/AndroidQuestions/comments/1euvg7r/) | Separate sound, launcher visibility and priority controls. |
| E9 | Work/life boundaries and repeated chat bursts need time-based controls. | Repeated-chat discussion requests temporary relief; FilterBox advertises after-hours rules. Product availability is a demand signal, not a measured user outcome. [Chat discussion](https://www.reddit.com/r/GooglePixel/comments/1atgkh0/), [FilterBox listing](https://play.google.com/store/apps/details?id=com.catchingnow.np&hl=en_US) | Timed launcher pauses, quiet schedules and later digests. |

The strongest repeated themes in this sample are selective control, recoverability and understandable behavior. Privacy is a product requirement because notification content is sensitive; this sample does not quantify privacy complaints.

### Existing alternatives and Comfer's opportunity

| Alternative | Publicly documented offer | Implication for Comfer |
|---|---|---|
| Other apps | Rules, cooldown, snoozing, history and reminders; advertises local processing. | Avoid competing first on the number of automation actions. Make controls available directly from the launcher row. |
| [FilterBox](https://play.google.com/store/apps/details?id=com.catchingnow.np&hl=en_US) | History, rules and offline classification. | Prefer understandable defaults and visible outcomes over an opaque classifier. |
| [Recent Notification](https://play.google.com/store/apps/details?id=com.libin.notification&hl=en_US) | App-grouped history and age-based deletion. | A history-only screen is insufficient differentiation; connect it to current notifications and app controls. |
| [Pixel Modes/DND](https://support.google.com/pixelphone/answer/6111295?hl=en) | Schedules and interruption filters for people, apps and other sounds. | Integrate with system controls where available. A launcher does not need to reproduce every device setting. |

## 3. Current Comfer baseline

Direct source inspection found:

- [MyNotificationListenerService.kt](app/src/main/java/com/jeerovan/comfer/MyNotificationListenerService.kt) publishes an in-memory live snapshot, debounces updates by 250 ms and resynchronizes every 30 seconds. It sorts by post time and retains one notification per package with `distinctBy { it.packageName }`.
- [MainActivity.kt](app/src/main/java/com/jeerovan/comfer/MainActivity.kt), `NotificationIconRow` and its icon helpers, render icons and an overflow indicator. The inspected icon helper has no per-notification management action.
- [AndroidManifest.xml](app/src/main/AndroidManifest.xml) declares the listener and disables application backup. [app/build.gradle.kts](app/build.gradle.kts) declares minimum API 24 and target API 37.

The current package deduplication is suitable for an icon row but loses the individual records needed for counts and an inbox. Preserve all accessible notifications internally and derive the compact row from that collection. Counts must mean active notification records, not unread messages: one record can contain several messages.

Scope: these are targeted source findings, not a repository-wide architecture audit. Graph coverage metadata for these four files reported no recorded gaps and matching metadata; source was inspected directly.

## 4. Capability boundaries

| Requested behavior | Supported product approach | Boundary |
|---|---|---|
| Group notifications for an app | Group records in Comfer's own inbox. | Does not regroup another app's original notifications in the Android shade. Shade grouping is controlled by the posting app and Android, including system automatic grouping. [Grouping documentation](https://developer.android.com/develop/ui/views/notifications/group) |
| Mute an app | Open that app's Android notification settings; offer channel settings when available. | Ordinary listener access does not grant unrestricted channel editing. Do not present a Comfer toggle as a confirmed system mute. [Listener API](https://developer.android.com/reference/android/service/notification/NotificationListenerService), [channels](https://developer.android.com/develop/ui/compose/notifications/channels) |
| Pause an app temporarily | Hide its records from the Comfer row/main inbox until an expiry; keep a review route. | This is visual suppression in Comfer, not guaranteed silence or suppression in the shade. |
| Snooze one notification | Use system snooze on API 26+. | Applies to a selected notification, not every future alert from its app. API 24–25 use a clearly labeled Comfer-only alternative. [Listener API](https://developer.android.com/reference/android/service/notification/NotificationListenerService#snoozeNotification(java.lang.String,long)) |
| Dismiss unwanted alerts | Explicit cancellation of eligible notifications. | Post-arrival cancellation cannot guarantee prevention of the initial sound, vibration or popup. This is an engineering inference from the listener's post-event interface. |
| Mute notifications during a time range | A Comfer-owned DND rule with policy access, or a shortcut to system schedules. | On Android 15+ with target 35+, contribute an `AutomaticZenRule`; do not overwrite global DND. Other active rules can keep the device quiet. [Android behavior change](https://developer.android.com/about/versions/15/behavior-changes-15#dnd-changes), [NotificationManager](https://developer.android.com/reference/android/app/NotificationManager) |
| Recover a dismissed notification | Review locally captured text and open the source app. | History is not restoration of the original notification, its actions, or unread state. Content never delivered to Comfer cannot be recovered. |
| Read every message/OTP | Display only accessible content. | Android 15 redacts detected OTP content for untrusted listeners; do not bypass this. [Android privacy changes](https://developer.android.com/about/versions/15/behavior-changes-all#otp-redaction) |

Listener availability is restricted on older low-RAM devices and by work-profile policies. Show unavailable states rather than an empty “all caught up” inbox. [Listener API](https://developer.android.com/reference/android/service/notification/NotificationListenerService)

## 5. Functional requirements

P0 = initial release requirement. P1 = follow-up after validation. P2 = exploratory, not a launch commitment. Section 7 is the authoritative priority register; section 11 resolves cross-feature behavior. Acceptance criteria are release targets unless explicitly identified as prototype measurements.

### NF-01 — Expandable notification inbox (P0; E1, E6, E7)

After notification access is granted, the home-screen notification row always remains visible. Show app icons when viewable notifications exist; otherwise show one Notification inbox icon. The entire row is one tap target that opens the full All apps inbox. App icons and the overflow indicator are visual content, with no individual tap, double-tap or long-press actions. Offer grouped and chronological views, with priority apps optionally first. Grouped view has one expandable app header with an up/down caret and no displayed count; child cards remain individually actionable. Chronological view is a flat list. Show app, time and accessible preview. Do not display app-group or selection counts. Keep silent notifications eligible for priority placement. Section 10 defines the required layout and gestures.

Acceptance:

- Three child records and a group summary produce three counted items, with no duplicate summary card; a summary-only notification remains visible.
- Expanding or collapsing never launches an app or dismisses a notification. A separately labeled item action opens its valid content intent; unavailable intents fall back to opening the app.
- One-item dismissal does not issue a group-wide cancellation. Bulk dismissal explicitly states the app and count and skips protected records.
- An updated notification key replaces its prior live record. Personal and work-profile records are never merged merely because their package names match.

### NF-02 — Per-app controls with clear scope (P0; E3, E8, E9)

Offer “Hide from Comfer for…” with 15 minutes, 1 hour, until tomorrow and custom end time; “Always hide from Comfer”; “Pin app first”; and “Sound and notification settings.” Display the next resume time and a Resume now action. Hidden items remain accessible in a separate Hidden view without increasing the primary row count.

Acceptance:

- Timed hiding affects existing and newly arriving matching records only in Comfer. No system cancellation or volume adjustment occurs.
- Pauses survive process restart; expired pauses are removed before the next UI render. Returning live records appear without generating new alerts.
- A rule summary explicitly says “Hidden in Comfer; Android alerts may still sound.” Opening system settings never marks the app muted merely because the settings activity returned.
- On devices lacking a channel-settings destination, fall back to app notification settings, then application details.

### NF-03 — Quiet hours and focus timer (P0, gated by platform prototype; E8, E9)

Provide a manual timer and recurring weekday schedules, including overnight ranges. Separate “Hide selected apps in Comfer” from “Quiet device notifications.” The latter requires DND setup and displays the effective system state. Offer system settings for supported people/app exceptions; do not promise arbitrary per-package exceptions across all Android versions.

Acceptance:

- A 22:00–07:00 schedule spans midnight correctly; weekday membership refers to the start day. Show local timezone and next start/end. Recompute after reboot, timezone and clock changes.
- Ending focus deactivates only Comfer's contribution. An independently enabled system mode remains active, with an explanation.
- If policy access is missing/revoked, show “Device quiet mode unavailable”; the launcher-only schedule can still work.
- Preserve alarms by default; calls, repeat callers and other exceptions are shown during setup. Avoid a blanket “all notifications muted” promise, including for system-critical alerts. [System exception behavior](https://support.google.com/pixelphone/answer/6111295?hl=en)
- Prototype schedule activation and expiry under Doze before release. Do not use periodic background work as a promise of minute-exact DND transitions; fall back to system schedule setup where reliability cannot be achieved.

### NF-04 — Snooze and review later (P1; E5, E9)

Offer native snooze for an individual eligible notification on supported versions. Separately offer a Comfer bookmark/reminder, labeled as a local copy. Show which mechanism is being used; do not claim a bookmark changes the source app.

Acceptance: native snooze uses the selected key; reposting updates its state without repeated automated snoozing. For a local reminder, expiry produces at most one Comfer reminder, respects DND and handles denied posting permission with an inbox-only result.

### NF-05 — Optional searchable history (P1; E5)

History is off until explicitly enabled. Offer 24-hour, 7-day and 30-day retention, with 7 days proposed as the initial selection. Search by app, date and available text; filter active, dismissed and hidden entries. Allow per-app exclusion, deletion and bookmarks with a separately visible expiry.

Acceptance: history begins at consent, captures no earlier dismissed notifications, excludes selected apps before persistence, survives a normal restart and expires records automatically. Show capture gaps after listener downtime. A saved item without a usable action offers Open app, not a misleading Restore button. Deleting history also removes associated search data and bookmarks in scope.

### NF-06 — Simple rules and explanations (P1; E2, E3, E4)

Create a rule from a notification using app, available channel and optional title/body text. Support contains, excludes and explicit ANY/ALL matching. Start with hide-in-Comfer actions; auto-dismiss is an advanced, separately enabled action. Templates are editable examples, never silently enabled universal spam filters.

Acceptance:

- Preview shows the matched fields, action, exceptions and scope before saving. A user can run observation-only mode before enabling cancellation.
- Missing/redacted text does not satisfy text-dependent rules. Broader fallback matching requires an explicit separate rule.
- “Why is this hidden?” identifies the rule and provides Disable/Edit. A recent decisions view records attempted, succeeded, failed or unknown outcomes.
- Precedence: protected records bypass automatic destructive actions; explicit keep exceptions beat hide/dismiss; remaining matching rules use visible user order with first-match behavior. System DND remains independent.
- Example: a delivery app's promotional phrase matches a hide rule, while an explicit order-status exception keeps a relevant update visible. Test both before activation.

### NF-07 — Scheduled digest and burst control (P1/P2; E1, E9)

NF-07A (P1) provides scheduled review inside Comfer and an optional Comfer digest alert. NF-07B (P2) investigates sound cooldown or holding/reposting notifications in the system shade. Offer a Comfer review digest for user-selected apps, grouped by app and interval, with counts and next review time. Collapse repetitive updates in the inbox. Suppress repeated alerts generated by Comfer itself.

Gate: a digest must not claim to delay or silence original app delivery. Guaranteed per-app sound cooldown is outside the ordinary-listener commitment. Prefer existing system sound controls; investigate stronger mechanisms only as a separate feasibility project. Reposting other apps' notifications is not the default design because it changes identity and action behavior.

Acceptance: one digest per scheduled window, no duplicate alerts on restart, no replay burst when focus ends, and an explicit distinction between notification counts and message counts. History consent is required for retaining content beyond the live set.

### NF-08 — Service health and recovery (P0; E4)

Show Connected, Access needed, Reconnecting, or Restricted; include last successful synchronization. Provide a synthetic example for learning controls and a separate live connectivity check.

Acceptance: revoke access and stale records become visibly unavailable; reconnect reconciles the complete accessible live set without duplicate history or actions. Never suggest that Comfer caused an upstream notification delay when no post event was received. Record operational diagnostics without notification bodies.

### NF-09 — Privacy and safe defaults (P0; all features)

Process rules locally. Do not send notification text, sender identities, app usage inferred from alerts, or saved content to wallpaper/weather services, analytics, advertising or crash reporting. Comfer already uses the network for other functions, so describe this as notification-data isolation rather than an app-wide no-internet claim.

Require separate opt-ins for persistent history, automatic cancellation and DND integration. Explain access in context before the Android consent screen. Update in-app privacy information and Play disclosures to reflect actual behavior. [Google Play user-data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)

Acceptance: exclusion happens before persistence; captured notification content is omitted from logs and configuration backups; retained notification history is private and encrypted with appropriately protected keys; history deletion clears indexes. Offer an authentication gate and hidden previews. Respect OS redaction and do not infer unavailable OTPs. A content capture screen must not expose private text while the device is locked.

Default automatic dismissal must exclude identifiable call/alarm, ongoing service, navigation/media and system-critical records, plus a user-maintained protected-app list. Category metadata is imperfect: do not claim universal critical-alert detection. Review and recovery features cannot substitute for this protection.

### NF-10 — Notification insights (P2; E1, E4)

Show local per-app counts, busiest periods and rules frequently reversed. Distinguish new records from updates and summaries. Suggestions require confirmation and explain their evidence. Do not automatically downgrade an app just because it sends many notifications. Metrics never claim “interruptions prevented” from visual hiding alone.

## 6. Engineering requirements and validation

These are proposed implementation constraints, not descriptions of existing architecture.

- Maintain an accessible live store keyed by notification key plus user/profile identity. Derive groups, counts and the icon row from it; keep history in a separate consent-controlled store.
- Capture rule-relevant events before UI debouncing. Periodic snapshots reconcile state but cannot recover short-lived notifications missed between snapshots. Deduplicate replayed events and ignore Comfer-generated digests in rule processing.
- Keep parsing, persistence and rule evaluation off the main thread. Bound content length, retained records and rule execution; defer regex until execution can be bounded safely.
- Separate display decisions, history retention, system dismissal and DND state. One feature failing must not silently enable a more destructive alternative.
- Define explicit state for hidden-until, removed, snoozed, saved-copy and unknown outcome. Viewing a Comfer card does not mark the source conversation read.
- Request notification posting permission only if Comfer-generated reminders/digests are enabled on Android 13+. Listener access and permission to post are separate. [Posting permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- The initial scope requires no root, ADB, Shizuku, accessibility automation, device-owner status or notification-assistant role.

### Test matrix and release criteria

| Area | Required validation |
|---|---|
| Versions/devices | API 24–25 fallback; API 26 snooze/channels; API 33 posting permission; API 35 DND/redaction; API 36 and the project's API 37 target environment. Include physical Pixel, Samsung and one OEM with aggressive background management. Document untested combinations. |
| Notification types | Single records, summaries with/without children, multi-message records, rapid updates, multiple accounts/profiles, empty/redacted content, expired intents and non-clearable records. |
| Lifecycle | Reboot, process death, permission revocation, disconnected listener, app uninstall, force-stop and recovery. Do not promise capture while Android prevents execution. |
| Time | Overnight/weekend overlap, daylight-saving transitions, timezone change, manual time change, overlapping DND rules and expiry while idle. |
| Correctness | No duplicate child count; no unintended sibling dismissal in test cases; every automated action traceable; protected records remain unaffected by automatic cancellation. |
| Performance | Proposed target: stable inbox within 1 second of a normal received callback; test 1,000 active records and a synthetic 100-update burst. Compare launcher frame timing, memory and a 24-hour idle battery run against baseline before setting a battery budget. |
| Privacy/accessibility | Verify no notification payload in network/log output, exclusions and deletion, large fonts, TalkBack labels, minimum 48 dp interactive targets and non-color-only status. |

Do not ship the device-quiet feature until DND ownership, supported exceptions and schedule expiry pass physical-device tests. Feature-detect and expose a settings shortcut on unsupported configurations.

## 7. Delivery sequence and success measures

The following register supersedes earlier comparative recommendations. All IDs are mapped to phases in [Plan-Notifications.md](Plan-Notifications.md).

| Priority | Requirements | Release condition |
|---|---|---|
| P0 | NF-01, NF-02, NF-08, NF-09, NF-16; DC-01–DC-05; AC-01–AC-04 and AC-07–AC-08 for shipped actions | Usable live inbox, persistent entry, reachable controls, manual actions, privacy and recovery gates pass. |
| P0, gated | NF-03 | Device-quiet behavior ships only where the owned-DND and scheduling prototype passes; other devices receive system-settings guidance. |
| P1 | NF-04, NF-05, NF-06, NF-07A, NF-11, NF-12, NF-13, NF-15; AC-05–AC-06 | Persistence, rule behavior, reminders, configuration backup and restore pass their gates. |
| P1 extension | NF-01 Copy/Share | Explicit preview and sharing consent; no background export. |
| P2 | NF-07B, NF-10, NF-14; optional source-app actions in AC-01 | Separate feasibility and product decision; no promise of sound interception or external action support. |
| P2, uncommitted | Multiple actions per rule, local classification, custom reminder sounds, speech, pocket/flashlight actions, automation integrations | Research only; require a scoped requirement and acceptance criteria before implementation. |

NF-13 Undo is a P1 improvement; P0 manual dismissal uses an explicit labeled action, with confirmation for bulk dismissal. “All available actions” means actions shipped and supported for the selected item, not every possible operation exposed by another app.

Validate with 8–12 consenting users across work-heavy, chat-heavy and minimalist-launcher use cases; this is a proposed study, not completed research. Ask participants to pause an app, find an older alert, explain a hidden item and end focus while another DND mode is active.

Proposed usability targets: at least 80% complete each core task without assistance; all participants distinguish launcher hiding from device silence after setup. Track setup abandonment, rules reversed within 24 hours, reported missed important notifications and listener downtime. Collect only consented aggregate counters or local study observations, never message content. Establish real baselines before claiming a reduction in distraction.

## 8. Decisions to revisit after validation

- Default grouping style and whether counts add value to Comfer's minimal row.
- Whether users need retained text, or live grouping plus bookmarks is sufficient.
- Supported DND exception controls on each Android/OEM combination.
- Pricing, if any: access revocation, deletion, exclusions, service health and recovery should remain available regardless of subscription status; demonstrate device compatibility before purchase.
- Native OS overlap: recheck platform capabilities at implementation time, especially API 37 and OEM behavior. The declared target is not proof that notification features work there.

Not in the proposed scope: guaranteed sound interception for arbitrary apps, rewriting the system shade, reading content Android withholds, automatic replies, or acting as an emergency-alert replacement.

## 9. Features from other apps and proposed additions

Reviewed 7 September 2026: the developer's help articles, public changelog, Play listing and feature tour dated 30 August 2026. This is a documentation review, not a hands-on test or source-code audit. Feature availability and workaround reliability have not been verified on a device. The comparison distinguishes documented behavior in other apps from Comfer's proposed adaptation. References to the app in this focused review are anonymized as 'other apps'; identifying source links are omitted.

### Findings that change the recommendation

A useful pattern in other apps is the rule lifecycle: configuration, temporary activation, conflict handling, debugging and recovery. The reviewed changelog documents rule folders, an activity view explaining matched/unmatched rules, and timed rule enable/disable. These make automation easier to maintain.

The earlier caution about sound control needs a precise interpretation: **reliable per-app silence is not guaranteed, but best-effort muting is worth a feasibility prototype.** Documentation for other apps describes three approaches: requesting system muting, temporarily snoozing, and combining snoozing with temporary DND. The documentation does not specify every underlying API or prove effectiveness across devices. Comfer should not guess that implementation or present temporary device-wide DND as isolated app muting.

The “Summarize” action documented in other apps groups notifications into a replacement notification with an expand action; it is not described as an AI-written text summary. The documented “Sticky” action also uses a copy and, on Android 14+, reposts it after a swipe. For Comfer, equivalent benefits can often be achieved more predictably inside the launcher inbox.

### Feature comparison and decisions

| Candidate | Evidence from other apps | Comfer decision |
|---|---|---|
| Rule groups and one-tap presets | Shortcuts toggle several selected rules from the home screen or Quick Settings. | **Add NF-11, P1.** Particularly useful in a launcher: Work, Evening or Reading controls next to the notification row. |
| Explain both matches and non-matches | Activity view was added in version 35. | **Strengthen NF-06, P1.** Show why a notification was untouched as well as hidden; distinguish a match from a successful action. |
| Repeated reminders | Configurable intervals and reminder limits; reminders stop when the notification leaves the shade. | **Add NF-12, P1.** Bounded follow-up reminders and explicit Done; useful for messages requiring action. |
| Undo dismissal | A 20-second restore prompt follows qualifying user dismissals. | **Add NF-13, P1.** Provide real undo for actions initiated in Comfer by delaying cancellation; distinguish this from reviewing a saved copy after external dismissal. |
| Sticky important items | Replacement copies resist accidental loss through reposting. | **Extend NF-05, P1.** A Needs attention shelf survives Comfer's Clear all; avoid fighting swipes in the system shade. |
| Fixed-time and interval batches | Batch supports release times; Batch every uses midnight-anchored intervals. Updated records can be released when they no longer match. | **Split NF-07.** Promote Comfer-only scheduled review to P1; keep shade holding/reposting and sound suppression at P2. |
| Conversation-level cooldown | First matching notification passes, later ones are muted/dismissed within a window; the developer also describes conversation scope. | **Prototype NF-14, P2.** Keep one noisy conversation from affecting every conversation in an app, where identity is reliable. |
| Rule export/import | Rules can be transferred through a user-selected file. | **Add NF-15, P1.** Preserve carefully configured rules without introducing notification-history cloud sync. |
| Share notification text | An added action forwards text to another app. | **Extend NF-01, P1.** User-initiated Copy/Share inside Comfer, with a preview and explicit privacy boundary. |
| Several actions per rule | Multi-tool permits multiple actions; conflicting actions are ordered. | **Defer to P2.** Begin with one visible action and exceptions; later allow compatible combinations such as bookmark plus remind. |
| Custom sound/vibration, spoken alerts, pocket check and flashlight | These are documented attention actions. | **P2 research.** Custom Comfer reminder sounds have more immediate value than sensors or flashlight automation. Any speech feature should be separately enabled and offer private-output controls. |
| Alarm/unsilence and automatic app actions | Other apps advertise escalation, automatic opening, replies and button presses. | **Defer.** They add substantial interruption and action risk, and are less central to a minimal launcher. |

### NF-11 - Rule presets and temporary overrides (P1)

Users can group rules and activate a named preset from the notification row, an app shortcut or an optional Quick Settings tile. A preset can expire after a duration or at a chosen time. Folders organize rules; they must not silently change precedence.

Acceptance: show active preset and expiry; make repeated activation idempotent; label Activate/Deactivate instead of blindly inverting mixed rule states. Expiry removes the preset's overrides rather than restoring a stale snapshot that overwrites intervening user edits. Resolve overlapping presets using a visible order. A preset containing device-quiet behavior shows its DND scope and access requirement.

### NF-12 - Bounded reminders and Needs attention (P1)

Extend NF-04's one-shot reminder with an optional repeating mode. Proposed initial choices: every 5, 15 or 30 minutes, at most three repeats by default. Users can select Done, Snooze or Stop reminders. A saved task remains visibly distinct from its source notification.

Acceptance: one reminder sequence per item; notification updates do not multiply sequences. Default live-notification reminders stop on source removal or a Comfer open/done action. An explicitly saved follow-up may survive source removal. Merely unlocking or glancing at the home screen never counts as completion. Honor DND and notification permission; no catch-up alert burst after downtime. A pinned copy requires the retention consent defined in NF-05.

### NF-13 - Undo and delayed cleanup (P1)

For a dismiss initiated inside Comfer, show an Undo action for five seconds and defer the system cancellation until that window ends. For delayed automatic cleanup, show the scheduled removal time and a Keep action. This extends NF-01 and NF-06; it does not change Android's external swipe behavior.

Acceptance: Undo prevents cancellation. Before delayed cancellation, re-check that the exact key, content revision and eligibility still match; cancel a pending removal if the app updates the notification. Skip protected records. On process failure, prefer leaving the original notification intact. If cancellation already happened elsewhere, offer history review only when a saved copy exists; do not label that operation restoration of the source app's state.

### NF-14 - Conversation-aware cooldown feasibility (P2)

First investigate a stable scope based on package, profile and available conversation metadata. Android messaging notifications can expose structured conversation information, but this is not universal. Avoid identifying a sender solely by a common name appearing anywhere in the body. [MessagingStyle API](https://developer.android.com/reference/android/app/Notification.MessagingStyle)

Acceptance for the prototype: test first-versus-subsequent alerts, two simultaneous conversations, changing notification keys, new messages updating an existing key, summaries, app-level fallback and cooldown expiry. Measure audible/vibration leakage and unintended suppression of unrelated apps on physical devices. Any temporary DND approach must preserve other rules and surface device-wide effects. If no dependable scope exists, offer only an explicitly selected app-level scope. If sound suppression fails, retain Comfer-only visual burst collapsing with accurate labeling.

This is a validation task, not authorization to ship an unreliable silent mode. Android's listener API exposes post-event callbacks and snoozing, not a general guaranteed pre-alert interception contract. [Listener reference](https://developer.android.com/reference/android/service/notification/NotificationListenerService)

### NF-15 - Notification configuration backup and restore (P1)

Back up all durable notification configuration so users can recover their setup after reinstalling Comfer or moving to another device. Include a Notifications category in Comfer's configuration backup/restore flow and provide Backup now and Restore from the inbox's bottom-accessible Configuration menu. Support a standalone notification-configuration file as well as inclusion in a broader Comfer configuration backup when available. This is an explicit application feature; it must not depend on enabling Android application backup.

#### Configuration included

- Rules: stable identifiers, names, matching conditions, actions, exceptions, priorities, order, folders and configured enabled/disabled preferences.
- Per-app preferences: visibility, permanent hiding, pinned priority, grouping, protected-app status, history exclusions and available channel/conversation selectors.
- Presets and schedules: preset membership, rule overrides, recurring quiet hours, digest schedules, timezone behavior and intended DND exception preferences.
- Reminder and history settings: default snooze durations, reminder intervals/limits, retention periods and preview/privacy preferences. Preserve choices separately from consent to start storing notification content.
- Inbox and row design: orientation/layout choices, icon styling, default view/filter, bottom-control preferences, gesture mappings where configurable. Store density-independent values; recalculate bounds for the destination device.
- Backup preferences: whether automatic configuration backup is enabled and its retention preference. A destination folder must be selected again if its access cannot be reused.

#### Data excluded

Exclude notification history, titles/bodies captured from notifications, sender payloads, saved notification copies, bookmarks containing notification content, search queries, activity logs and diagnostic records. User-authored rule phrases and conversation selectors may themselves contain private information: disclose this in the backup preview and offer a separate redacted sharing export. Redacted exports must mark affected rules incomplete and must not silently broaden their matching conditions.

Never restore Android permission grants, current device-wide DND state, system rule identifiers, notification keys, pending intents, active snoozes, reminder jobs or temporary pause/preset timers. Back up recurring schedule definitions, not an assertion that a schedule is currently active. Require fresh access checks and recreate any Comfer-owned system integration only after review.

#### Backup behavior

Provide manual export to a user-selected destination and optional automatic configuration backups after settings changes. Automatic backups are off by default; coalesce successive changes and retain the latest five successful versions by default. Keep the previous successful backup until a new one has been written and validated. Show the last successful backup time, destination and any failure, with Retry and Change destination actions.

Configuration backups are unencrypted, freely shareable files that anyone possessing the file can inspect and load into Comfer. Do not require a password, account, owner identity, device-bound key or credential setup to export or import them. Explain that a selected cloud-backed file provider may upload the exported configuration; Comfer itself must not silently sync notification data. Losing destination access pauses automatic backups without affecting notification behavior. Retention cleanup may remove only backup versions created and managed by Comfer.

#### Restore behavior and acceptance

- Use a versioned format containing schema version, source app version and creation time. Validate size, structure, integrity and supported versions before changing live settings. A corrupt file or unsupported newer schema leaves the current configuration intact.
- Preview included categories, additions, replacements, duplicates and compatibility issues. Let users select Merge or Replace for the selected notification categories; preserve unrelated launcher configuration. Stable IDs and explicit conflict choices must prevent duplicate rules and schedules on repeated imports.
- Restore configuration atomically with rollback on failure. Missing apps, channels, unavailable profile mappings or unsupported actions are reported and retained as inactive configuration where possible. Never map a work-profile rule to a personal-profile app implicitly.
- Restore ordinary layout and display preferences immediately after confirmation, while preserving the mandatory inbox entry from DC-01. Preserve a rule's intended enabled preference in the preview, but keep imported automation inactive until the user reviews and activates it. History capture and system DND integration require fresh consent/access checks.
- Recalculate thumb-reach dimensions and responsive layout for the destination window. Imported reach settings must not place actions outside the available screen or override minimum touch sizes.
- After activation, calculate the next valid recurring schedule boundary from the current time and chosen timezone behavior. Do not replay missed reminders, elapsed focus sessions or earlier digest windows.
- Show a result summary of restored, skipped and review-needed settings, with direct links to resolve issues. Backup/restore controls and confirmation actions follow the portrait reach and bottom-placement requirements in section 10.
- Verify a full export/restore round trip, repeated merge, selective replacement, older-schema migration, missing apps/profiles, corrupt files and files shared by another user, interrupted writes, lost folder access, automatic version retention and restore onto a device with different density and window dimensions. Confirm that excluded notification content never appears in exported files.

### Refinements to existing requirements

- **NF-06:** Add a bounded observation log of evaluated rules, failed conditions, winning rule and action outcome. Use the current sample in preview unless history is enabled. Do not persist notification content implicitly through debugging. Other apps use specificity and priority to resolve conflicts; Comfer should retain its simpler documented precedence until user testing justifies changing it.
- **NF-07:** Show exact next review time for both fixed slots and intervals, plus Review now. Release items from Comfer's held view when a changed record no longer matches. At review time use currently active records; source-cleared items appear only as labeled history copies with consent. Do not revive already handled notifications or replay every original sound.
- **NF-01/NF-09:** Copy/Share requires a deliberate tap and preview. Sharing can send selected text outside Comfer through the chosen app, so qualify the local-processing promise accordingly. Respect redaction; never auto-copy OTPs or share content from the lock screen.
- **NF-05:** Needs attention items survive ordinary inbox clearing, but not explicit deletion of their saved data. Show their retention deadline and an independent Complete action.

### Scope and release update

Use the authoritative register in section 7 and the dependencies in Plan-Notifications.md. NF-07A is the Comfer-only P1 review schedule; NF-07B and NF-14 remain feasibility work. NF-16 provides lifecycle controls from the initial release, expanding as later features ship.

Do not copy full-screen alarm promises from other apps into Comfer: Android restricts full-screen intent eligibility and access, so a launcher cannot assume it has the same permissions or product justification. [Android full-screen restrictions](https://developer.android.com/about/versions/14/behavior-changes-14#secure-fsi-notifications)

No application code was changed by this analysis. This Markdown is the current requirements source; previously generated PDFs are snapshots and are no longer updated.

## 10. Notification inbox design requirements (P0)

These requirements apply to the inbox entry point and every Comfer-owned inbox screen, including configuration, filters, history, action menus and confirmations. They supersede any earlier behavior that hides the row when its notification list is empty. Later-release actions become available in this layout when implemented; listing them here does not move their feature release priority to P0.

### DC-01 - Always-visible home-screen entry

- Once notification access is granted, reserve a visible row with at least one interactive icon, including while loading, reconnecting or showing no viewable notifications.
- When notifications are available to view, show their app icons and the existing overflow affordance as needed. When none qualify for the row, show exactly one Notification inbox icon. Hidden, paused or history-only items do not prevent this fallback.
- The fallback opens the inbox even when empty, so configuration, hidden items, history and other available actions remain accessible. An empty inbox says there are no current notifications; it must not remove its navigation or settings controls.
- Change the existing row-visibility preference so it cannot remove the only inbox entry after access is granted. Offer layout/style choices instead of a hide toggle in this state, and migrate any previously disabled row to the visible inbox entry.
- Preserve the fallback while the listener reconnects. If access is revoked after setup, retain a clearly labeled access-needed inbox entry with a route to restore access; never represent stale records as current notifications.

Acceptance: zero-to-one and one-to-zero notification transitions never leave a blank row. Tapping anywhere in the row, including icons and gaps, opens All apps. TalkBack exposes one “Notification inbox” button, with no individually actionable app icons.

### DC-02 - Full-height portrait layout with a reachable starting position

The inbox occupies the full available height. On initial rendering, scrollable top padding places the first content element within the initial thumb-reach area. The padding belongs to the scrolling content, not a fixed outer margin: scrolling upward moves that first element naturally into the upper screen and makes the full viewport available for content. Scrolling back to the beginning restores the reachable starting position.

- Calculate `availableHeightDp = availableHeightPx / density` from the keyboard-closed safe window.
- Use fixed `reachHeightDp = min(360, 0.40 * availableHeightDp)` and `initialTopPaddingDp = availableHeightDp - reachHeightDp`. Landscape uses zero starting padding.
- Fix portrait reach at 40% of available height, capped at 360 dp, without user-facing height controls. Ignore legacy saved reach adjustments. Settings and inbox always use the full safe screen width, with ordinary internal spacing. Do not offer content-width or left/right panel-alignment controls.
- Newly opened inbox scopes, configuration screens and action menus start at the reachable position. Preserve meaningful scroll position during rotation/recreation and ordinary notification updates; do not jump the list back during reading.
- After deliberate scrolling, cards and other scrollable actions may move above the starting reach boundary and remain interactive for tap, double tap and long press. Users can scroll them back into reach. Fixed navigation, primary actions, confirmations and input controls remain at the bottom.

Density and window height estimate a starting position rather than measuring hand size. Content spans the full safe screen width in both orientations.

Acceptance: the surface and list viewport use the full safe height above the fixed bottom controls; initial content begins within reach. Upward scrolling consumes the starting padding and reveals content in the upper area without moving the bottom navigation. Returning to the beginning restores padding. Large text increases scrolling without shrinking targets. AC-04 still governs keyboard input and fixed controls. System-owned keyboards and destination pages retain their own layouts.

### DC-03 - Landscape and bottom navigation

Landscape uses the full available screen, respecting system bars, cutouts and hinges. It may show an app/group list and detail pane side by side. Keep the primary navigation and action bar at the bottom in both orientations.

The bottom bar provides inbox scope/filter navigation and Configuration. Settings and the notification inbox do not show an app Back button; use Android’s native Back gesture or system navigation button. Selection replaces or supplements it with relevant actions such as Open, Snooze, Keep or Dismiss, plus a bottom-accessible More menu for the remaining available actions. Avoid a top-right settings button or any app-rendered Back arrow. Place app selectors, search entry and action-menu controls at the bottom as well. Portrait configuration screens and nested action menus use the same full-height viewport and reachable starting padding as the inbox; fixed confirmation controls remain at the bottom.

Acceptance: every implemented notification-management action is discoverable from the inbox; no action requires a home-screen gesture or opening a separate Comfer settings route. Rotation preserves selected app, selected notification, filter, draft configuration and a meaningful scroll position. Back first closes the active menu or selection, then returns through inbox navigation to home; the system Back gesture follows the same order.

Option groups must wrap onto additional lines within the available width instead of requiring horizontal scrolling. Apply this consistently to selected-notification actions, connection and confirmation controls, and all settings choice groups (including quiet hours, appearance and widget choices). Keep touch targets accessible and let the containing settings page scroll vertically when needed. Center each selection ring/dot vertically against its notification card while preserving its reserved space.

### DC-04 - Tap, double tap, long press and slide

Use gestures as shortcuts with visible alternatives. The following defaults are proposed; destructive actions must remain deliberate and reversible where supported.

| Gesture | Home-screen notification row | Notification inbox |
|---|---|---|
| Tap | Open the full All apps inbox from anywhere in the row. | Open the source notification. In selection mode, toggle selection instead. |
| Double tap | No separate shortcut. | No separate shortcut. |
| Long press | No separate shortcut. | Enter selection mode and select the card. Reserve indicator space before every card even outside selection mode. In selection mode show 14 dp rings, filled solid when selected, and the bottom Hide/Snooze/Priority/More actions menu. |
| Slide horizontally | Scroll an overflowing horizontal row without changing notifications. | Dismiss the individual notification directly in either direction when eligible; preserve protected items. |
| Slide vertically | Scroll an overflowing vertical row. | Scroll the notification list within its panel. Do not dismiss the inbox through an incidental scroll. |

Acceptance: recognize tap, double tap and long press as mutually exclusive outcomes; there is no separate double-tap action in the inbox. Movement beyond the gesture threshold cancels tap recognition. Separate horizontal dismissal from vertical scrolling and system edge gestures. Swiping affects only the swiped notification and never commits a bulk action. Provide button/menu equivalents and accessibility actions for every gesture, with at least 48 dp targets, labels and visible selection states. Selection mode is derived from the current nonempty selection and exits immediately at zero, including after source removal. Show Cancel only for multiple selections. Multi-selection menus expose shared actions only; Snooze and bulk Dismiss require every selected item to be eligible. More actions is single-selection only. Returning from Android notification settings preserves selection and scrolls the selected card into view.

### DC-05 - Design validation

Verify empty, active, hidden-only, reconnecting and revoked-access states; long lists; all available action menus; keyboard-open search; large text; left/right-hand use; small and tall phones; tablets/foldables; landscape; and resized windows. Check initial content bounds against the computed portrait starting position, then verify content can scroll above that boundary while fixed controls remain reachable. In usability testing, ask users to open the empty inbox, configure it, act on a notification and return home without changing grip. Adjust the proposed reach formula from those results before release.


## 11. Implementation decisions and cross-feature contracts

These decisions close the pre-planning gaps and take precedence over ambiguous wording elsewhere. They are product defaults; measured platform limitations must be recorded, not hidden behind a success state.

### AC-01 - Action availability and initial content scope

| Item state | Available behavior | Unavailable behavior |
|---|---|---|
| Active and current | Expand, Open, Hide/Resume, app controls; Dismiss if eligible; Snooze when supported and shipped | Actions without a valid current source handle |
| Hidden but active | Same eligible actions, plus Show in inbox | Treating Hide as sound suppression |
| System-snoozed | Display known snooze status; Open app; configuration | Dismiss/Open original from a stale handle; promise early system unsnooze |
| History or saved copy | Read, Open app, Delete copy; bookmark/reminder and Copy/Share when shipped | Source notification dismissal, archive or reply through a saved handle |
| Protected or non-clearable | Read/Open and app controls; eligible explicit user actions | Bulk or automated destructive action; Dismiss when non-clearable |
| Stale, disconnected or profile unavailable | Status and recovery guidance; saved copies only with prior consent | Source operations and new destructive automation |

Initially support normalized text, app identity, timestamps, available messaging lines and a plain progress description. Show a safe text fallback for media, images and custom layouts; offer Open app for richer content. Do not embed arbitrary source-app layouts, fetch image URLs, save attachments or add media transport controls in the initial release. Redacted or missing content receives an explanatory placeholder. Long content scrolls through the full-height viewport, starting within reach.

Manual Reply, Archive and Mark as read are P2, separately gated; they are not implied by the initial action menu. If later implemented, use only a current action supplied by the source notification, preserve its label and authentication requirements, and require an explicit user gesture. Reply needs a supported reply action, a visible draft and Send. Never infer support from app identity or claim delivery from dispatch alone. Automatic replies remain excluded. [Android action and reply model](https://developer.android.com/develop/ui/compose/notifications/create-notification)

### AC-02 - Precise action names and bulk scope

- **Hide in Comfer** changes visibility; **Show in inbox** reverses it. No generic Clear action silently hides notifications.
- **Dismiss notification** requests removal of a live Android notification. **Dismiss selected** and **Dismiss app notifications** show the affected app/profile and count; do not include hidden records or new arrivals unless explicitly selected in the preview.
- **Complete saved item** removes it from Needs attention and stops its local reminders; the saved copy remains subject to retention. It does not mark the source conversation read.
- **Delete saved copy/history** removes only stored Comfer data and related indexes/reminders; it does not dismiss the live source notification.
- Each bulk operation snapshots selected IDs and revisions, checks eligibility again and reports succeeded, skipped, failed and unknown counts. Never silently change a filtered operation to all apps. Partial success is not rolled back by reposting source notifications.

### AC-03 - Updates during interaction

All source actions reference profile, notification key and a monotonic local content/action revision. Content, available actions and protection changes increment the revision; unchanged synchronization does not. Serialize operations for the same item. Revalidate immediately before dispatch; a changed item refreshes the menu and requires a new deliberate action. Removed items leave selection with a clear notice. Disable duplicate submission while pending and report unknown outcomes without automatic external-action retry. Delayed dismissal follows NF-13. Record only minimal local outcome metadata; it is not permission to retain notification text.

### AC-04 - Keyboard, reach and gesture arbitration

For fixed input and primary controls, treat the computed portrait upper boundary as a physical limit relative to the keyboard-closed window. Scrollable content may occupy the upper viewport after user scrolling; this does not permit moving fixed input controls upward. Use only the intersection of that area with space not occupied by the keyboard. While typing, hide results/secondary actions and keep a compact input strip only if accessible 48 dp controls fit. Otherwise temporarily let the keyboard own input, retaining the draft in memory; the keyboard's completion action or system Back dismisses it and restores Comfer controls and results within reach. Do not move Comfer buttons above the boundary to make room. No custom input method is required. Verify this with actual keyboard sizes, large text and floating/split keyboards.

In normal mode, a single tap opens the source notification; in selection mode it toggles selection. Long press enters selection mode and selects the card. There is no separate double-tap action. Use configured platform gesture timing/slop rather than inventing a very short threshold. Back closes keyboard, then action menu, then selection, then nested screen/inbox. Preserve drafts through rotation but do not persist notification reply text in backup/history implicitly.

### AC-05 - Bounded persistence and storage failures (P1)

Initial defaults: at most 10,000 retained records and 50 MiB of logical retained notification data, including saved copies and search indexes; whichever bound or age limit is reached first triggers oldest-unpinned eviction. Cap each normalized record at 16 KiB of UTF-8 text, truncating at a character boundary with a visible marker. Cap bookmarks at 100 and include them in the same total budget; if protected saved items prevent eviction, pause new capture and explain how to free space. Retention expiry still applies to bookmarks.

Store at most 1,000 content-free activity decisions for 24 hours. These are product limits to validate under load, not current measured capacity. Database maintenance must reclaim deleted space with bounded transient overhead. On full storage, transaction failure or inaccessible encryption keys, stop capture and dependent saved reminders, show an error and keep the memory-only live inbox available. Never fall back to plaintext or delete unreadable data automatically. Recovery offers Retry or explicitly confirmed Delete saved data. A settings-write failure must not display a preference as successfully saved.

### AC-06 - Shareable backups and restore coordination (P1)

Use an openly readable, versioned configuration format with no encryption or credential protection. Anyone with a compatible backup file can inspect, share and import it, including on another device or into another user's Comfer installation. File validation checks compatibility and integrity, not ownership. Do not require a signature from the original device or an account to accept a shared backup.

Automatic export requires only access to the current configuration and user-selected destination; there is no backup-password or encryption-key lifecycle. If either is temporarily unavailable after reboot, defer export and retry when accessible. Losing destination access pauses automatic backups and exposes Change destination. Optional redacted sharing remains available for users who choose to omit private rule phrases; the standard complete backup is also freely shareable.

Pause rule execution and queue configuration changes during restore. Commit one configuration generation before resuming; discard obsolete scheduled jobs. Rollback restores the pre-restore generation and its pause state. Failed rollback keeps automation paused and surfaces recovery. Keep temporary restore journals app-private and delete them after successful completion/recovery; they contain configuration only, not notification history. Notification-category replacement cannot reset unrelated launcher data. Preview and explicit activation of imported automation still apply; loading a shared configuration never grants Android permissions automatically.

### NF-16 - Pause, reset and delete controls (P0; extends with later features)

Provide bottom-accessible, separately labeled controls:

- **Pause notification automation:** stop automatic hide/dismiss rules, preset overrides, Comfer reminder/digest alerts and future Comfer DND activation; cancel pending uncommitted automatic actions and deactivate only Comfer-owned DND contributions. Keep live inbox, manual actions and saved configuration. History capture is a separate preference and its continued status must be shown. Already system-snoozed items retain their system expiry. Resuming recalculates future schedules without replaying missed actions; no automatic dismissal of the existing backlog.
- **Reset notification settings:** confirm scope, pause automation, clear notification configuration and owned jobs/rules, restore layout defaults and turn history capture/automatic backup off. Preserve saved notification data unless separately selected for deletion. Preserve the mandatory inbox entry after access has been granted. A cleanup failure remains visible and retryable.
- **Delete saved notification data:** confirm and delete history, saved copies, associated search indexes, activity records and saved-item reminders. Preserve rules/layout, and show whether future history capture is still enabled. This does not delete exported configuration files or cancel live Android notifications.

Pause persists across process restart and reboot. Each operation is idempotent. Permission loss disables dependent actions without pretending owned system state was successfully removed. Changing the default launcher does not silently stop an explicitly configured service; provide Pause and an access-revocation shortcut so users can stop it deliberately.

### AC-07 - Rule inputs and ordering

For P1 text rules, use case-insensitive Unicode-normalized literal matching by default, selectable title/body fields and explicit ANY/ALL. Missing, truncated or redacted fields cannot establish a negative condition or an allow-only match; report insufficient content and skip the destructive decision. No regex or inferred sender identities in this phase. Stable user order breaks otherwise equal priority; preview must use exactly the evaluator used for live events. Re-evaluate visibility on rule edits, but do not bulk-dismiss already active items without a separate explicit preview and apply action.

### AC-08 - Onboarding, migration and release evidence

First use explains notification access before opening system settings; decline leaves the ordinary launcher usable without a permission loop. Track whether inbox setup has occurred so temporary loss of access does not remove its recovery entry. Migrate existing row visibility once, preserve styling and show a brief explanation. App/profile removal disables affected automation instead of matching another installation silently. Present unsupported feature states as unavailable with a reason.

Use localized strings and locale-aware time/count formatting. Validate right-to-left layout, large fonts, TalkBack and keyboard navigation. Each phase must link implemented IDs to tests and device evidence; untested OEM/API combinations remain explicit. Store/schema changes require migration and interrupted-upgrade tests. Feature rollout can disable automation while leaving the live inbox and user data accessible; rollback must not destructively downgrade a newer data schema.


### Swipe feedback and settings selection controls

Notification cards must move horizontally with the finger while swiping. Cancelled or incomplete swipes return smoothly to the starting position; dismissal eligibility and notification revision checks remain authoritative. Notification settings use the same 14 dp ring/solid-dot visual as notification selection, centered vertically beside the label, with a minimum 48 dp touch target and accessible checked/unchecked semantics.


### Complete swipe-to-dismiss transition

After a qualifying swipe is released, continue translating the card in the same direction until its entire bounds clear the viewport, then request Android dismissal. Do not reset its position or remove it at finger release. A slow drag must exceed 60% of the card width; a same-direction fling above 500 dp/s also qualifies. Cancelled/short gestures spring back, and protected items resist movement without dismissal. Revalidate notification identity and eligibility after the animation. Restore the card on rejection or missing removal confirmation.


### Separate group selection and expansion

The app-group title and expand/collapse caret are separate controls. Outside selection mode only the caret changes expansion. During selection, tapping the group title selects all its current inbox children (including collapsed children); if all are selected it deselects that group. Partial selection becomes full selection, other groups remain unchanged, and selecting zero items exits selection mode. Expose full/partial/empty selection semantics without displaying a count. Opening must not issue a Comfer dismissal, including for auto-cancel flags; source applications can independently remove their own notifications. Decision: do not retain separate inbox copies when a source app or Android removes a notification. The live inbox follows active Android notifications; opening does not request dismissal from Comfer.
