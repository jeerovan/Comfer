package com.jeerovan.comfer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// 1. Create the DataStore extension
val Context.dataStore by preferencesDataStore(name = "wallpaper_settings")

// 2. Define your keys
object PreferenceKeys {
    val WALLPAPER_UPDATE = longPreferencesKey("wallpaper_update")
    val WALLPAPER_CHANGE = longPreferencesKey("wallpaper_change")
}
