package com.jeerovan.comfer

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeerovan.comfer.data.AppFolderEntity
import com.jeerovan.comfer.data.ComferRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class LegacyFolderData(
    val title: String,
    val packages: List<String>,
)

internal fun parseLegacyFolders(jsonString: String): List<AppFolderEntity> =
    Json.decodeFromString<Map<String, LegacyFolderData>>(jsonString).map { (id, folder) ->
        AppFolderEntity(id, folder.title, Json.encodeToString(folder.packages))
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
    private const val SETTINGS_PREFS = "com.jeerovan.comfer.Prefs"
    private const val APP_INFO_PREFS = "com.jeerovan.comfer.AppInfoPrefs"
    private const val WIDGET_PREFS_KEY = "bound_widgets_v2"
    private const val FOLDERS_PREF_KEY = "folders_data"

    // Legacy per-list delimiter in AppInfoManager (U+201A U+FFFD U+FFFD U+201A).
    private const val OLD_DELIMITER = "\u201A\uFFFD\uFFFD\u201A"

    private val migratedFlag = booleanPreferencesKey("prefs_migrated_v2")

    private val widgetSlots = listOf(
        "widgets_center",
        "widgets_prefs_left",
        "widgets_prefs_right"
    )
    /** Non-blocking; call from a background coroutine (e.g. [ComferApp.onCreate]).
     *  Must never be run on the main thread — the first-ever Room build + full
     *  import can exceed the input-dispatch timeout. */
    suspend fun runOnce(context: Context) {
        if (context.settingsDataStore.data.first()[migratedFlag] == true) return

        importSettings(context)
        importAppInfo(context)
        importWidgets(context)

        // Only delete source files once the new stores hold the data.
        context.deleteSharedPreferences(SETTINGS_PREFS)
        context.deleteSharedPreferences(APP_INFO_PREFS)
        widgetSlots.forEach(context::deleteSharedPreferences)

        context.settingsDataStore.edit { it[migratedFlag] = true }
    }

    private suspend fun importSettings(context: Context) {
        val source = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val all = source.all ?: return
        for ((key, value) in all) {
            if (value == null) continue
            val prefKey = stringPreferencesKey(key)
            context.settingsDataStore.edit { it[prefKey] = value.toString() }
        }
    }

    private suspend fun importAppInfo(context: Context) {
        val source = context.getSharedPreferences(APP_INFO_PREFS, Context.MODE_PRIVATE)
        val all = source.all ?: return

        // App lists: every key except the folders blob.
        for ((key, value) in all) {
            if (key == FOLDERS_PREF_KEY) continue
            val joined = value?.toString().orEmpty()
            val packages = joined.split(OLD_DELIMITER).filter { it.isNotEmpty() }
            if (packages.isNotEmpty()) {
                ComferRepository.saveAppList(context, key, packagesToJson(packages))
            }
        }

        // Folders: JSON map { id -> {title, packages[]} }.
        val foldersJson = all[FOLDERS_PREF_KEY]?.toString() ?: "{}"
        // Malformed folder data must abort migration. runOnce then preserves all
        // legacy files and leaves the flag unset so a corrected build can retry.
        val entities = parseLegacyFolders(foldersJson)
        if (entities.isNotEmpty()) {
            ComferRepository.saveFolders(context, entities)
        }
    }

    private suspend fun importWidgets(context: Context) {
        for (slot in widgetSlots) {
            val source = context.getSharedPreferences(slot, Context.MODE_PRIVATE)
            val jsonString = source.getString(WIDGET_PREFS_KEY, null)
            if (!jsonString.isNullOrEmpty()) {
                ComferRepository.saveWidgetPlacement(context, slot, jsonString)
            }
        }
    }

    private fun packagesToJson(packages: List<String>): String = Json.encodeToString(packages)
}
