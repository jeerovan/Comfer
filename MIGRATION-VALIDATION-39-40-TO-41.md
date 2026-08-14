# Storage migration validation: versions 39/40 to 41

Date: 2026-08-14  
Target: versionCode 41 / versionName 41.0

## Outcome

- Version 39 -> 41: supported through the SharedPreferences importer. Scalar settings, app lists (including explicitly empty lists), folders, and all three widget slots are prevalidated and preserved before legacy files are deleted.
- Version 40 -> 41: no storage migration is required. Version 40 and 41 use the same Preferences DataStore filename (`comfer_settings`), migration flag (`prefs_migrated_v2`), Room filename (`comfer.db`), Room schema version 1, entities, and DAOs. A true v40 flag bypasses all legacy reads/deletes and the existing DataStore snapshot loads directly.

## Git-history evidence

- `5c5b670` is version 39 and stores scalar settings in `com.jeerovan.comfer.Prefs`, app lists/folders in `com.jeerovan.comfer.AppInfoPrefs`, and widget JSON under `bound_widgets_v2` in `widgets_center`, `widgets_prefs_left`, and `widgets_prefs_right`.
- `0d694ef` introduced Preferences DataStore, Room schema 1, and `PrefMigrator`.
- `7c676da` is version 40. Its storage filenames, schema, entities, DAOs, delimiter, widget slots, and completion flag match version 41.
- `git diff --exit-code 7c676da -- app/schemas/com.jeerovan.comfer.data.ComferDatabase/1.json` passes. The same comparison passes for `DataStoreModule.kt`, `ComferDatabase.kt`, `Entities.kt`, and `Daos.kt`.

## Defects found and fixed for version 41

1. The old importer skipped a v39 app-list key when its saved value was empty. Version 41 now writes `[]`, preserving an intentionally empty list instead of treating it as missing.
2. Widget placement JSON was copied without validation. A malformed widget slot could be committed while its only legacy source was deleted. Version 41 decodes the exact v39 `PersistableBoundWidget` schema during preflight and aborts before any destination write on failure.
3. Settings were imported through one DataStore edit per key. Version 41 preflights the whole legacy payload, then imports all scalar settings in one DataStore transaction.
4. Folder failure already aborts rather than being swallowed. Execution tests now prove a persistence failure does not delete sources or set the completion flag.
5. Version metadata is raised from 40/40.0 to 41/41.0.

## Deterministic coverage

`PrefMigratorTest` covers:

- a realistic v39 payload containing Boolean, Float, Int, Long, and String settings;
- the exact v39 app-list delimiter and empty-list preservation;
- folder ID/title/package JSON preservation;
- exact widget placement JSON preservation;
- malformed folder and malformed widget preflight failure;
- required read -> persist -> delete -> complete ordering;
- persistence failure leaving deletion/completion untouched;
- v40 completion flag bypassing every legacy operation;
- v40 DataStore values loading into the current snapshot;
- stable DataStore, database, and migration-flag names.

## Residual historical limitation

Version 40 swallowed malformed legacy folder JSON, then deleted the SharedPreferences source and set its completion flag. If a user already ran that exact failure path on version 40, version 41 cannot reconstruct the deleted folder metadata. Users with valid v39 data upgrading directly to 41 are protected; users with a normal completed v40 migration are unaffected.

## Release recommendation

Keep `prefs_migrated_v2`, `comfer_settings`, `comfer.db`, and Room schema version 1 unchanged for version 41. Do not remove the v39 importer yet. Before a staged rollout, perform one install-over-upgrade smoke on disposable devices/emulators for 39 -> 41 and 40 -> 41, verifying settings, app lists/folders, and widget placement after process restart.
