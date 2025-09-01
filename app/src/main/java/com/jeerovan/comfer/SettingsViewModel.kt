package com.jeerovan.comfer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.impl.utils.PREFERENCE_FILE_KEY
import com.google.android.datatransport.runtime.time.TimeModule_UptimeClockFactory
import com.jeerovan.comfer.utils.CommonUtil.setWallpaper
import io.ktor.http.cio.encodeChunked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val wallpaperMotionEnabled: Boolean = true,
    val wallpaperOnLockScreen: Boolean = false,
    val enhancedIcons : Boolean = true,
    val iconSize: Int = 48,
    val leftSwipeApp:String? = null,
    val rightSwipeApp:String? = null,
    val dateTimeColor:String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            val wallpaperMotion = PreferenceManager.getWallpaperMotion(getApplication())
            val wallpaperOnLockScreen = PreferenceManager.getWallpaperOnLockScreen(getApplication())
            val iconSize = PreferenceManager.getIconSize(getApplication())
            val enhancedIcons = PreferenceManager.getEnhancedIcons(getApplication(),true)
            val leftSwipeApp = PreferenceManager.getSwipeApp(getApplication(),"left")
            val rightSwipeApp = PreferenceManager.getSwipeApp(getApplication(),"right")
            val imageData = PreferenceManager.getImageData(getApplication())
            val dateTimeColor = imageData?.color
            _uiState.update {
                it.copy(
                    wallpaperMotionEnabled = wallpaperMotion,
                    wallpaperOnLockScreen = wallpaperOnLockScreen,
                    iconSize = iconSize,
                    leftSwipeApp = leftSwipeApp,
                    rightSwipeApp = rightSwipeApp,
                    enhancedIcons = enhancedIcons,
                    dateTimeColor = dateTimeColor
                )
            }
        }
    }

    fun setSwipeApp(swipeDirection:String, appName: String){
        viewModelScope.launch {
            PreferenceManager.setSwipeApp(getApplication(),swipeDirection,appName)
            if( swipeDirection == "left"){
                _uiState.update { it.copy( leftSwipeApp =  appName) }
            }
            if( swipeDirection == "right"){
                _uiState.update { it.copy( rightSwipeApp =  appName) }
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

    fun setEnhancedIcons(enabled: Boolean){
        viewModelScope.launch {
            PreferenceManager.setEnhancedIcons(getApplication(),enabled)
            _uiState.update { it.copy(enhancedIcons = enabled) }
        }
    }

    fun changeIconSize(increase: Boolean) {
        viewModelScope.launch {
            val currentSize = _uiState.value.iconSize
            val newSize = if (increase) {
                (currentSize + 4).coerceAtMost(56) // Max size 56
            } else {
                (currentSize - 4).coerceAtLeast(36) // Min size 36
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
}
