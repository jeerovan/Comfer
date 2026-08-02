package com.jeerovan.comfer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Thin data-access layer over Room. Exposes suspend functions for coroutine
 * callers and blocking helpers for the legacy synchronous facades
 * (AppInfoManager, image-data readers) so existing call sites keep working
 * during migration.
 */
object ComferRepository {

    private fun db(context: Context): ComferDatabase = ComferDatabase.get(context)

    // ---------- App lists ----------

    suspend fun getAppList(context: Context, name: String): AppListEntity? =
        db(context).appListDao().get(name)

    suspend fun saveAppList(context: Context, name: String, packagesJson: String) {
        db(context).appListDao().upsert(AppListEntity(name, packagesJson))
    }

    suspend fun getAppLists(context: Context): List<AppListEntity> =
        db(context).appListDao().getAll()

    // ---------- Folders ----------

    suspend fun getFolders(context: Context): List<AppFolderEntity> =
        db(context).appFolderDao().getAll()

    suspend fun saveFolders(context: Context, folders: List<AppFolderEntity>) {
        db(context).appFolderDao().upsertAll(folders)
    }

    suspend fun getFolder(context: Context, id: String): AppFolderEntity? =
        db(context).appFolderDao().get(id)

    // ---------- Widget placements ----------

    suspend fun getWidgetPlacement(context: Context, slot: String): WidgetPlacementEntity? =
        db(context).widgetPlacementDao().get(slot)

    suspend fun saveWidgetPlacement(context: Context, slot: String, widgetsJson: String) {
        db(context).widgetPlacementDao().upsert(WidgetPlacementEntity(slot, widgetsJson))
    }

    // ---------- Image data ----------

    suspend fun getImageEntry(context: Context, id: String): ImageDataEntity? =
        db(context).imageDataDao().get(id)

    suspend fun saveImageEntry(context: Context, entry: ImageDataEntity) {
        db(context).imageDataDao().upsert(entry)
    }

    // ---------- Settings (optional single-store table) ----------

    suspend fun getSetting(context: Context, key: String): SettingEntity? =
        db(context).settingDao().get(key)

    suspend fun saveSetting(context: Context, setting: SettingEntity) {
        db(context).settingDao().upsert(setting)
    }

    // ---------- Blocking (legacy synchronous facades) ----------

    fun getFoldersBlocking(context: Context): List<AppFolderEntity> =
        runBlocking(Dispatchers.IO) { getFolders(context) }

    fun getAppListBlocking(context: Context, name: String): AppListEntity? =
        runBlocking(Dispatchers.IO) { getAppList(context, name) }

    fun getWidgetPlacementBlocking(context: Context, slot: String): WidgetPlacementEntity? =
        runBlocking(Dispatchers.IO) { getWidgetPlacement(context, slot) }

    fun getImageEntryBlocking(context: Context, id: String): ImageDataEntity? =
        runBlocking(Dispatchers.IO) { getImageEntry(context, id) }
}
