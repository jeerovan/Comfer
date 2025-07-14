package com.jeerovan.comfer

import android.content.Context
import androidx.core.content.edit

object PreferenceManager {
    private const val PREF_BACKGROUND_IMAGE = "background_image"
    private const val PREFS_NAME = "com.jeerovan.comfer.Prefs"

    private fun getString(context: Context,string:String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(string, null)
    }
    private fun putString(context: Context,key:String,value:String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(key, value)
            apply()
        }
    }
    fun getBackgroundImagePath(context: Context): String? {
        return getString(context,PREF_BACKGROUND_IMAGE)
    }

    fun setBackgroundImagePath(context: Context, path: String) {
        putString(context,PREF_BACKGROUND_IMAGE,path)
    }
}
