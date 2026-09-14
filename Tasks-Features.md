# Tasks: features and UI

Updated 13 September 2026. This is the authoritative Tasks behavior and UI specification, consolidating the former separate UI document. [Plan-Tasks.md](Plan-Tasks.md) tracks delivery; [storage](docs/tasks-storage.md) explains persistence/reminders; [validation](docs/tasks-validation.md) records actual tests and limits.

The current build is for testing, with English labels, localized dates/times and RTL support. Subtask UI is deferred. Cloud sync, accounts, collaboration, shared lists and companion clients are outside scope. Tasks stays local except for user-directed sharing, links and manual backup files.

## Entry and shared layout

- Long-press home Search opens Tasks in circular and column layouts. Ordinary tap opens Search; a completed long-press must not trigger Search on release. Swipes cancel the long-press. Folder Close retains its existing behavior. Provide a labeled Open Tasks accessibility action.
- Opening Tasks through launcher entry points uses Notification Inbox’s 300 ms bottom-up slide over a stationary launching screen.
- Do not render a Tasks panel on launcher home or expose panel settings, including when an older stored preference enabled it.
- Browsing and Tasks settings use a 360 dp portrait reach area measured from the bottom safe edge, including the action bar and capped by available height. The heading belongs to scrollable content. Initial top padding scrolls away, allowing the full safe viewport; landscape has no reach padding.
- Keep content above measured bottom controls, Undo and system/keyboard insets. Support short windows, large text, RTL, readable contrast and at least 48 dp touch targets. Preserve focus, draft and view scroll state where applicable.
- Match Notification Inbox’s wallpaper-backed background: theme surface at 80% opacity, onSurface content color and zero tonal elevation across Tasks browsing, details and settings.
- Use Comfer typography, colors and shapes. Respect reduced motion, interruptible animations and platform gesture timing.
- Android Back dismisses the current sheet or returns one step. Do not add an on-screen Back button or silently save a draft.

## Browsing, search and sorting

The bottom bar has five accessible icon-only controls: **Search, Star, Add, TaskList, Settings**. TaskList uses a three-line menu icon. Search and Settings show active state. The heading displays the selected list/view; tapping a concrete list heading edits that list. Sort sits beside the heading.

- Search titles and notes across lists. Place its input above the bar; show Clear only for nonempty text. Tapping Search again exits search. Include completed has a trailing switch at 70% visual scale with a minimum 48 dp toggle target.
- Star opens Starred across lists. All tasks, Today, Upcoming and Overdue are available as separate Views in the list sheet. Today includes overdue tasks. Aggregate views preserve the last concrete list.
- Sort options wrap in a bottom sheet: My Order, Reminder Date, Starred, Title. Reminder Date is the due date, not snoozed delivery time; group undated tasks separately. Starred-first preserves manual order within groups. Changing sort mode preserves My Order.
- Hold-and-drag reorders tasks in My Order; a stationary hold does nothing and never opens Move to list.

## Cards, gestures and feedback

All incomplete rows belong to one actual enclosing card. Completed title, caret and completed rows belong to a second card, initially collapsed. Render this card only when the current filtered task list, Starred view or search results contain completed tasks. Hide it when the last matching completed task is reopened/deleted or excluded by search; completed tasks elsewhere do not make it visible. Keep rows inside their card during dragging.

Incomplete rows show a completion ring, title, optional description/date-time/repeat indicator and star. Completed rows show a check, title and stored completion timestamp without description. Tap a row to edit; tap its completion control to complete/reopen. Completion checks the ring and squeezes the row into Completed. Double-tap the body toggles Star once without opening details. No separate row options or drag-handle button.

Both cards share drag feedback: elevate/highlight the held row, animate neighbors into proposed positions and tint the drop slot subtly without a border. Preview does not persist intermediate order; commit on drop, restore on cancellation. Reorder incomplete and completed siblings independently. Off-screen drag auto-scroll is not certified; see validation limits.

A deliberate horizontal swipe in either direction deletes. Partial/cancelled swipes must not mutate data or interfere with scrolling/edge Back. Retain accessible row actions. Confirm recurring-occurrence scope and legacy group effects where needed. Clear completed is confirmed and must not stop an active recurring series.

Only task or task-list deletion shows **Deleted / Undo**, for five seconds. A new deletion resets the timer. Undo restores affected records without overwriting subsequent conflicting edits. Do not show Saved/Undo after add, edit, completion, reopening, starring or moving. Preserve focus/scroll context and use restrained haptics for committed actions.

## Task details and capture

Start content with the required nonblank Title, followed by optional multiline Description with tappable links. Fields grow as text wraps; new-task fields sit above the bottom bar. Do not show a three-dot menu, completion/reopen button, Delete button, inline Move section or inline list chips.

The bottom bar contains Schedule, Star, **Move to list immediately beside Star**, Cancel and Save check icons. Move opens a bottom sheet of wrapped list names without search. Selection changes the draft destination; Save applies it. Back dismisses the sheet without moving the stored task.

Add uses the selected concrete list; from search/aggregate views use the last selected/default Tasks list. Add from Starred preselects Star. Save validates and persists before returning, retaining input on failure. Closing unchanged details returns directly; changed fields or recurrence require discard confirmation. Shared text/links create a draft requiring explicit Save and survive recreation.

Show the selected date/time and repeat frequency as tappable values. Frequency includes Daily/Weekly/Monthly/Yearly or Every N units and selected weekly days. Schedule and repeat pages update the draft. All Done actions use check icons with accessible Done labels.

## Lists

Keep at least one list. Choose list is a bottom sheet with wrapped, saved-order names and semantic selected state. Long names wrap; many lists scroll vertically. No search input or Manage lists entry. Selecting a list restores its browsing position.

Place **Add list** alongside names. Put All tasks, Today, Upcoming and Overdue in a separate Views group with horizontal and vertical spacing.

Add and rename use the same bottom sheet: required name input plus Save check. Add starts empty and hides Delete. Tapping the current concrete list heading opens the prefilled name with Delete and Save icons. Validate blank/duplicate names and retain input on failure. Empty-list deletion is immediate; populated deletion confirms the affected count. Preserve the final-list guard and Deleted/Undo. Do not expose Manual order/list reordering in Tasks settings. Stored list ordering remains compatible with existing data.

## Dates, recurrence and reminders

Support unscheduled, date-only and timed tasks, Today/Tomorrow and native date/time pickers. Time is available before Date; choosing it first defaults to today, visible and editable before Save. Clearing a schedule cancels its reminder. Date-only tasks become overdue on the following local day; timed tasks after their due time.

- Daily, weekly, monthly, yearly and custom intervals; selected weekly days; no end, inclusive end date or occurrence count. Show next-occurrence preview.
- Edit/delete this occurrence or this and future occurrences, preserving earlier history. Completion timing does not shift the calendar pattern. Missed occurrences remain overdue without a notification flood.
- Store local calendar dates and optional local minutes. Recalculate on time-zone/clock changes. Clamp unavailable month dates to month end and February 29 to February 28 in non-leap years without shifting later anchors. DST gaps use the next valid instant; overlaps notify once at the first occurrence.
- Date-only reminders have a configurable default time, initially 09:00, and can be disabled. Dedicated-channel notifications support Complete/Snooze/Open and lock-screen privacy. Snooze changes delivery intent, not due date or recurrence. Dismissal is not completion.
- Persist scheduling intent before registering AlarmManager alarms. Use exact idle alarms when allowed and inexact fallback otherwise. Show blocked/approximate/scheduled/snoozed status; keep tasks usable with denied permissions.
- Reconcile after committed edits, completion/deletion, reboot, updates, clock/time-zone changes and permission changes. Versioned actions reject stale delivery; group simultaneous/missed reminders. Reopening future tasks restores scheduling; reopening overdue tasks does not replay old alerts immediately.
- Delivery cannot be guaranteed during force-stop/power-off or under every OEM policy. Android grants, DND, channel settings and exact-alarm access remain device-owned.

## Tasks settings

Use the shared thumb-reach layout, including the scrollable heading and fixed bottom action bar. Keep notification status/settings, exact-alarm access when needed, date-only reminders, default reminder time and lock-screen privacy. Settings switches render at 70% within minimum 48 dp toggle targets.

Notification settings opens the app's system notification settings, with app-info fallback. It controls posting, not notification-listener access. Do not duplicate this action on Reminder Date. Omit Manual order, home-panel controls/view choices and the usage guide from settings. My Order stays in the sort sheet. Do not show first-use browsing guidance or a Got it button, including when opening the default Tasks list for the first time.

## Persistence and backup

Use private transactional Room storage with stable IDs and explicit migrations. Preserve tasks across restart, recurrence/history, reminder intent and conflict-aware Undo. Clearing app data or uninstalling removes local tasks.

Include lists, tasks, notes/links, stars, ordering, completion history, recurrence, due/snooze intent, preferences and legacy subtask data in user-initiated Comfer backups. Preview task/list counts and explain replacement/privacy. A missing task section preserves current tasks; an explicitly empty section replaces them only through the reviewed restore flow. Validate before mutation and recover all participating stores after failed/interrupted restore.

Export portable intent, not Android grants, live alarm/notification handles or delivery acknowledgements. Rebuild future reminders under receiving-device grants/time zone without overdue replay. Automatic system/cloud backup remains disabled; no task network synchronization. See [storage and archive compatibility](docs/tasks-storage.md).

## Deferred scope and acceptance

Subtask creation, nesting, promotion, progress and expansion UI remain planned for later in [Plan-Tasks.md](Plan-Tasks.md). Preserve existing relationships and backup compatibility; existing groups retain completion/deletion confirmation, atomic moves and recurrence restrictions. Do not flatten or delete stored hierarchy as part of UI cleanup.

Release acceptance covers persistent task/list lifecycle, calendar boundaries, repeated/cancelled gestures, permission/Doze/reboot recovery, real backup transfer/rollback, both launcher entries, large datasets, short/tall screens, RTL/large text/keyboard, TalkBack and physical gesture comfort. These are requirements, not claims that every check passed. Consult [actual results and untested cases](docs/tasks-validation.md).
