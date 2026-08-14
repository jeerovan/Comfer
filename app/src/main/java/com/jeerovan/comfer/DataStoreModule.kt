package com.jeerovan.comfer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// 1. Create the DataStore extension
val Context.dataStore by preferencesDataStore(name = "wallpaper_settings")

// Scalar settings store (replaces com.jeerovan.comfer.Prefs SharedPreferences).
internal const val SETTINGS_DATASTORE_NAME = "comfer_settings"
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_DATASTORE_NAME)

// 2. Define your keys
object PreferenceKeys {
    val WALLPAPER_UPDATE = longPreferencesKey("wallpaper_update")
    val WALLPAPER_CHANGE = longPreferencesKey("wallpaper_change")
    val WALLPAPER_RESET = longPreferencesKey("wallpaper_reset")
    val ICON_PACK_LOAD = longPreferencesKey("icon_pack_load")
}
