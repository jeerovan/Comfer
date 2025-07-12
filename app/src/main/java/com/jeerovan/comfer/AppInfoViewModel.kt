package com.jeerovan.comfer

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val REST_LIST_NAME = "Rest"

data class AppInfoUiState(
    val quickApps: List<AppInfo> = emptyList(),
    val primaryApps: List<AppInfo> = emptyList(),
    val restApps: List<AppInfo> = emptyList()
)

data class AppInfo(
    val resolveInfo: ResolveInfo,
    val icon: Drawable,
    val label: CharSequence,
    val packageName: String
)

class AppInfoViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppInfoUiState())
    val uiState: StateFlow<AppInfoUiState> = _uiState.asStateFlow()

    private val packageManager: PackageManager
        get() = getApplication<Application>().packageManager

    init {
        loadAppLists()
    }

    fun moveApp(fromListName: String, fromIndex: Int, toListName: String, toIndex: Int) {
        viewModelScope.launch {
            if (fromListName == toListName) {
                val list = getListByName(fromListName).toMutableList()
                if (fromIndex !in list.indices) return@launch

                val targetIndex = if (toIndex == -1) list.size else toIndex
                if (targetIndex < 0 || targetIndex > list.size) return@launch

                val item = list.removeAt(fromIndex)
                val adjustedToIndex = if (fromIndex < targetIndex) targetIndex - 1 else targetIndex
                list.add(adjustedToIndex, item)
                updateStateWithNewList(fromListName, list)
            } else {
                val fromList = getListByName(fromListName).toMutableList()
                val toList = getListByName(toListName).toMutableList()
                if (fromIndex !in fromList.indices) return@launch

                val item = fromList.removeAt(fromIndex)
                val targetIndex = if (toIndex < 0 || toIndex > toList.size) toList.size else toIndex
                toList.add(targetIndex, item)

                updateStateWithNewList(fromListName, fromList)
                updateStateWithNewList(toListName, toList)
            }
        }
    }

    private fun getListByName(listName: String): List<AppInfo> {
        return when (listName) {
            AppInfoManager.QUICK_APPS_LIST_NAME -> _uiState.value.quickApps
            AppInfoManager.PRIMARY_APPS_LIST_NAME -> _uiState.value.primaryApps
            REST_LIST_NAME -> _uiState.value.restApps
            else -> emptyList()
        }
    }

    private fun updateStateWithNewList(listName: String, newList: List<AppInfo>) {
        val packageNames = newList.map { it.packageName }
        if (listName != REST_LIST_NAME) {
            AppInfoManager.saveAppPackageNames(getApplication(), listName, packageNames)
        }

        when (listName) {
            AppInfoManager.QUICK_APPS_LIST_NAME -> _uiState.update { it.copy(quickApps = newList) }
            AppInfoManager.PRIMARY_APPS_LIST_NAME -> _uiState.update { it.copy(primaryApps = newList) }
            REST_LIST_NAME -> _uiState.update { it.copy(restApps = newList) }
        }
    }


    fun loadAppLists() {
        viewModelScope.launch {
            var packageNames = AppInfoManager.getAppPackageNames(
                getApplication(),
                AppInfoManager.ALL_APPS_LIST_NAME
            )?.toSet() ?: emptySet()

            if (packageNames.isEmpty()) { // First time launch or cache cleared
                val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
                val allResolveInfos = packageManager.queryIntentActivities(intent, 0)
                packageNames = allResolveInfos.map { it.activityInfo.packageName }.toSet()
                AppInfoManager.saveAppPackageNames(
                    getApplication(),
                    AppInfoManager.ALL_APPS_LIST_NAME,
                    packageNames
                )
                val quickAppList: List<String> = filterStandardApps(packageNames).toList()
                AppInfoManager.saveAppPackageNames(
                    getApplication(),
                    AppInfoManager.QUICK_APPS_LIST_NAME,
                    quickAppList
                )
                val primaryAppList: List<String> = packageNames.filter { packageName ->
                    !quickAppList.contains(packageName)
                }.toList()
                AppInfoManager.saveAppPackageNames(
                    getApplication(),
                    AppInfoManager.PRIMARY_APPS_LIST_NAME,
                    primaryAppList
                ) // Default primary to all
            }

            val allApps = packageNames.mapNotNull { mapPackageNameToAppInfo(packageManager, it) }

            val quickAppNames = AppInfoManager.getAppPackageNames(
                getApplication(),
                AppInfoManager.QUICK_APPS_LIST_NAME
            ) ?: emptyList()
            val quickApps = quickAppNames.mapNotNull { mapPackageNameToAppInfo(packageManager, it) }

            val primaryAppNames = AppInfoManager.getAppPackageNames(
                getApplication(),
                AppInfoManager.PRIMARY_APPS_LIST_NAME
            ) ?: emptyList()
            val primaryApps =
                primaryAppNames.mapNotNull { mapPackageNameToAppInfo(packageManager, it) }

            val quickAndPrimaryPackages =
                quickApps.map { it.packageName }.toSet() + primaryApps.map { it.packageName }
                    .toSet()
            val restApps = allApps.filter { it.packageName !in quickAndPrimaryPackages }

            _uiState.update {
                it.copy(
                    quickApps = quickApps,
                    primaryApps = primaryApps,
                    restApps = restApps
                )
            }
        }
    }

    private fun mapPackageNameToAppInfo(
        packageManager: PackageManager,
        packageName: String
    ): AppInfo? {
        val cachedIcon = AppIconCache.getIcon(packageName)
        return try {
            packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
                packageManager.resolveActivity(launchIntent, 0)?.let { resolveInfo ->
                    val icon = cachedIcon ?: resolveInfo.loadIcon(packageManager).also {
                        AppIconCache.cacheIcon(packageName, it)
                    }
                    AppInfo(
                        resolveInfo = resolveInfo,
                        icon = icon,
                        label = resolveInfo.loadLabel(packageManager),
                        packageName = packageName
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

fun filterStandardApps(allPackageNames: Set<String>): Set<String> {
    val standardAppPackageNames = setOf(
        // Telephony/Dialer
        "com.android.dialer",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.providers.telephony",
        "com.google.android.dialer",
        "com.google.android.apps.messaging",
        "com.samsung.android.dialer",
        "com.samsung.android.contacts",
        "com.samsung.android.app.telephonyui",
        "com.miui.dialer",
        "com.android.contacts", // Xiaomi
        "com.android.mms",      // Xiaomi

        // Camera
        "com.android.camera",
        "com.android.camera2",
        "com.google.android.camera",
        "com.sec.android.app.camera",
        "com.samsung.android.camera.internal",
        "com.miui.camera",

        // Gallery
        "com.android.gallery3d",
        "com.android.gallery",
        "com.google.android.apps.photos",
        "com.sec.android.gallery3d",
        "com.samsung.android.gallery",
        "com.miui.gallery"
        // Add more as you discover them
    )

    return allPackageNames.filter { packageName ->
        standardAppPackageNames.contains(packageName)
    }.toSet()
}
