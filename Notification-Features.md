# Notification Inbox: current features and behavior

Current source reference, 12 September 2026. This document describes the implemented Notification Inbox.

## Entry and navigation

The home notification row opens the All apps inbox as one tap target. Its notification small icons and overflow indicator are display-only. It supports horizontal and vertical layouts, the configured icon size/color, and an optional border. Pinned apps appear first. An inbox icon occupies the row when there are no active app groups. The entry is hidden only when notification access is absent and notification setup has never been completed.

The bottom navigation contains **All apps**, **Saved**, and **Settings**. All apps is the live Android notification view; Saved contains eligible local history copies. Notification settings contains grouped/chronological view choices, Pause/Resume automation, Quiet hours, Filters, History, and Connection and privacy.

The inbox fills the safe screen area with the theme surface at 80% opacity over wallpaper. In portrait, the inbox and launcher settings share a fixed 360 dp reach area measured from the bottom safe edge, capped by the available safe height. Scrollable top padding positions initial content within this area, giving shorter screens a larger proportion of space for content. Scrolling can use the full viewport. Landscape has no starting padding. Bottom navigation and applicable action controls stay outside the scrolling list; controls wrap when needed. Android Back handles nested pages, confirmation, selection, return from Saved to All apps, and exit. There is no separate app Back button.

Active, Saved, settings, and More actions have separate list states. Returning from Android notification settings restores the source list and selected-card anchor when the source remains valid. A rule opened from More actions returns through that route with Back; saving returns to the source list.

## Live notifications

The listener maintains individual records keyed by Android notification key, with separate app/profile identities and revisions. An updated record replaces its prior version. Group summaries are omitted when matching children exist; summary-only records remain visible. Displayed records are notifications, not a count of unread messages.

Grouped view is the default. App groups are ordered with pinned apps first, then by profile/package identity; records within each group are newest first. Chronological view is a flat newest-first list and ignores pin ordering. Groups have a title and a separate expand/collapse caret. Group and selection counts are not displayed.

Cards show available title, notification time, a one-line message preview, and progress when supplied by the source. Chronological cards also show the app label. Messaging-style text is assembled from the delivered message entries. Missing previews have an unavailable label. Comfer does not reconstruct content hidden by Android.

### Gestures and selection

| Interaction | Behavior |
|---|---|
| Tap an overflowing, collapsed message preview | Expands the card; does not launch or dismiss. |
| Tap a short preview or an already expanded card | Opens the notification, subject to current-state validation. |
| Long press a card | Selects it and enters selection mode. |
| Tap a card in selection mode | Toggles that record's selection. |
| Tap a group title in selection mode | Selects or deselects all current children of that group, including collapsed children. Other groups retain their selections. |
| Tap a group caret | Expands/collapses only; does not select, launch, or dismiss. |
| Swipe horizontally in either direction | Dismisses an eligible live notification after the card completes its off-screen animation. |
| Short/cancelled swipe | Returns the card to its position. Protected cards resist dragging and return without dismissal. |

Selection uses a 14 dp ring/dot centered in a reserved 48 dp slot, so entering selection does not rewrap the preview. No selection indicator is exposed outside selection mode. Selecting one record exposes Priority, More actions, and Snooze when eligible. Multiple selection also exposes confirmed bulk dismissal when every selected item is manageable; Cancel is shown for multiple live selections.

Changed or removed records, connection loss, or a new listener session invalidate affected selections and pending confirmation. A fresh selection clears the obsolete changed-notification notice. Operations are disabled while a submission is pending, and actions recheck the key, revision, session, and current Android record.

### Open, dismiss, and snooze

Opening first uses the current content intent. A missing or cancelled intent falls back to launching the source app in the current profile. Cross-profile fallback is unavailable.

When history is enabled and the record is eligible for capture, Comfer saves the copy before opening. If that save fails, the open operation stops with feedback. Otherwise it launches first, then requests dismissal of that same live record if it is eligible. A failed launch does not trigger dismissal. A successful launch with a failed dismissal reports that the notification could not be dismissed. Opening protected or non-clearable notifications does not cancel them.

Manual dismissal targets individual keys; bulk dismissal requires confirmation and validates each selected record. A dismissal request is not treated as proof that Android has removed the record. A swiped card returns if the request fails or removal is not observed after the recovery delay.

Native **Snooze** is available on Android 8/API 26 and newer for eligible selections, using a fixed 15-minute duration. It acts on the selected notifications, not future alerts from the app. There is no snooze action on API 24–25.

Ongoing, call, alarm, media transport, navigation, and system-category notifications are protected. Non-clearable records, group summaries, and user-protected apps are also excluded from ordinary dismissal controls. Protection applies to automatic dismissal and dismissal after opening.

## App controls

One selected live or saved record opens the same **More actions** page:

- **Create content rule** uses the selected live record or saved text as a reference.
- **Protect this app from dismissal** applies to the app/profile, also excludes it from history, and removes its saved copies. Removing protection permits future eligible capture; it does not restore deleted copies.
- **Sound and notification settings** opens Android settings. For a live source with a channel, destinations are tried in order: channel settings, app notification settings, app details, general settings. A saved source has no historical channel. Cross-profile settings navigation reports its limitation.

Priority pins the selected apps in grouped views and the home row. It does not change Android sound, importance, or DND exceptions. Comfer's app controls do not directly mute another app or regroup its Android shade notifications.

## Saved and history capture

History is off by default and requires explicit consent. Settings offers **24 hours**, **7 days** (default), or **30 days**, per-app exclusions, and confirmed deletion of all saved history. Turning capture off retains existing copies until expiry or deletion.

Eligible future posts and updates are captured while history is enabled. Opening an eligible current notification can also save it before launch. Initial synchronization and Refresh do not backfill history. Earlier dismissed notifications cannot be recovered.

Capture skips summaries, protected apps/records, excluded apps, secret notifications, locked-device events, incomplete content, empty previews, and recognized redaction/unavailable placeholders. Source removal does not delete an already saved copy. Queue overload or inaccessible content can leave capture gaps.

Saved displays a copy only when its profile/key/post-time identity is absent from the complete live snapshot. Filtering or collapsing an active group does not make its copy appear in Saved. After opening and successful source removal, an eligible retained copy becomes visible there. Reposts with a new post time have a different saved identity.

Saved supports:

- Grouped or chronological presentation using the shared view choice and pin ordering; visible records are ordered by original notification time.
- Case-insensitive search over title, message, and app package name.
- Expandable message previews, app/date/time labels, long-press selection, and group selection including collapsed children.
- Tap to open the source app in the current profile after preview expansion when needed. This cannot reopen the original notification intent and does not delete the copy.
- Swipe or single-selection Delete to remove a local copy. Multiple deletion requires confirmation. Deletion does not dismiss Android notifications.
- More actions for one selected copy; Delete and Cancel for multiple selections.

History retains at most 500 copies, pruning by save age and oldest save time. Excluding or protecting an app also prunes existing copies. Saved content is hidden while the device is locked. Saved/history-related views use secure-window rendering. See [storage and privacy](docs/notifications-storage.md) for persistence, limits, and failure behavior.

## Content rules

Filters contains up to 20 ordered rules. Rules can be created directly, from a selected live notification, or from one saved copy.

A rule has an editable name, optional app/profile and channel scope, Title/Message/Title and message field choice, 1–8 required phrases, up to 8 exception phrases, and ANY/ALL matching. Phrases are limited to 100 characters and names to 80. Matching is case-insensitive, Unicode-normalized literal substring matching. Any matching exception prevents that rule from matching. Title and message matching requires both selected fields to be available; protected, incomplete, redacted, or insufficient content does not match.

New rules start in **Test only** mode. Preview explains matches against current records without acting; saved-source editors also preview the saved reference separately. Saving requires preview of the unchanged draft. Enabling automatic dismissal requires separate confirmation.

A saved reference seeds app/profile and a short editable title or message phrase. It does not recover a historical channel or action handle. The editor can select channels currently available for that app. Locked or deleted saved sources are unavailable for rule creation.

Rules have enable switches; long press edits/previews, Move up changes priority, and swipe requests confirmed deletion. Enabled test-only rules record matches without acting. Among enabled automatic rules, the first matching rule wins. An exception belongs to its rule; it is not a global keep rule.

Evaluation occurs on fresh posts/updates, with configuration and source content rechecked before cancellation. Saving, resuming, reconnecting, and refreshing do not automatically dismiss the existing backlog. History capture is offered before automatic dismissal. Cancellation cannot guarantee prevention of the original sound/vibration, and no Undo is provided.

Recent rule activity retains the last 50 outcomes in memory without notification text; the settings page displays the latest 10. Outcomes describe test matches, changed-record skips, dismissal requests, or unavailability.

## Quiet hours and focus

Quiet hours uses one Comfer-owned Android priority-DND rule and requires notification policy access, separate from notification listener access. It displays Comfer's contribution, the effective system DND state, local timezone, and the next boundary. Android settings manages supported exceptions.

Focus offers **15**, **30**, and **60 minutes**, plus End focus. The timer measures elapsed time; changing the wall clock does not extend it. Manual focus ends across reboot.

There is one recurring schedule with selected weekdays and local start/end times. The editor requires at least one day and different start/end times. The initial values are every day, 22:00–07:00, disabled. Overnight intervals belong to their starting weekday. Start is inclusive and end exclusive. Schedule changes must be applied; disabling preserves the selected times.

Focus and schedule overlap: Comfer remains active while either applies. Ending focus does not stop an active schedule. Ending Comfer's contribution does not turn off another active DND rule. A rule disabled or removed in Android is not silently recreated by routine reconciliation; explicit activation can restore it.

Precise boundaries use an elapsed-realtime exact idle-capable alarm when allowed, with a separate inexact fallback. On Android 12/API 31 and newer, Focus and Schedule offer **Allow precise timing** when access is absent. Without it, Android can delay boundaries. Schedule state is reconciled on relevant boot, time/timezone, package-update, and access-change events.

On API 29+, Comfer's rule explicitly allows alarms. Earlier versions use system policy behavior; API 28 refuses activation when the current priority policy excludes alarms. Access loss, disabled rules, scheduling failures, and cleanup problems are surfaced. Quiet hours does not promise universal silence or arbitrary per-app exceptions.

## Connection, pause, and reset

The UI distinguishes access needed, reconnecting, restricted, recovery needed, and reconciliation in progress from a connected empty inbox. Connection and privacy shows the last successful synchronization, Refresh, live-data disclosure, and Reset. Access/retry controls remain reachable when the live view is unavailable.

The listener reconciles the full accessible live set, coalesces bursts, and requests bounded recovery on entry/resume or disconnect. API 24–25 additionally has an enabled-component registration refresh during recovery. Recovery does not change the user's notification-access grant.

**Pause automation** stops content-rule processing and Comfer's active quiet contribution while leaving manual inbox actions and history capture available. Configuration and focus deadlines remain; resuming evaluates the current schedule and any unexpired focus timer without processing old notifications through rules.

**Reset notification settings** requires confirmation and successful cleanup of Comfer's DND runtime/rule first. It restores default notification configuration with automation paused and history capture off, preserving the setup marker. It does not revoke Android access, delete live Android notifications, or serve as Delete all saved history. Existing saved copies remain subject to retention and explicit deletion.

Invalid stored configuration preserves its original data, enters a paused recovery state, and blocks ordinary writes until reset. History storage/encryption failures stop capture, preserve existing files, and leave the live inbox available; explicit saved-data deletion resets history storage.

## Current limits

The inbox provides accessible text/progress and the source open action. It has no inline reply, arbitrary source action buttons, rich-media reconstruction, saved original intents, bookmarks, reminders, digest alerts, regex rules, or Undo. Saved search has no date filter or app-label search. Notification-specific configuration export/import is not integrated with the full-app backup feature; notification text and Android handles are not exported.

Behavior depends on notifications Android delivers, source-app actions, profile access, and device DND/alarm behavior. A successful platform request is not a guarantee of its eventual effect.

## Source and verification references

- [Inbox, home entry, actions, and navigation](app/src/main/java/com/jeerovan/comfer/notifications/NotificationInboxActivity.kt)
- [Listener and action lifecycle](app/src/main/java/com/jeerovan/comfer/MyNotificationListenerService.kt)
- [Live model and eligibility](app/src/main/java/com/jeerovan/comfer/notifications/NotificationModel.kt)
- [History](app/src/main/java/com/jeerovan/comfer/notifications/NotificationHistory.kt), [saved filtering](app/src/main/java/com/jeerovan/comfer/notifications/NotificationSavedContent.kt)
- [Rules](app/src/main/java/com/jeerovan/comfer/notifications/NotificationRules.kt), [rule editor](app/src/main/java/com/jeerovan/comfer/notifications/NotificationRulesSettings.kt)
- [Quiet hours](app/src/main/java/com/jeerovan/comfer/notifications/NotificationQuietHours.kt), [schedule calculation](app/src/main/java/com/jeerovan/comfer/notifications/QuietSchedule.kt)
- [Storage reference](docs/notifications-storage.md), [behavior scenarios](Notification-Test-Scenarios.md), [test execution](docs/notifications-validation.md)
