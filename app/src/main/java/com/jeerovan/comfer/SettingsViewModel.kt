package com.jeerovan.comfer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val wallpaperMotionEnabled: Boolean = true,
    val wallpaperOnLockScreen: Boolean = false,
    val iconSize: Int = 48,
    val leftSwipeApp:String? = null,
    val rightSwipeApp:String? = null
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
            val leftSwipeApp = PreferenceManager.getSwipeApp(getApplication(),"left")
            val rightSwipeApp = PreferenceManager.getSwipeApp(getApplication(),"right")
            _uiState.update {
                it.copy(
                    wallpaperMotionEnabled = wallpaperMotion,
                    wallpaperOnLockScreen = wallpaperOnLockScreen,
                    iconSize = iconSize,
                    leftSwipeApp = leftSwipeApp,
                    rightSwipeApp = rightSwipeApp
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
        }
    }

    fun changeIconSize(increase: Boolean) {
        viewModelScope.launch {
            val currentSize = _uiState.value.iconSize
            val newSize = if (increase) {
                (currentSize + 4).coerceAtMost(64) // Max size 64
            } else {
                (currentSize - 4).coerceAtLeast(32) // Min size 32
            }
            if (newSize != currentSize) {
                PreferenceManager.setIconSize(getApplication(), newSize)
                _uiState.update { it.copy(iconSize = newSize) }
            }
        }
    }
}
