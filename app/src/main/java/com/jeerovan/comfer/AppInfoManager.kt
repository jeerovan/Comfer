package com.jeerovan.comfer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object AppInfoManager {
    private const val PREFS_NAME = "com.jeerovan.comfer.AppInfoPrefs"
    const val ALL_APPS_LIST_NAME = "all_apps"
    const val QUICK_APPS_LIST_NAME = "quick"
    const val PRIMARY_APPS_LIST_NAME = "primary"


    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAppPackageNames(context: Context, listName: String): Set<String>? {
        return getSharedPreferences(context).getStringSet(listName, null)
    }

    fun saveAppPackageNames(context: Context, listName: String, packageNames: Set<String>) {
        getSharedPreferences(context).edit().putStringSet(listName, packageNames).apply()
    }

    fun addAppToLayer(context: Context, listName: String, packageName: String) {
        val packageNames = getAppPackageNames(context, listName)?.toMutableSet() ?: mutableSetOf()
        packageNames.add(packageName)
        saveAppPackageNames(context, listName, packageNames)
    }

    fun removeAppFromLayer(context: Context, listName: String, packageName: String) {
        val packageNames = getAppPackageNames(context, listName)?.toMutableSet() ?: return
        packageNames.remove(packageName)
        saveAppPackageNames(context, listName, packageNames)
    }
}
