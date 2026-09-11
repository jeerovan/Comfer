# Notification storage and privacy

Current implementation reference, 11 September 2026. User-facing behavior is documented in [Notification Inbox](../Notification-Features.md).

## Data boundaries

| Data | Location and lifetime |
|---|---|
| Live records and Android handles | Listener memory. Immutable text records reach the UI; PendingIntent/raw Android notification objects remain in the listener. Reconnection reconciles the accessible live set. |
| Notification configuration | Dedicated `notification_configuration` SharedPreferences, JSON schema version 2. Stores setup/pause, display preferences, app/profile pin/protection/exclusion sets, retention consent/settings, schedule, and rules. |
| Quiet runtime | Separate `notification_quiet_runtime` preferences. Stores the owned Android rule ID and focus wall/elapsed deadlines with a boot marker. |
| Saved history | Encrypted per-record AtomicFiles in `noBackupFilesDir/notification-history`. |
| Recent rule outcomes and capture-gap flag | Process memory; no durable decision log. Rule outcomes do not include notification text. |

Configuration writes are serialized, committed on an IO dispatcher, and published with a new generation only after success. Invalid configuration leaves original bytes intact and exposes a paused recovery state. Ordinary writes are blocked until explicit reset. Version-1 decoding discards retired visibility settings without converting them into destructive rules; supported dismissal rules and DND schedules are retained.

The full-app backup uses explicit launcher preference/database DTOs and wallpaper. Notification configuration is not integrated into its payload. History files, notification action handles, quiet runtime, and system grants are not included. Application backup is disabled in the manifest; history also resides in Android's no-backup directory.

## Saved records and encryption

A saved record contains ID, package, profile, title, message, original post time, save time, and an internal opened-copy flag. No channel, raw notification, or Android action handle is serialized. Identity is a SHA-256 digest of profile, notification key, and post time; updates to the same identity replace its copy, while a new post time produces another identity.

Each record uses Android Keystore AES-256/GCM with a randomized 12-byte IV and a 128-bit authentication tag. The envelope version and opaque record ID are authenticated as associated data. Plaintext is limited to 16 KiB; the encrypted envelope is limited to 16 KiB plus 30 bytes. AtomicFile provides replacement/recovery of each record. There is no history Room database or plaintext full-text index.

Stored package/title/message strings are bounded to 256/256/2,048 characters. Live normalization is separately bounded to 2,048 title and 8,192 body characters; content exceeding those live bounds is marked incomplete and cannot drive capture/rules.

The retained set contains at most 500 records, with 1-, 7-, or 30-day expiry measured from save time. This bounds ordinary encrypted record payloads to roughly 8 MiB; it is not a physical filesystem quota including metadata or transient AtomicFile backups. Retention is enforced on load, writes, explicit refresh, and approximately once per minute while the history process is running. There is no independent background expiry worker.

## Capture and display

Capture requires history consent, an eligible readable record, and an unlocked device. It skips summaries, intrinsic/user protection, exclusions, secret/incomplete content, empty previews, and recognized redaction placeholders. Posted events offer capture before rule evaluation; full listener synchronization does not backfill it. Eligible opening explicitly awaits retention before dispatching the source action.

History work uses a serialized IO queue with capacity 128. Queued captures recheck consent epoch and exclusions before writing. Deletion advances an epoch so earlier queued captures cannot recreate deleted content. Queue overload and skipped capture conditions can set a session capture-gap flag.

Copies are decrypted into a bounded in-memory list. Saved search filters that list by title, text, and package. Saved hides identities still present in the complete live snapshot. The internal opened-copy flag does not place historical cards in the live list.

Saved content and saved-source rule editing are unavailable while the device is locked. The inbox requests secure-window rendering for Saved, enabled history, history settings, and rule editing. This is UI protection; the Keystore key does not require a separate biometric prompt or per-use authentication.

## Failure and deletion

A missing key with retained records, unreadable/altered records, or storage failure sets history failure state and attempts to turn capture off. Existing files are preserved; there is no plaintext fallback or silent replacement of a lost key. Live notification operations remain available. If required retention before opening fails, that open is stopped.

Turning capture off does not delete saved copies. Per-app exclusion and app protection remove matching copies through pruning. Individual and bulk deletion affect only local copies. Confirmed Delete all saved history deletes files, resets the history key, clears failure/gap state, and leaves the capture preference as it is. After a failure has disabled capture, the user enables it again after resetting storage.

Reset notification configuration first cleans up Comfer's quiet rule/runtime, then restores notification defaults with automation paused. It is separate from saved-data deletion; retained files continue to obey the resulting retention policy.

## Source

[Preferences](../app/src/main/java/com/jeerovan/comfer/notifications/NotificationPreferences.kt), [history store](../app/src/main/java/com/jeerovan/comfer/notifications/NotificationHistory.kt), [cipher](../app/src/main/java/com/jeerovan/comfer/notifications/NotificationHistoryCipher.kt), [capture and normalization](../app/src/main/java/com/jeerovan/comfer/MyNotificationListenerService.kt), [backup payload](../app/src/main/java/com/jeerovan/comfer/BackupRestoreManager.kt), [manifest](../app/src/main/AndroidManifest.xml).
