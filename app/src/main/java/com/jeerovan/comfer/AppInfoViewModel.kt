package com.jeerovan.comfer

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.get
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import androidx.core.graphics.scale

import android.content.ComponentName
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.min
private const val REST_LIST_NAME = "Rest"
private const val ICON_ANALYSIS_SIZE = 192
private const val ICON_ALPHA_THRESHOLD = 32

data class AppInfoUiState(
    val quickApps: List<AppInfo> = emptyList(),
    val primaryApps: List<AppInfo> = emptyList(),
    val restApps: List<AppInfo> = emptyList(),
)
data class WallpaperThemeColors(
    val lightBg: Int,
    val lightFg: Int,
    val darkBg: Int,
    val darkFg: Int,
    val textFg: Int,
    val textBg: Int,
)
data class AppInfo(
    val background: Drawable?,
    val foreground: Drawable?,
    val label: String,
    val scale: Float,
    val packageName: String,
    val icon: Drawable,
    val componentName: ComponentName,
    val user: UserHandle // Important for work profiles
)

private val packageManagerDispatcher = Dispatchers.IO.limitedParallelism(2)
suspend fun getAppInfo(
    context: Context,
    info: LauncherActivityInfo,
    showThemedIcons: Boolean,
    themedColors: WallpaperThemeColors,
    isLightHour: Boolean,
    iconPackPackage: String?,
    iconProcessor: ThemedIconProcessor
): AppInfo? = withContext(Dispatchers.Default) {
    try {
        val packageName = info.componentName.packageName
        val user = info.user
        val cacheKey = "$packageName" // Add userId here if you support work profiles

        // 1. Check Cache
        val cachedIcon = AppIconCache.getIcon(cacheKey)

        // 2. Load Drawable (Heavy I/O & IPC)
        // Wrapped strictly in limited parallelism to avoid ResourcesManager ANR
        val loadedDrawable = withContext(packageManagerDispatcher) {
            val customIcon = if (iconPackPackage != null) {
                IconPackManager.getCustomIcon(context, info.componentName)
            } else null
            customIcon ?: cachedIcon ?: info.getBadgedIcon(0).also { AppIconCache.cacheIcon(cacheKey, it) }
        }

        // 3. Create a mutable copy for processing to ensure thread safety
        // and prevent modifying the cached instance.
        val iconDrawable = loadedDrawable.constantState?.newDrawable()?.mutate()
            ?: loadedDrawable // Fallback if constantState is null

        var backgroundDrawable: Drawable?
        var foregroundDrawable: Drawable?

        // Wrapped label extraction in limited parallelism for the same lock-contention reasons
        val appLabel = withContext(packageManagerDispatcher) {
            info.label.toString().trim()
        }
        val foregroundColor = getThemedIconColor(themedColors, isLightHour)

        val isAdaptive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && iconDrawable is AdaptiveIconDrawable
        var scale = if (isAdaptive) 1.5f else 1f

        // 4. Heavy Image Processing (CPU bound, but fine inside IO context)
        if (isAdaptive && iconDrawable is AdaptiveIconDrawable) {
            if (showThemedIcons) {
                val backgroundColor = getThemedBackgroundColor(themedColors, isLightHour)
                backgroundDrawable = backgroundColor.toDrawable()

                foregroundDrawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Try monochrome first for Android 13+
                    iconDrawable.monochrome?.mutate()?.apply {
                        setTint(foregroundColor)
                    } ?: iconDrawable.foreground?.mutate()?.apply {
                        colorFilter = PorterDuffColorFilter(foregroundColor, PorterDuff.Mode.SRC_IN)
                    }
                } else {
                    iconProcessor.applyThemedColor(
                        iconDrawable.foreground,
                        foregroundColor,
                        backgroundColor,
                        isLightHour
                    )
                }
            } else {
                // Original adaptive icon
                backgroundDrawable = iconDrawable.background
                foregroundDrawable = iconDrawable.foreground
            }
        } else {
            // Legacy / Standard Icons
            val iconBitmap = iconDrawable.toBitmap(
                width = ICON_ANALYSIS_SIZE,
                height = ICON_ANALYSIS_SIZE,
                config = Bitmap.Config.ARGB_8888
            )
            val fallbackBackgroundColor = if (showThemedIcons) {
                getThemedBackgroundColor(themedColors, isLightHour)
            } else {
                getBackgroundColor(isLightHour).toArgb()
            }
            scale = calculateLegacyForegroundScale(iconBitmap)
            val backgroundColor = if (showThemedIcons) {
                fallbackBackgroundColor
            } else {
                derivePropagatedBackgroundColor(iconBitmap, fallbackBackgroundColor)
            }
            backgroundDrawable = backgroundColor.toDrawable()

            foregroundDrawable = if (showThemedIcons && iconDrawable != null) {
                iconProcessor.applyThemedColor(
                    iconDrawable,
                    foregroundColor,
                    backgroundColor,
                    isLightHour
                )
            } else {
                iconDrawable
            }
        }

        AppInfo(
            background = backgroundDrawable,
            foreground = foregroundDrawable,
            scale = scale,
            label = appLabel,
            packageName = packageName,
            icon = loadedDrawable,
            componentName = info.componentName,
            user = user
        )
    } catch (e: Exception) {
        // Log generic error to avoid spamming logs with specific package failures
        Log.e("getAppInfo", "Failed to load ${info.componentName.packageName}: ${e.message}")
        null
    }
}

private fun calculateLegacyForegroundScale(bitmap: Bitmap): Float {
    val bounds = findOpaqueBounds(bitmap) ?: return 1f
    val contentFraction = max(
        bounds.width().toFloat() / bitmap.width,
        bounds.height().toFloat() / bitmap.height
    )
    return (0.84f / contentFraction).coerceIn(0.92f, 1.32f)
}

private fun derivePropagatedBackgroundColor(bitmap: Bitmap, fallbackColor: Int): Int {
    val edgeColor = sampleEdgeColor(bitmap)
    val paletteColor = Palette.from(bitmap)
        .clearFilters()
        .maximumColorCount(12)
        .generate()
        .run {
            dominantSwatch?.rgb
                ?: mutedSwatch?.rgb
                ?: vibrantSwatch?.rgb
                ?: lightMutedSwatch?.rgb
                ?: darkMutedSwatch?.rgb
        }

    val sourceColor = edgeColor ?: paletteColor ?: fallbackColor
    return ColorUtils.blendARGB(
        ColorUtils.setAlphaComponent(sourceColor, 255),
        ColorUtils.setAlphaComponent(fallbackColor, 255),
        0.15f
    )
}

private fun sampleEdgeColor(bitmap: Bitmap): Int? {
    val edgeInsetX = max(1, bitmap.width / 8)
    val edgeInsetY = max(1, bitmap.height / 8)
    var red = 0L
    var green = 0L
    var blue = 0L
    var sampleCount = 0

    for (y in 0 until bitmap.height) {
        for (x in 0 until bitmap.width) {
            val isEdgePixel =
                x < edgeInsetX || x >= bitmap.width - edgeInsetX ||
                    y < edgeInsetY || y >= bitmap.height - edgeInsetY
            if (!isEdgePixel) continue

            val pixel = bitmap[x, y]
            if (android.graphics.Color.alpha(pixel) < ICON_ALPHA_THRESHOLD) continue

            red += android.graphics.Color.red(pixel)
            green += android.graphics.Color.green(pixel)
            blue += android.graphics.Color.blue(pixel)
            sampleCount++
        }
    }

    if (sampleCount < 24) return null

    return android.graphics.Color.argb(
        255,
        (red / sampleCount).toInt(),
        (green / sampleCount).toInt(),
        (blue / sampleCount).toInt()
    )
}

private fun findOpaqueBounds(bitmap: Bitmap): android.graphics.Rect? {
    var minX = bitmap.width
    var minY = bitmap.height
    var maxX = -1
    var maxY = -1

    for (y in 0 until bitmap.height) {
        for (x in 0 until bitmap.width) {
            val pixel = bitmap[x, y]
            if (android.graphics.Color.alpha(pixel) < ICON_ALPHA_THRESHOLD) continue

            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
        }
    }

    if (maxX < minX || maxY < minY) return null

    return android.graphics.Rect(minX, minY, maxX + 1, maxY + 1)
}


/**
 * Legacy helper: Tries to find an app by string package name.
 * Defaults to the CURRENT user only.
 */
suspend fun mapPackageNameToAppInfo(
    context: Context,
    packageName: String?
): AppInfo? {
    if (packageName == null) return null

    val autoWallpapers = PreferenceManager.getAutoWallpapers(context)
    val monochrome = PreferenceManager.getMonochrome(context)
    val showThemedIcons = PreferenceManager.getThemedIcons(context) && (autoWallpapers || monochrome)
    val themedColors = PreferenceManager.getThemedColors(context)
    val isLightHour = PreferenceManager.isLightHour(context)
    val iconPackPackage = PreferenceManager.getIconPack(context)

    return try {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        // process.myUserHandle() ensures we look for the app in the current profile
        // Note: This won't find the app if it ONLY exists in the Work Profile.
        // To find Work Profile apps, you must iterate userManager.userProfiles as done in the ViewModel.
        val activityList = launcherApps.getActivityList(null, android.os.Process.myUserHandle())

        val activityInfo = activityList.find { it.componentName.packageName == packageName }

        val themedIconProcessor = ThemedIconProcessor()
        if (activityInfo != null) {
            getAppInfo(
                context,
                activityInfo,
                showThemedIcons,
                themedColors,
                isLightHour,
                iconPackPackage,
                themedIconProcessor
            )
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}
class AppInfoViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppInfoUiState())
    val uiState: StateFlow<AppInfoUiState> = _uiState.asStateFlow()

    // System Services for modern launcher tracking
    private val launcherApps = application.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = application.getSystemService(Context.USER_SERVICE) as UserManager

    init {
        // Start observing system changes immediately
        observePackageChanges()
        viewModelScope.launch {
            application.dataStore.data
                .map { it[PreferenceKeys.ICON_PACK_LOAD] ?: 0L }
                .distinctUntilChanged()
                .collect { timestamp ->
                    loadIconPack()
                }
        }
    }
    private fun loadIconPack(){
        val iconPackPackage = PreferenceManager.getIconPack(getApplication())
        if(iconPackPackage != null) {
            IconPackManager.loadIconPack(getApplication(), iconPackPackage)
        } else {
            IconPackManager.unloadIconPack(getApplication())
        }
    }
    private fun observePackageChanges() {
        viewModelScope.launch {
            callbackFlow {
                val callback = object : LauncherApps.Callback() {
                    override fun onPackageAdded(packageName: String, user: UserHandle) { trySend(Unit) }
                    override fun onPackageRemoved(packageName: String, user: UserHandle) { trySend(Unit) }
                    override fun onPackageChanged(packageName: String, user: UserHandle) { trySend(Unit) }
                    override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
                    override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
                }

                // 1. Registration MUST happen on a thread with a Looper (Main)
                launcherApps.registerCallback(callback)

                trySend(Unit) // Initial load

                awaitClose {
                    // Unregister is safe to call here
                    launcherApps.unregisterCallback(callback)
                }
            }
                // 2. Remove .flowOn(Dispatchers.IO) here!
                // callbackFlow block runs on the collector's context (Main, since launched in viewModelScope).
                // This allows registerCallback to succeed.
                .collectLatest {
                    // 3. Move the background thread switch inside refreshAppLists()
                    // or use flowOn just for the collection part if needed, but simpler is:
                    refreshAppLists()
                }
        }
    }

    fun reloadList() {
        viewModelScope.launch {
            refreshAppLists()
        }
    }
    private suspend fun refreshAppLists() = withContext(Dispatchers.Default) {
        try {
            Log.i("LoadAppLists", "Loading started")

            // --- Stage 1: Fetch All Launchable Activities (Personal + Work) ---
            val allActivitiesMap = mutableMapOf<String, LauncherActivityInfo>()

            val profiles = userManager.userProfiles
            for (user in profiles) {
                val activities = launcherApps.getActivityList(null, user)
                for (info in activities) {
                    val pkg = info.componentName.packageName
                    if (!allActivitiesMap.containsKey(pkg)) {
                        allActivitiesMap[pkg] = info
                    }
                    //Original allActivitiesMap[pkg]?.add(info) not adding multiple
                }
            }

            val allCurrentPackageNames = allActivitiesMap.keys.toSet()
            val context: Application = getApplication()

            // --- Stage 2: List Management (Quick/Primary/Rest) ---
            val savedQuickPackageNames = AppInfoManager.getAppPackageNames(context, AppInfoManager.QUICK_APPS_LIST_NAME) ?: emptyList()
            val savedPrimaryPackageNames = AppInfoManager.getAppPackageNames(context, AppInfoManager.PRIMARY_APPS_LIST_NAME) ?: emptyList()
            val savedAllPackageNames = AppInfoManager.getAppPackageNames(context, AppInfoManager.ALL_APPS_LIST_NAME)?.toSet() ?: emptySet()

            val isFirstLaunch = savedAllPackageNames.isEmpty()
            val finalQuickPackageNames: List<String>
            val finalPrimaryPackageNames: List<String>

            if (isFirstLaunch) {
                PreferenceManager.onFirstOpen(context)
                val allStandardApps = filterStandardApps(allCurrentPackageNames).toList()
                var eightStandardApps = allStandardApps.take(8)

                if (eightStandardApps.size < 8) {
                    val remainingSpace = 8 - eightStandardApps.size
                    val remainingPackageNames = allCurrentPackageNames.filter { it !in eightStandardApps }
                    eightStandardApps = eightStandardApps + remainingPackageNames.take(remainingSpace)
                }
                finalQuickPackageNames = eightStandardApps
                finalPrimaryPackageNames = allCurrentPackageNames.filter { it !in finalQuickPackageNames }
            } else {
                val addedPackages = allCurrentPackageNames - savedAllPackageNames
                val removedPackages = savedAllPackageNames - allCurrentPackageNames

                // Unload app icons loaded from icon pack
                val iconPackApp = PreferenceManager.getIconPack(context)
                if(iconPackApp != null && removedPackages.contains(iconPackApp)){
                    IconPackManager.unloadIconPack(context)
                }

                var currentQuickPackages = savedQuickPackageNames.filter { it !in removedPackages }
                var currentPrimaryPackages = savedPrimaryPackageNames.filter { it !in removedPackages }

                if (addedPackages.isNotEmpty()) {
                    val quickAppsCapacity = 8
                    val quickAppsSpace = quickAppsCapacity - currentQuickPackages.size
                    if (quickAppsSpace > 0) {
                        currentQuickPackages = currentQuickPackages + addedPackages.take(quickAppsSpace)
                    }
                    currentPrimaryPackages = currentPrimaryPackages + addedPackages.drop(quickAppsSpace)
                }
                finalQuickPackageNames = currentQuickPackages
                finalPrimaryPackageNames = currentPrimaryPackages
            }
            // Save updated lists
            AppInfoManager.saveAppPackageNames(context, AppInfoManager.QUICK_APPS_LIST_NAME, finalQuickPackageNames.toSet())
            AppInfoManager.saveAppPackageNames(context, AppInfoManager.PRIMARY_APPS_LIST_NAME, finalPrimaryPackageNames.toSet())
            AppInfoManager.saveAppPackageNames(context, AppInfoManager.ALL_APPS_LIST_NAME, allCurrentPackageNames.toSet())

            // --- Stage 3: Load App Info / Icons Concurrently ---
            val autoWallpapers = PreferenceManager.getAutoWallpapers(context)
            val monochrome = PreferenceManager.getMonochrome(context)
            val showThemedIcons = PreferenceManager.getThemedIcons(context) && (autoWallpapers || monochrome)
            val themedColors = PreferenceManager.getThemedColors(context)
            val isLightHour = PreferenceManager.isLightHour(context)
            val iconPackPackage = PreferenceManager.getIconPack(context)
            // Function to map package names to your UI models
            // NOTE: handle if a package exists on multiple profiles (Work + Personal)
            val semaphore = Semaphore(8)
            val themedIconProcessor = ThemedIconProcessor()
            suspend fun mapPackagesToAppInfo(packageNames: List<String>): List<AppInfo> {
                return packageNames.map { packageName ->
                    async {
                        // Acquire a permit before entering the heavy lifting
                        semaphore.withPermit {
                            val activityInfo = allActivitiesMap[packageName] ?: return@withPermit null
                            createAppInfo(
                                context,
                                activityInfo,
                                showThemedIcons,
                                themedColors,
                                isLightHour,
                                iconPackPackage,
                                themedIconProcessor
                            )
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            // 1. Quick Apps - Update Immediately
            val quickApps = mapPackagesToAppInfo(finalQuickPackageNames.toSet().toList())

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(quickApps = quickApps) }
            }

            // 2. Primary Apps
            val primaryApps = mapPackagesToAppInfo(finalPrimaryPackageNames.toSet().toList())

            // 3. Rest Apps
            val quickAndPrimaryPackages = finalQuickPackageNames.toSet() + finalPrimaryPackageNames.toSet()
            val restPackages = allCurrentPackageNames - quickAndPrimaryPackages
            val restApps = mapPackagesToAppInfo(restPackages.toList())

            // Final UI Update
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(primaryApps = primaryApps, restApps = restApps)
                }
            }

        } catch (e: Exception) {
            Log.e("AppInfoViewModel", e.toString())
        }
    }

    suspend fun createAppInfo(
        context: Context,
        info: LauncherActivityInfo,
        showThemedIcons: Boolean,
        themedColors: WallpaperThemeColors,
        isLightHour: Boolean,
        iconPackPackage: String?,
        themedIconProcessor: ThemedIconProcessor
    ): AppInfo? {
        return getAppInfo(
            context,
            info,
            showThemedIcons,
            themedColors,
            isLightHour,
            iconPackPackage,
            themedIconProcessor
        )
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
            PreferenceManager.increaseAppListVersion(getApplication()) // triggers UI update
        }
    }

    fun moveAppsToList(fromListName: String, toListName: String, selectedPackageNames: Set<String>) {
        viewModelScope.launch {
            if (selectedPackageNames.isEmpty()) return@launch

            val currentState = _uiState.value
            val alphabeticalOrder = PreferenceManager.getAlphabeticalOrder(getApplication())
            val fromList = when (fromListName) {
                AppInfoManager.QUICK_APPS_LIST_NAME -> currentState.quickApps
                AppInfoManager.PRIMARY_APPS_LIST_NAME -> if(alphabeticalOrder) currentState.primaryApps.sortedBy { it.label.toString() } else currentState.primaryApps
                REST_LIST_NAME -> currentState.restApps
                else -> return@launch
            }

            val appsToMove = fromList.filter { it.packageName in selectedPackageNames }
            if (appsToMove.isEmpty()) return@launch

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
                    restApps = newRestApps,
                )
            }
            PreferenceManager.increaseAppListVersion(getApplication()) // triggers UI update
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
        "com.phonepe.app",
        "com.openai.chatgpt",
        "com.instagram.android"
    )

    return allPackageNames.filter { packageName ->
        standardAppPackageNames.contains(packageName)
    }.toSet()
}

fun getThemedIconColor(
                       themeColors: WallpaperThemeColors,
                       isLightHour: Boolean): Int {
    return if(isLightHour){
        themeColors.lightFg
    } else {
        themeColors.darkFg
    }
}

fun getThemedBackgroundColor(
                             themeColors: WallpaperThemeColors,
                             isLightHour: Boolean): Int {
    //return Color.Cyan.toArgb()
    return if(isLightHour){
        themeColors.lightBg
    } else {
        themeColors.darkBg
    }
}

fun getBackgroundColor(isLightHour: Boolean):Color {
    //return Color.Cyan
    return if (isLightHour) {
        Color.White.copy(alpha = 0.5f)
    } else {
        Color.Black.copy(alpha = 0.5f)
    }
}

class ThemedIconProcessor {
    fun applyThemedColor(drawable: Drawable,
                         foregroundColor: Int,
                         backgroundColor: Int,
                         isLightHour: Boolean): Drawable {
        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable){
            handleAdaptiveIcon(
                drawable,
                foregroundColor,
                backgroundColor,
                isLightHour)
        } else {
            applyColorWithMask(drawable,
                foregroundColor,
                backgroundColor,
                isLightHour)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleAdaptiveIcon(
        adaptiveIcon: AdaptiveIconDrawable,
        foregroundColor: Int,
        backgroundColor: Int,
        isLightHour: Boolean
    ): Drawable {
        val foreground = adaptiveIcon.foreground ?: return adaptiveIcon
        // Convert to bitmap
        val bitmap = drawableToBitmap(foreground)
        // Check if it has meaningful transparency
        return if (hasSignificantTransparency(bitmap)) {
            foreground
                .apply {
                    colorFilter = PorterDuffColorFilter(
                        foregroundColor,
                        PorterDuff.Mode.SRC_IN
                    )
                }
        } else {
            getThemedIconWithShades(bitmap,
                foregroundColor,
                backgroundColor,
                isLightHour)
        }
    }

    private fun applyColorWithMask(drawable: Drawable,
                                   foregroundColor: Int,
                                   backgroundColor: Int,
                                   isLightHour: Boolean): Drawable {
        val bitmap = drawableToBitmap(drawable)
        return if (hasSignificantTransparency(bitmap)) {
            drawable
                .apply {
                colorFilter = PorterDuffColorFilter(foregroundColor, PorterDuff.Mode.SRC_IN)
            }
        } else {
            getThemedIconWithShades(bitmap,
                foregroundColor,
                backgroundColor,
                isLightHour)
        }
    }

    fun getThemedIconWithShades(icon: Bitmap,
                                foregroundColor: Int,
                                backgroundColor: Int,
                                isLightHour: Boolean
    ): Drawable {
        val width = icon.width
        val height = icon.height
        val pixels = IntArray(width * height)
        icon.getPixels(pixels, 0, width, 0, 0, width, height)

        val fgHsl = FloatArray(3)
        ColorUtils.colorToHSL(foregroundColor, fgHsl)
        val bgLuminance = ColorUtils.calculateLuminance(backgroundColor)

        val opaqueBackgroundColor = ColorUtils.setAlphaComponent(backgroundColor, 255)

        // Define the luminance range to simulate system theming (avoids pure black/white)
        val minLuminance = 0.1f
        val maxLuminance = 0.9f

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val alpha = android.graphics.Color.alpha(pixel)

            // Skip fully transparent pixels
            if (alpha == 0) continue

            val pixelLuminance = ColorUtils.calculateLuminance(pixel)

            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(pixel, hsv)

            // 1. Check for and replace the icon's own background plate
            if (hsv[1] < 0.1) { // Low saturation indicates a grayscale/white/black pixel
                if (pixelLuminance < 0.1) { // Near-black background
                    // Replace with a DARK shade of the foreground color
                    val fghslAlpha = 0.3f
                    val newHsl = floatArrayOf(fgHsl[0], fgHsl[1], fghslAlpha)
                    val newColor = ColorUtils.HSLToColor(newHsl)
                    pixels[i] = android.graphics.Color.argb(alpha, android.graphics.Color.red(newColor), android.graphics.Color.green(newColor), android.graphics.Color.blue(newColor))
                    continue // Move to the next pixel
                } else if (pixelLuminance > 0.90) { // Near-white background
                    // Replace with a LIGHT shade of the foreground color
                    val fghslAlpha = 0.7f
                    val newHsl = floatArrayOf(fgHsl[0], fgHsl[1], fghslAlpha)
                    val newColor = ColorUtils.HSLToColor(newHsl)
                    pixels[i] = android.graphics.Color.argb(alpha, android.graphics.Color.red(newColor), android.graphics.Color.green(newColor), android.graphics.Color.blue(newColor))
                    continue // Move to the next pixel
                }
            }

            // 2. Map original brightness to the constrained luminance range
            val targetLuminance = minLuminance + (pixelLuminance * (maxLuminance - minLuminance)).toFloat()

            // Create the new HSL color by applying the target luminance to the foreground hue/saturation
            val newHsl = floatArrayOf(fgHsl[0], fgHsl[1], targetLuminance)
            var newColor = ColorUtils.HSLToColor(newHsl)

            // 3. Contrast Guarantee: Check contrast against the background and adjust if needed
            val contrast = ColorUtils.calculateContrast(newColor, opaqueBackgroundColor)
            if (contrast < 2.0) { // 3.0:1 is a good minimum contrast for icons
                // If contrast is too low, shift the lightness away from the background's luminance
                val adjustedLuminance = if (bgLuminance > 0.5) {
                    (targetLuminance - 0.2f).coerceAtLeast(minLuminance) // Make it darker
                } else {
                    (targetLuminance + 0.2f).coerceAtMost(maxLuminance) // Make it lighter
                }
                newHsl[2] = adjustedLuminance
                newColor = ColorUtils.HSLToColor(newHsl)
            }

            // 4. Alpha Enhancement: Boost alpha for better visibility of semi-transparent edges
            val scaledAlpha = (alpha + 100).coerceAtMost(255)

            pixels[i] = android.graphics.Color.argb(
                scaledAlpha,
                android.graphics.Color.red(newColor),
                android.graphics.Color.green(newColor),
                android.graphics.Color.blue(newColor)
            )
        }

        val themedBitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val themedDrawable = BitmapDrawable(null,themedBitmap)
        return themedDrawable
    }


    /**
     * Check if bitmap has meaningful transparency
     */
    private fun hasSignificantTransparency(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false

        val width = bitmap.width
        val height = bitmap.height
        val sampleSize = max(1, width / 20) // Sample every 5% of width

        var transparentPixels = 0
        var totalSampled = 0

        for (x in 0 until width step sampleSize) {
            for (y in 0 until height step sampleSize) {
                val pixel = bitmap[x, y]
                val alpha = android.graphics.Color.alpha(pixel)
                if (alpha < 250) transparentPixels++
                totalSampled++
            }
        }

        // Consider significant if > 90% pixels have some transparency
        return (transparentPixels.toFloat() / totalSampled) > 0.9f
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val width = 284
        val height = 284

        if (drawable is BitmapDrawable) {
            return drawable.bitmap.scale(width = width, height = height)
        }

        //val width = drawable.intrinsicWidth.coerceAtLeast(1)
        //val height = drawable.intrinsicHeight.coerceAtLeast(1)

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)

        return bitmap
    }
}
