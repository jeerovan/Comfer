package com.jeerovan.comfer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per app list. [id] is the list name: all_apps / quick / primary / Rest.
 * [packagesJson] is a JSON array of package names.
 */
@Entity(tableName = "app_lists")
data class AppListEntity(
    @PrimaryKey val id: String,
    val packagesJson: String
)

/**
 * One row per folder. [packagesJson] is a JSON array of package names.
 */
@Entity(tableName = "app_folders")
data class AppFolderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val packagesJson: String
)

/**
 * One row per widget host page. [slot] is widgets_center / widgets_prefs_left /
 * widgets_prefs_right. [widgetsJson] is the JSON-serialized
 * List<PersistableBoundWidget> for that page.
 */
@Entity(tableName = "widget_placements")
data class WidgetPlacementEntity(
    @PrimaryKey val slot: String,
    val widgetsJson: String
)

/**
 * Current + pending wallpaper image data. [json] is the serialized ImageData.
 */
@Entity(tableName = "image_data")
data class ImageDataEntity(
    @PrimaryKey val id: String,
    val isTemp: Boolean,
    val imageAvailable: Boolean,
    val json: String
)

/**
 * Generic key/value setting row. Used only if a single store is required;
 * scalar settings otherwise live in Preferences DataStore (see
 * PreferenceManager), which prefers streams over arbitrary rows.
 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val type: String
)
