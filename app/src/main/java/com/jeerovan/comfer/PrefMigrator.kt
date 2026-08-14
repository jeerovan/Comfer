package com.jeerovan.comfer

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeerovan.comfer.data.AppFolderEntity
import com.jeerovan.comfer.data.ComferRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val LEGACY_SETTINGS_PREFS = "com.jeerovan.comfer.Prefs"
internal const val LEGACY_APP_INFO_PREFS = "com.jeerovan.comfer.AppInfoPrefs"
internal const val LEGACY_WIDGET_PREFS_KEY = "bound_widgets_v2"
internal const val LEGACY_FOLDERS_PREF_KEY = "folders_data"
internal const val LEGACY_APP_LIST_DELIMITER = "\u201A\uFFFD\uFFFD\u201A"
internal const val PREFS_MIGRATED_FLAG = "prefs_migrated_v2"

internal fun shouldRunLegacyMigration(migrated: Boolean?): Boolean = migrated != true

internal val LEGACY_WIDGET_SLOTS = listOf(
    "widgets_center",
    "widgets_prefs_left",
    "widgets_prefs_right",
)

@Serializable
internal data class LegacyFolderData(
    val title: String,
    val packages: List<String>,
)

internal fun parseLegacyFolders(jsonString: String): List<AppFolderEntity> =
    Json.decodeFromString<Map<String, LegacyFolderData>>(jsonString).map { (id, folder) ->
        AppFolderEntity(id, folder.title, Json.encodeToString(folder.packages))
    }

internal data class LegacyMigrationPayload(
    val settings: Map<String, String>,
    val appListsJson: Map<String, String>,
    val folders: List<AppFolderEntity>,
    val widgetsJson: Map<String, String>,
)

internal suspend fun executeLegacyMigration(
    migrated: Boolean?,
    readAndValidate: () -> LegacyMigrationPayload,
    persist: suspend (LegacyMigrationPayload) -> Unit,
    deleteLegacySources: () -> Unit,
    markComplete: suspend () -> Unit,
): Boolean {
    if (!shouldRunLegacyMigration(migrated)) return false
    val payload = readAndValidate()
    persist(payload)
    deleteLegacySources()
    markComplete()
    return true
}

/** Preflight the complete v39 payload before mutating any v41 destination. */
internal fun prepareLegacyMigration(
    settings: Map<String, *>,
    appInfo: Map<String, *>,
    widgetValues: Map<String, String?>,
): LegacyMigrationPayload {
    val scalarSettings = settings.mapNotNull { (key, value) ->
        value?.let { key to it.toString() }
    }.toMap()

    val appListsJson = appInfo
        .filterKeys { it != LEGACY_FOLDERS_PREF_KEY }
        .mapValues { (_, value) ->
            val packages = value?.toString().orEmpty()
                .split(LEGACY_APP_LIST_DELIMITER)
                .filter(String::isNotEmpty)
            Json.encodeToString(packages)
        }

    val folders = parseLegacyFolders(
        appInfo[LEGACY_FOLDERS_PREF_KEY]?.toString() ?: "{}",
    )

    val widgetsJson = widgetValues.mapNotNull { (slot, jsonString) ->
        jsonString?.takeIf(String::isNotEmpty)?.let { json ->
            // v39 and v41 share PersistableBoundWidget. Decode only for validation;
            // retain the original JSON so no widget IDs/options are transformed.
            Json.decodeFromString<List<PersistableBoundWidget>>(json)
            slot to json
        }
    }.toMap()

    return LegacyMigrationPayload(scalarSettings, appListsJson, folders, widgetsJson)
}

/**
 * One-time importer of legacy SharedPreferences data into the new stores
 * (Preferences DataStore for scalar settings, Room for app lists / folders /
 * widget placements). Runs once at application start, guarded by a DataStore
 * flag so it is idempotent. Old prefs files are deleted only after the import
 * has succeeded.
 *
 * This is intentionally the single place that still touches SharedPreferences:
 * it exists only to migrate existing users' data on first launch of the new
 * build. getAll()/getString() reads can never run again after the flag is set.
 */
object PrefMigrator {
    private val migratedFlag = booleanPreferencesKey(PREFS_MIGRATED_FLAG)

    /** Non-blocking; call from a background coroutine (e.g. [ComferApp.onCreate]).
     *  Must never be run on the main thread — the first-ever Room build + full
     *  import can exceed the input-dispatch timeout. */
    suspend fun runOnce(context: Context) {
        val migrated = context.settingsDataStore.data.first()[migratedFlag]
        executeLegacyMigration(
            migrated = migrated,
            readAndValidate = { readAndValidateLegacyData(context) },
            persist = { payload ->
                // One DataStore transaction imports all v39 scalar values. Room
                // upserts make retries safe if the process stops before the flag.
                context.settingsDataStore.edit { preferences ->
                    payload.settings.forEach { (key, value) ->
                        preferences[stringPreferencesKey(key)] = value
                    }
                }
                payload.appListsJson.forEach { (key, packagesJson) ->
                    // Preserve present-but-empty v39 lists as [] instead of
                    // allowing first-launch defaults to repopulate them.
                    ComferRepository.saveAppList(context, key, packagesJson)
                }
                if (payload.folders.isNotEmpty()) {
                    ComferRepository.saveFolders(context, payload.folders)
                }
                payload.widgetsJson.forEach { (slot, widgetsJson) ->
                    ComferRepository.saveWidgetPlacement(context, slot, widgetsJson)
                }
            },
            deleteLegacySources = {
                context.deleteSharedPreferences(LEGACY_SETTINGS_PREFS)
                context.deleteSharedPreferences(LEGACY_APP_INFO_PREFS)
                LEGACY_WIDGET_SLOTS.forEach(context::deleteSharedPreferences)
            },
            markComplete = {
                context.settingsDataStore.edit { it[migratedFlag] = true }
            }
        )
    }

    private fun readAndValidateLegacyData(context: Context): LegacyMigrationPayload {
        val settings = context.getSharedPreferences(
            LEGACY_SETTINGS_PREFS,
            Context.MODE_PRIVATE,
        ).all
        val appInfo = context.getSharedPreferences(
            LEGACY_APP_INFO_PREFS,
            Context.MODE_PRIVATE,
        ).all
        val widgets = LEGACY_WIDGET_SLOTS.associateWith { slot ->
            context.getSharedPreferences(slot, Context.MODE_PRIVATE)
                .getString(LEGACY_WIDGET_PREFS_KEY, null)
        }
        return prepareLegacyMigration(settings, appInfo, widgets)
    }
}
