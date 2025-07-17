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

fun mapPackageNameToAppInfo(
    packageManager: PackageManager,
    packageName: String?
): AppInfo? {
    if (packageName == null) return null
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

class AppInfoViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppInfoUiState())
    val uiState: StateFlow<AppInfoUiState> = _uiState.asStateFlow()

    private val packageManager: PackageManager
        get() = getApplication<Application>().packageManager

    init {
        loadAppLists()
    }

    fun moveAppInList(listName: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val currentList = when (listName) {
                AppInfoManager.QUICK_APPS_LIST_NAME -> _uiState.value.quickApps
                AppInfoManager.PRIMARY_APPS_LIST_NAME -> _uiState.value.primaryApps
                REST_LIST_NAME -> _uiState.value.restApps
                else -> return@launch
            }.toMutableList()

            val app = currentList.removeAt(fromIndex)
            currentList.add(toIndex, app)
            val packageNames = currentList.map { it.packageName }

            when (listName) {
                AppInfoManager.QUICK_APPS_LIST_NAME -> {
                    AppInfoManager.saveAppPackageNames(getApplication(), listName, packageNames)
                    _uiState.update { it.copy(quickApps = currentList) }
                }

                AppInfoManager.PRIMARY_APPS_LIST_NAME -> {
                    AppInfoManager.saveAppPackageNames(getApplication(), listName, packageNames)
                    _uiState.update { it.copy(primaryApps = currentList) }
                }

                REST_LIST_NAME -> {
                    _uiState.update { it.copy(restApps = currentList) }
                }
            }
        }
    }

    fun loadAppLists() {
        viewModelScope.launch {
            val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val allResolveInfos = packageManager.queryIntentActivities(intent, 0)
            val packageNames = allResolveInfos.map { it.activityInfo.packageName }.toSet()

            var quickPackageNames = AppInfoManager.getAppPackageNames(
                getApplication(),
                AppInfoManager.QUICK_APPS_LIST_NAME
            )?.toSet() ?: emptySet()
            var primaryPackageNames = AppInfoManager.getAppPackageNames(
                getApplication(),
                AppInfoManager.PRIMARY_APPS_LIST_NAME
            )?.toSet() ?: emptySet()
            val allPackageNames = AppInfoManager.getAppPackageNames(
                getApplication(),
                AppInfoManager.ALL_APPS_LIST_NAME
            )?.toSet() ?: emptySet()

            if(allPackageNames.isNotEmpty()) { // for newly added/removed apps
                val addedPackages = packageNames.filter { it !in allPackageNames }
                if(addedPackages.isNotEmpty()){
                    val quickCanHave = 5 - quickPackageNames.size
                    quickPackageNames = quickPackageNames + addedPackages.take(quickCanHave)
                    AppInfoManager.saveAppPackageNames(
                        getApplication(),
                        AppInfoManager.QUICK_APPS_LIST_NAME,
                        quickPackageNames
                    )
                    primaryPackageNames = addedPackages.drop(quickCanHave).toSet() + primaryPackageNames
                    AppInfoManager.saveAppPackageNames(
                        getApplication(),
                        AppInfoManager.PRIMARY_APPS_LIST_NAME,
                        primaryPackageNames
                    )
                }
                val removedPackages = allPackageNames.filter {it !in packageNames}
                if(removedPackages.isNotEmpty()) {
                    quickPackageNames = quickPackageNames - removedPackages.toSet()
                    AppInfoManager.saveAppPackageNames(
                        getApplication(),
                        AppInfoManager.QUICK_APPS_LIST_NAME,
                        quickPackageNames
                    )
                    primaryPackageNames = primaryPackageNames - removedPackages.toSet()
                    AppInfoManager.saveAppPackageNames(
                        getApplication(),
                        AppInfoManager.PRIMARY_APPS_LIST_NAME,
                        primaryPackageNames
                    )
                }
            }

            if (primaryPackageNames.isEmpty()) { // First time launch or cache cleared
                val quickAppListAll: List<String> = filterStandardApps(packageNames).toList()
                val quickAppList = quickAppListAll.take(5)
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
                )
            }
            AppInfoManager.saveAppPackageNames(
                getApplication(),
                AppInfoManager.ALL_APPS_LIST_NAME,
                packageNames
            )

            val allApps = packageNames.mapNotNull { mapPackageNameToAppInfo(packageManager, it) }

            val quickApps = quickPackageNames.mapNotNull { mapPackageNameToAppInfo(packageManager, it) }

            val primaryApps =
                primaryPackageNames.mapNotNull { mapPackageNameToAppInfo(packageManager, it) }

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

    fun moveAppsToList(fromListName: String, toListName: String, appIndexes: List<Int>) {
        viewModelScope.launch {
            val currentState = _uiState.value

            val fromList = when (fromListName) {
                AppInfoManager.QUICK_APPS_LIST_NAME -> currentState.quickApps
                AppInfoManager.PRIMARY_APPS_LIST_NAME -> currentState.primaryApps
                REST_LIST_NAME -> currentState.restApps
                else -> return@launch
            }

            val appsToMove = appIndexes.map { fromList[it] }
            val appsToMovePackageNames = appsToMove.map { it.packageName }.toSet()

            var newQuickApps = currentState.quickApps
            var newPrimaryApps = currentState.primaryApps

            // Remove from source list
            when (fromListName) {
                AppInfoManager.QUICK_APPS_LIST_NAME ->
                    newQuickApps = newQuickApps.filter { it.packageName !in appsToMovePackageNames }
                AppInfoManager.PRIMARY_APPS_LIST_NAME ->
                    newPrimaryApps = newPrimaryApps.filter { it.packageName !in appsToMovePackageNames }
                REST_LIST_NAME -> {
                    // No change to quick/primary lists when removing from rest.
                    // The apps will be added to a target list below.
                }
            }

            // Add to destination list
            when (toListName) {
                AppInfoManager.QUICK_APPS_LIST_NAME ->
                    newQuickApps = newQuickApps + appsToMove
                AppInfoManager.PRIMARY_APPS_LIST_NAME ->
                    newPrimaryApps = newPrimaryApps + appsToMove
                REST_LIST_NAME -> {
                    // Moving to REST_LIST_NAME means removing from a persisted list.
                    // This is already handled in the "Remove from source list" block.
                }
            }

            // Save the updated persisted lists
            AppInfoManager.saveAppPackageNames(getApplication(), AppInfoManager.QUICK_APPS_LIST_NAME, newQuickApps.map { it.packageName })
            AppInfoManager.saveAppPackageNames(getApplication(), AppInfoManager.PRIMARY_APPS_LIST_NAME, newPrimaryApps.map { it.packageName })

            // Recalculate restApps
            val allApps = currentState.quickApps + currentState.primaryApps + currentState.restApps
            val quickAndPrimaryPackages = newQuickApps.map { it.packageName }.toSet() + newPrimaryApps.map { it.packageName }.toSet()
            val newRestApps = allApps.filter { it.packageName !in quickAndPrimaryPackages }.distinctBy { it.packageName }

            _uiState.update {
                it.copy(
                    quickApps = newQuickApps,
                    primaryApps = newPrimaryApps,
                    restApps = newRestApps
                )
            }
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