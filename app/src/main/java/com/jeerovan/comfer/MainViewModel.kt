package com.jeerovan.comfer

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeerovan.comfer.utils.CommonUtil.downloadImage
import com.jeerovan.comfer.utils.CommonUtil.fetchImageData
import com.jeerovan.comfer.utils.CommonUtil.isDefaultLauncher
import com.jeerovan.comfer.utils.CommonUtil.reloadWallpaper
import com.jeerovan.comfer.utils.CommonUtil.setWallpaper
import com.jeerovan.comfer.utils.CommonUtil.setWallpaperThemedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MainUiState (
    val imagePath:String? = null,
    val iconVersion:Int = 0,
    val isDefaultLauncher: Boolean = false
)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()
    private var backgroundLoadJob: Job? = null
    private var wallpaperChangeJob: Job? = null
    private val _backPressEvent = MutableSharedFlow<Unit>()
    val backPressEvent = _backPressEvent.asSharedFlow()
    fun onBackButtonPressed() {
        viewModelScope.launch {
            _backPressEvent.emit(Unit)
        }
    }

    init {
        // Observer for Wallpaper Updates
        viewModelScope.launch {
            application.dataStore.data
                .map { it[PreferenceKeys.WALLPAPER_UPDATE] ?: 0L }
                .distinctUntilChanged() // Critical: ignore unrelated DataStore updates
                .collect { timestamp ->
                    Log.d("MainViewModel", "Wallpaper Updated At: $timestamp")
                    loadBackgroundData()
                }
        }

        // Observer for Wallpaper Changes
        viewModelScope.launch {
            application.dataStore.data
                .map { it[PreferenceKeys.WALLPAPER_CHANGE] ?: 0L }
                .distinctUntilChanged() // Critical: ignore unrelated DataStore updates
                .collect { timestamp ->
                    if(timestamp > 0L) {
                        Log.d("MainViewModel", "Wallpaper Changed At: $timestamp")
                        changeWallpaper()
                    }
                }
        }
        // Observer for Wallpaper Changes
        viewModelScope.launch {
            application.dataStore.data
                .map { it[PreferenceKeys.WALLPAPER_RESET] ?: 0L }
                .distinctUntilChanged() // Critical: ignore unrelated DataStore updates
                .collect { timestamp ->
                    if(timestamp > 0L) {
                        Log.d("MainViewModel", "Wallpaper Reset At: $timestamp")
                        reapplyWallpaper()
                    }
                }
        }
    }
    fun reloadImagePath() { // reloads after screen turns on
        viewModelScope.launch {
            val context: Context = getApplication()
            val backgroundImage = PreferenceManager.getBackgroundImagePath(context)
            if(_uiState.value.imagePath == null) {
                _uiState.update { it.copy(imagePath = backgroundImage) }
            }
            PreferenceManager.setWallpaperApplied(context, true)
            val isDefaultLauncher = isDefaultLauncher(context)
            _uiState.update {
                it.copy(isDefaultLauncher = isDefaultLauncher)
            }
            if (isDefaultLauncher){
                val appliedWallpaperImage = PreferenceManager.getAppliedWallpaperImage(context)
                if (appliedWallpaperImage != backgroundImage){
                    reapplyWallpaper()
                }
            }
        }
    }
    fun clearImagePath() { // unloads on screen off
        viewModelScope.launch {
            _uiState.update { it.copy(imagePath = null) }
        }
    }
    fun loadBackgroundData(){
        if(backgroundLoadJob?.isActive == true) return
        backgroundLoadJob = viewModelScope.launch {
            try {
                val applicationContext: Application = getApplication()
                val imageData = PreferenceManager.getImageData(applicationContext)
                val backgroundImagePath = PreferenceManager.getBackgroundImagePath(applicationContext)
                if (imageData == null || backgroundImagePath == null) {
                    withContext(Dispatchers.IO) {
                        fetchImageData(applicationContext)
                    }
                    delay(500)
                    withContext(Dispatchers.IO) {
                        downloadImage(applicationContext)
                    }
                    delay(500)
                    // update uiState
                    val imageData = PreferenceManager.getImageData(applicationContext)
                    val filePath = PreferenceManager.getBackgroundImagePath(applicationContext)
                    if (imageData != null && filePath != null) {
                        withContext(Dispatchers.IO){
                            val imageFile = File(filePath)
                            setWallpaperThemedColors(applicationContext, imageFile)
                            // check if current image was already set as wallpaper
                            if (PreferenceManager.getAppliedWallpaperImage(applicationContext) != filePath){
                                setWallpaper(applicationContext)
                            }
                        }
                        // let themed colors generate before we change the background
                        _uiState.update {
                            it.copy(
                                imagePath = filePath
                            )
                        }
                    }
                    PreferenceManager.setWallpaperApplied(applicationContext, true)
                } else {
                    if (_uiState.value.imagePath != backgroundImagePath) {
                        _uiState.update {
                            it.copy(
                                imagePath = backgroundImagePath,
                                iconVersion = _uiState.value.iconVersion + 1
                            )
                        }
                    }
                }
            }
            catch (e: Exception){
                Log.e("MainViewModel",e.toString())
            }
        }
    }
    fun reapplyWallpaper(){
        viewModelScope.launch {
            reloadWallpaper(getApplication())
        }
    }
    fun changeWallpaper(){
        if(wallpaperChangeJob?.isActive == true) return
        wallpaperChangeJob = viewModelScope.launch {
            val context:Context = getApplication()
            try {
                delay(100)
                withContext(Dispatchers.IO) {
                    fetchImageData(context, manualChange = true)
                }
                delay(100)
                withContext(Dispatchers.IO) {
                    downloadImage(context)
                }
            }
            catch (e: Exception){
                Log.e("MainViewModel",e.toString())
            }
        }
    }
}