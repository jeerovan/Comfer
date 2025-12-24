package com.jeerovan.comfer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AppInfoManager {
    private const val PREFS_NAME = "com.jeerovan.comfer.AppInfoPrefs"
    const val ALL_APPS_LIST_NAME = "all_apps"
    const val QUICK_APPS_LIST_NAME = "quick"
    const val PRIMARY_APPS_LIST_NAME = "primary"
    private const val DELIMITER = "‚��‚"


    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAppPackageNames(context: Context, listName: String): List<String>? {
        val prefs = getSharedPreferences(context)
        return prefs.getString(listName, null)?.split(DELIMITER)?.filter { it.isNotEmpty() }
    }

    fun saveAppPackageNames(context: Context, listName: String, packageNames: Collection<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            getSharedPreferences(context).edit(commit = true) {
                val stringToSave = packageNames.joinToString(DELIMITER)
                putString(listName, stringToSave)
            }
        }
    }
}
