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
    val iconSize: Int = 48
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val wallpaperMotion = PreferenceManager.getWallpaperMotion(getApplication())
            val iconSize = PreferenceManager.getIconSize(getApplication())
            _uiState.update {
                it.copy(
                    wallpaperMotionEnabled = wallpaperMotion,
                    iconSize = iconSize
                )
            }
        }
    }

    fun setWallpaperMotion(enabled: Boolean) {
        viewModelScope.launch {
            PreferenceManager.setWallpaperMotion(getApplication(), enabled)
            _uiState.update { it.copy(wallpaperMotionEnabled = enabled) }
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
