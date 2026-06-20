package com.jeerovan.comfer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class FolderData(
    val id: String,
    var title: String,
    var packages: List<String>
)
object AppInfoManager {
    private const val PREFS_NAME = "com.jeerovan.comfer.AppInfoPrefs"
    const val ALL_APPS_LIST_NAME = "all_apps"
    const val QUICK_APPS_LIST_NAME = "quick"
    const val PRIMARY_APPS_LIST_NAME = "primary"
    const val REST_APPS_LIST_NAME = "Rest"
    private const val DELIMITER = "‚��‚"

    private const val FOLDERS_PREF_KEY = "folders_data"


    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAppPackageNames(context: Context, listName: String): List<String>? {
        val prefs = getSharedPreferences(context)
        return prefs.getString(listName, null)?.split(DELIMITER)?.filter { it.isNotEmpty() }
    }

    fun saveAppPackageNames(context: Context, listName: String, packageNames: Collection<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            getSharedPreferences(context).edit {
                val stringToSave = packageNames.joinToString(DELIMITER)
                putString(listName, stringToSave)
            }
        }
    }

    fun getFolders(context: Context): Map<String, FolderData> {
        val prefs = getSharedPreferences(context)
        val jsonString = prefs.getString(FOLDERS_PREF_KEY, "{}") ?: "{}"
        val map = mutableMapOf<String, FolderData>()
        try {
            val jsonObject = JSONObject(jsonString)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val folderObj = jsonObject.getJSONObject(key)
                val title = folderObj.getString("title")
                val packagesArray = folderObj.getJSONArray("packages")
                val packages = mutableListOf<String>()
                for (i in 0 until packagesArray.length()) {
                    packages.add(packagesArray.getString(i))
                }
                map[key] = FolderData(key, title, packages)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    fun saveFolders(context: Context, folders: Map<String, FolderData>) {
        CoroutineScope(Dispatchers.IO).launch {
            val jsonObject = JSONObject()
            try {
                for ((key, folder) in folders) {
                    val folderObj = JSONObject().apply {
                        put("title", folder.title)
                        val packagesArray = JSONArray()
                        folder.packages.forEach { packagesArray.put(it) }
                        put("packages", packagesArray)
                    }
                    jsonObject.put(key, folderObj)
                }
                getSharedPreferences(context).edit {
                    putString(FOLDERS_PREF_KEY, jsonObject.toString())
                    commit() // Keep commit() as true was in your original code to avoid ANRs in quick sequence changes
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
