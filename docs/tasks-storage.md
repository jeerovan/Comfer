# Tasks storage and reminders

Current behavior: [Tasks-Features.md](../Tasks-Features.md). Progress: [Plan-Tasks.md](../Plan-Tasks.md). Tests: [tasks-validation.md](tasks-validation.md).

Subtask UI is deferred; stored relationships and validation remain for compatibility. Legacy home-panel preferences may remain in stored/portable data but no longer enable a home panel.

Tasks uses its own Room database, `tasks.db`, under `Context.noBackupFilesDir`. Lists, task/occurrence rows, series definitions and preferences use stable IDs. Schema 4 retains the explicit 1→2 migration for the legacy guidance preference and adds 2→3 for independent swipe/reorder guide-shown flags (default false), followed by 3→4 for the list-name tap guide flag; no destructive migration fallback is enabled. The existing launcher database is unchanged. Notification Inbox configuration remains JSON in `notification_configuration` SharedPreferences; Tasks does not migrate notification settings into Room.

`TaskStore` serializes reads and changes through one coroutine mutex. It validates a proposed snapshot before writing a Room transaction and publishes observable state after the transaction commits. Validation covers required titles/names, duplicate IDs/names/occurrences, references, one-level subtasks, completion consistency and the restriction against recurring parents/subtasks. A completed parent cannot retain an unfinished child; reopening a child also reopens its parent. Undo restores only affected records and rejects conflicts with subsequent changes instead of overwriting them.

Dates are local calendar dates (`epochDay`), with an optional local minute. Supported dates span years 1900–9999. Recurrence is anchored to the original calendar date: monthly/yearly clamping does not shift later anchors, DST gaps use the first valid instant and overlaps use the earlier occurrence. Missed occurrences remain independent overdue rows, plus one future occurrence. Completing late does not move the series. Stopped/ended series remain available to completed history.

## Reminder state

The database stores due/snooze intent, completion, delivery acknowledgement and action version. A single AlarmManager wake-up PendingIntent is recomputed from committed data; active recurring series also reconcile at the next local midnight. Exact alarms are used when permitted, with `setAndAllowWhileIdle` fallback when exact access is unavailable. Tasks remain usable when posting permission is denied. See the official [alarm scheduling guidance](https://developer.android.com/develop/background-work/services/alarms).

Notification actions check the current task ID/version. Complete on a parent with unfinished children opens confirmation; snooze changes reminder intent without moving the task's due date. Notifications use a dedicated channel, public lock-screen replacement and stable task tags. Missed reminders collapse into a summary; up to five fresh simultaneous reminders retain individual actions. Larger groups open the Tasks screen. Summary members are versioned so completion/restore removes stale content. Obsolete summaries are cancelled before replacement children are posted, because Android may cancel a summary's child notifications with it.

Reconciliation runs after committed changes, launch, reboot/update, clock/time-zone changes and exact-alarm grant changes. Settings return/resume also refreshes permission status. Notification posting is acknowledged after handing the notification to Android; this is not proof that the user saw it. Force-stop/power-off prevent delivery until Android permits the app to run again. Device power policies, notification/channel grants and exact-alarm access remain device-owned.

## Manual backup

Archive format 3 adds a version-1 task snapshot alongside the existing notification configuration DTO. Formats 1–3 are readable. Export includes lists, tasks/subtasks, notes/links, stars, ordering, completion history, recurrence/exception rows, due/snooze intent and task preferences. Delivery acknowledgements are cleared and action versions normalized in portable export; live alarm/notification handles and grants are not exported.

Backup and restore acquire locks in the fixed order **notification configuration → Tasks**. Task data participates in the same rollback journal as launcher preferences, launcher Room data and notification configuration. A missing task section preserves current tasks. An explicitly empty task collection replaces current tasks, with that effect shown in the restore confirmation. Preview and success messages include task/list counts and explain that the chosen file contains private task content.

Restore assigns fresh action versions and suppresses replay of already-past reminders while preserving overdue task rows. Future/snoozed reminders are reconciled under the receiving device's current grants and time zone. Interrupted restoration recovers the pre-restore snapshots before normal startup. JSON is limited to 24 MiB, wallpaper to 32 MiB and the complete archive to 64 MiB; oversized serialized data fails before opening the export destination.

System/cloud backup remains disabled. No task network synchronization is implemented. Sharing into Tasks creates an editable draft and requires explicit Save. Opening a link or selecting a backup provider is a user-directed action.
