package com.jeerovan.comfer

import android.app.Application
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import android.provider.Settings
import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.jeerovan.comfer.ui.theme.fontProvider
import android.graphics.Bitmap
import androidx.datastore.preferences.core.edit
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class SettingsUiState(
    val hasPro: Boolean = false,
    val autoWallpapers: Boolean = false,
    val wallpaperMotionEnabled: Boolean = true,
    val wallpaperDirectory: String? = null,
    val wallpaperFrequency:String = "Hourly",
    val wallpaperOnLockScreen: Boolean = false,
    val monochrome: Boolean = false,
    val iconSize: Int = 48,
    val iconShapeString: String = "circle",
    val iconShape: Shape = CircleShape,
    val showThemedIcons: Boolean = false,
    val isLightHour: Boolean = false,
    val appListsUpdateCounter: Int = 0,
    val quickAppsLayout: String = "circular",
    val appDrawerLayout: String = "circular",
    val drawerHeight:Int = 0,
    val drawerOffset:Int = 0,
    val leftSwipeApp:AppInfo? = null,
    val rightSwipeApp:AppInfo? = null,
    val isLeftSwipeWidgets: Boolean = false,
    val isRightSwipeWidgets: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val hasCustomWidgets: Boolean = false,
    val widgetIds: List<String> = emptyList(),
    val widgetPositions: Map<String,Offset?> = emptyMap(),
    val patternApps: Map<String,AppInfo?> = emptyMap(),
    val showAnalog:Boolean = false,
    val clockSize: Int = 150,
    val clockBgColor: Color = Color.Black,
    val clockBgAlpha: Int = 40,
    val clockHourColor: Color = Color.White,
    val clockHourAlpha: Int = 100,
    val clockMinuteColor: Color = Color.White,
    val clockMinuteAlpha: Int = 100,
    val timeFormat: String = "H12",
    val timeFontSize: Int = 100,
    val timeFontName: String = "Roboto",
    val timeFontColor: Color = Color.White,
    val timeFontFamily: FontFamily = FontFamily.Default,
    val timeFontWeight: String = "Light",
    val timeFontAlpha: Int = 100,
    val timeLayoutId: Int = 1,
    val timeAngle: Int = 0,
    val timeRadius: Int = 0,
    val timeHasShadow: Boolean = false,
    val timeShadowColor: Color = Color.White,
    val dateFormat: String? = "EEE,MMM d",
    val dateFontSize: Int = 30,
    val dateFontName: String = "Roboto",
    val dateFontColor: Color = Color.White,
    val dateFontFamily: FontFamily = FontFamily.Default,
    val dateFontWeight: String = "Normal",
    val dateFontAlpha: Int = 100,
    val dateLayoutId: Int = 1,
    val dateAngle: Int = 0,
    val dateRadius: Int = 0,
    val dateHasShadow: Boolean = false,
    val dateShadowColor: Color = Color.Transparent,
    val showBatteryIcon: Boolean  = false,
    val batteryColor: Color = Color.White,
    val batteryAlpha: Int = 100,
    val showBatteryPercentage: Boolean = true,
    val batteryFontName: String = "Roboto",
    val batteryFontSize: Int = 20,
    val batteryFontFamily: FontFamily = FontFamily.Default,
    val batteryFontWeight: String = "Normal",
    val showNotificationRow : Boolean = true,
    val notificationColor: Color = Color.White,
    val notificationAlpha: Int = 100,
    val notificationSize: Int = 18,
    val notificationLayoutId: Int = 1,
    val arrangeInAlphabeticalOrder: Boolean = false,
    val shouldAppUpdatePromptUserCounter: Int = 0,
    val themedColors: WallpaperThemeColors? = null,
)

data class KeyTextObject(
    val text: String,
    val key: String
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()
    // const
    private val ANALOG_CLOCK = "analog_clock"
    private val CLOCK_BG_COLOR = "clock_bg_color"
    private val CLOCK_BG_ALPHA = "clock_bg_alpha"
    private val CLOCK_SIZE = "clock_size"
    private val CLOCK_HOUR_COLOR = "clock_hour_color"
    private val CLOCK_HOUR_ALPHA = "clock_hour_alpha"
    private val CLOCK_MINUTE_COLOR = "clock_minute_color"
    private val CLOCK_MINUTE_ALPHA = "clock_minute_alpha"
    private val TIME_FORMAT = "time_format"
    private val TIME_FONT_SIZE = "time_font_size"
    private val TIME_FONT_NAME = "time_font_name"
    private val TIME_FONT_COLOR = "time_font_color"
    private val TIME_FONT_ALPHA = "time_font_alpha"
    private val TIME_FONT_WEIGHT = "time_font_weight"
    private val TIME_LAYOUT_ID = "time_layout_id"
    private val TIME_ANGLE = "time_angle"
    private val TIME_RADIUS = "time_radius"
    private val TIME_HAS_SHADOW = "time_has_shadow"
    private val TIME_SHADOW_COLOR = "time_shadow_color"
    private val DATE_FONT_SIZE = "date_font_size"
    private val DATE_FONT_COLOR = "date_font_color"
    private val DATE_FONT_ALPHA = "date_font_alpha"
    private val DATE_FONT_NAME = "date_font_name"
    private val DATE_FONT_WEIGHT = "date_font_weight"
    private val DATE_LAYOUT_ID = "date_layout_id"
    private val DATE_ANGLE = "date_angle"
    private val DATE_RADIUS = "date_radius"
    private val DATE_HAS_SHADOW = "date_has_shadow"
    private val DATE_SHADOW_COLOR = "date_shadow_color"
    private val SHOW_BATTERY_ICON = "show_battery_icon"
    private val SHOW_BATTERY_PERCENTAGE = "show_battery_percentage"
    private val BATTERY_COLOR = "battery_color"
    private val BATTERY_ALPHA = "battery_alpha"
    private val BATTERY_FONT_SIZE = "battery_size"
    private val BATTERY_FONT_NAME = "battery_font_name"
    private val BATTERY_FONT_WEIGHT = "battery_font_weight"
    private val NOTIFICATION_COLOR = "notification_color"
    private val NOTIFICATION_ALPHA = "notification_alpha"
    private val SHOW_NOTIFICATIONS_ROW = "show_notifications_row"
    private val NOTIFICATION_SIZE = "notification_size"
    private val NOTIFICATION_LAYOUT_ID = "notification_layout_id"
    private val DRAWER_HEIGHT = "drawer_height"
    private val DRAWER_OFFSET = "drawer_offset"
    val predefinedColors = listOf(
        Color.Red, Color.Green, Color.Blue, Color.Yellow,
        Color.Cyan, Color.Magenta, Color.Black, Color.Gray,
        Color.White, Color(0xFF_FFA500), Color(0xFF_800080), Color(0xFF_008080)
    )

    val widgetIds = listOf("time", "date", "battery", "notifications")
    val patternIds = listOf("TopLeft","TopRight","BottomRight","BottomLeft","Center")
    fun getWidgetIds(showBatteryIcon:Boolean,
                     showBatteryPercentage:Boolean,
                     isNotificationServiceEnabled:Boolean,
                     showNotificationRow:Boolean

    ): List<String> {
        val widgetIds = mutableListOf("time", "date")
        if(showBatteryIcon || showBatteryPercentage){
            widgetIds.add("battery")
        }
        if (isNotificationServiceEnabled && showNotificationRow){
            widgetIds.add("notifications")
        }
        return widgetIds
    }
    init {
        loadSettings()
        setupPurchaseListener()
        checkSubscriptionStatus()
        viewModelScope.launch {
            application.dataStore.data
                .map { it[PreferenceKeys.WALLPAPER_UPDATE] ?: 0L }
                .distinctUntilChanged()
                .collect { timestamp ->
                    setThemedColors()
                }
        }
    }
    fun loadSettings() {
        Log.i("SettingsViewModel","LoadSettings")
        viewModelScope.launch {
            val hasPro = PreferenceManager.getPro(getApplication())
            val autoWallpapers = PreferenceManager.getAutoWallpapers(getApplication(),true)
            val monochrome = PreferenceManager.getMonochrome(getApplication(), default = false)
            val wallpaperMotion = PreferenceManager.getWallpaperMotion(getApplication())
            val wallpaperOnLockScreen = PreferenceManager.getWallpaperOnLockScreen(getApplication())
            val wallpaperDirectory = if(hasPro) PreferenceManager.getWallpaperDirectory(getApplication()) else null
            val wallpaperFrequency = PreferenceManager.getWallpaperFrequency(getApplication())
            val iconSize = PreferenceManager.getIconSize(getApplication())
            val iconShapeString  = PreferenceManager.getIconShapeString(getApplication())
            val iconShape  = PreferenceManager.getIconShape(getApplication())
            val showThemedIcons = PreferenceManager.getThemedIcons(getApplication())
            val isLightHour = PreferenceManager.isLightHour(getApplication())
            val appListUpdateCounter = PreferenceManager.getAppListUpdateCounter(getApplication())
            val quickAppsLayout = PreferenceManager.getQuickAppsLayout(getApplication())
            val appDrawerLayout = PreferenceManager.getAppDrawerLayout(getApplication())
            val drawerHeight = PreferenceManager.getInt(getApplication(),DRAWER_HEIGHT,0)
            val drawerOffset = PreferenceManager.getInt(getApplication(),DRAWER_OFFSET,0)
            val leftSwipeApp = mapPackageNameToAppInfo(
                getApplication(),
                PreferenceManager.getSwipeApp(getApplication(),"left"))
            val rightSwipeApp = mapPackageNameToAppInfo(
                getApplication(),
                PreferenceManager.getSwipeApp(getApplication(),"right"))
            val isLeftSwipeWidgets = PreferenceManager.getWidgetsOnSwipe(getApplication(),"left")
            val isRightSwipeWidgets = PreferenceManager.getWidgetsOnSwipe(getApplication(),"right")
            val isNotificationServiceEnabled = isNotificationServiceEnabled(getApplication())
            val hasCustomWidgets = if(hasPro)PreferenceManager.getCustomWidgets(getApplication()) else false
            val widgetPositions = widgetIds.associateWith { id ->
                loadWidgetPosition(id)
            }
            val patternApps = if(hasPro) patternIds.associateWith {id -> loadPatternApp(id)} else emptyMap()
            val showAnalog = if(hasPro)PreferenceManager.getBoolean(getApplication(),ANALOG_CLOCK,false) else false
            val clockSize = PreferenceManager.getInt(getApplication(),CLOCK_SIZE,150)
            val clockBgColor = Color(PreferenceManager.getInt(getApplication(),CLOCK_BG_COLOR,Color.Black.toArgb()))
            val clockBgAlpha = PreferenceManager.getInt(getApplication(),CLOCK_BG_ALPHA,40)
            val clockHourColor = Color(PreferenceManager.getInt(getApplication(),CLOCK_HOUR_COLOR,Color.White.toArgb()))
            val clockMinuteColor = Color(PreferenceManager.getInt(getApplication(),CLOCK_MINUTE_COLOR,Color.White.toArgb()))
            val timeFormat = PreferenceManager.getString(getApplication(),TIME_FORMAT, "H12") ?: "H12"
            val timeFontSize = PreferenceManager.getInt(getApplication(),TIME_FONT_SIZE,100)
            val timeFontColor = Color(PreferenceManager.getInt(getApplication(),TIME_FONT_COLOR,Color.White.toArgb()))
            val timeFontAlpha = PreferenceManager.getInt(getApplication(),TIME_FONT_ALPHA,100)
            val timeFontName = if(hasPro){
                PreferenceManager.getString(getApplication(),TIME_FONT_NAME,"Iter") ?: "Iter"
            } else {
                "Iter"
            }
            val timeFontFamily = try {
                FontFamily(
                    Font(
                        googleFont = GoogleFont(timeFontName),
                        fontProvider = fontProvider
                    )
                )
            } catch (_: Exception) {
                FontFamily.Default
            }
            val timeFontWeight = PreferenceManager.getString(getApplication(),TIME_FONT_WEIGHT,
                "Light") ?: "Light"
            val timeLayoutId = if(hasPro)PreferenceManager.getInt(getApplication(),TIME_LAYOUT_ID,1) else 1
            val timeAngle = if(hasPro)PreferenceManager.getInt(getApplication(),TIME_ANGLE,0) else 0
            val timeRadius = if(hasPro)PreferenceManager.getInt(getApplication(),TIME_RADIUS,0) else 0
            val timeHasShadow = if(hasPro)PreferenceManager.getBoolean(getApplication(),TIME_HAS_SHADOW,false) else false
            val timeShadowColor = Color(PreferenceManager.getInt(getApplication(),TIME_SHADOW_COLOR,Color.White.toArgb()))
            val dateFontSize = PreferenceManager.getInt(getApplication(),DATE_FONT_SIZE,30)
            val dateFontColor = Color(PreferenceManager.getInt(getApplication(),DATE_FONT_COLOR,Color.White.toArgb()))
            val dateFontAlpha = PreferenceManager.getInt(getApplication(),DATE_FONT_ALPHA,100)
            val dateFontName = if(hasPro){
                PreferenceManager.getString(getApplication(),DATE_FONT_NAME,"Iter") ?: "Iter"
            } else {
                "Iter"
            }
            val dateFontFamily = try {
                FontFamily(
                    Font(
                        googleFont = GoogleFont(dateFontName),
                        fontProvider = fontProvider
                    )
                )
            } catch (_: Exception) {
                FontFamily.Default
            }
            val dateFontWeight = PreferenceManager.getString(getApplication(),DATE_FONT_WEIGHT,"Normal") ?: "Normal"
            val dateLayoutId = if(hasPro)PreferenceManager.getInt(getApplication(),DATE_LAYOUT_ID,1) else 1
            val dateAngle = if(hasPro)PreferenceManager.getInt(getApplication(),DATE_ANGLE,0) else 0
            val dateRadius = if(hasPro)PreferenceManager.getInt(getApplication(),DATE_RADIUS,0) else 0
            val dateHasShadow = if(hasPro)PreferenceManager.getBoolean(getApplication(),DATE_HAS_SHADOW,false) else false
            val dateShadowColor = Color(PreferenceManager.getInt(getApplication(),DATE_SHADOW_COLOR,Color.White.toArgb()))

            val showBatteryIcon = PreferenceManager.getBoolean(getApplication(),SHOW_BATTERY_ICON,false)
            val batteryColor = Color(PreferenceManager.getInt(getApplication(),BATTERY_COLOR,Color.White.toArgb()))
            val batteryAlpha = PreferenceManager.getInt(getApplication(),BATTERY_ALPHA,100)
            val batteryFontSize = PreferenceManager.getInt(getApplication(),BATTERY_FONT_SIZE,20)
            val batteryFontName = if(hasPro){
                PreferenceManager.getString(getApplication(),BATTERY_FONT_NAME,"Iter") ?: "Iter"
            } else {
                "Iter"
            }
            val batteryFontFamily = try {
                FontFamily(
                    Font(
                        googleFont = GoogleFont(batteryFontName),
                        fontProvider = fontProvider
                    )
                )
            } catch (_: Exception) {
                FontFamily.Default
            }
            val batteryFontWeight = PreferenceManager.getString(getApplication(),BATTERY_FONT_WEIGHT,"Normal") ?: "Normal"
            val showBatteryPercentage = PreferenceManager.getBoolean(getApplication(),SHOW_BATTERY_PERCENTAGE,true)
            val notificationColor = Color(PreferenceManager.getInt(getApplication(),NOTIFICATION_COLOR,Color.White.toArgb()))
            val notificationAlpha = PreferenceManager.getInt(getApplication(),NOTIFICATION_ALPHA,100)
            val notificationSize = PreferenceManager.getInt(getApplication(),NOTIFICATION_SIZE,18)
            val notificationLayoutId = PreferenceManager.getInt(getApplication(),NOTIFICATION_LAYOUT_ID,1)
            val showNotificationRow = PreferenceManager.getBoolean(getApplication(),SHOW_NOTIFICATIONS_ROW,true)
            val widgetIds = getWidgetIds(showBatteryIcon,
                showBatteryPercentage,
                isNotificationServiceEnabled,
                showNotificationRow)
            val alphabeticalOrder = PreferenceManager.getAlphabeticalOrder(getApplication())
            val shouldAppUpdatePromptUserCounter = PreferenceManager.getAppUpdatePromptUserCounter(getApplication())
            val themedColors = PreferenceManager.getThemedColors(getApplication())
            _uiState.update {
                it.copy(
                    hasPro = hasPro,
                    autoWallpapers = autoWallpapers,
                    wallpaperMotionEnabled = wallpaperMotion,
                    wallpaperOnLockScreen = wallpaperOnLockScreen,
                    wallpaperDirectory = wallpaperDirectory,
                    wallpaperFrequency = wallpaperFrequency,
                    monochrome = monochrome,
                    iconSize = iconSize,
                    iconShape = iconShape,
                    showThemedIcons = showThemedIcons,
                    isLightHour = isLightHour,
                    appListsUpdateCounter = appListUpdateCounter,
                    quickAppsLayout = quickAppsLayout,
                    appDrawerLayout = appDrawerLayout,
                    drawerHeight = drawerHeight,
                    drawerOffset = drawerOffset,
                    iconShapeString =  iconShapeString,
                    leftSwipeApp = leftSwipeApp,
                    rightSwipeApp = rightSwipeApp,
                    isLeftSwipeWidgets = isLeftSwipeWidgets,
                    isRightSwipeWidgets = isRightSwipeWidgets,
                    patternApps = patternApps,
                    hasNotificationAccess = isNotificationServiceEnabled,
                    hasCustomWidgets =  hasCustomWidgets,
                    widgetIds = widgetIds,
                    widgetPositions = widgetPositions,
                    showAnalog = showAnalog,
                    clockSize = clockSize,
                    clockBgColor = clockBgColor,
                    clockBgAlpha = clockBgAlpha,
                    clockHourColor = clockHourColor,
                    clockMinuteColor = clockMinuteColor,
                    timeFormat =  timeFormat,
                    timeFontSize = timeFontSize,
                    timeFontColor = timeFontColor,
                    timeFontAlpha = timeFontAlpha,
                    timeFontName = timeFontName,
                    timeFontFamily = timeFontFamily,
                    timeFontWeight = timeFontWeight,
                    timeLayoutId = timeLayoutId,
                    timeAngle = timeAngle,
                    timeRadius = timeRadius,
                    timeHasShadow = timeHasShadow,
                    timeShadowColor = timeShadowColor,
                    dateFontSize = dateFontSize,
                    dateFontColor = dateFontColor,
                    dateFontAlpha = dateFontAlpha,
                    dateFontName = dateFontName,
                    dateFontFamily = dateFontFamily,
                    dateFontWeight = dateFontWeight,
                    dateLayoutId = dateLayoutId,
                    dateAngle = dateAngle,
                    dateRadius = dateRadius,
                    dateHasShadow = dateHasShadow,
                    dateShadowColor = dateShadowColor,
                    showBatteryIcon = showBatteryIcon,
                    batteryColor = batteryColor,
                    batteryAlpha = batteryAlpha,
                    batteryFontSize = batteryFontSize,
                    batteryFontName = batteryFontName,
                    batteryFontFamily = batteryFontFamily,
                    batteryFontWeight = batteryFontWeight,
                    showBatteryPercentage = showBatteryPercentage,
                    showNotificationRow = showNotificationRow,
                    notificationColor = notificationColor,
                    notificationAlpha = notificationAlpha,
                    notificationSize = notificationSize,
                    notificationLayoutId = notificationLayoutId,
                    arrangeInAlphabeticalOrder = alphabeticalOrder,
                    shouldAppUpdatePromptUserCounter = shouldAppUpdatePromptUserCounter,
                    themedColors = themedColors
                )
            }
        }
    }
    private fun setupPurchaseListener() {
        Purchases.sharedInstance.updatedCustomerInfoListener =
            UpdatedCustomerInfoListener { customerInfo ->
                processCustomerInfo(customerInfo)
            }
    }

    fun checkSubscriptionStatus() {
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { if(!isTesting)setPro(false) },
            onSuccess = { processCustomerInfo(it) }
        )
    }

    private fun processCustomerInfo(info: CustomerInfo) {
        val isPro = info.entitlements.active.isNotEmpty()
        if(!isTesting)setPro(isPro)
    }

    override fun onCleared() {
        super.onCleared()
        // Prevent memory leaks by removing listener
        Purchases.sharedInstance.updatedCustomerInfoListener = null
    }
    fun setPro(enabled:Boolean){
        viewModelScope.launch {
            Log.d("SettingsViewModel","SetPro:$enabled")
            //TODO set time-date pro settings
            PreferenceManager.setPro(getApplication(),enabled)
            val showAnalog = if(enabled)PreferenceManager.getBoolean(getApplication(),ANALOG_CLOCK,false) else false
            val timeFontName = if(enabled){
                PreferenceManager.getString(getApplication(),TIME_FONT_NAME,"Iter") ?: "Iter"
            } else {
                "Iter"
            }
            val timeFontFamily = try {
                FontFamily(
                    Font(
                        googleFont = GoogleFont(timeFontName),
                        fontProvider = fontProvider
                    )
                )
            } catch (_: Exception) {
                FontFamily.Default
            }
            val timeLayoutId = if (enabled){
                PreferenceManager.getInt(getApplication(),TIME_LAYOUT_ID,1)
            } else {
                1
            }
            val timeAngle = if (enabled){
                PreferenceManager.getInt(getApplication(),TIME_ANGLE,0)
            } else {
                0
            }
            val timeRadius = if (enabled) {
                PreferenceManager.getInt(getApplication(),TIME_RADIUS,0)
            } else {
                0
            }
            val timeHasShadow = if (enabled) {
                PreferenceManager.getBoolean(getApplication(),TIME_HAS_SHADOW,false)
            } else {
                false
            }
            val dateFontName = if(enabled){
                PreferenceManager.getString(getApplication(),DATE_FONT_NAME,"Iter") ?: "Iter"
            } else {
                "Iter"
            }
            val dateFontFamily = try {
                FontFamily(
                    Font(
                        googleFont = GoogleFont(dateFontName),
                        fontProvider = fontProvider
                    )
                )
            } catch (_: Exception) {
                FontFamily.Default
            }
            val dateLayoutId = if (enabled){
                PreferenceManager.getInt(getApplication(),DATE_LAYOUT_ID,1)
            } else {
                1
            }
            val dateAngle = if (enabled){
                PreferenceManager.getInt(getApplication(),DATE_ANGLE,0)
            } else {
                0
            }
            val dateRadius = if (enabled) {
                PreferenceManager.getInt(getApplication(),DATE_RADIUS,0)
            } else {
                0
            }
            val dateHasShadow = if (enabled) {
                PreferenceManager.getBoolean(getApplication(),DATE_HAS_SHADOW,false)
            } else {
                false
            }
            val batteryFontName = if(enabled){
                PreferenceManager.getString(getApplication(),BATTERY_FONT_NAME,"Iter") ?: "Iter"
            } else {
                "Iter"
            }
            val batteryFontFamily = try {
                FontFamily(
                    Font(
                        googleFont = GoogleFont(batteryFontName),
                        fontProvider = fontProvider
                    )
                )
            } catch (_: Exception) {
                FontFamily.Default
            }
            _uiState.update { it.copy(
                hasPro = enabled,
                showAnalog = showAnalog,
                timeFontFamily = timeFontFamily,
                timeLayoutId = timeLayoutId,
                timeAngle = timeAngle,
                timeRadius = timeRadius,
                timeHasShadow = timeHasShadow,
                dateFontFamily = dateFontFamily,
                dateLayoutId = dateLayoutId,
                dateAngle = dateAngle,
                dateRadius = dateRadius,
                dateHasShadow = dateHasShadow,
                batteryFontFamily = batteryFontFamily) }
        }
    }
    fun setThemedColors(){
        viewModelScope.launch {
            val themedColors = PreferenceManager.getThemedColors(getApplication())
            _uiState.update { it.copy(themedColors = themedColors) }
        }
    }
    fun setThemedIcons(enabled: Boolean){
        viewModelScope.launch {
            PreferenceManager.setThemedIcons(getApplication(),enabled)
            val leftSwipeApp = mapPackageNameToAppInfo(
                getApplication(),
                PreferenceManager.getSwipeApp(getApplication(),"left"))
            val rightSwipeApp = mapPackageNameToAppInfo(
                getApplication(),
                PreferenceManager.getSwipeApp(getApplication(),"right"))
            _uiState.update { it.copy(showThemedIcons = enabled, leftSwipeApp = leftSwipeApp, rightSwipeApp = rightSwipeApp) }
        }
    }
    fun setLightHour(enabled: Boolean){
        viewModelScope.launch {
            PreferenceManager.setLightHour(getApplication(),enabled)
            _uiState.update { it.copy(isLightHour = enabled) }
        }
    }
    fun setAlphabeticalOrder(enabled:Boolean){
        viewModelScope.launch {
            PreferenceManager.setAlphabeticalOrder(getApplication(),enabled)
            _uiState.update { it.copy(arrangeInAlphabeticalOrder = enabled) }
        }
    }
    fun saveWidgetPosition(id: String, offsetX: Float, offsetY: Float) {
        viewModelScope.launch {
            PreferenceManager.setFloat(getApplication(),"widget_${id}_x",offsetX)
            PreferenceManager.setFloat(getApplication(),"widget_${id}_y",offsetY)
            // Update state with new position
            _uiState.update { currentState ->
                val updatedPositions = currentState.widgetPositions.toMutableMap().apply {
                    this[id] = Offset(offsetX, offsetY)
                }
                currentState.copy(widgetPositions = updatedPositions)
            }
        }
    }
    fun loadWidgetPosition(id: String): Offset? {
        if(!hasWidgetPosition(id)){
            return null
        }
        return Offset(
            x = PreferenceManager.getFloat(getApplication(),"widget_${id}_x",0f),
            y = PreferenceManager.getFloat(getApplication(),"widget_${id}_y",0f)
        )
    }
    fun setPatternApp(id: String, app: String) {
        viewModelScope.launch {
            val patternKey = "Pattern_$id"
            PreferenceManager.setString(getApplication(),patternKey,app)
            val appInfo = withContext(Dispatchers.Default){
                mapPackageNameToAppInfo(getApplication(),app)
            }
            _uiState.update { currentState ->
                val updatedPatternApps = currentState.patternApps.toMutableMap().apply {
                    this[id] = appInfo
                }
                currentState.copy(patternApps = updatedPatternApps)
            }
        }
    }
    suspend fun loadPatternApp(id:String): AppInfo? {
        val patternKey = "Pattern_$id"
        val packageName = PreferenceManager.getString(getApplication(),patternKey,null)
        return withContext(Dispatchers.Default){
            mapPackageNameToAppInfo(getApplication(),packageName)
        }
    }
    fun clearAllWidgetPositions() {
        viewModelScope.launch {
            for (id in widgetIds){
                PreferenceManager.clear(getApplication(),"widget_${id}_x")
                PreferenceManager.clear(getApplication(),"widget_${id}_y")
            }
            _uiState.update {
                it.copy(widgetPositions = emptyMap())
            }
        }
    }

    fun hasWidgetPosition(id: String): Boolean {
        return PreferenceManager.hasKey(getApplication(),"widget_${id}_x")
    }
    fun setBatterySize(size: Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),BATTERY_FONT_SIZE,size)
            _uiState.update { it.copy(batteryFontSize = size) }
        }
    }
    fun setBatteryFontName(fontName: String) {
        viewModelScope.launch {
            // Save the new font name to preferences
            PreferenceManager.setString(getApplication(), BATTERY_FONT_NAME, fontName)

            // Create the new FontFamily
            val dateFontFamily = try {
                FontFamily(
                    Font(
                        googleFont = GoogleFont(fontName),
                        fontProvider = fontProvider
                    )
                )
            } catch (e: Exception) {
                Log.e("SetTimeFontName", e.toString())
                FontFamily.Default
            }

            // Update the UI state with the new font name and family
            _uiState.update {
                it.copy(
                    batteryFontName = fontName,
                    batteryFontFamily = dateFontFamily
                )
            }
        }
    }
    fun setBatteryFontWeight(style: String) {
        viewModelScope.launch {
            PreferenceManager.setString(getApplication(),BATTERY_FONT_WEIGHT,style)
            _uiState.update { it.copy(batteryFontWeight = style) }
        }
    }
    fun setNotificationSize(size: Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),NOTIFICATION_SIZE,size)
            _uiState.update { it.copy(notificationSize = size) }
        }
    }
    fun setNotificationLayoutId(id:Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),NOTIFICATION_LAYOUT_ID,id)
            _uiState.update { it.copy(notificationLayoutId = id) }
        }
    }
    fun setTimeFontColor(color: Color){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),TIME_FONT_COLOR,color.toArgb())
            _uiState.update { it.copy(timeFontColor = color) }
        }
    }
    fun setTimeFontAlpha(alpha: Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),TIME_FONT_ALPHA,alpha)
            _uiState.update { it.copy(timeFontAlpha = alpha) }
        }
    }
    fun setDateFontColor(color: Color){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),DATE_FONT_COLOR,color.toArgb())
            _uiState.update { it.copy(dateFontColor = color) }
        }
    }
    fun setDateFontAlpha(alpha: Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),DATE_FONT_ALPHA,alpha)
            _uiState.update { it.copy(dateFontAlpha = alpha) }
        }
    }
    fun setBatteryColor(color: Color){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),BATTERY_COLOR,color.toArgb())
            _uiState.update { it.copy(batteryColor = color) }
        }
    }
    fun setBatteryAlpha(alpha: Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),BATTERY_ALPHA,alpha)
            _uiState.update { it.copy(batteryAlpha = alpha) }
        }
    }
    fun setNotificationColor(color: Color){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),NOTIFICATION_COLOR,color.toArgb())
            _uiState.update { it.copy(notificationColor = color) }
        }
    }
    fun setNotificationAlpha(alpha: Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),NOTIFICATION_ALPHA,alpha)
            _uiState.update { it.copy(notificationAlpha = alpha) }
        }
    }
    fun setWallpaperFrequency(frequency: String){
        viewModelScope.launch {
            PreferenceManager.setWallpaperFrequency(getApplication(),frequency)
            _uiState.update { it.copy(wallpaperFrequency = frequency) }
        }
    }
    fun setWallpaperDirectory(directoryUri: String?){
        viewModelScope.launch {
            val context: Context = getApplication()
            PreferenceManager.setWallpaperDirectory(context,directoryUri)
            _uiState.update { it.copy(wallpaperDirectory = directoryUri) }
            if(directoryUri != null) {
                signalToChangeWallpaper()
            }
        }
    }
    fun showAnalog(show: Boolean){
        viewModelScope.launch {
            PreferenceManager.setBoolean(getApplication(),ANALOG_CLOCK,show)
            _uiState.update { it.copy(showAnalog = show) }
        }
    }
    fun setClockSize(size: Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),CLOCK_SIZE,size)
            _uiState.update { it.copy(clockSize = size) }
        }
    }
    fun setClockBgColor(color: Color){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),CLOCK_BG_COLOR,color.toArgb())
            _uiState.update { it.copy(clockBgColor = color) }
        }
    }
    fun setClockBgAlpha(alpha: Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),CLOCK_BG_ALPHA,alpha)
            _uiState.update { it.copy(clockBgAlpha = alpha) }
        }
    }
    fun setClockHourColor(color: Color){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),CLOCK_HOUR_COLOR,color.toArgb())
            _uiState.update { it.copy(clockHourColor = color) }
        }
    }
    fun setClockHourAlpha(alpha: Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),CLOCK_HOUR_ALPHA,alpha)
            _uiState.update { it.copy(clockHourAlpha = alpha) }
        }
    }
    fun setClockMinuteColor(color: Color){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),CLOCK_MINUTE_COLOR,color.toArgb())
            _uiState.update { it.copy(clockMinuteColor = color) }
        }
    }
    fun setClockMinuteAlpha(alpha: Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),CLOCK_MINUTE_ALPHA,alpha)
            _uiState.update { it.copy(clockMinuteAlpha = alpha) }
        }
    }
    fun setTimeFormat(format: String) {
        viewModelScope.launch {
            PreferenceManager.setString(getApplication(),TIME_FORMAT,format)
            _uiState.update { it.copy(timeFormat = format) }
        }
    }

    fun setTimeFontSize(size: Int) {
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),TIME_FONT_SIZE,size)
            _uiState.update { it.copy(timeFontSize = size) }
        }
    }

    fun setTimeFontName(fontName: String) {
        viewModelScope.launch {
            // Save the new font name to preferences
            PreferenceManager.setString(getApplication(), TIME_FONT_NAME, fontName)

            // Create the new FontFamily
            val timeFontFamily = try {
                FontFamily(
                    Font(
                        googleFont = GoogleFont(fontName),
                        fontProvider = fontProvider
                    )
                )
            } catch (e: Exception) {
                Log.e("SetTimeFontName",e.toString())
                FontFamily.Default
            }

            // Update the UI state with the new font name and family
            _uiState.update {
                it.copy(
                    timeFontName = fontName,
                    timeFontFamily = timeFontFamily
                )
            }
        }
    }

    fun setTimeFontWeight(style: String) {
        viewModelScope.launch {
            PreferenceManager.setString(getApplication(),TIME_FONT_WEIGHT,style)
            _uiState.update { it.copy(timeFontWeight = style) }
        }
    }
    fun setTimeLayoutId(id:Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),TIME_LAYOUT_ID,id)
            _uiState.update { it.copy(timeLayoutId = id) }
        }
    }
    fun setTimeAngle(value:Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),TIME_ANGLE,value)
            _uiState.update { it.copy(timeAngle = value) }
        }
    }
    fun setTimeRadius(value:Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),TIME_RADIUS,value)
            _uiState.update { it.copy(timeRadius = value) }
        }
    }
    fun setTimeHasShadow(has:Boolean){
        viewModelScope.launch {
            PreferenceManager.setBoolean(getApplication(),TIME_HAS_SHADOW,has)
            _uiState.update { it.copy(timeHasShadow = has) }
        }
    }
    fun setTimeShadow(color:Color){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),TIME_SHADOW_COLOR,color.toArgb())
            _uiState.update { it.copy(timeShadowColor = color) }
        }
    }

    fun setDateFontSize(size: Int) {
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),DATE_FONT_SIZE,size)
            _uiState.update { it.copy(dateFontSize = size) }
        }
    }

    fun setDateFontName(fontName: String) {
        viewModelScope.launch {
            // Save the new font name to preferences
            PreferenceManager.setString(getApplication(), DATE_FONT_NAME, fontName)

            // Create the new FontFamily
            val dateFontFamily = try {
                FontFamily(
                    Font(
                        googleFont = GoogleFont(fontName),
                        fontProvider = fontProvider
                    )
                )
            } catch (e: Exception) {
                Log.e("SetTimeFontName", e.toString())
                FontFamily.Default
            }

            // Update the UI state with the new font name and family
            _uiState.update {
                it.copy(
                    dateFontName = fontName,
                    dateFontFamily = dateFontFamily
                )
            }
        }
    }
    fun setDateFontWeight(style: String) {
        viewModelScope.launch {
            PreferenceManager.setString(getApplication(),DATE_FONT_WEIGHT,style)
            _uiState.update { it.copy(dateFontWeight = style) }
        }
    }
    fun setDateLayoutId(id:Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),DATE_LAYOUT_ID,id)
            _uiState.update { it.copy(dateLayoutId = id) }
        }
    }
    fun setDateAngle(value:Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),DATE_ANGLE,value)
            _uiState.update { it.copy(dateAngle = value) }
        }
    }
    fun setDateRadius(value:Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),DATE_RADIUS,value)
            _uiState.update { it.copy(dateRadius = value) }
        }
    }
    fun setDateHasShadow(has:Boolean){
        viewModelScope.launch {
            PreferenceManager.setBoolean(getApplication(),DATE_HAS_SHADOW,has)
            _uiState.update { it.copy(dateHasShadow = has) }
        }
    }
    fun setDateShadow(color:Color){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),DATE_SHADOW_COLOR,color.toArgb())
            _uiState.update { it.copy(dateShadowColor = color) }
        }
    }

    fun setShowBatteryIcon(show: Boolean) {
        viewModelScope.launch {
            PreferenceManager.setBoolean(getApplication(),SHOW_BATTERY_ICON,show)
            val isNotificationServiceEnabled = _uiState.value.hasNotificationAccess
            val showBatteryPercentage = _uiState.value.showBatteryPercentage
            val showNotificationRow = _uiState.value.showNotificationRow
            val widgetIds = getWidgetIds(show,
                showBatteryPercentage,isNotificationServiceEnabled,showNotificationRow)
            _uiState.update { it.copy(showBatteryIcon = show, widgetIds = widgetIds) }
        }
    }

    fun setShowBatteryPercentage(show: Boolean) {
        viewModelScope.launch {
            PreferenceManager.setBoolean(getApplication(),SHOW_BATTERY_PERCENTAGE,show)
            val showBatteryIcon = _uiState.value.showBatteryIcon
            val isNotificationServiceEnabled = _uiState.value.hasNotificationAccess
            val showNotificationRow = _uiState.value.showNotificationRow
            val widgetIds = getWidgetIds(showBatteryIcon,
                show,isNotificationServiceEnabled,showNotificationRow)
            _uiState.update { it.copy(showBatteryPercentage = show, widgetIds = widgetIds) }
        }
    }

    fun setShowNotificationRow(show: Boolean) {
        viewModelScope.launch {
            PreferenceManager.setBoolean(getApplication(),SHOW_NOTIFICATIONS_ROW,show)
            val showBatteryIcon = _uiState.value.showBatteryIcon
            val showBatteryPercentage = _uiState.value.showBatteryPercentage
            val isNotificationServiceEnabled = _uiState.value.hasNotificationAccess
            val widgetIds = getWidgetIds(showBatteryIcon,
                showBatteryPercentage,isNotificationServiceEnabled,show)
            _uiState.update { it.copy(showNotificationRow = show, widgetIds = widgetIds) }
        }
    }

    fun setCustomWidgets(enabled: Boolean){
        viewModelScope.launch {
            PreferenceManager.setCustomWidgets(getApplication(),enabled)
            _uiState.update { it.copy(hasCustomWidgets = enabled) }
        }
    }
    fun setQuickAppsLayout(layout:String){
        viewModelScope.launch {
            PreferenceManager.setQuickAppsLayout(getApplication(),layout)
            _uiState.update { it.copy(quickAppsLayout = layout) }
        }
    }
    fun setAppDrawerLayout(layout:String){
        viewModelScope.launch {
            PreferenceManager.setAppDrawerLayout(getApplication(),layout)
            _uiState.update { it.copy(appDrawerLayout = layout) }
        }
    }
    fun setDrawerHeight(height:Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),DRAWER_HEIGHT,height)
            _uiState.update { it.copy(drawerHeight = height) }
        }
    }
    fun setDrawerOffset(offset:Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),DRAWER_OFFSET,offset)
            _uiState.update { it.copy(drawerOffset = offset) }
        }
    }
    fun setSwipeApp(swipeDirection:String, appName: String){
        viewModelScope.launch {
            PreferenceManager.setSwipeApp(getApplication(),swipeDirection,appName)
            PreferenceManager.setWidgetsOnSwipe(getApplication(),swipeDirection,false)
            val swipeApp = mapPackageNameToAppInfo(
                getApplication(),
                PreferenceManager.getSwipeApp(getApplication(),swipeDirection))
            if( swipeDirection == "left"){
                _uiState.update { it.copy( leftSwipeApp =  swipeApp, isLeftSwipeWidgets = false) }
            }
            if( swipeDirection == "right"){
                _uiState.update { it.copy( rightSwipeApp =  swipeApp, isRightSwipeWidgets = false) }
            }
        }
    }

    fun setWidgetsOnSwipe(swipeDirection: String) {
        viewModelScope.launch {
            PreferenceManager.setWidgetsOnSwipe(getApplication(),swipeDirection,true)
            PreferenceManager.setSwipeApp(getApplication(),swipeDirection,null)
            if( swipeDirection == "left"){
                _uiState.update { it.copy( isLeftSwipeWidgets = true, leftSwipeApp = null) }
            }
            if( swipeDirection == "right"){
                _uiState.update { it.copy( isRightSwipeWidgets = true, rightSwipeApp = null) }
            }
        }
    }
    fun setAutoWallpapers(enabled: Boolean) {
        viewModelScope.launch {
            PreferenceManager.setAutoWallpapers(getApplication(),enabled)
            if(enabled){
                setMonochrome(false)
            }
            _uiState.update { it.copy(autoWallpapers = enabled) }
        }
    }
    fun setWallpaperMotion(enabled: Boolean) {
        viewModelScope.launch {
            PreferenceManager.setWallpaperMotion(getApplication(), enabled)
            _uiState.update { it.copy(wallpaperMotionEnabled = enabled) }
        }
    }
    fun setWallpaperOnLockScreen(enabled: Boolean) {
        viewModelScope.launch {
            val context: Context = getApplication()
            PreferenceManager.setWallpaperOnLockScreen(context, enabled)
            _uiState.update { it.copy(wallpaperOnLockScreen = enabled) }
            if(enabled){
                signalToChangeWallpaper()
            }
        }
    }
    fun setMonochrome(enabled: Boolean) {
        viewModelScope.launch {
            val context:Context = getApplication()
            PreferenceManager.setMonochrome(context,enabled)
            _uiState.update { it.copy(monochrome = enabled) }
            if(enabled) {
                withContext(Dispatchers.IO) {
                    generateMonochromeColorWallpapers(context)
                }
            }
            // signal to loadBackgroundData
            context.dataStore.edit { preferences ->
                preferences[PreferenceKeys.WALLPAPER_UPDATE] = System.currentTimeMillis()
            }
        }
    }
    fun setIconShape(shapeString: String){
        viewModelScope.launch {
            PreferenceManager.setIconShape(getApplication(),shapeString)
            val iconShape = getShapeFromString(shapeString)
            _uiState.update { it.copy(iconShapeString = shapeString, iconShape = iconShape) }
        }
    }

    fun changeIconSize(increase: Boolean) {
        viewModelScope.launch {
            val currentSize = _uiState.value.iconSize
            val newSize = if (increase) {
                (currentSize + 4).coerceAtMost(56) // Max size 56
            } else {
                (currentSize - 4).coerceAtLeast(40) // Min size 40
            }
            if (newSize != currentSize) {
                PreferenceManager.setIconSize(getApplication(), newSize)
                _uiState.update { it.copy(iconSize = newSize) }
            }
        }
    }

    // Function to check if the notification listener permission is enabled
    fun isNotificationServiceEnabled(context: Context): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledListeners.contains(context.packageName)
    }
    fun requestNotificationPermission(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        context.startActivity(intent)
    }
    fun signalToChangeWallpaper(){
        viewModelScope.launch {
            val context:Context = getApplication()
            context.dataStore.edit { preferences ->
                preferences[PreferenceKeys.WALLPAPER_CHANGE] = System.currentTimeMillis()
            }
        }
    }
    fun generateMonochromeColorWallpapers(context: Context) {
        // 1. Define the files
        val fileBlack = File(context.filesDir, "comfer_black.jpg")
        val fileWhite = File(context.filesDir, "comfer_white.jpg")
        if(fileBlack.exists() && fileWhite.exists()) return
        // 2. Create a helper function to save a solid color
        // We use 100x100 for efficiency; it scales perfectly as a solid color.
        fun saveColorToFile(file: File, color: Color) {
            try {
                // Create a mutable bitmap
                val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

                // Fill it with the specified color
                bitmap.eraseColor(color.toArgb())

                // Save to file
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }

                // Recycle to free native memory immediately
                bitmap.recycle()

            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        // 3. Generate and save the Black image
        saveColorToFile(fileBlack, Color.Black)

        // 4. Generate and save the White image
        saveColorToFile(fileWhite, Color.White)
    }

}
