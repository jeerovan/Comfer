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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.Shape
import kotlin.collections.map

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

private const val ICON_ANALYSIS_SIZE = 192
private const val ICON_ALPHA_THRESHOLD = 32

data class AppInfoUiState(
    val quickApps: List<AppInfo> = emptyList(),
    val primaryApps: List<AppInfo> = emptyList(),
    val restApps: List<AppInfo> = emptyList(),
    val folderApps: Map<String, List<AppInfo>> = emptyMap(),
    val folders : Map<String, FolderData> = emptyMap()
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
    val icon: Drawable?,
    val componentName: ComponentName?,
    val user: UserHandle? // Important for work profiles
)

private data class LegacyIconAnalysis(
    val scale: Float,
    val propagatedColor: Int?,
)

private object LegacyIconAnalysisCache {
    private val analyses = ConcurrentHashMap<String, LegacyIconAnalysis>()

    fun getOrPut(cacheKey: String, block: () -> LegacyIconAnalysis): LegacyIconAnalysis {
        return analyses.getOrPut(cacheKey, block)
    }
}

private val packageManagerDispatcher = Dispatchers.IO.limitedParallelism(4)
private val iconLoadingDispatcher = Dispatchers.IO.limitedParallelism(8)
suspend fun getAppInfo(
    context: Context,
    info: LauncherActivityInfo,
    showThemedIcons: Boolean,
    themedColors: WallpaperThemeColors,
    isLightHour: Boolean,
    iconPackPackage: String?,
    iconProcessor: ThemedIconProcessor
): AppInfo? = withContext(Dispatchers.IO) {
    try {
        val packageName = info.componentName.packageName
        val user = info.user
        val cacheKey = "$packageName" // Add userId here to support work profiles

        val cachedIcon = AppIconCache.getIcon(cacheKey)

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
            val legacyAnalysis = LegacyIconAnalysisCache.getOrPut(
                "$cacheKey|${iconPackPackage.orEmpty()}"
            ) {
                val iconBitmap = iconDrawable.toBitmap(
                    width = ICON_ANALYSIS_SIZE,
                    height = ICON_ANALYSIS_SIZE,
                    config = Bitmap.Config.ARGB_8888
                )
                LegacyIconAnalysis(
                    scale = calculateLegacyForegroundScale(iconBitmap),
                    propagatedColor = derivePropagatedSourceColor(iconBitmap),
                )
            }
            val fallbackBackgroundColor = if (showThemedIcons) {
                getThemedBackgroundColor(themedColors, isLightHour)
            } else {
                getBackgroundColor(isLightHour).toArgb()
            }
            scale = legacyAnalysis.scale
            val backgroundColor = if (showThemedIcons) {
                fallbackBackgroundColor
            } else {
                blendLegacyBackgroundColor(
                    propagatedColor = legacyAnalysis.propagatedColor,
                    fallbackColor = fallbackBackgroundColor,
                )
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


fun generateFolderForeground(
    context: Context,
    title: String,
    appIcons: List<AppInfo>,
    foregroundColor: Int,
    shape: Shape
): Drawable {
    val size = 192 // Ensure this matches ICON_ANALYSIS_SIZE constant
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val mainCanvas = Canvas(bitmap)

    if (appIcons.isEmpty()) {
        val displayText = title.take(2).lowercase().replaceFirstChar { it.uppercase() }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor
            textSize = 72f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val xPos = mainCanvas.width / 2f
        val yPos = (mainCanvas.height / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        mainCanvas.drawText(displayText, xPos, yPos, paint)

    } else {
        val padding = 8
        val iconsToDraw = appIcons.take(8)

        val rowCounts = when (iconsToDraw.size) {
            1 -> listOf(1)
            2 -> listOf(2)
            3 -> listOf(2, 1)
            4 -> listOf(2, 2)
            5 -> listOf(3, 2)
            else -> {
                val list = mutableListOf<Int>()
                var remaining = iconsToDraw.size
                while (remaining > 0) {
                    val count = minOf(3, remaining)
                    list.add(count)
                    remaining -= count
                }
                list
            }
        }

        val totalRows = rowCounts.size
        val maxCols = rowCounts.maxOrNull() ?: 1

        val maxIconWidth = (size - (padding * (maxCols + 1))) / maxCols
        val maxIconHeight = (size - (padding * (totalRows + 1))) / totalRows
        val iconSize = minOf(maxIconWidth, maxIconHeight)

        val totalHeight = (totalRows * iconSize) + ((totalRows - 1) * padding)
        val startY = (size - totalHeight) / 2

        // 1. Create the clip path based on the calculated mini-icon size
        val density = Density(context.resources.displayMetrics.density)
        val outline = shape.createOutline(
            Size(iconSize.toFloat(), iconSize.toFloat()),
            layoutDirection = LayoutDirection.Ltr,
            density = density
        )

        val clipPath = when (outline) {
            is Outline.Rounded -> android.graphics.Path().apply {
                addRoundRect(
                    outline.roundRect.left, outline.roundRect.top,
                    outline.roundRect.right, outline.roundRect.bottom,
                    outline.roundRect.topLeftCornerRadius.x, outline.roundRect.topLeftCornerRadius.y,
                    android.graphics.Path.Direction.CW
                )
            }
            is Outline.Rectangle -> android.graphics.Path().apply {
                addRect(outline.rect.left, outline.rect.top, outline.rect.right, outline.rect.bottom, android.graphics.Path.Direction.CW)
            }
            is Outline.Generic -> outline.path.asAndroidPath()
        }

        // 2. Prepare a reusable buffer to render each individual shaped mini-icon
        val miniBitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val miniCanvas = Canvas(miniBitmap)

        var iconIndex = 0
        for ((rowIndex, countInRow) in rowCounts.withIndex()) {
            val totalWidth = (countInRow * iconSize) + ((countInRow - 1) * padding)
            val startX = (size - totalWidth) / 2
            val currentY = startY + (rowIndex * (iconSize + padding))

            for (colIndex in 0 until countInRow) {
                val currentX = startX + (colIndex * (iconSize + padding))
                val app = iconsToDraw[iconIndex]

                // Clear the reusable buffer for the next icon
                miniBitmap.eraseColor(android.graphics.Color.TRANSPARENT)

                miniCanvas.save()
                miniCanvas.clipPath(clipPath) // Apply shape clip

                // Draw background layer inside the shape
                app.background?.let { bg ->
                    bg.mutate()
                    bg.setBounds(0, 0, iconSize, iconSize)
                    bg.draw(miniCanvas)
                }

                // Draw foreground layer inside the shape
                val fg = app.foreground ?: app.icon
                val scaledSize = (iconSize * app.scale).toInt()
                val offset = (iconSize - scaledSize) / 2

                fg?.mutate()
                fg?.setBounds(offset, offset, offset + scaledSize, offset + scaledSize)
                fg?.draw(miniCanvas)

                miniCanvas.restore()

                // Stamp the fully rendered, shaped mini-icon onto the main folder canvas
                mainCanvas.drawBitmap(miniBitmap, currentX.toFloat(), currentY.toFloat(), null)

                iconIndex++
            }
        }

        // Clean up the buffer to immediately free memory
        miniBitmap.recycle()
    }

    return BitmapDrawable(context.resources, bitmap)
}

private fun calculateLegacyForegroundScale(bitmap: Bitmap): Float {
    val bounds = findOpaqueBounds(bitmap) ?: return 1f
    val contentFraction = max(
        bounds.width().toFloat() / bitmap.width,
        bounds.height().toFloat() / bitmap.height
    )
    return (0.707f / contentFraction).coerceIn(0.8f, 1.15f)
}

private fun derivePropagatedSourceColor(bitmap: Bitmap): Int? {
    val edgeColor = sampleEdgeColor(bitmap)
    return edgeColor ?: Palette.from(bitmap)
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
}

private fun blendLegacyBackgroundColor(propagatedColor: Int?, fallbackColor: Int): Int {
    val sourceColor = propagatedColor ?: fallbackColor
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
    private suspend fun loadIconPack(){
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
    private suspend fun refreshAppLists() = withContext(Dispatchers.IO) {
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
            val shape = PreferenceManager.getIconShape(context)

            val savedFolders = AppInfoManager.getFolders(context)
            // Function to map package names to your UI models
            // NOTE: handle if a package exists on multiple profiles (Work + Personal)
            val themedIconProcessor = ThemedIconProcessor()
            suspend fun mapPackagesToAppInfo(packageNames: List<String>): List<AppInfo> = withContext(iconLoadingDispatcher) {
                packageNames.map { packageName ->
                    async {
                            if(packageName.startsWith("folder")){
                                val folderData = savedFolders[packageName] ?: return@async null
                                val packages = folderData.packages
                                val activitiesMap = allActivitiesMap.filter { it.key in packages }
                                val activities = activitiesMap.values.toList()
                                createFolderAppInfo(
                                    context,
                                    folderData,
                                    activities,
                                    showThemedIcons,
                                    themedColors,
                                    isLightHour,
                                    iconPackPackage,
                                    themedIconProcessor,
                                    shape)
                            } else {
                                val activityInfo =
                                    allActivitiesMap[packageName] ?: return@async null
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

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(primaryApps = primaryApps)
                }
            }

            // 3. Rest Apps
            val quickAndPrimaryPackages = finalQuickPackageNames.toSet() + finalPrimaryPackageNames.toSet()
            val restPackages = allCurrentPackageNames - quickAndPrimaryPackages
            val restApps = mapPackagesToAppInfo(restPackages.toList())

            // Final UI Update
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(restApps = restApps)
                }
            }

            val restAppMap = restApps.associateBy { it.packageName }

            // for each FolderData, map its packages to find AppInfo with matching packageName
            val foldersWithAppInfo: Map<String, List<AppInfo>> = savedFolders.mapValues { (_, folderData) ->
                folderData.packages.mapNotNull { pkgName ->
                    restAppMap[pkgName]
                }
            }

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(folderApps = foldersWithAppInfo,folders = savedFolders)
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

    suspend fun createFolderAppInfo(
        context: Context,
        folderData: FolderData,
        activities: List<LauncherActivityInfo>,
        showThemedIcons: Boolean,
        themedColors: WallpaperThemeColors,
        isLightHour: Boolean,
        iconPackPackage: String?,
        themedIconProcessor: ThemedIconProcessor,
        shape: Shape
    ): AppInfo? {
        val appInfos = activities.mapNotNull { info ->
            getAppInfo(
                context,
                info,
                showThemedIcons,
                themedColors,
                isLightHour,
                iconPackPackage,
                themedIconProcessor
            )
        }
        val foregroundColorInt = if (showThemedIcons) {
            getThemedIconColor(themedColors, isLightHour)
        } else {
            getForegroundColor(isLightHour).toArgb()
        }
        val foreground = generateFolderForeground(context,
            folderData.title,
            appInfos,
            foregroundColorInt,
            shape
            )
        val backgroundColorInt = if (showThemedIcons) {
            getThemedBackgroundColor(themedColors, isLightHour)
        } else {
            getBackgroundColor(isLightHour).toArgb()
        }
        val background = backgroundColorInt.toDrawable()

        return AppInfo(
            background,
            foreground,
            folderData.title,
            1.0f,
            folderData.id,
            foreground,
            null,
            null,
        )
    }

    fun moveAppInList(listName: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = when (listName) {
                AppInfoManager.QUICK_APPS_LIST_NAME -> _uiState.value.quickApps
                AppInfoManager.PRIMARY_APPS_LIST_NAME -> _uiState.value.primaryApps
                AppInfoManager.REST_APPS_LIST_NAME -> _uiState.value.restApps
                else -> return@launch
            }.toMutableList()

            val app = currentList.removeAt(fromIndex)
            currentList.add(toIndex, app)
            val packageNames = currentList.map { it.packageName }

            when (listName) {
                AppInfoManager.QUICK_APPS_LIST_NAME -> {
                    AppInfoManager.saveAppPackageNames(getApplication(), listName, packageNames)
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(quickApps = currentList) }
                    }
                }

                AppInfoManager.PRIMARY_APPS_LIST_NAME -> {
                    AppInfoManager.saveAppPackageNames(getApplication(), listName, packageNames)
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(primaryApps = currentList) }
                    }
                }

                AppInfoManager.REST_APPS_LIST_NAME -> {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(restApps = currentList) }
                    }
                }
            }
            PreferenceManager.increaseAppListVersion(getApplication()) // triggers UI update
        }
    }

    fun moveAppsInFolder(folderName: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val context: Context = getApplication()

            // 1. Update persisted folder data
            val savedFolders = AppInfoManager.getFolders(context).toMutableMap()
            val folderData = savedFolders[folderName] ?: return@launch

            val packages = folderData.packages.toMutableList()

            // Ensure indices are within valid bounds
            if (fromIndex !in packages.indices) return@launch
            val validToIndex = toIndex.coerceIn(0, packages.size - 1)

            // Move app package position
            val pkgToMove = packages.removeAt(fromIndex)
            packages.add(validToIndex, pkgToMove)

            // Save back to preferences
            savedFolders[folderName] = folderData.copy(packages = packages)
            AppInfoManager.saveFolders(context, savedFolders)

            // 2. Update UI state
            val currentFolders = _uiState.value.folderApps.toMutableMap()
            val appInfos = currentFolders[folderName]?.toMutableList() ?: return@launch

            if (fromIndex in appInfos.indices) {
                // Move appInfo position
                val appToMove = appInfos.removeAt(fromIndex)
                appInfos.add(validToIndex, appToMove)

                // 3. Update _uiState atomically
                currentFolders[folderName] = appInfos
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(folderApps = currentFolders)
                    }
                }
            }
        }
    }

    fun moveAppsToList(fromListName: String, toListName: String, selectedPackageNames: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (selectedPackageNames.isEmpty()) return@launch

            val currentState = _uiState.value
            val alphabeticalOrder = PreferenceManager.getAlphabeticalOrder(getApplication())
            val fromList = when (fromListName) {
                AppInfoManager.QUICK_APPS_LIST_NAME -> currentState.quickApps
                AppInfoManager.PRIMARY_APPS_LIST_NAME -> if(alphabeticalOrder) currentState.primaryApps.sortedBy { it.label } else currentState.primaryApps
                AppInfoManager.REST_APPS_LIST_NAME -> currentState.restApps
                else -> return@launch
            }

            var appsToMove = fromList.filter { it.packageName in selectedPackageNames }
            // Rest app list is derived from quick+primary and not saved
            if(toListName == AppInfoManager.REST_APPS_LIST_NAME){
                appsToMove = appsToMove.filter { !it.packageName.startsWith("folder") }
            }
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
                AppInfoManager.REST_APPS_LIST_NAME -> {
                    // No change to quick/primary lists when removing from rest.
                    // The apps will be added to a target list below.
                }
            }

            // Add to destination list
            when (toListName) {
                AppInfoManager.QUICK_APPS_LIST_NAME ->
                    newQuickApps = appsToMove + newQuickApps
                AppInfoManager.PRIMARY_APPS_LIST_NAME ->
                    newPrimaryApps = appsToMove + newPrimaryApps
                AppInfoManager.REST_APPS_LIST_NAME -> {
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

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        quickApps = newQuickApps,
                        primaryApps = newPrimaryApps,
                        restApps = newRestApps,
                    )
                }
            }
            PreferenceManager.increaseAppListVersion(getApplication()) // triggers UI update
        }
    }

    fun createNewFolder(title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context:Context = getApplication()
            val currentFolders = _uiState.value.folderApps.toMutableMap()
            if (currentFolders.size >= 10) return@launch // Max 10 folders restriction

            val newFolderId = "folder_${System.currentTimeMillis()}" // Ensuring unique Number/ID
            val newFolder = FolderData(id = newFolderId, title = title, packages = emptyList())
            val savedFolders = AppInfoManager.getFolders(context).toMutableMap()
            savedFolders[newFolderId] = newFolder
            AppInfoManager.saveFolders(context, savedFolders)

            val autoWallpapers = PreferenceManager.getAutoWallpapers(context)
            val monochrome = PreferenceManager.getMonochrome(context)
            val showThemedIcons = PreferenceManager.getThemedIcons(context) && (autoWallpapers || monochrome)
            val themedColors = PreferenceManager.getThemedColors(context)
            val isLightHour = PreferenceManager.isLightHour(context)
            val shape = PreferenceManager.getIconShape(context)
            val activities:List<LauncherActivityInfo> = emptyList()
            val themedIconProcessor = ThemedIconProcessor()

            val folderAppInfo = createFolderAppInfo(
                context,
                newFolder,
                activities,
                showThemedIcons,
                themedColors,
                isLightHour,
                null,
                themedIconProcessor,
                shape)
            val primaryApps = _uiState.value.primaryApps.toMutableList()
            if(folderAppInfo != null){ primaryApps.add(0,folderAppInfo)}
            currentFolders[newFolderId] = emptyList()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        primaryApps = primaryApps,
                        folderApps = currentFolders,
                        folders = savedFolders
                    )
                }
            }
            AppInfoManager.saveAppPackageNames(
                getApplication(),
                AppInfoManager.PRIMARY_APPS_LIST_NAME, primaryApps.map { it.packageName }
            )
        }
    }

    fun renameFolder(folderId: String, title: String) {
        viewModelScope.launch(Dispatchers.Main) { // Saving preferences should be on IO dispatcher
            val context = getApplication<Application>()
            _uiState.update { currentState ->
                val currentFolders = currentState.folders.toMutableMap()
                // Check if folder exists
                val folder = currentFolders[folderId]
                if (folder != null) {
                    currentFolders[folderId] = folder.copy(title = title)
                    // Save asynchronously
                    AppInfoManager.saveFolders(context, currentFolders)
                }
                // Return updated state
                currentState.copy(folders = currentFolders)
            }
        }
    }

    fun deleteFolder(selectedList: String?, folderPackageName: String) {
        // 1. Move to Dispatchers.IO for safe disk operations
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            // Grab a snapshot of the current state
            val currentState = _uiState.value
            val appList = when (selectedList) {
                AppInfoManager.QUICK_APPS_LIST_NAME -> currentState.quickApps
                AppInfoManager.PRIMARY_APPS_LIST_NAME -> currentState.primaryApps
                AppInfoManager.REST_APPS_LIST_NAME -> currentState.restApps
                else -> return@launch
            }

            val currentFolders = currentState.folders.toMutableMap()
            val currentFolderApps = currentState.folderApps.toMutableMap()

            currentFolders.remove(folderPackageName)
            currentFolderApps.remove(folderPackageName)

            val newList = appList.filter { it.packageName != folderPackageName }

            // 2. Perform a single batch update to the UI state
            withContext(Dispatchers.Main){
                _uiState.update { state ->
                    val updatedState = state.copy(
                        folders = currentFolders,
                        folderApps = currentFolderApps
                    )
                    when (selectedList) {
                        AppInfoManager.QUICK_APPS_LIST_NAME -> updatedState.copy(quickApps = newList)
                        AppInfoManager.PRIMARY_APPS_LIST_NAME -> updatedState.copy(primaryApps = newList)
                        AppInfoManager.REST_APPS_LIST_NAME -> updatedState.copy(restApps = newList)
                        else -> updatedState
                    }
                }
            }

            // 3. Persist list changes to disk safely in the IO thread
            AppInfoManager.saveAppPackageNames(
                context,
                selectedList,
                newList.map { it.packageName }
            )

            // 4. Persist folder deletions to disk using our already modified map
            AppInfoManager.saveFolders(context, currentFolders)
        }
    }

    fun moveAppsToFolder(selectedList: String?, folderPackageName: String, selectedPackageNames: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (selectedList == null || selectedPackageNames.isEmpty()) return@launch
            val context: Context = getApplication()

            val currentFolders = _uiState.value.folderApps.toMutableMap()
            val folderAppInfos = currentFolders[folderPackageName]?.toMutableList() ?: return@launch
            val existingPackages = folderAppInfos.map { it.packageName }

            // Prevent adding duplicates and putting folders inside folders
            val packagesToAdd = selectedPackageNames.filter {
                it !in existingPackages && !it.startsWith("folder_")
            }.take(8 - existingPackages.size) // Enforce max 8 limit

            if (packagesToAdd.isEmpty()) return@launch

            // Grab AppInfos from the source list
            val fromAppInfos = when (selectedList) {
                AppInfoManager.QUICK_APPS_LIST_NAME -> _uiState.value.quickApps
                AppInfoManager.PRIMARY_APPS_LIST_NAME -> _uiState.value.primaryApps
                AppInfoManager.REST_APPS_LIST_NAME -> _uiState.value.restApps
                else -> return@launch
            }

            val appInfosToAdd = fromAppInfos.filter { it.packageName in packagesToAdd }
            folderAppInfos.addAll(0,appInfosToAdd)
            currentFolders[folderPackageName] = folderAppInfos

            // Update Preferences
            val savedFolders = AppInfoManager.getFolders(context).toMutableMap()
            val folderData = savedFolders[folderPackageName]
            var folderTitle = "??"
            if (folderData != null) {
                folderTitle = folderData.title
                val updatedPackages = folderAppInfos.map { it.packageName }
                savedFolders[folderPackageName] = folderData.copy(packages = updatedPackages)
                AppInfoManager.saveFolders(context, savedFolders)
            }

            // Atomically update folders map
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(folderApps = currentFolders) }
            }

            // Move to Rest list to hide them from the primary/quick lists
            moveAppsToList(
                fromListName = selectedList,
                toListName = AppInfoManager.REST_APPS_LIST_NAME,
                selectedPackageNames = packagesToAdd.toSet()
            )

            // Regenerate visual folder icon
            updateFolderIcon(context,
                selectedList,
                folderTitle,
                folderPackageName,
                folderAppInfos)
        }
    }

    fun moveAppsFromFolder(selectedList: String?, folderPackageName: String, selectedPackageNames: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (selectedPackageNames.isEmpty() || selectedList == null) return@launch
            val context: Context = getApplication()

            val currentFolders = _uiState.value.folderApps.toMutableMap()
            val folderAppInfos = currentFolders[folderPackageName]?.toMutableList() ?: return@launch

            val remainingAppInfos = folderAppInfos.filter { it.packageName !in selectedPackageNames }
            currentFolders[folderPackageName] = remainingAppInfos

            // Update Preferences
            val savedFolders = AppInfoManager.getFolders(context).toMutableMap()
            val folderData = savedFolders[folderPackageName]
            var folderTitle = "??"
            if (folderData != null) {
                folderTitle = folderData.title
                val updatedPackages = remainingAppInfos.map { it.packageName }
                savedFolders[folderPackageName] = folderData.copy(packages = updatedPackages)
                AppInfoManager.saveFolders(context, savedFolders)
            }

            // Atomically update folders map
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(folderApps = currentFolders) }
            }

            // Add back to primaryApps (pulling them out of the Rest list)
            moveAppsToList(
                fromListName = AppInfoManager.REST_APPS_LIST_NAME,
                toListName = AppInfoManager.PRIMARY_APPS_LIST_NAME,
                selectedPackageNames = selectedPackageNames
            )

            // Regenerate visual folder icon
            updateFolderIcon(context,
                selectedList,
                folderTitle,
                folderPackageName,
                remainingAppInfos)
        }
    }

    // Helper function to regenerate the composite icon so the folder reflects its new contents
    private suspend fun updateFolderIcon(context: Context,
                                 selectedList:  String,
                                 folderTitle: String,
                                 folderPackageName: String,
                                 folderApps: List<AppInfo>) {
        val autoWallpapers = PreferenceManager.getAutoWallpapers(context)
        val monochrome = PreferenceManager.getMonochrome(context)
        val showThemedIcons =
            PreferenceManager.getThemedIcons(context) && (autoWallpapers || monochrome)
        val themedColors = PreferenceManager.getThemedColors(context)
        val isLightHour = PreferenceManager.isLightHour(context)
        val shape = PreferenceManager.getIconShape(context)
        val foregroundColorInt = if (showThemedIcons) {
            getThemedIconColor(themedColors, isLightHour)
        } else {
            getForegroundColor(isLightHour).toArgb()
        }
        val folderForeground = generateFolderForeground(
            context,
            folderTitle,
            folderApps,
            foregroundColorInt,
            shape
        )
        when (selectedList) {
            AppInfoManager.QUICK_APPS_LIST_NAME -> {
                withContext(Dispatchers.Main) {
                    _uiState.update { currentState ->
                        val updatedQuickApps = currentState.quickApps.map { app ->
                            if (app.packageName == folderPackageName) {
                                app.copy(foreground = folderForeground)
                            } else {
                                app
                            }
                        }
                        currentState.copy(quickApps = updatedQuickApps)
                    }
                }
            }
            AppInfoManager.PRIMARY_APPS_LIST_NAME -> {
                withContext(Dispatchers.Main) {
                    _uiState.update { currentState ->
                        val updatedPrimaryApps = currentState.primaryApps.map { app ->
                            if (app.packageName == folderPackageName) {
                                app.copy(foreground = folderForeground)
                            } else {
                                app
                            }
                        }
                        currentState.copy(primaryApps = updatedPrimaryApps)
                    }
                }
            }

            else -> {
                withContext(Dispatchers.Main) {
                    _uiState.update { currentState ->
                        val updatedRestApps = currentState.restApps.map { app ->
                            if (app.packageName == folderPackageName) {
                                app.copy(foreground = folderForeground)
                            } else {
                                app
                            }
                        }
                        currentState.copy(restApps = updatedRestApps)
                    }
                }
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
    return if (isLightHour) {
        Color.White.copy(alpha = 0.5f)
    } else {
        Color.Black.copy(alpha = 0.5f)
    }
}
fun getForegroundColor(isLightHour: Boolean):Color {
    return if (isLightHour) {
        Color.Black.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = 0.5f)
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
            val bitmap = drawableToBitmap(drawable)
            if (hasSignificantTransparency(bitmap)) {
                drawable
                    .apply {
                        colorFilter = PorterDuffColorFilter(foregroundColor, PorterDuff.Mode.SRC_IN)
                    }
            } else {
                drawable
            }
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
            foreground
        }
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

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)

        return bitmap
    }
}
