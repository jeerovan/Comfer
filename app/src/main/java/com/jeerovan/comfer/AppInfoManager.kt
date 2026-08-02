package com.jeerovan.comfer

import android.content.Context
import com.jeerovan.comfer.data.AppFolderEntity
import com.jeerovan.comfer.data.ComferRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class FolderData(
    val id: String,
    var title: String,
    var packages: List<String>
)

/**
 * Facade for app lists and folders. Storage is Room (app_lists / app_folders)
 * accessed through [ComferRepository], replacing the previous SharedPreferences
 * delimiter-join / JSON-map serialization. The public API is unchanged so all
 * existing callers keep working.
 */
object AppInfoManager {
    const val ALL_APPS_LIST_NAME = "all_apps"
    const val QUICK_APPS_LIST_NAME = "quick"
    const val PRIMARY_APPS_LIST_NAME = "primary"
    const val REST_APPS_LIST_NAME = "Rest"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getAppPackageNames(context: Context, listName: String): List<String>? {
        val entity = ComferRepository.getAppList(context, listName) ?: return null
        return try {
            json.decodeFromString<List<String>>(entity.packagesJson)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveAppPackageNames(context: Context, listName: String, packageNames: Collection<String>) {
        ComferRepository.saveAppList(context, listName, json.encodeToString(packageNames.toList()))
    }

    suspend fun getFolders(context: Context): Map<String, FolderData> {
        val entities = ComferRepository.getFolders(context)
        val map = mutableMapOf<String, FolderData>()
        for (e in entities) {
            val packages = try {
                json.decodeFromString<List<String>>(e.packagesJson)
            } catch (ex: Exception) {
                emptyList()
            }
            map[e.id] = FolderData(e.id, e.title, packages)
        }
        return map
    }

    /** Persist folders directly; the caller provides the coroutine context. */
    suspend fun saveFolders(context: Context, folders: Map<String, FolderData>) {
        val entities = folders.values.map { f ->
            AppFolderEntity(
                f.id,
                f.title,
                try { json.encodeToString(f.packages) } catch (e: Exception) { "[]" }
            )
        }
        ComferRepository.saveFolders(context, entities)
    }
}

