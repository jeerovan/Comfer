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
import androidx.core.app.NotificationManagerCompat



data class SettingsUiState(
    val wallpaperMotionEnabled: Boolean = true,
    val wallpaperOnLockScreen: Boolean = false,
    val iconSize: Int = 48,
    val iconShapeString: String? = "circle",
    val iconShape: Shape = CircleShape,
    val quickAppsLayout: String? = "linear",
    val leftSwipeApp:String? = null,
    val rightSwipeApp:String? = null,
    val isLeftSwipeWidgets: Boolean = false,
    val isRightSwipeWidgets: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val hasCustomWidgets: Boolean = false,
    val dateTimeColor:String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val logger = LoggerManager(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()
    init {
        loadSettings()
    }
    fun loadSettings() {
        logger.setLog("SettingsViewModel","LoadSettings")
        viewModelScope.launch {
            val wallpaperMotion = PreferenceManager.getWallpaperMotion(getApplication())
            val wallpaperOnLockScreen = PreferenceManager.getWallpaperOnLockScreen(getApplication())
            val iconSize = PreferenceManager.getIconSize(getApplication())
            val iconShapeString  = PreferenceManager.getIconShapeString(getApplication())
            val iconShape  = PreferenceManager.getIconShape(getApplication())
            val quickAppsLayout = PreferenceManager.getQuickAppsLayout(getApplication())
            val leftSwipeApp = PreferenceManager.getSwipeApp(getApplication(),"left")
            val rightSwipeApp = PreferenceManager.getSwipeApp(getApplication(),"right")
            val isLeftSwipeWidgets = PreferenceManager.getWidgetsOnSwipe(getApplication(),"left")
            val isRightSwipeWidgets = PreferenceManager.getWidgetsOnSwipe(getApplication(),"right")
            val imageData = PreferenceManager.getImageData(getApplication())
            val dateTimeColor = imageData?.color
            val isNotificationServiceEnabled = isNotificationServiceEnabled(getApplication())
            val hasCustomWidgets = PreferenceManager.getCustomWidgets(getApplication())
            _uiState.update {
                it.copy(
                    wallpaperMotionEnabled = wallpaperMotion,
                    wallpaperOnLockScreen = wallpaperOnLockScreen,
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
                    dateTimeColor = dateTimeColor
                )
            }
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

    fun changeDateTimeColor(white:Boolean){
        viewModelScope.launch {
            PreferenceManager.updateImageData(getApplication(),white)
            val newColor = if (white) "White" else "Black"
            _uiState.update { it.copy(dateTimeColor = newColor) }
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
