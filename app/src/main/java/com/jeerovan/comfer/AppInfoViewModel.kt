package com.jeerovan.comfer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
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
import androidx.core.graphics.get
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import android.graphics.Color as AndroidColor
import androidx.core.graphics.scale

private const val REST_LIST_NAME = "Rest"

data class AppInfoUiState(
    val quickApps: List<AppInfo> = emptyList(),
    val primaryApps: List<AppInfo> = emptyList(),
    val restApps: List<AppInfo> = emptyList()
)
data class WallpaperThemeColors(
    val lightBg: Int,
    val lightFg: Int,
    val darkBg: Int,
    val darkFg: Int,
)
data class AppInfo(
    val background: Drawable?,
    val foreground: Drawable?,
    val label: CharSequence,
    val scale: Float,
    val packageName: String
)

suspend fun getAppInfo(
    context: Context,
    packageManager: PackageManager,
    resolveInfo: ResolveInfo,
    packageName: String,
    showThemedIcons: Boolean,
    themedColors: WallpaperThemeColors,
    isLightHour: Boolean
): AppInfo? = withContext(Dispatchers.Default){
    try {
        val defaultIcon = packageManager.defaultActivityIcon
        val cachedIcon = AppIconCache.getIcon(packageName)
        val savedIcon = if (cachedIcon == defaultIcon) null else cachedIcon
        val loadedDrawable = savedIcon ?: resolveInfo.loadIcon(packageManager).also {
            AppIconCache.cacheIcon(packageName, it)
        }
        val iconDrawable = loadedDrawable.constantState?.newDrawable()?.mutate()
        var backgroundDrawable: Drawable?
        var foregroundDrawable: Drawable?
        val appLabel = resolveInfo.loadLabel(packageManager).trim()
        val foregroundColor = getThemedIconColor(themedColors,isLightHour)

        val iconProcessor = ThemedIconProcessor()
        // Determine scale and process icon
        val isAdaptive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && iconDrawable is AdaptiveIconDrawable
        val scale = if (isAdaptive) 1.5f else 0.8f

        when {
            isAdaptive -> {
                val adaptiveIcon = iconDrawable
                if (showThemedIcons) {
                    val backgroundColor = getThemedBackgroundColor(themedColors,isLightHour)
                    backgroundDrawable = backgroundColor.toDrawable()

                    foregroundDrawable = when {
                        // Android 13+ (Tiramisu): Try monochrome first
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                            adaptiveIcon.monochrome?.mutate()
                                ?.apply {
                                setTint(foregroundColor)
                            } ?: adaptiveIcon.foreground?.mutate()
                                ?.apply {
                                colorFilter = PorterDuffColorFilter(foregroundColor, PorterDuff.Mode.SRC_IN)
                                }
                        }
                        else -> {
                            iconProcessor.applyThemedColor(
                                adaptiveIcon.foreground,
                                foregroundColor,
                                backgroundColor,
                                isLightHour
                            )
                        }
                    }
                } else {
                    // Original adaptive icon
                    backgroundDrawable = adaptiveIcon.background
                    foregroundDrawable = adaptiveIcon.foreground
                }
            }

            else -> {
                // Non-adaptive / legacy icons
                val backgroundColor = if (showThemedIcons) {
                    getThemedBackgroundColor(themedColors, isLightHour)
                } else {
                    getBackgroundColor(isLightHour).toArgb()
                }
                backgroundDrawable = backgroundColor.toDrawable()

                foregroundDrawable = if (showThemedIcons) {
                    if(iconDrawable != null) iconProcessor.applyThemedColor(
                        iconDrawable,
                        foregroundColor,
                        backgroundColor,
                        isLightHour) else iconDrawable
                } else {
                    iconDrawable
                }
            }
        }

        AppInfo(
            background = backgroundDrawable,
            foreground = foregroundDrawable,
            scale = scale,
            label = appLabel,
            packageName = packageName
        )
    } catch (e: Exception) {
        Log.e("getAppInfo",e.stackTraceToString())
        null
    }
}
suspend fun mapPackageNameToAppInfo(
    context: Context,
    packageManager: PackageManager,
    packageName: String?
): AppInfo? {
    if (packageName == null) return null
    val showThemedIcons = PreferenceManager.getThemedIcons(context)
    val themedColors = PreferenceManager.getThemedColors(context)
    val isLightHour = !PreferenceManager.getDarkMode(context)
    return try {
        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            packageManager.resolveActivity(launchIntent, 0)?.let { resolveInfo ->
                getAppInfo(context,
                    packageManager,
                    resolveInfo,
                    packageName,
                    showThemedIcons,
                    themedColors,
                    isLightHour)
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
    private val packageManager: PackageManager
        get() = getApplication<Application>().packageManager

    init {
        loadAppLists()
    }

    private var isWorking = false

    fun loadAppLists() {
        // Basic check to avoid launching a coroutine if already working
        if (isWorking) return
        isWorking = true
        logger.setLog("LoadAppLists","Loading")
        viewModelScope.launch {
            try {
                // All work now happens on a background thread.
                withContext(Dispatchers.Default) {
                    // --- Stage 1: Determine Package Lists (No Icon Loading) ---
                    val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
                    val allCurrentResolveInfos = packageManager.queryIntentActivities(intent, 0)
                    val allCurrentPackageNames = allCurrentResolveInfos.map { it.activityInfo.packageName }.toSet()
                    val context: Application = getApplication()
                    val savedQuickPackageNames = AppInfoManager.getAppPackageNames(
                        context,
                        AppInfoManager.QUICK_APPS_LIST_NAME
                    ) ?: emptyList()
                    val savedPrimaryPackageNames = AppInfoManager.getAppPackageNames(
                        context,
                        AppInfoManager.PRIMARY_APPS_LIST_NAME
                    ) ?: emptyList()
                    val savedAllPackageNames = AppInfoManager.getAppPackageNames(
                        context,
                        AppInfoManager.ALL_APPS_LIST_NAME
                    )?.toSet() ?: emptySet()
                    val isFirstLaunch = savedAllPackageNames.isEmpty()

                    val finalQuickPackageNames: List<String>
                    val finalPrimaryPackageNames: List<String>

                    if (isFirstLaunch) {
                        PreferenceManager.onFirstOpen(context)
                        val allStandardApps = filterStandardApps(allCurrentPackageNames).toList()
                        var eightStandardApps = allStandardApps.take(8)
                        if(eightStandardApps.size < 8){
                            val remainingSpace = 8 - eightStandardApps.size
                            val remainingPackageNames = allCurrentPackageNames.filter { it !in eightStandardApps}
                            eightStandardApps = eightStandardApps + remainingPackageNames.take(remainingSpace)
                        }
                        finalQuickPackageNames = eightStandardApps
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

                    // --- Stage 2: Load App Info Concurrently ---
                    val resolveInfoMap = allCurrentResolveInfos.associateBy { it.activityInfo.packageName }
                    val showThemedIcons = PreferenceManager.getThemedIcons(context)
                    val themedColors = PreferenceManager.getThemedColors(context)
                    val isLightHour = !PreferenceManager.getDarkMode(context)
                    // Load quick apps first and update UI immediately
                    val quickApps = finalQuickPackageNames.map { packageName ->
                        async { createAppInfo(context,
                            packageManager,
                            packageName,
                            resolveInfoMap,
                            showThemedIcons,
                            themedColors,
                            isLightHour) }
                    }.awaitAll().filterNotNull()

                    // **Immediate UI Update**
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(quickApps = quickApps)
                        }
                    }

                    // Now load the rest of the apps in parallel
                    val primaryApps = finalPrimaryPackageNames.map { packageName ->
                        async { createAppInfo(context,
                            packageManager,
                            packageName,
                            resolveInfoMap,
                            showThemedIcons,
                            themedColors,
                            isLightHour) }
                    }.awaitAll().filterNotNull()

                    val quickAndPrimaryPackages = finalQuickPackageNames.toSet() + finalPrimaryPackageNames.toSet()
                    val restPackages = allCurrentPackageNames - quickAndPrimaryPackages

                    val restApps = restPackages.map { packageName ->
                        async { createAppInfo(context,
                            packageManager,
                            packageName,
                            resolveInfoMap,
                            showThemedIcons,
                            themedColors,
                            isLightHour) }
                    }.awaitAll().filterNotNull()

                    // **Final UI Update**
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                primaryApps = primaryApps,
                                restApps = restApps,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logger.setLog("AppInfoViewModel", e.toString())
            } finally {
                // Release the lock and reset the flag
                isWorking = false
            }
        }
    }

    suspend fun createAppInfo(context: Context,
                              packageManager: PackageManager,
                              packageName: String,
                              resolveInfoMap: Map<String,
                              ResolveInfo>,
                              showThemedIcons: Boolean,
                              themedColors: WallpaperThemeColors,
                              isLightHour: Boolean): AppInfo? {
        val resolveInfo = resolveInfoMap[packageName] ?: return null
        return getAppInfo(context,
            packageManager,
            resolveInfo,
            packageName,
            showThemedIcons,
            themedColors,
            isLightHour)
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
            val alphabeticalOrder = PreferenceManager.getAlphabeticalOrder(getApplication())
            val fromList = when (fromListName) {
                AppInfoManager.QUICK_APPS_LIST_NAME -> currentState.quickApps
                AppInfoManager.PRIMARY_APPS_LIST_NAME -> if(alphabeticalOrder) currentState.primaryApps.sortedBy { it.label.toString() } else currentState.primaryApps
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
            val alpha = AndroidColor.alpha(pixel)

            // Skip fully transparent pixels
            if (alpha == 0) continue

            val pixelLuminance = ColorUtils.calculateLuminance(pixel)

            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(pixel, hsv)

            // 1. Check for and replace the icon's own background plate
            if (hsv[1] < 0.1) { // Low saturation indicates a grayscale/white/black pixel
                if (pixelLuminance < 0.1) { // Near-black background
                    // Replace with a DARK shade of the foreground color
                    val fghslAlpha = 0.3f
                    val newHsl = floatArrayOf(fgHsl[0], fgHsl[1], fghslAlpha)
                    val newColor = ColorUtils.HSLToColor(newHsl)
                    pixels[i] = AndroidColor.argb(alpha, AndroidColor.red(newColor), AndroidColor.green(newColor), AndroidColor.blue(newColor))
                    continue // Move to the next pixel
                } else if (pixelLuminance > 0.90) { // Near-white background
                    // Replace with a LIGHT shade of the foreground color
                    val fghslAlpha = 0.7f
                    val newHsl = floatArrayOf(fgHsl[0], fgHsl[1], fghslAlpha)
                    val newColor = ColorUtils.HSLToColor(newHsl)
                    pixels[i] = AndroidColor.argb(alpha, AndroidColor.red(newColor), AndroidColor.green(newColor), AndroidColor.blue(newColor))
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

            pixels[i] = AndroidColor.argb(
                scaledAlpha,
                AndroidColor.red(newColor),
                AndroidColor.green(newColor),
                AndroidColor.blue(newColor)
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
    fun resizeBitmapWithAspectRatio(source: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val sourceWidth = source.width
        val sourceHeight = source.height

        if (sourceHeight <= maxHeight && sourceWidth <= maxWidth) {
            return source // No need to resize if it's already smaller
        }

        val ratio = sourceWidth.toFloat() / sourceHeight.toFloat()
        var targetWidth = maxWidth
        var targetHeight = maxHeight

        if (ratio > 1) { // Landscape
            targetHeight = (targetWidth / ratio).toInt()
        } else { // Portrait or square
            targetWidth = (targetHeight * ratio).toInt()
        }

        return source.scale(targetWidth, targetHeight)
    }
}