package com.jeerovan.comfer

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.Dispatchers

private const val REST_LIST_NAME = "Rest"

data class AppInfoUiState(
    val quickApps: List<AppInfo> = emptyList(),
    val primaryApps: List<AppInfo> = emptyList(),
    val restApps: List<AppInfo> = emptyList()
)

data class AppInfo(
    val background: Drawable?,
    val foreground: Drawable?,
    val label: CharSequence,
    val scale: Float,
    val packageName: String
)

fun mapPackageNameToAppInfo(
    packageManager: PackageManager,
    packageName: String?
): AppInfo? {
    if (packageName == null) return null
    val cachedIcon = AppIconCache.getIcon(packageName)
    val defaultIcon = packageManager.defaultActivityIcon
    val savedIcon = if (cachedIcon === defaultIcon) null else cachedIcon
    return try {
        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            packageManager.resolveActivity(launchIntent, 0)?.let { resolveInfo ->

                val iconDrawable = savedIcon ?: resolveInfo.loadIcon(packageManager).also {
                    AppIconCache.cacheIcon(packageName, it)
                }
                var backgroundDrawable: Drawable?
                var foregroundDrawable: Drawable?
                var scale = 0.8f
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    if(iconDrawable is AdaptiveIconDrawable) {
                        backgroundDrawable = iconDrawable.background
                        foregroundDrawable = iconDrawable.foreground
                        scale = 1.5f
                    } else {
                        backgroundDrawable = Color.Transparent.toArgb().toDrawable()
                        foregroundDrawable = iconDrawable
                    }
                } else {
                    backgroundDrawable = Color.Transparent.toArgb().toDrawable()
                    foregroundDrawable = iconDrawable
                }
                AppInfo(
                    background = backgroundDrawable,
                    foreground = foregroundDrawable,
                    scale = scale,
                    label = resolveInfo.loadLabel(packageManager),
                    packageName = packageName
                )
            }
        }
    } catch (_: Exception) {
        null
    }
}

class AppInfoViewModel(application: Application) : AndroidViewModel(application) {
    private val logger = LoggerManager(application)
    private val _uiState = MutableStateFlow(AppInfoUiState())
    val uiState: StateFlow<AppInfoUiState> = _uiState.asStateFlow()
    private var isWorking = false
    private val packageManager: PackageManager
        get() = getApplication<Application>().packageManager

    init {
        loadAppLists()
    }

    fun loadAppLists() {
        if(isWorking) return
        isWorking = true
        logger.setLog("AppInfoViewModel","LoadAppLists")
        viewModelScope.launch {
            try {
                // --- Stage 1: Fast, Main-Thread Work ---
                // 1. Get package names and determine lists (no icon loading yet)
                val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
                val allCurrentResolveInfos = packageManager.queryIntentActivities(intent, 0)
                val allCurrentPackageNames =
                    allCurrentResolveInfos.map { it.activityInfo.packageName }.toSet()

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
                    PreferenceManager.onFirstOpen(getApplication())
                    val standardApps = filterStandardApps(allCurrentPackageNames).toList()
                    finalQuickPackageNames = standardApps.take(8)
                    finalPrimaryPackageNames =
                        allCurrentPackageNames.filter { it !in finalQuickPackageNames }
                } else {
                    val addedPackages = allCurrentPackageNames - savedAllPackageNames
                    val removedPackages = savedAllPackageNames - allCurrentPackageNames

                    var currentQuickPackages =
                        savedQuickPackageNames.filter { it !in removedPackages }
                    var currentPrimaryPackages =
                        savedPrimaryPackageNames.filter { it !in removedPackages }

                    if (addedPackages.isNotEmpty()) {
                        val quickAppsCapacity = 8
                        val quickAppsSpace = quickAppsCapacity - currentQuickPackages.size
                        if (quickAppsSpace > 0) {
                            currentQuickPackages =
                                currentQuickPackages + addedPackages.take(quickAppsSpace)
                        }
                        currentPrimaryPackages =
                            currentPrimaryPackages + addedPackages.drop(quickAppsSpace)
                    }

                    finalQuickPackageNames = currentQuickPackages
                    finalPrimaryPackageNames = currentPrimaryPackages
                }

                // 2. Save the updated package name lists
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
                    allCurrentPackageNames.toList()
                )

                // 3. Create a helper function to build AppInfo objects (this will be used in both stages)
                val resolveInfoMap =
                    allCurrentResolveInfos.associateBy { it.activityInfo.packageName }

                fun createAppInfo(packageName: String): AppInfo? {
                    val resolveInfo = resolveInfoMap[packageName] ?: return null
                    val defaultIcon = packageManager.defaultActivityIcon
                    return try {
                        val cachedIcon = AppIconCache.getIcon(packageName)
                        val savedIcon = if (cachedIcon === defaultIcon) null else cachedIcon
                        val iconDrawable = savedIcon ?: resolveInfo.loadIcon(packageManager).also {
                            AppIconCache.cacheIcon(packageName, it)
                        }
                        var backgroundDrawable: Drawable?
                        var foregroundDrawable: Drawable?
                        var scale = 0.8f
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            if (iconDrawable is AdaptiveIconDrawable) {
                                backgroundDrawable = iconDrawable.background
                                foregroundDrawable = iconDrawable.foreground
                                scale = 1.5f
                            } else {
                                backgroundDrawable = Color.White.toArgb().toDrawable()
                                foregroundDrawable = iconDrawable
                            }
                        } else {
                            backgroundDrawable = Color.White.toArgb().toDrawable()
                            foregroundDrawable = iconDrawable
                        }
                        AppInfo(
                            background = backgroundDrawable,
                            foreground = foregroundDrawable,
                            scale = scale,
                            label = resolveInfo.loadLabel(packageManager),
                            packageName = packageName
                        )
                    } catch (_: Exception) {
                        // Could fail if app is being uninstalled, etc.
                        null
                    }
                }

                // 4. Load icons *only* for the quick apps list
                val quickApps = finalQuickPackageNames.mapNotNull { createAppInfo(it) }

                // 5. **Immediate UI Update**: Show the quick apps right away
                _uiState.update {
                    it.copy(
                        quickApps = quickApps,
                        primaryApps = emptyList(), // Keep these empty for now
                        restApps = emptyList()
                    )
                }

                // --- Stage 2: Slow, Background Work ---
                // 6. Launch a new coroutine for the heavy work
                viewModelScope.launch(Dispatchers.IO) {
                    // Load icons for the primary and rest of the apps
                    val primaryApps = finalPrimaryPackageNames.mapNotNull { createAppInfo(it) }

                    val quickAndPrimaryPackages =
                        finalQuickPackageNames.toSet() + finalPrimaryPackageNames.toSet()
                    val restPackages = allCurrentPackageNames - quickAndPrimaryPackages
                    val restApps = restPackages.mapNotNull { createAppInfo(it) }

                    // 7. **Final UI Update**: Update the state with the complete lists
                    _uiState.update {
                        it.copy(
                            quickApps = quickApps, // This list is already loaded
                            primaryApps = primaryApps,
                            restApps = restApps
                        )
                    }
                }
            } catch (e: Exception){
                logger.setLog("AppInfoViewModel",e.toString())
            } finally {
                isWorking = false
            }
        }
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
        "com.miui.gallery",

        // Generally available / must have
        "com.whatsapp",
        "com.facebook.katana",
        "com.google.android.gm",
        "com.google.android.chrome",
        "com.google.android.apps.maps",
        "com.google.android.youtube",
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app"
    )

    return allPackageNames.filter { packageName ->
        standardAppPackageNames.contains(packageName)
    }.toSet()
}