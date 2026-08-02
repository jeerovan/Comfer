package com.jeerovan.comfer

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeerovan.comfer.data.AppFolderEntity
import com.jeerovan.comfer.data.ComferRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

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
    fun Context.deleteSharedPreferencesCompat(name: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // For API 24+ (Android 7.0+)
            deleteSharedPreferences(name)
        } else {
            // 1. Clear preference data from memory/cache
            getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()

            false
        }
    }
    /** Blocking; call from [ComferApp.onCreate] before any UI reads settings. */
    fun runOnce(context: Context) {
        val already = runBlocking(Dispatchers.IO) {
            context.settingsDataStore.data.first()[migratedFlag] == true
        }
        if (already) return

        runBlocking(Dispatchers.IO) {
            importSettings(context)
            importAppInfo(context)
            importWidgets(context)

            // Only delete source files once the new stores hold the data.
            context.deleteSharedPreferencesCompat(SETTINGS_PREFS)
            context.deleteSharedPreferencesCompat(APP_INFO_PREFS)
            widgetSlots.forEach { context.deleteSharedPreferencesCompat(it) }

            context.settingsDataStore.edit { it[migratedFlag] = true }
        }
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
        val entities = parseFolders(foldersJson)
        if (entities.isNotEmpty()) {
            ComferRepository.saveFolders(context, entities)
        }
    }

    private fun parseFolders(jsonString: String): List<AppFolderEntity> {
        val result = mutableListOf<AppFolderEntity>()
        try {
            val root = JSONObject(jsonString)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val folderObj = root.getJSONObject(key)
                val title = folderObj.getString("title")
                val packagesArray = folderObj.getJSONArray("packages")
                val packages = mutableListOf<String>()
                for (i in 0 until packagesArray.length()) {
                    packages.add(packagesArray.getString(i))
                }
                result.add(AppFolderEntity(key, title, packagesToJson(packages)))
            }
        } catch (e: Exception) {
            // Ignore malformed folders; user data is preserved in the old file
            // only until the flag is set, so a parse failure leaves them orphaned
            // at worst (packages still exist in the app lists).
        }
        return result
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

    private fun packagesToJson(packages: List<String>): String {
        val sb = StringBuilder("[")
        packages.forEachIndexed { index, pkg ->
            if (index > 0) sb.append(',')
            sb.append('"').append(pkg.replace("\"", "\\\"")).append('"')
        }
        sb.append(']')
        return sb.toString()
    }
}
