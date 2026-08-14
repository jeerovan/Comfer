package com.jeerovan.comfer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

internal const val COMFER_DATABASE_NAME = "comfer.db"

@Database(
    entities = [
        AppListEntity::class,
        AppFolderEntity::class,
        WidgetPlacementEntity::class,
        ImageDataEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ComferDatabase : RoomDatabase() {
    abstract fun appListDao(): AppListDao
    abstract fun appFolderDao(): AppFolderDao
    abstract fun widgetPlacementDao(): WidgetPlacementDao
    abstract fun imageDataDao(): ImageDataDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var instance: ComferDatabase? = null

        /**
         * Process-wide singleton. Called lazily from any thread; the same
         * activity in this app attaches a single Application context.
         */
        fun get(context: Context): ComferDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ComferDatabase::class.java,
                    COMFER_DATABASE_NAME
                ).build().also { instance = it }
            }
    }
}
