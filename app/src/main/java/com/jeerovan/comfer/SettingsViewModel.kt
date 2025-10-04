package com.jeerovan.comfer

import android.app.Application
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromString
import com.jeerovan.comfer.utils.CommonUtil.setWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import android.provider.Settings
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.core.net.toUri
import com.jeerovan.comfer.ui.theme.fontProvider
import com.jeerovan.comfer.utils.CommonUtil.setBackgroundImageFromImageUri

data class SettingsUiState(
    val wallpaperMotionEnabled: Boolean = true,
    val wallpaperDirectory: String? = null,
    val wallpaperFrequency:String = "Hourly",
    val wallpaperOnLockScreen: Boolean = false,
    val iconSize: Int = 48,
    val iconShapeString: String = "circle",
    val iconShape: Shape = CircleShape,
    val quickAppsLayout: String = "linear",
    val leftSwipeApp:String? = null,
    val rightSwipeApp:String? = null,
    val isLeftSwipeWidgets: Boolean = false,
    val isRightSwipeWidgets: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val hasCustomWidgets: Boolean = false,
    val showAnalog:Boolean = false,
    val clockSize: Int = 150,
    val clockBgColor: Color = Color.Black,
    val clockBgAlpha: Float = 0.6f,
    val clockHourColor: Color = Color.White,
    val clockMinuteColor: Color = Color.White,
    val timeFormat: String = "H12",
    val showAmPm: Boolean = true,
    val timeFontSize: Int = 60,
    val timeFontName: String = "Roboto",
    val timeFontColor: Color = Color.White,
    val timeFontFamily: FontFamily = FontFamily.Default,
    val timeFontWeight: String = "Light",
    val dateFormat: String? = "EEE,MMM d",
    val dateFontSize: Int = 20,
    val dateFontName: String = "Roboto",
    val dateFontColor: Color = Color.White,
    val dateFontFamily: FontFamily = FontFamily.Default,
    val dateFontWeight: String = "Normal",
    val showBatteryIcon: Boolean  = true,
    val batteryColor: Color = Color.White,
    val showBatteryPercentage: Boolean = true,
    val batterySize: Int = 20,
    val showNotificationRow : Boolean = true,
    val notificationColor: Color = Color.White,
    val notificationSize: Int = 18
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val logger = LoggerManager(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    // const
    private val ANALOG_CLOCK = "analog_clock"
    private val CLOCK_BG_COLOR = "clock_bg_color"
    private val CLOCK_BG_ALPHA = "clock_bg_alpha"
    private val CLOCK_SIZE = "clock_size"
    private val CLOCK_HOUR_COLOR = "clock_hour_color"
    private val CLOCK_MINUTE_COLOR = "clock_minute_color"
    private val TIME_FORMAT = "time_format"
    private val SHOW_AM_PM = "show_am_pm"
    private val TIME_FONT_SIZE = "time_font_size"
    private val TIME_FONT_NAME = "time_font_name"
    private val TIME_FONT_COLOR = "time_font_color"
    private val TIME_FONT_WEIGHT = "time_font_weight"
    private val DATE_FONT_SIZE = "date_font_size"
    private val DATE_FONT_COLOR = "date_font_color"
    private val DATE_FONT_NAME = "date_font_name"
    private val DATE_FONT_WEIGHT = "date_font_weight"
    private val SHOW_BATTERY_ICON = "show_battery_icon"
    private val SHOW_BATTERY_PERCENTAGE = "show_battery_percentage"
    private val BATTERY_COLOR = "battery_color"
    private val BATTERY_SIZE = "battery_size"
    private val NOTIFICATION_COLOR = "notification_color"
    private val SHOW_NOTIFICATIONS_ROW = "show_notifications_row"
    private val NOTIFICATION_SIZE = "notification_size"

    private var working = false
    val predefinedColors = listOf(
        Color.Red, Color.Green, Color.Blue, Color.Yellow,
        Color.Cyan, Color.Magenta, Color.Black, Color.Gray,
        Color.White, Color(0xFF_FFA500), Color(0xFF_800080), Color(0xFF_008080)
    )

    init {
        loadSettings()
    }
    fun loadSettings() {
        if(working)return
        working = true
        logger.setLog("SettingsViewModel","LoadSettings")
        viewModelScope.launch {
            val wallpaperMotion = PreferenceManager.getWallpaperMotion(getApplication())
            val wallpaperOnLockScreen = PreferenceManager.getWallpaperOnLockScreen(getApplication())
            val wallpaperDirectory = PreferenceManager.getWallpaperDirectory(getApplication())
            val wallpaperFrequency = PreferenceManager.getWallpaperFrequency(getApplication())
            val iconSize = PreferenceManager.getIconSize(getApplication())
            val iconShapeString  = PreferenceManager.getIconShapeString(getApplication())
            val iconShape  = PreferenceManager.getIconShape(getApplication())
            val quickAppsLayout = PreferenceManager.getQuickAppsLayout(getApplication())
            val leftSwipeApp = PreferenceManager.getSwipeApp(getApplication(),"left")
            val rightSwipeApp = PreferenceManager.getSwipeApp(getApplication(),"right")
            val isLeftSwipeWidgets = PreferenceManager.getWidgetsOnSwipe(getApplication(),"left")
            val isRightSwipeWidgets = PreferenceManager.getWidgetsOnSwipe(getApplication(),"right")
            val isNotificationServiceEnabled = isNotificationServiceEnabled(getApplication())
            val hasCustomWidgets = PreferenceManager.getCustomWidgets(getApplication())
            val showAnalog = PreferenceManager.getBoolean(getApplication(),ANALOG_CLOCK,false)
            val clockSize = PreferenceManager.getInt(getApplication(),CLOCK_SIZE,150)
            val clockBgColor = Color(PreferenceManager.getInt(getApplication(),CLOCK_BG_COLOR,Color.Black.toArgb()))
            val clockBgAlpha = PreferenceManager.getInt(getApplication(),CLOCK_BG_ALPHA,70) / 100f
            val clockHourColor = Color(PreferenceManager.getInt(getApplication(),CLOCK_HOUR_COLOR,Color.White.toArgb()))
            val clockMinuteColor = Color(PreferenceManager.getInt(getApplication(),CLOCK_MINUTE_COLOR,Color.White.toArgb()))
            val timeFormat = PreferenceManager.getString(getApplication(),TIME_FORMAT, "H12") ?: "H12"
            val showAmPm = PreferenceManager.getBoolean(getApplication(),SHOW_AM_PM,true)
            val timeFontSize = PreferenceManager.getInt(getApplication(),TIME_FONT_SIZE,60)
            val timeFontColor = Color(PreferenceManager.getInt(getApplication(),TIME_FONT_COLOR,Color.White.toArgb()))
            val timeFontName = PreferenceManager.getString(getApplication(),TIME_FONT_NAME,"Roboto") ?: "Roboto"
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
            val dateFontSize = PreferenceManager.getInt(getApplication(),DATE_FONT_SIZE,20)
            val dateFontColor = Color(PreferenceManager.getInt(getApplication(),DATE_FONT_COLOR,Color.White.toArgb()))
            val dateFontName = PreferenceManager.getString(getApplication(),DATE_FONT_NAME,"Roboto") ?: "Roboto"
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
            val showBatteryIcon = PreferenceManager.getBoolean(getApplication(),SHOW_BATTERY_ICON,true)
            val batteryColor = Color(PreferenceManager.getInt(getApplication(),BATTERY_COLOR,Color.White.toArgb()))
            val batterySize = PreferenceManager.getInt(getApplication(),BATTERY_SIZE,20)
            val showBatteryPercentage = PreferenceManager.getBoolean(getApplication(),SHOW_BATTERY_PERCENTAGE,true)
            val notificationColor = Color(PreferenceManager.getInt(getApplication(),NOTIFICATION_COLOR,Color.White.toArgb()))
            val notificationSize = PreferenceManager.getInt(getApplication(),NOTIFICATION_SIZE,18)
            val showNotificationRow = PreferenceManager.getBoolean(getApplication(),SHOW_NOTIFICATIONS_ROW,true)
            _uiState.update {
                it.copy(
                    wallpaperMotionEnabled = wallpaperMotion,
                    wallpaperOnLockScreen = wallpaperOnLockScreen,
                    wallpaperDirectory = wallpaperDirectory,
                    wallpaperFrequency = wallpaperFrequency,
                    iconSize = iconSize,
                    iconShape = iconShape,
                    quickAppsLayout = quickAppsLayout,
                    iconShapeString =  iconShapeString,
                    leftSwipeApp = leftSwipeApp,
                    rightSwipeApp = rightSwipeApp,
                    isLeftSwipeWidgets = isLeftSwipeWidgets,
                    isRightSwipeWidgets = isRightSwipeWidgets,
                    hasNotificationAccess = isNotificationServiceEnabled,
                    hasCustomWidgets =  hasCustomWidgets,
                    showAnalog = showAnalog,
                    clockSize = clockSize,
                    clockBgColor = clockBgColor,
                    clockBgAlpha = clockBgAlpha,
                    clockHourColor = clockHourColor,
                    clockMinuteColor = clockMinuteColor,
                    timeFormat =  timeFormat,
                    showAmPm = showAmPm,
                    timeFontSize = timeFontSize,
                    timeFontColor = timeFontColor,
                    timeFontName = timeFontName,
                    timeFontFamily = timeFontFamily,
                    timeFontWeight = timeFontWeight,
                    dateFontSize = dateFontSize,
                    dateFontColor = dateFontColor,
                    dateFontName = dateFontName,
                    dateFontFamily = dateFontFamily,
                    dateFontWeight = dateFontWeight,
                    showBatteryIcon = showBatteryIcon,
                    batteryColor = batteryColor,
                    batterySize = batterySize,
                    showBatteryPercentage = showBatteryPercentage,
                    showNotificationRow = showNotificationRow,
                    notificationColor = notificationColor,
                    notificationSize = notificationSize
                )
            }
            working = false
        }
    }
    fun setBatterySize(size: Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),BATTERY_SIZE,size)
            _uiState.update { it.copy(batterySize = size) }
        }
    }
    fun setNotificationSize(size: Int){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),NOTIFICATION_SIZE,size)
            _uiState.update { it.copy(notificationSize = size) }
        }
    }
    fun setTimeFontColor(color: Color){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),TIME_FONT_COLOR,color.toArgb())
            _uiState.update { it.copy(timeFontColor = color) }
        }
    }
    fun setDateFontColor(color: Color){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),DATE_FONT_COLOR,color.toArgb())
            _uiState.update { it.copy(dateFontColor = color) }
        }
    }
    fun setBatteryColor(color: Color){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),BATTERY_COLOR,color.toArgb())
            _uiState.update { it.copy(batteryColor = color) }
        }
    }
    fun setNotificationColor(color: Color){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),NOTIFICATION_COLOR,color.toArgb())
            _uiState.update { it.copy(notificationColor = color) }
        }
    }
    fun setWallpaperFrequency(frequency:String){
        viewModelScope.launch {
            PreferenceManager.setWallpaperFrequency(getApplication(),frequency)
            _uiState.update { it.copy(wallpaperFrequency = frequency) }
        }
    }
    fun setWallpaperDirectory(directoryUri: String?){
        viewModelScope.launch {
            PreferenceManager.setWallpaperDirectory(getApplication(),directoryUri)
            _uiState.update { it.copy(wallpaperDirectory = directoryUri) }
            if(directoryUri != null) {
                withContext(Dispatchers.IO) {
                    setBackgroundImageFromImageUri(getApplication(),directoryUri.toUri())
                }
                PreferenceManager.setApplyWallpaperNow(getApplication(),true)
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
            _uiState.update { it.copy(clockBgAlpha = alpha/100f) }
        }
    }
    fun setClockHourColor(color: Color){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),CLOCK_HOUR_COLOR,color.toArgb())
            _uiState.update { it.copy(clockHourColor = color) }
        }
    }
    fun setClockMinuteColor(color: Color){
        viewModelScope.launch {
            PreferenceManager.setInt(getApplication(),CLOCK_MINUTE_COLOR,color.toArgb())
            _uiState.update { it.copy(clockMinuteColor = color) }
        }
    }
    fun setTimeFormat(format: String) {
        viewModelScope.launch {
            PreferenceManager.setString(getApplication(),TIME_FORMAT,format)
            _uiState.update { it.copy(timeFormat = format) }
        }
    }

    fun setShowAmPm(show: Boolean) {
        viewModelScope.launch {
            PreferenceManager.setBoolean(getApplication(),SHOW_AM_PM,show)
            _uiState.update { it.copy(showAmPm = show) }
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
                Log.e("SetTimeFontName",e.toString())
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

    fun setShowBatteryIcon(show: Boolean) {
        viewModelScope.launch {
            PreferenceManager.setBoolean(getApplication(),SHOW_BATTERY_ICON,show)
            _uiState.update { it.copy(showBatteryIcon = show) }
        }
    }

    fun setShowBatteryPercentage(show: Boolean) {
        viewModelScope.launch {
            PreferenceManager.setBoolean(getApplication(),SHOW_BATTERY_PERCENTAGE,show)
            _uiState.update { it.copy(showBatteryPercentage = show) }
        }
    }

    fun setShowNotificationRow(show: Boolean) {
        viewModelScope.launch {
            PreferenceManager.setBoolean(getApplication(),SHOW_NOTIFICATIONS_ROW,show)
            _uiState.update { it.copy(showNotificationRow = show) }
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
    fun setSwipeApp(swipeDirection:String, appName: String){
        viewModelScope.launch {
            PreferenceManager.setSwipeApp(getApplication(),swipeDirection,appName)
            PreferenceManager.setWidgetsOnSwipe(getApplication(),swipeDirection,false)
            if( swipeDirection == "left"){
                _uiState.update { it.copy( leftSwipeApp =  appName, isLeftSwipeWidgets = false) }
            }
            if( swipeDirection == "right"){
                _uiState.update { it.copy( rightSwipeApp =  appName, isRightSwipeWidgets = false) }
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

    fun setWallpaperMotion(enabled: Boolean) {
        viewModelScope.launch {
            PreferenceManager.setWallpaperMotion(getApplication(), enabled)
            _uiState.update { it.copy(wallpaperMotionEnabled = enabled) }
        }
    }

    fun setWallpaperOnLockScreen(enabled: Boolean) {
        viewModelScope.launch {
            PreferenceManager.setWallpaperOnLockScreen(getApplication(), enabled)
            _uiState.update { it.copy(wallpaperOnLockScreen = enabled) }
            if(enabled){
                withContext(Dispatchers.IO){
                    setWallpaper(getApplication())
                }
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
}
