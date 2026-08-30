package com.jeerovan.comfer.data

import android.content.Context
import androidx.room.withTransaction

data class RoomDataSnapshot(
    val appLists: List<AppListEntity>,
    val folders: List<AppFolderEntity>,
    val widgetPlacements: List<WidgetPlacementEntity>,
    val imageData: List<ImageDataEntity>,
    val settings: List<SettingEntity>,
)

/**
 * Thin data-access layer over Room. All functions are suspend and must be
 * called from a coroutine (e.g. viewModelScope/Dispatchers.IO) — never from the
 * main thread. The former synchronous `runBlocking` facades were removed
 * because any accidental main-thread call stalled/ANR'd the UI.
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

    // ---------- Complete backup / restore snapshot ----------

    suspend fun snapshot(context: Context): RoomDataSnapshot {
        val database = db(context)
        return database.withTransaction {
            RoomDataSnapshot(
                appLists = database.appListDao().getAll(),
                folders = database.appFolderDao().getAll(),
                widgetPlacements = database.widgetPlacementDao().getAll(),
                imageData = database.imageDataDao().getAll(),
                settings = database.settingDao().getAll(),
            )
        }
    }

    suspend fun replaceSnapshot(context: Context, snapshot: RoomDataSnapshot) {
        val database = db(context)
        database.withTransaction {
            database.appListDao().deleteAll()
            database.appFolderDao().deleteAll()
            database.widgetPlacementDao().deleteAll()
            database.imageDataDao().deleteAll()
            database.settingDao().deleteAll()

            database.appListDao().upsertAll(snapshot.appLists)
            database.appFolderDao().upsertAll(snapshot.folders)
            database.widgetPlacementDao().upsertAll(snapshot.widgetPlacements)
            database.imageDataDao().upsertAll(snapshot.imageData)
            database.settingDao().upsertAll(snapshot.settings)
        }
    }
}
