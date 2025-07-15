package com.jeerovan.comfer

import android.content.Context
import androidx.core.content.edit

object PreferenceManager {
    private const val PREF_BACKGROUND_IMAGE = "background_image"
    private const val PREFS_NAME = "com.jeerovan.comfer.Prefs"
    private const val KEY_WALLPAPER_MOTION = "wallpaper_motion"
    private const val KEY_ICON_SIZE = "icon_size"

    private fun getPrefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setWallpaperMotion(context: Context, enabled: Boolean) {
        getPrefs(context).edit {
            putBoolean(KEY_WALLPAPER_MOTION, enabled)
        }
    }

    fun getWallpaperMotion(context: Context, default: Boolean = true): Boolean {
        return getPrefs(context).getBoolean(KEY_WALLPAPER_MOTION, default)
    }

    fun setIconSize(context: Context, size: Int) {
        getPrefs(context).edit {
            putInt(KEY_ICON_SIZE, size)
        }
    }

    fun getIconSize(context: Context, default: Int = 48): Int {
        return getPrefs(context).getInt(KEY_ICON_SIZE, default)
    }

    fun getBackgroundImagePath(context: Context): String? {
        return getPrefs(context).getString(PREF_BACKGROUND_IMAGE, null)
    }

    fun setBackgroundImagePath(context: Context, path: String) {
        getPrefs(context).edit {
            putString(PREF_BACKGROUND_IMAGE, path)
        }
    }

    fun getSwipeApp(context:Context,swipeDirection:String):String?{
        return getPrefs(context).getString("${swipeDirection}_swipe_app",null)
    }

    fun setSwipeApp(context:Context,swipeDirection:String,appPackage:String){
        getPrefs(context).edit {
            putString("${swipeDirection}_swipe_app",appPackage)
        }
    }

    fun getBoolean(context: Context,key:String):Boolean{
        return getPrefs(context).getBoolean(key,false)
    }
    fun setBoolean(context: Context,key:String,state:Boolean) {
        getPrefs(context).edit {
            putBoolean(key,state)
        }
    }
}
