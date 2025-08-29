package com.jeerovan.comfer

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeerovan.comfer.utils.CommonUtil.findOutermostColor
import com.jeerovan.comfer.utils.CommonUtil.toBitmapSafely
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
    val resolveInfo: ResolveInfo?,
    val icon: Drawable?,
    val color: Color,
    val label: CharSequence,
    val packageName: String
)

fun mapPackageNameToAppInfo(
    packageManager: PackageManager,
    packageName: String?
): AppInfo? {
    if (packageName == null) return null
    val cachedIcon = AppIconCache.getIcon(packageName)
    val color = AppIconCache.getColor(packageName)
    return try {
        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            packageManager.resolveActivity(launchIntent, 0)?.let { resolveInfo ->
                val icon = cachedIcon ?: resolveInfo.loadIcon(packageManager).also {
                    AppIconCache.cacheIcon(packageName, it)
                }
                AppInfo(
                    resolveInfo = resolveInfo,
                    icon = icon,
                    color = if (color == null) { Color.White } else { Color(color)},
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
            // 1. Get all currently installed launchable apps
            val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val allCurrentResolveInfos = packageManager.queryIntentActivities(intent, 0)
            val allCurrentPackageNames = allCurrentResolveInfos.map { it.activityInfo.packageName }.toSet()

            // 2. Load previously saved app lists, preserving order
            val savedQuickPackageNames = AppInfoManager.getAppPackageNames(
                getApplication(),
                AppInfoManager.QUICK_APPS_LIST_NAME
            ) ?: emptyList()
            val savedPrimaryPackageNames = AppInfoManager.getAppPackageNames(
                getApplication(),
                AppInfoManager.PRIMARY_APPS_LIST_NAME
            ) ?: emptyList()
            val savedAllPackageNames = AppInfoManager.getAppPackageNames(
                getApplication(),
                AppInfoManager.ALL_APPS_LIST_NAME
            )?.toSet() ?: emptySet()

            val isFirstLaunch = savedAllPackageNames.isEmpty()

            val finalQuickPackageNames: List<String>
            val finalPrimaryPackageNames: List<String>

            if (isFirstLaunch) {
                // 3a. First launch: Populate lists with standard and other apps
                PreferenceManager.onFirstOpen(getApplication())
                val standardApps = filterStandardApps(allCurrentPackageNames).toList()
                finalQuickPackageNames = standardApps.take(4)
                finalPrimaryPackageNames = allCurrentPackageNames.filter { it !in finalQuickPackageNames }
            } else {
                // 3b. Subsequent launch: Update lists based on installed/uninstalled apps
                val addedPackages = allCurrentPackageNames - savedAllPackageNames
                val removedPackages = savedAllPackageNames - allCurrentPackageNames

                // Remove uninstalled apps, preserving order
                var currentQuickPackages = savedQuickPackageNames.filter { it !in removedPackages }
                var currentPrimaryPackages = savedPrimaryPackageNames.filter { it !in removedPackages }

                // Add newly installed apps, preserving order. New apps are added to the end.
                if (addedPackages.isNotEmpty()) {
                    val quickAppsCapacity = 4
                    val quickAppsSpace = quickAppsCapacity - currentQuickPackages.size
                    if (quickAppsSpace > 0) {
                        currentQuickPackages = currentQuickPackages + addedPackages.take(quickAppsSpace)
                    }
                    currentPrimaryPackages = currentPrimaryPackages + addedPackages.drop(quickAppsSpace)
                }
                // If quick apps has 5, move last one to primary
                if (currentQuickPackages.size == 5){
                    val lastPackage = currentQuickPackages.drop(4)
                    currentQuickPackages = currentQuickPackages.take(4)
                    currentPrimaryPackages = lastPackage + currentPrimaryPackages
                }

                finalQuickPackageNames = currentQuickPackages
                finalPrimaryPackageNames = currentPrimaryPackages
            }

            // 4. Save the updated lists for the next launch
            AppInfoManager.saveAppPackageNames(
                getApplication(),
                AppInfoManager.QUICK_APPS_LIST_NAME,
                finalQuickPackageNames
            )
            AppInfoManager.saveAppPackageNames(
                getApplication(),
                AppInfoManager.PRIMARY_APPS_LIST_NAME,
                finalPrimaryPackageNames
            )
            AppInfoManager.saveAppPackageNames(
                getApplication(),
                AppInfoManager.ALL_APPS_LIST_NAME,
                allCurrentPackageNames.toList() // Order doesn't matter for this one
            )

            // 5. Efficiently create AppInfo objects and update UI state
            val resolveInfoMap = allCurrentResolveInfos.associateBy { it.activityInfo.packageName }

            fun createAppInfo(packageName: String): AppInfo? {
                val resolveInfo = resolveInfoMap[packageName] ?: return null
                val defaultIcon = packageManager.defaultActivityIcon
                return try {
                    val cachedIcon = AppIconCache.getIcon(packageName)
                    val savedIcon = if (cachedIcon === defaultIcon) null else cachedIcon
                    val icon = savedIcon ?: resolveInfo.loadIcon(packageManager).also {
                        AppIconCache.cacheIcon(packageName, it)
                    }
                    var colourInt: Int? = AppIconCache.getColor(packageName)
                    if (colourInt == null){
                        val bitmap = icon.toBitmapSafely(
                            width = 16,
                            height = 16
                        )
                        if(bitmap != null) {
                            colourInt =
                                findOutermostColor(bitmap, Color.White.toArgb())
                            AppIconCache.cacheColor(packageName,colourInt)
                        }
                    }
                    val color = if(colourInt == null) { Color.White } else {Color(colourInt)}
                    AppInfo(
                        resolveInfo = resolveInfo,
                        icon = icon,
                        color = color,
                        label = resolveInfo.loadLabel(packageManager),
                        packageName = packageName
                    )
                } catch (e: Exception) {
                    // Could fail if app is being uninstalled, etc.
                    null
                }
            }

            val quickApps = finalQuickPackageNames.mapNotNull { createAppInfo(it) }
            val primaryApps = finalPrimaryPackageNames.mapNotNull { createAppInfo(it) }

            val quickAndPrimaryPackages = finalQuickPackageNames.toSet() + finalPrimaryPackageNames.toSet()
            val restPackages = allCurrentPackageNames - quickAndPrimaryPackages
            val restApps = restPackages.mapNotNull { createAppInfo(it) }

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