package com.jeerovan.comfer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppListDao {
    @Query("SELECT * FROM app_lists")
    suspend fun getAll(): List<AppListEntity>

    @Query("SELECT * FROM app_lists WHERE id = :id")
    suspend fun get(id: String): AppListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AppListEntity>)

    @Query("DELETE FROM app_lists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM app_lists")
    suspend fun deleteAll()
}

@Dao
interface AppFolderDao {
    @Query("SELECT * FROM app_folders")
    suspend fun getAll(): List<AppFolderEntity>

    @Query("SELECT * FROM app_folders WHERE id = :id")
    suspend fun get(id: String): AppFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppFolderEntity)

    @Query("DELETE FROM app_folders WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AppFolderEntity>)

    @Query("DELETE FROM app_folders")
    suspend fun deleteAll()
}

@Dao
interface WidgetPlacementDao {
    @Query("SELECT * FROM widget_placements")
    suspend fun getAll(): List<WidgetPlacementEntity>

    @Query("SELECT * FROM widget_placements WHERE slot = :slot")
    suspend fun get(slot: String): WidgetPlacementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WidgetPlacementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WidgetPlacementEntity>)

    @Query("DELETE FROM widget_placements WHERE slot = :slot")
    suspend fun delete(slot: String)

    @Query("DELETE FROM widget_placements")
    suspend fun deleteAll()
}

@Dao
interface ImageDataDao {
    @Query("SELECT * FROM image_data")
    suspend fun getAll(): List<ImageDataEntity>

    @Query("SELECT * FROM image_data WHERE id = :id")
    suspend fun get(id: String): ImageDataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ImageDataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ImageDataEntity>)

    @Query("DELETE FROM image_data WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM image_data")
    suspend fun deleteAll()
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings WHERE key = :key")
    suspend fun get(key: String): SettingEntity?

    @Query("SELECT * FROM settings")
    suspend fun getAll(): List<SettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SettingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SettingEntity>)

    @Query("DELETE FROM settings WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM settings")
    suspend fun deleteAll()
}
